-- ============================================================================
-- Student Codex migration 0013 — TEACHER SELF-SERVICE SIGN-UP
-- The Teacher Dashboard (WebView) needs open account creation: a teacher
-- signs up with email + password, picks campus (optional) / program /
-- year level / section block, and lands on the cohort calendar.
--
-- Why an RPC: user_roles and enrollments are trusted, admin-managed data
-- with NO client write policies (plan §2: a student must not be able to
-- make themselves a teacher by editing their profile). The client cannot
-- grant its own role or enrollment. This SECURITY DEFINER function is the
-- single guarded door: it validates every input against the registry,
-- grants the role, aligns the profile, and enrolls the caller as teacher
-- of the sections that match the chosen cohort.
--
-- Deliberate product decision (documented for reviewers): teacher sign-up
-- is open to any authenticated account in this deployment. That is what
-- the product asked for (self-service teacher onboarding). Hardening
-- options if the school wants them later: invite codes, an admin approval
-- queue, or restricting the grant to whitelisted email domains — all can
-- be added INSIDE this one function without touching the app.
--
-- Section matching: course sections encode the cohort in their name
-- ('BSCS 2-A' = BSCS program, 2nd year, block A). The RPC enrolls the new
-- teacher in every section of the chosen program whose name ends with
-- ' <year>-<block>'. A cohort with no sections yet still gets the role and
-- profile; the dashboard shows an explicit empty state in that case.
--
-- Idempotent: role and enrollments use ON CONFLICT DO NOTHING, so repeat
-- calls (re-signup, retry after a network failure) never duplicate rows.
-- ============================================================================

create or replace function public.teacher_signup(
  p_program uuid,
  p_year_level integer,
  p_year_section uuid,
  p_campus uuid default null
) returns integer
language plpgsql security definer set search_path = '' as $$
declare
  v_uid uuid := auth.uid();
  v_block text;
  v_matched integer := 0;
begin
  -- Gate: a signed-in session is required (the dashboard signs up AFTER
  -- auth.createUser; the anon key alone can never call this).
  if v_uid is null then
    raise exception 'Sign in before signing up as a teacher';
  end if;

  -- Inputs are dropdown values; validate each against the registry so a
  -- crafted client payload cannot inject free text or foreign ids.
  if p_program is null or not exists (select 1 from public.programs p where p.id = p_program) then
    raise exception 'Pick a valid program';
  end if;
  if p_year_level is null or not exists (select 1 from public.year_levels y where y.level = p_year_level) then
    raise exception 'Pick a valid year level';
  end if;
  if p_year_section is null or not exists (select 1 from public.year_sections s where s.id = p_year_section) then
    raise exception 'Pick a valid section';
  end if;
  if p_campus is not null and not exists (select 1 from public.campuses c where c.id = p_campus) then
    raise exception 'Invalid campus';
  end if;

  -- Teacher role: granted_by is intentionally null — this is self-service
  -- signup, not an admin grant (see the decision note above).
  insert into public.user_roles (user_id, role)
  values (v_uid, 'teacher')
  on conflict (user_id, role) do nothing;

  -- Keep the profile's school details aligned with the signup choices.
  -- Campus left empty falls back to the program's own campus: after
  -- migration 0012 every program row IS campus-specific.
  update public.profiles
    set program_id = p_program,
        year_level = p_year_level,
        campus_id = coalesce(p_campus, (select p.campus_id from public.programs p where p.id = p_program)),
        year_section_id = p_year_section
  where id = v_uid;

  -- Enroll as teacher in the matching cohort sections. The name encodes
  -- '<short program> <year>-<block>' (demo seeds: 'BSCS 2-A'), so match on
  -- the year-block suffix of the chosen program's sections.
  select name into v_block from public.year_sections where id = p_year_section;

  insert into public.enrollments (section_id, user_id, role)
  select s.id, v_uid, 'teacher'
  from public.sections s
  join public.courses c on c.id = s.course_id
  where c.program_id = p_program
    and s.name like '% ' || p_year_level::text || '-' || v_block
  on conflict (section_id, user_id) do nothing;

  -- Report how many sections this cohort resolves to (0 = no sections yet;
  -- the role/profile are still granted, the dashboard shows an empty state).
  select count(*) into v_matched
  from public.sections s
  join public.courses c on c.id = s.course_id
  where c.program_id = p_program
    and s.name like '% ' || p_year_level::text || '-' || v_block;

  return v_matched;
end $$;

-- Execute: authenticated callers only (auth.uid() inside does the rest).
revoke execute on function public.teacher_signup(uuid, integer, uuid, uuid) from public, anon;
grant execute on function public.teacher_signup(uuid, integer, uuid, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Verify block (sandbox psql / Dashboard SQL Editor):
--   select public.teacher_signup(
--     'e1000000-0000-4000-8000-000000000012', 2,
--     (select id from public.year_sections where name='A'), null);
-- should return 6 for the demo registry (BSCS 2-A x 6 courses).
-- ---------------------------------------------------------------------------
