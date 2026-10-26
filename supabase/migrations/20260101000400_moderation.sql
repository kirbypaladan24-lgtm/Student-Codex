-- ============================================================================
-- Student Codex migration 0005 — MODERATION
-- content_reports: the report-content workflow required for a responsible
-- community release. Reporters file reports; moderators/admins triage them.
-- Rate limiting lives at the Edge/API layer (Phase 9), not in SQL.
-- ============================================================================

create table if not exists public.content_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users (id) on delete cascade,
  target_type text not null check (target_type in ('post', 'comment')),
  target_id uuid not null,
  reason text not null check (reason in ('spam', 'harassment', 'off-topic', 'inappropriate', 'other')),
  details text check (details is null or char_length(details) <= 1000),
  status text not null default 'open' check (status in ('open', 'actioned', 'dismissed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists content_reports_status_idx on public.content_reports (status);
create index if not exists content_reports_target_idx on public.content_reports (target_type, target_id);

create trigger content_reports_set_updated_at before update on public.content_reports
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Guard: reports integrity. Reporters cannot edit the report substance or
-- its status; only moderators/admins triage.
-- ---------------------------------------------------------------------------
create or replace function public.guard_content_report_update() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.reporter_id is distinct from old.reporter_id then
    raise exception 'content_reports.reporter_id is immutable';
  end if;
  if (
    new.target_type is distinct from old.target_type
    or new.target_id is distinct from old.target_id
    or new.reason is distinct from old.reason
    or new.details is distinct from old.details
  ) and not (public.has_role('moderator') or public.has_role('admin')) then
    raise exception 'only moderators or admins can edit a report';
  end if;
  if new.status is distinct from old.status
     and not (public.has_role('moderator') or public.has_role('admin')) then
    raise exception 'only moderators or admins can change report status';
  end if;
  return new;
end $$;

create trigger content_reports_guard_update before update on public.content_reports
  for each row execute function public.guard_content_report_update();

-- ===========================================================================
-- RLS
-- ===========================================================================
alter table public.content_reports enable row level security;

-- Reporters see their own reports; moderators/admins see the queue.
create policy content_reports_select on public.content_reports
  for select to authenticated
  using (
    reporter_id = auth.uid()
    or public.has_role('moderator')
    or public.has_role('admin')
  );

-- Any authenticated user can report content (target validity is checked by
-- the app/Edge layer; target_id is polymorphic by design).
create policy content_reports_insert on public.content_reports
  for insert to authenticated
  with check (
    reporter_id = auth.uid()
    and status = 'open'
  );

-- Only moderators/admins triage; row-level the reporter may "own" the row,
-- but the guard trigger above blocks every substance/status change for
-- non-staff, so the policy can stay simple.
create policy content_reports_update on public.content_reports
  for update to authenticated
  using (
    public.has_role('moderator')
    or public.has_role('admin')
  )
  with check (
    public.has_role('moderator')
    or public.has_role('admin')
  );
-- no delete policy: reports are retained, not deleted
