-- ============================================================================
-- Student Codex migration 0009 — PHASE 5 / 6-READY / 9 COMPLETION
--
-- Covers the remaining database contracts of the build plan:
--   * Phase 9 community completion: image-only posts allowed (text OR
--     photo posts), database-enforced rate limiting (no Edge layer exists
--     yet, so the guards live here — the strongest place anyway), and
--     feed views extended with author display names, liked-by-me flags and
--     image paths so the app can render a post with one query.
--   * Phase 6-ready: google_connections is the connection STATE registry.
--     OAuth tokens NEVER live here (server-side Edge Function store only,
--     plan Phase 6) — this table only records status/last-sync so the app
--     can show honest connection state today and the sync can fill it in
--     when the Classroom integration is enabled.
--   * Phase 5: upsert_activity_from_source() is the trusted, atomic,
--     retry-safe import path every Classroom sync call will use
--     (idempotent by the unique source_key; converges instead of
--     duplicating). Service-role only.
--   * Phase 11: section_workload_daily gives the teacher dashboard its
--     aggregate workload source (security_invoker — RLS keeps personal
--     student activities private from teachers).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Phase 9: allow photo-only posts. The 1..5000 check becomes 0..5000; the
-- requirement "text or at least one image" is enforced by the client (the
-- database cannot see uncommitted image rows during post insert). A
-- contentless, imageless post is harmless to render; the app never creates
-- one.
-- ---------------------------------------------------------------------------
alter table public.posts drop constraint if exists posts_content_check;
alter table public.posts add constraint posts_content_len_check
  check (char_length(content) <= 5000);

-- ---------------------------------------------------------------------------
-- Phase 9: database-enforced rate limiting (upload/validation + anti-spam).
-- SECURITY DEFINER with pinned search_path so the count sees the author's
-- rows regardless of the caller's RLS context.
--   * posts:        max 8 per rolling 10 minutes
--   * comments:     max 30 per rolling 10 minutes
--   * content_reports: max 10 per rolling hour
-- Trusted service context (no JWT) is exempt — the future moderation/sync
-- tooling is rate-limited at its own layer.
-- ---------------------------------------------------------------------------
create or replace function public.guard_post_insert_rate() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
  recent integer;
begin
  if caller is null then
    return new; -- trusted service context
  end if;
  select count(*) into recent
  from public.posts p
  where p.author_id = caller
    and p.created_at > now() - interval '10 minutes';
  if recent >= 8 then
    raise exception 'posting too quickly — try again in a few minutes';
  end if;
  return new;
end $$;

create trigger posts_guard_insert_rate before insert on public.posts
  for each row execute function public.guard_post_insert_rate();

create or replace function public.guard_comment_insert_rate() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
  recent integer;
begin
  if caller is null then
    return new; -- trusted service context
  end if;
  select count(*) into recent
  from public.comments c
  where c.author_id = caller
    and c.created_at > now() - interval '10 minutes';
  if recent >= 30 then
    raise exception 'commenting too quickly — try again in a few minutes';
  end if;
  return new;
end $$;

create trigger comments_guard_insert_rate before insert on public.comments
  for each row execute function public.guard_comment_insert_rate();

create or replace function public.guard_content_report_insert_rate() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
  recent integer;
begin
  if caller is null then
    return new; -- trusted service context
  end if;
  select count(*) into recent
  from public.content_reports r
  where r.reporter_id = caller
    and r.created_at > now() - interval '1 hour';
  if recent >= 10 then
    raise exception 'too many reports already filed this hour';
  end if;
  return new;
end $$;

create trigger content_reports_guard_insert_rate before insert on public.content_reports
  for each row execute function public.guard_content_report_insert_rate();

-- ---------------------------------------------------------------------------
-- Phase 6-ready: google_connections — connection STATE registry only.
--
-- REFRESH/ACCESS TOKENS ARE NEVER STORED HERE. The plan (Phase 6) requires
-- tokens to live only in a server-side, client-inaccessible store inside
-- the Edge Function runtime. When the Classroom integration is enabled,
-- the Edge Function writes status/last-sync rows here; the Android app
-- reads them to render the connection card and can request a disconnect.
-- ---------------------------------------------------------------------------
create table if not exists public.google_connections (
  user_id uuid primary key references auth.users (id) on delete cascade,
  google_email text,
  status text not null default 'disconnected'
    check (status in ('connected', 'disconnected', 'revoked', 'expired')),
  last_sync_at timestamptz,
  last_sync_status text check (last_sync_status in ('ok', 'partial', 'failed')),
  last_sync_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger google_connections_set_updated_at before update on public.google_connections
  for each row execute function public.set_updated_at();

-- Guard: the OWNER may only disconnect (status -> 'disconnected'). Every
-- other field (google_email, last_sync_*) is service-managed. The trusted
-- service context (no JWT) may write anything.
create or replace function public.guard_google_connection_update() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
begin
  if caller is null or caller <> new.user_id then
    return new; -- trusted service context
  end if;
  if new.user_id is distinct from old.user_id then
    raise exception 'google_connections.user_id is immutable';
  end if;
  if new.google_email is distinct from old.google_email
     or new.last_sync_at is distinct from old.last_sync_at
     or new.last_sync_status is distinct from old.last_sync_status
     or new.last_sync_error is distinct from old.last_sync_error then
    raise exception 'connection details are managed by the sync service';
  end if;
  if new.status is distinct from old.status and new.status <> 'disconnected' then
    raise exception 'you can only disconnect a Google account';
  end if;
  return new;
end $$;

create trigger google_connections_guard_update before update on public.google_connections
  for each row execute function public.guard_google_connection_update();

alter table public.google_connections enable row level security;

create policy google_connections_select_own on public.google_connections
  for select to authenticated
  using (user_id = auth.uid());

-- The connect/reconnect flow and token exchange are Edge Function work
-- (Phase 6, not enabled yet). The only client write today is the owner
-- requesting a disconnect; inserts are service-only.
create policy google_connections_update_own on public.google_connections
  for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());
-- no client insert/delete policies: rows are created/removed by the service

-- ---------------------------------------------------------------------------
-- Phase 5: the trusted, atomic, retry-safe provider import used by every
-- future Classroom sync run. Idempotent by the unique source_key: a
-- repeated or retried sync of the same coursework item converges to ONE
-- source row and ONE canonical activity (dedup rule 1) and refreshes its
-- content instead of duplicating it.
--
-- Service-role ONLY (revoke from public/authenticated, guard rejects any
-- JWT caller): provider imports are a trusted server-side operation.
-- ---------------------------------------------------------------------------
create or replace function public.upsert_activity_from_source(
  p_source_key text,
  p_provider text,
  p_external_course_id text,
  p_external_item_id text,
  p_requesting_user_id uuid,
  p_title text,
  p_description text,
  p_activity_type text,
  p_course_id uuid,
  p_section_id uuid,
  p_due_at timestamptz,
  p_estimated_effort_minutes integer,
  p_scope text,
  p_source_meta jsonb
) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  existing_activity_id uuid;
begin
  if auth.uid() is not null then
    raise exception 'upsert_activity_from_source is a service-only operation';
  end if;
  if p_provider <> 'google_classroom' then
    raise exception 'this upsert path is for provider imports only';
  end if;

  select s.activity_id into existing_activity_id
  from public.activity_sources s
  where s.source_key = p_source_key;

  if existing_activity_id is not null then
    -- Retry/repeat import: refresh the canonical activity + evidence.
    update public.activities a
      set title = p_title,
          description = p_description,
          activity_type = p_activity_type,
          course_id = p_course_id,
          section_id = p_section_id,
          due_at = p_due_at,
          estimated_effort_minutes = p_estimated_effort_minutes,
          scope = p_scope,
          updated_at = now()
      where a.id = existing_activity_id;

    update public.activity_sources s
      set source_meta = p_source_meta,
          updated_at = now()
      where s.source_key = p_source_key;

    return existing_activity_id;
  end if;

  -- First import of this provider item: create the canonical activity.
  insert into public.activities (
    title, description, activity_type, course_id, section_id,
    due_at, estimated_effort_minutes, scope, verification_status,
    created_by_user_id
  ) values (
    p_title, p_description, p_activity_type, p_course_id, p_section_id,
    p_due_at, p_estimated_effort_minutes, p_scope, 'imported',
    p_requesting_user_id
  )
  returning id into existing_activity_id;

  insert into public.activity_sources (
    activity_id, provider, external_course_id, external_item_id,
    source_key, reported_by_user_id, source_meta
  ) values (
    existing_activity_id, p_provider, p_external_course_id, p_external_item_id,
    p_source_key, p_requesting_user_id, p_source_meta
  );

  return existing_activity_id;
end $$;

revoke execute on function public.upsert_activity_from_source(
  text, text, text, text, uuid, text, text, text, uuid, uuid, timestamptz, integer, text, jsonb
) from public;
grant execute on function public.upsert_activity_from_source(
  text, text, text, text, uuid, text, text, text, uuid, uuid, timestamptz, integer, text, jsonb
) to service_role;

-- ---------------------------------------------------------------------------
-- Phase 9: feed views extended for the real app: author display names
-- (anonymous stays anonymous — author_id is nulled BEFORE the join, so a
-- name can never leak), liked-by-me, and ordered image paths (up to 3).
-- Recreated (drop + create) because column additions must go last and the
-- name columns belong beside author_id.
-- ---------------------------------------------------------------------------
drop view if exists public.feed_posts;
drop view if exists public.feed_comments;

create view public.feed_posts with (security_invoker = true) as
select
  p.id,
  p.content,
  p.is_anonymous,
  case when p.is_anonymous then null else p.author_id end as author_id,
  pr.first_name || ' ' || pr.last_name as author_name,
  p.created_at,
  p.edited_at,
  (select count(*) from public.post_likes pl where pl.post_id = p.id) as like_count,
  (select count(*) from public.comments c where c.post_id = p.id and c.deleted_at is null) as comment_count,
  (select count(*) from public.post_images pi where pi.post_id = p.id) as image_count,
  exists (
    select 1 from public.post_likes pl
    where pl.post_id = p.id and pl.user_id = auth.uid()
  ) as liked_by_me,
  (
    select coalesce(jsonb_agg(pi.storage_path order by pi.position), '[]'::jsonb)
    from public.post_images pi
    where pi.post_id = p.id
  ) as image_paths
from public.posts p
left join public.profiles pr
  on pr.id = case when p.is_anonymous then null else p.author_id end
where p.deleted_at is null;

-- My own posts (Profile page), including my ANONYMOUS ones — the author
-- always sees their own history. auth.uid() evaluates per requesting user,
-- so this view can never read another student's anonymous posts.
create view public.my_posts with (security_invoker = true) as
select
  p.id,
  p.content,
  p.is_anonymous,
  p.author_id,
  pr.first_name || ' ' || pr.last_name as author_name,
  p.created_at,
  p.edited_at,
  (select count(*) from public.post_likes pl where pl.post_id = p.id) as like_count,
  (select count(*) from public.comments c where c.post_id = p.id and c.deleted_at is null) as comment_count,
  (select count(*) from public.post_images pi where pi.post_id = p.id) as image_count,
  exists (
    select 1 from public.post_likes pl
    where pl.post_id = p.id and pl.user_id = p.author_id
  ) as liked_by_me,
  (
    select coalesce(jsonb_agg(pi.storage_path order by pi.position), '[]'::jsonb)
    from public.post_images pi
    where pi.post_id = p.id
  ) as image_paths
from public.posts p
left join public.profiles pr on pr.id = p.author_id
where p.deleted_at is null
  and p.author_id = auth.uid();

create view public.feed_comments with (security_invoker = true) as
select
  c.id,
  c.post_id,
  c.parent_comment_id,
  c.content,
  c.is_anonymous,
  case when c.is_anonymous then null else c.author_id end as author_id,
  pr.first_name || ' ' || pr.last_name as author_name,
  c.created_at,
  c.edited_at,
  (select count(*) from public.comment_likes cl where cl.comment_id = c.id) as like_count,
  exists (
    select 1 from public.comment_likes cl
    where cl.comment_id = c.id and cl.user_id = auth.uid()
  ) as liked_by_me
from public.comments c
left join public.profiles pr
  on pr.id = case when c.is_anonymous then null else c.author_id end
where c.deleted_at is null;

comment on view public.feed_posts is
  'Public community feed. Anonymous posts never expose author identity; RLS applies to the selecting user (security_invoker).';
comment on view public.my_posts is
  'The requesting user''s own posts (anonymous included — the author sees their own history only).';
comment on view public.feed_comments is
  'Public comment thread view. Anonymous comments never expose author identity; RLS applies to the selecting user (security_invoker).';

-- ---------------------------------------------------------------------------
-- Phase 11: teacher workload aggregate. security_invoker keeps activities
-- RLS active for the querying teacher: only section-scoped activities of
-- sections the teacher belongs to are counted; personal student activities
-- are invisible here BY DESIGN (privacy rule: teachers see aggregate
-- workload of class records, never named personal histories).
-- ---------------------------------------------------------------------------
create or replace view public.section_workload_daily with (security_invoker = true) as
select
  a.section_id,
  s.course_id,
  c.code as course_code,
  c.title as course_title,
  s.name as section_name,
  a.due_at::date as due_date,
  count(*)::integer as activity_count,
  coalesce(sum(a.estimated_effort_minutes), 0)::integer as total_effort_minutes
from public.activities a
join public.sections s on s.id = a.section_id
join public.courses c on c.id = s.course_id
where a.deleted_at is null
  and a.scope = 'section'
  and a.due_at is not null
group by a.section_id, s.course_id, c.code, c.title, s.name, a.due_at::date;
