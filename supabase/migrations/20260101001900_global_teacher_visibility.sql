-- ============================================================================
-- Student Codex migration 0020 — GLOBAL TEACHER VISIBILITY
--
-- Product decision (2026-10-25, user request): teacher sign-up carries NO
-- campus / program / year / section / course choices. A teacher is just an
-- account with the teacher role; the dashboard shows activities from EVERY
-- campus, program, year, block and course in the university, and the
-- teacher narrows the view with the filter bar that already exists
-- (campus / program / year / block). Cohort-restricted teacher calendars
-- (enrollment-driven visibility) are retired for reads.
--
-- Changes:
-- 1. activities_select: a teacher (has_role('teacher')) may read every
--    non-personal activity (scope 'section' or 'course'), regardless of
--    enrollment. Students keep the exact same visibility as before: own
--    rows, rows they participate in, and section/course-scoped rows of
--    sections they are enrolled in. Personal-scope rows of OTHER users
--    stay private from everyone but their creator (and participants) —
--    teachers included. Soft-deleted rows stay hidden (creator excepted).
-- 2. activity_visible_to_user(): same teacher branch added so the helper
--    (used by participants/reports/sources policies) stays the single
--    source of truth for activity visibility.
-- 3. teacher_signup_global(): the new sign-up RPC — grants the teacher
--    role and nothing else. teacher_signup (0013) and teacher_signup_multi
--    (0016) are kept untouched: legacy pending activations with
--    class-group metadata still complete the old way, and their
--    enrollments remain (they no longer gate READS; they still gate
--    teacher-confirmed INSERT/UPDATE via is_section_teacher).
--
-- Why the policy uses has_role() and not a raw EXISTS on user_roles:
-- has_role is the established SECURITY DEFINER helper (0001) — it bypasses
-- user_roles RLS, avoids recursion, and is what every other policy in the
-- schema already trusts. It only reads user_roles, never activities, so
-- the INSERT ... RETURNING snapshot caveat from migration 0003 (which
-- forbids activity_visible_to_user in this policy) does not apply.
--
-- Verification: test-0020.sql (11 scenarios) on the local mirror.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. activities_select — teachers read all non-personal activities
-- ---------------------------------------------------------------------------
drop policy if exists activities_select on public.activities;

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
      -- 0020: university-wide teacher read — no enrollment needed.
      or (
        public.has_role('teacher')
        and scope in ('section', 'course')
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

-- ---------------------------------------------------------------------------
-- 2. activity_visible_to_user — keep the helper in sync (single source of
--    truth). The teacher branch guards viewer = auth.uid() because
--    has_role() resolves the CALLER's roles; the viewer parameter must
--    never leak role powers to a different user id.
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
        -- 0020: university-wide teacher read (caller's own session only).
        or (
          viewer = auth.uid()
          and public.has_role('teacher')
          and a.scope in ('section', 'course')
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
-- 3. teacher_signup_global — role-only teacher onboarding (the 0020 flow)
-- ---------------------------------------------------------------------------
create or replace function public.teacher_signup_global() returns integer
language plpgsql security definer set search_path = '' as $$
declare
  v_uid uuid := auth.uid();
begin
  -- Gate: a signed-in session is required (the dashboard signs up AFTER
  -- auth.signUp; the anon key alone can never call this).
  if v_uid is null then
    raise exception 'Sign in before signing up as a teacher';
  end if;

  -- Teacher role only: no campus/program/year/block choice, no profile
  -- alignment, no enrollments. Visibility comes from activities_select
  -- (teachers read all non-personal activities university-wide).
  -- granted_by is intentionally null — self-service signup (same
  -- deliberate product decision as migrations 0013/0016).
  insert into public.user_roles (user_id, role)
  values (v_uid, 'teacher')
  on conflict (user_id, role) do nothing;

  return 1;
end $$;

revoke execute on function public.teacher_signup_global() from public, anon;
grant execute on function public.teacher_signup_global() to authenticated;

commit;

-- ---------------------------------------------------------------------------
-- Verify block (sandbox psql / Dashboard SQL Editor):
--   select public.teacher_signup_global();          -- as a signed-in user
--   select count(*) from activities;                -- as that teacher now
--   -- should include every section/course-scoped row, no personal rows
--   -- of other users, no soft-deleted rows
-- ---------------------------------------------------------------------------
