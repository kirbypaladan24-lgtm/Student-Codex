-- ============================================================================
-- Student Codex migration 0015 — STUDENT SELF-PUBLISH (no approval queue)
--
-- Product decision (2026-10-24, user review): student reports publish
-- INSTANTLY. When a student reports a face-to-face (not-in-Classroom)
-- activity from the app, it is written DIRECTLY into `activities` as a
-- section-scoped, student-reported row. The teacher dashboard is VIEW-ONLY
-- (sign up, look) — no confirm/reject queue anywhere. The origin of an
-- entry stays visible as a badge (teacher-confirmed vs student-reported):
-- a LABEL, not a gate.
--
-- Cohort auto-derivation: the student is never asked for campus, program,
-- year or section. They pick only WHICH CLASS (one of their own enrolled
-- sections); the section row itself encodes the cohort (e.g. "BSCS 2-A"
-- -> program BSCS, year 2, block A -> campus via the program), and their
-- profile already carries campus/program/year/section for display.
--
-- Changes:
-- 1. activities_insert policy: a student may insert scope='section' rows
--    with verification_status='student-reported' into a section they are
--    ENROLLED in (role='student'). Teacher/admin paths are unchanged.
-- 2. guard_activity_insert(): a 'student-reported' SECTION row must be
--    created by an enrolled student of that section (admins and the
--    service context pass). Teachers cannot file fake student reports;
--    students cannot publish into sections they do not belong to.
--    Personal-scope student-reported rows (the manual planner) are
--    untouched.
-- 3. New policy activities_update_own_student_report: the reporter can
--    edit or withdraw (soft-delete) their OWN student-reported section
--    rows — fix a typo, adjust the due date, take it back. The existing
--    guard_activity_update keeps verification upgrades teacher-only.
-- 4. Legacy queue drain: every 'pending' activity_report is converted to
--    a published student-reported activity (section from the report, else
--    the reporter's oldest enrollment) and the report row is marked
--    'matched' with activity_id set. Nothing waits for approval again.
--    activity_reports remains as an audit table; clients no longer write
--    'pending' rows — the app publishes activities directly.
--
-- Verification: pdf-work/test-0015.mjs (17 checks) on the local mirror.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. activities_insert — add the student self-publish path
-- ---------------------------------------------------------------------------
drop policy if exists activities_insert on public.activities;

create policy activities_insert on public.activities
  for insert to authenticated
  with check (
    created_by_user_id = auth.uid()
    and (
      scope = 'personal'
      or (
        scope = 'section'
        and verification_status = 'student-reported'
        and exists (
          select 1 from public.enrollments e
          where e.section_id = activities.section_id
            and e.user_id = auth.uid()
            and e.role = 'student'
        )
      )
      or (scope = 'section' and public.is_section_teacher(activities.section_id))
      or (scope = 'course' and public.is_course_teacher(activities.course_id))
    )
  );

-- ---------------------------------------------------------------------------
-- 2. guard_activity_insert — origin honesty for student-reported rows
--    (same shape as before; adds ONLY the student-reported section rule)
-- ---------------------------------------------------------------------------
create or replace function public.guard_activity_insert() returns trigger
  language plpgsql
  security definer
  set search_path to ''
as $function$
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
  if new.scope = 'section' and new.verification_status = 'student-reported'
     and not (
       public.has_role('admin')
       or exists (
         select 1 from public.enrollments e
         where e.section_id = new.section_id
           and e.user_id = caller
           and e.role = 'student'
       )
     ) then
    raise exception 'verification_status ''student-reported'' on a section activity requires the creator to be enrolled in that section';
  end if;
  return new;
end $function$;

-- ---------------------------------------------------------------------------
-- 3. Reporter self-service on their own published class reports
-- ---------------------------------------------------------------------------
drop policy if exists activities_update_own_student_report on public.activities;

create policy activities_update_own_student_report on public.activities
  for update to authenticated
  using (
    scope = 'section'
    and verification_status = 'student-reported'
    and created_by_user_id = auth.uid()
  )
  with check (
    scope = 'section'
    and verification_status = 'student-reported'
    and created_by_user_id = auth.uid()
  );

-- ---------------------------------------------------------------------------
-- 4. Drain the legacy pending queue (idempotent; no-op once drained)
--    Runs in the migration's trusted context (auth.uid() is null), so the
--    guard trigger allows these inserts by design.
-- ---------------------------------------------------------------------------
do $block$
declare
  r record;
  target_section uuid;
  target_course uuid;
  new_activity_id uuid;
  atype text;
begin
  for r in
    select id, reporter_user_id, title, due_at, course_id, section_id, notes
    from public.activity_reports
    where status = 'pending'
    order by created_at
  loop
    -- section from the report itself, else the reporter's oldest enrollment
    target_section := coalesce(
      r.section_id,
      (
        select e.section_id
        from public.enrollments e
        where e.user_id = r.reporter_user_id and e.role = 'student'
        order by e.enrolled_at asc, e.section_id asc
        limit 1
      )
    );

    if target_section is null then
      -- reporter belongs to no section: nothing to publish to — withdraw
      update public.activity_reports
      set status = 'withdrawn', updated_at = now()
      where id = r.id;
      continue;
    end if;

    select s.course_id into target_course
    from public.sections s
    where s.id = target_section;

    -- best-effort type from the report's own wording
    atype := case
      when r.title ilike '%quiz%' then 'quiz'
      when r.title ilike '%exam%' or r.title ilike '%test%' then 'exam'
      when r.title ilike '%assignment%' or r.title ilike '%homework%' then 'assignment'
      when r.title ilike '%project%' then 'project'
      when r.title ilike '%announc%' then 'announcement'
      else 'other'
    end;

    insert into public.activities (
      title, description, activity_type, course_id, section_id, due_at,
      estimated_effort_minutes, scope, verification_status,
      created_by_user_id, created_at, updated_at
    ) values (
      r.title, r.notes, atype,
      coalesce(r.course_id, target_course),
      target_section,
      coalesce(r.due_at, now() + interval '7 days'),
      null, 'section', 'student-reported',
      r.reporter_user_id, now(), now()
    )
    returning id into new_activity_id;

    update public.activity_reports
    set status = 'matched', activity_id = new_activity_id, updated_at = now()
    where id = r.id;
  end loop;
end $block$;

commit;
