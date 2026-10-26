-- ============================================================================
-- Student Codex migration 0003 — ACTIVITY BEACON CORE
-- activities, activity_sources, activity_reports, activity_groups,
-- activity_group_members, activity_participants.
--
-- Design principles (plan §4):
--   * The canonical activity is what calendars/workload count — sources,
--     reports and sync attempts are evidence, never extra workload rows.
--   * Google Classroom identity is unique per provider item (source_key)
--     so repeated imports cannot duplicate activities (dedup rule 1).
--   * A student report is NOT automatically a confirmed class-wide
--     assignment (dedup rule 2/3): reports stay separate evidence with
--     their own status workflow.
--   * Personal activities stay private unless explicitly shared
--     (privacy rule 5).
-- ============================================================================

create table if not exists public.activities (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 200),
  description text,
  activity_type text not null default 'other'
    check (activity_type in ('assignment', 'quiz', 'exam', 'project', 'announcement', 'other')),
  course_id uuid references public.courses (id) on delete set null,
  section_id uuid references public.sections (id) on delete set null,
  due_at timestamptz,
  estimated_effort_minutes integer check (estimated_effort_minutes between 1 and 10080),
  scope text not null default 'personal'
    check (scope in ('personal', 'section', 'course')),
  verification_status text not null default 'student-reported'
    check (verification_status in ('imported', 'teacher-confirmed', 'student-reported', 'needs-review')),
  created_by_user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint activities_scope_section_requires_section
    check (scope <> 'section' or section_id is not null),
  constraint activities_scope_course_requires_course
    check (scope <> 'course' or course_id is not null)
);

create index if not exists activities_due_at_idx on public.activities (due_at)
  where deleted_at is null;
create index if not exists activities_section_idx on public.activities (section_id);
create index if not exists activities_course_idx on public.activities (course_id);
create index if not exists activities_creator_idx on public.activities (created_by_user_id);

-- ---------------------------------------------------------------------------
-- activity_sources — evidence about where an activity came from.
-- provider-owned records carry a unique source_key:
--   google_classroom:{courseId}:{courseWorkId}
--   google_classroom_announcement:{courseId}:{announcementId}
-- Many students syncing the same Classroom item must resolve to ONE source
-- row (unique constraint below).
-- ---------------------------------------------------------------------------
create table if not exists public.activity_sources (
  id uuid primary key default gen_random_uuid(),
  activity_id uuid not null references public.activities (id) on delete cascade,
  provider text not null check (provider in ('google_classroom', 'student_manual', 'teacher_manual')),
  external_course_id text,
  external_item_id text,
  source_key text,
  reported_by_user_id uuid references auth.users (id) on delete set null,
  source_meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists activity_sources_source_key_key
  on public.activity_sources (source_key) where source_key is not null;
create unique index if not exists activity_sources_provider_external_key
  on public.activity_sources (provider, external_course_id, external_item_id)
  where external_item_id is not null;
create index if not exists activity_sources_activity_idx on public.activity_sources (activity_id);

-- ---------------------------------------------------------------------------
-- activity_reports — student claims that an unlisted (usually face-to-face)
-- activity exists. Not automatically a confirmed class-wide assignment.
-- ---------------------------------------------------------------------------
create table if not exists public.activity_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_user_id uuid not null references auth.users (id) on delete cascade,
  activity_id uuid references public.activities (id) on delete set null,
  title text not null check (char_length(title) between 1 and 200),
  due_at timestamptz,
  course_id uuid references public.courses (id) on delete set null,
  section_id uuid references public.sections (id) on delete set null,
  notes text,
  status text not null default 'pending'
    check (status in ('pending', 'matched', 'confirmed', 'rejected', 'needs_review', 'withdrawn')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists activity_reports_reporter_idx on public.activity_reports (reporter_user_id);
create index if not exists activity_reports_activity_idx on public.activity_reports (activity_id);

-- ---------------------------------------------------------------------------
-- activity_groups — optional grouping of section-specific activities that
-- represent the same underlying assignment. Section-level records keep
-- their own id/source/deadline; grouping is organisational only (dedup
-- rule 4).
-- ---------------------------------------------------------------------------
create table if not exists public.activity_groups (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 200),
  created_by_user_id uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists public.activity_group_members (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.activity_groups (id) on delete cascade,
  activity_id uuid not null references public.activities (id) on delete cascade,
  added_at timestamptz not null default now(),
  unique (group_id, activity_id)
);

-- ---------------------------------------------------------------------------
-- activity_participants — explicit audience model: a personal activity can
-- be shared with specific students; a class activity can target selected
-- students. We never assume an activity affects every student in a section.
-- ---------------------------------------------------------------------------
create table if not exists public.activity_participants (
  id uuid primary key default gen_random_uuid(),
  activity_id uuid not null references public.activities (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  added_at timestamptz not null default now(),
  unique (activity_id, user_id)
);

-- ---------------------------------------------------------------------------
-- Visibility helper — single source of truth for "who can see an activity",
-- used by the activities policy itself and by sources/reports/policies so
-- there is exactly one definition of activity visibility (privacy rule 5).
-- SECURITY DEFINER with pinned search_path: the internal reads bypass table
-- RLS (owner context), avoiding recursive policy evaluation.
-- ---------------------------------------------------------------------------
create or replace function public.activity_visible_to_user(target_activity uuid, viewer uuid)
returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
    from public.activities a
    where a.id = target_activity
      and a.deleted_at is null
      and (
        a.created_by_user_id = viewer
        or exists (
          select 1 from public.activity_participants ap
          where ap.activity_id = a.id and ap.user_id = viewer
        )
        or (
          a.scope = 'section'
          and a.section_id is not null
          and exists (
            select 1 from public.enrollments e
            where e.section_id = a.section_id and e.user_id = viewer
          )
        )
        or (
          a.scope = 'course'
          and a.course_id is not null
          and exists (
            select 1
            from public.enrollments e
            join public.sections s on s.id = e.section_id
            where s.course_id = a.course_id and e.user_id = viewer
          )
        )
      )
  )
$$;

revoke execute on function public.activity_visible_to_user(uuid, uuid) from public;
grant execute on function public.activity_visible_to_user(uuid, uuid) to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Guard: activity integrity on insert.
--   * callers WITHOUT a JWT (auth.uid() is null) are the trusted service
--     context (Edge Functions / service role). This is safe because every
--     client-facing policy on this table is scoped `to authenticated`, so
--     an anonymous connection is rejected at the policy layer anyway.
--   * verification_status 'imported' is reserved for the sync service.
--   * verification_status 'teacher-confirmed' requires the caller to be a
--     teacher of the activity's section/course (or an admin).
-- ---------------------------------------------------------------------------
create or replace function public.guard_activity_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
begin
  if caller is null then
    return new; -- trusted service context
  end if;
  if new.verification_status = 'imported' then
    raise exception 'verification_status ''imported'' is reserved for the sync service';
  end if;
  if new.verification_status = 'teacher-confirmed'
     and not (
       public.is_section_teacher(new.section_id)
       or public.is_course_teacher(new.course_id)
       or public.has_role('admin')
     ) then
    raise exception 'verification_status ''teacher-confirmed'' requires a teacher of the section/course or an admin';
  end if;
  return new;
end $$;

create trigger activities_guard_insert before insert on public.activities
  for each row execute function public.guard_activity_insert();

-- ---------------------------------------------------------------------------
-- Guard: activity integrity on update.
--   * created_by_user_id is immutable.
--   * escalating to 'teacher-confirmed'/'imported' is restricted the same
--     way as on insert (a student must not be able to self-verify).
-- ---------------------------------------------------------------------------
create or replace function public.guard_activity_update() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
begin
  if new.created_by_user_id is distinct from old.created_by_user_id then
    raise exception 'activities.created_by_user_id is immutable';
  end if;
  if caller is null then
    return new; -- trusted service context
  end if;
  if new.verification_status is distinct from old.verification_status
     and new.verification_status in ('teacher-confirmed', 'imported') then
    if not (
      public.is_section_teacher(new.section_id)
      or public.is_section_teacher(old.section_id)
      or public.is_course_teacher(new.course_id)
      or public.is_course_teacher(old.course_id)
      or public.has_role('admin')
    ) then
      raise exception 'verification_status ''%'' can only be set by a teacher of the section/course or an admin', new.verification_status;
    end if;
  end if;
  return new;
end $$;

create trigger activities_guard_update before update on public.activities
  for each row execute function public.guard_activity_update();

create trigger activities_set_updated_at before update on public.activities
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Guard: sources integrity.
--   * google_classroom sources are created by the trusted sync only.
-- ---------------------------------------------------------------------------
create or replace function public.guard_activity_source_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.provider = 'google_classroom' and auth.uid() is not null then
    raise exception 'google_classroom sources are created by the sync service only';
  end if;
  return new;
end $$;

create trigger activity_sources_guard_insert before insert on public.activity_sources
  for each row execute function public.guard_activity_source_insert();

create trigger activity_sources_set_updated_at before update on public.activity_sources
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Guard: report status workflow.
--   * reporter_user_id is immutable.
--   * the reporter may withdraw their own report (from pending/needs_review)
--     but cannot confirm/reject/match it; teachers of the related section
--     and admins may drive the workflow.
-- ---------------------------------------------------------------------------
create or replace function public.guard_activity_report_update() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  is_staff boolean;
  caller uuid := auth.uid();
begin
  if new.reporter_user_id is distinct from old.reporter_user_id then
    raise exception 'activity_reports.reporter_user_id is immutable';
  end if;
  if caller is null then
    return new; -- trusted service context
  end if;
  is_staff :=
    public.has_role('admin')
    or public.is_section_teacher(old.section_id)
    or public.is_section_teacher(new.section_id)
    or exists (
      select 1 from public.activities a
      where a.id = old.activity_id and public.is_section_teacher(a.section_id)
    )
    or exists (
      select 1 from public.activities a
      where a.id = new.activity_id and public.is_section_teacher(a.section_id)
    );
  if not is_staff then
    if new.status is distinct from old.status and new.status <> 'withdrawn' then
      raise exception 'only teachers or admins can change a report status';
    end if;
    if old.status in ('confirmed', 'rejected') then
      raise exception 'report is closed';
    end if;
    if new.activity_id is distinct from old.activity_id then
      raise exception 'only teachers or admins can link a report to an activity';
    end if;
  end if;
  return new;
end $$;

create trigger activity_reports_guard_update before update on public.activity_reports
  for each row execute function public.guard_activity_report_update();

create trigger activity_reports_set_updated_at before update on public.activity_reports
  for each row execute function public.set_updated_at();

-- ===========================================================================
-- RLS
-- ===========================================================================

-- activities -----------------------------------------------------------------
alter table public.activities enable row level security;

-- IMPORTANT DESIGN NOTE: this policy is intentionally expressed with
-- row-local columns and EXISTS subqueries on OTHER tables. It must NOT call
-- activity_visible_to_user(), because that helper re-queries the activities
-- table and STABLE functions see the statement-start snapshot: a row inserted
-- by INSERT ... RETURNING (what PostgREST/supabase clients use) would be
-- invisible to the helper, so the SELECT-policy check on the RETURNING row
-- would fail and every client insert would be rejected. The helper is safe
-- for policies on OTHER tables, where the referenced activity is already
-- committed.
create policy activities_select on public.activities
  for select to authenticated
  using (
    -- NOTE: for UPDATE, PostgreSQL enforces SELECT policies' USING as a
    -- WITH CHECK on the new row — the updated row must remain visible.
    -- Soft-deleted rows therefore stay visible ONLY to their creator so
    -- authors can soft-delete their own activities; everyone else (and the
    -- feed) never sees them. The service (no JWT) bypasses RLS entirely.
    (deleted_at is null or created_by_user_id = auth.uid())
    and (
      created_by_user_id = auth.uid()
      or exists (
        select 1 from public.activity_participants ap
        where ap.activity_id = activities.id
          and ap.user_id = auth.uid()
      )
      or (
        scope = 'section'
        and exists (
          select 1 from public.enrollments e
          where e.section_id = activities.section_id
            and e.user_id = auth.uid()
        )
      )
      or (
        scope = 'course'
        and exists (
          select 1
          from public.enrollments e
          join public.sections s on s.id = e.section_id
          where s.course_id = activities.course_id
            and e.user_id = auth.uid()
        )
      )
    )
  );

-- Students create personal activities (their own face-to-face entries);
-- teachers may additionally create section/course-scoped activities.
create policy activities_insert on public.activities
  for insert to authenticated
  with check (
    created_by_user_id = auth.uid()
    and (
      scope = 'personal'
      or (scope = 'section' and public.is_section_teacher(section_id))
      or (scope = 'course' and public.is_course_teacher(course_id))
    )
    -- verification/created_by escalation is enforced by guard triggers
  );

-- Owner (personal), teacher of the section/course, admin.
create policy activities_update on public.activities
  for update to authenticated
  using (
    (scope = 'personal' and created_by_user_id = auth.uid())
    or public.is_section_teacher(section_id)
    or public.is_course_teacher(course_id)
    or public.has_role('admin')
  )
  with check (
    (scope = 'personal' and created_by_user_id = auth.uid())
    or public.is_section_teacher(section_id)
    or public.is_course_teacher(course_id)
    or public.has_role('admin')
  );

create policy activities_delete on public.activities
  for delete to authenticated
  using (
    (scope = 'personal' and created_by_user_id = auth.uid())
    or public.is_section_teacher(section_id)
    or public.is_course_teacher(course_id)
    or public.has_role('admin')
  );

-- activity_sources ------------------------------------------------------------
alter table public.activity_sources enable row level security;

-- Visible exactly when the parent activity is visible.
create policy activity_sources_select on public.activity_sources
  for select to authenticated
  using (public.activity_visible_to_user(activity_id, auth.uid()));

-- Students attach their own manual source to an activity they manage;
-- teachers attach teacher_manual sources to activities they manage.
create policy activity_sources_insert on public.activity_sources
  for insert to authenticated
  with check (
    (
      provider = 'student_manual'
      and reported_by_user_id = auth.uid()
      and exists (
        select 1 from public.activities a
        where a.id = activity_sources.activity_id
          and a.created_by_user_id = auth.uid()
      )
    )
    or (
      provider = 'teacher_manual'
      and exists (
        select 1 from public.activities a
        where a.id = activity_sources.activity_id
          and (public.is_section_teacher(a.section_id) or public.is_course_teacher(a.course_id))
      )
    )
  );

-- A reporter may remove their own manual source; activity managers too.
create policy activity_sources_delete on public.activity_sources
  for delete to authenticated
  using (
    (provider = 'student_manual' and reported_by_user_id = auth.uid())
    or exists (
      select 1 from public.activities a
      where a.id = activity_sources.activity_id
        and (
          a.created_by_user_id = auth.uid()
          or public.is_section_teacher(a.section_id)
          or public.is_course_teacher(a.course_id)
        )
    )
  );
-- no update policy: source evidence is immutable through the client API

-- activity_reports --------------------------------------------------------------
alter table public.activity_reports enable row level security;

create policy activity_reports_select on public.activity_reports
  for select to authenticated
  using (
    reporter_user_id = auth.uid()
    or public.has_role('admin')
    or public.is_section_teacher(section_id)
    or exists (
      select 1 from public.activities a
      where a.id = activity_reports.activity_id
        and public.is_section_teacher(a.section_id)
    )
  );

-- Students always file reports as 'pending'; linking/matching happens later
-- through the guarded update workflow.
create policy activity_reports_insert on public.activity_reports
  for insert to authenticated
  with check (
    reporter_user_id = auth.uid()
    and status = 'pending'
  );

create policy activity_reports_update on public.activity_reports
  for update to authenticated
  using (
    reporter_user_id = auth.uid()
    or public.has_role('admin')
    or public.is_section_teacher(section_id)
    or exists (
      select 1 from public.activities a
      where a.id = activity_reports.activity_id
        and public.is_section_teacher(a.section_id)
    )
  )
  with check (
    reporter_user_id = auth.uid()
    or public.has_role('admin')
    or public.is_section_teacher(section_id)
    or exists (
      select 1 from public.activities a
      where a.id = activity_reports.activity_id
        and public.is_section_teacher(a.section_id)
    )
  );

create policy activity_reports_delete on public.activity_reports
  for delete to authenticated
  using (reporter_user_id = auth.uid());

-- activity_groups / members ------------------------------------------------------
alter table public.activity_groups enable row level security;

-- A group is visible when any member activity is visible to the caller
-- (the members policy below applies inside the subquery).
create policy activity_groups_select on public.activity_groups
  for select to authenticated
  using (
    exists (
      select 1 from public.activity_group_members m
      where m.group_id = activity_groups.id
    )
  );
-- no client write policies: grouping is a trusted operation (plan §9 Phase 5)

alter table public.activity_group_members enable row level security;

create policy activity_group_members_select on public.activity_group_members
  for select to authenticated
  using (public.activity_visible_to_user(activity_id, auth.uid()));

-- activity_participants -----------------------------------------------------------
alter table public.activity_participants enable row level security;

create policy activity_participants_select on public.activity_participants
  for select to authenticated
  using (
    user_id = auth.uid()
    or public.activity_visible_to_user(activity_id, auth.uid())
  );

-- Sharing a personal activity: the owner adds participants. Teachers can
-- target selected students on their section activities.
create policy activity_participants_insert on public.activity_participants
  for insert to authenticated
  with check (
    exists (
      select 1 from public.activities a
      where a.id = activity_participants.activity_id
        and (
          a.created_by_user_id = auth.uid()
          or public.is_section_teacher(a.section_id)
          or public.is_course_teacher(a.course_id)
        )
    )
  );

create policy activity_participants_delete on public.activity_participants
  for delete to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.activities a
      where a.id = activity_participants.activity_id
        and (
          a.created_by_user_id = auth.uid()
          or public.is_section_teacher(a.section_id)
          or public.is_course_teacher(a.course_id)
        )
    )
  );
-- no update policy: a participant row is (activity, user); change = delete+insert
