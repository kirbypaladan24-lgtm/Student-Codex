-- ============================================================================
-- Student Codex migration 0016 — MULTI-CLASS TEACHER SIGN-UP
--
-- College teachers rarely handle a single class: one person may teach
-- BSCS 2-A in Goa on MT and BSHM 1-B in San Jose on WTh. Migration 0013's
-- teacher_signup() takes ONE (program, year, block) tuple, so a multi-class
-- teacher could only ever get half their calendar.
--
-- This adds teacher_signup_multi(p_groups jsonb): the same guarded
-- SECURITY DEFINER door as 0013, but it accepts an array of class groups
-- {program_id, year_level, year_section_id, campus_id} (campus optional),
-- validates EVERY group against the registry, grants the teacher role
-- once, and enrolls the caller as teacher of each group's sections.
--
-- Decisions worth documenting for reviewers:
--   * profiles keeps single-valued school details (it models a person's
--     PRIMARY affiliation), so the profile aligns with the FIRST submitted
--     group; enrollments cover them all.
--   * Duplicate groups in one payload (or across retries) are de-duplicated
--     server-side (distinct + ON CONFLICT DO NOTHING) — idempotent.
--   * Cap of 12 groups per call: generous for a real teaching load, small
--     enough to keep the RPC cheap.
--   * 0013's teacher_signup() is left untouched: old pending sign-ups
--     (single-group metadata) keep working; new clients call the _multi RPC
--     with a one-element array for the single-class case.
-- ============================================================================

create or replace function public.teacher_signup_multi(
  p_groups jsonb
) returns integer
language plpgsql security definer set search_path = '' as $$
declare
  v_uid uuid := auth.uid();
  v_count integer;
  v0 jsonb;
  v0_program uuid;
  v0_year integer;
  v0_block_id uuid;
  v0_campus uuid;
  v_program uuid;
  v_year integer;
  v_block_id uuid;
  v_campus uuid;
  v_block text;
  v_matched integer := 0;
  v_total integer := 0;
  r record;
begin
  -- Gate: a signed-in session is required (the dashboard signs up AFTER
  -- auth.createUser; the anon key alone can never call this).
  if v_uid is null then
    raise exception 'Sign in before signing up as a teacher';
  end if;

  -- Payload shape: a non-empty array of class groups (12 max — see header).
  if p_groups is null or jsonb_typeof(p_groups) <> 'array' then
    raise exception 'p_groups must be a JSON array of class groups';
  end if;
  v_count := jsonb_array_length(p_groups);
  if v_count < 1 then
    raise exception 'Add at least one class group';
  end if;
  if v_count > 12 then
    raise exception 'A teacher account can list up to 12 class groups';
  end if;

  -- Teacher role: granted_by is intentionally null — self-service signup
  -- (same deliberate product decision as migration 0013).
  insert into public.user_roles (user_id, role)
  values (v_uid, 'teacher')
  on conflict (user_id, role) do nothing;

  -- Profile alignment target: FIRST submitted group = primary affiliation.
  -- Extracted here, applied AFTER every group validates (see below) so a
  -- bad payload can never half-write the profile.
  v0 := p_groups->0;
  v0_program := nullif(v0->>'program_id', '');
  v0_year := nullif(v0->>'year_level', '');
  v0_block_id := nullif(v0->>'year_section_id', '');
  v0_campus := nullif(v0->>'campus_id', '');

  -- Validate + enroll every distinct group. 'distinct' folds duplicate
  -- groups in the same payload; ON CONFLICT folds re-runs.
  for r in
    select distinct
      e->>'program_id' as program_id,
      e->>'year_level' as year_level,
      e->>'year_section_id' as year_section_id,
      nullif(e->>'campus_id', '') as campus_id
    from jsonb_array_elements(p_groups) e
  loop
    v_program := nullif(r.program_id, '')::uuid;
    v_year := nullif(r.year_level, '')::integer;
    v_block_id := nullif(r.year_section_id, '')::uuid;
    v_campus := nullif(r.campus_id, '')::uuid;

    -- Same registry validation as 0013, per group: dropdown values only.
    if v_program is null or not exists (select 1 from public.programs p where p.id = v_program) then
      raise exception 'Pick a valid program';
    end if;
    if v_year is null or not exists (select 1 from public.year_levels y where y.level = v_year) then
      raise exception 'Pick a valid year level';
    end if;
    if v_block_id is null or not exists (select 1 from public.year_sections s where s.id = v_block_id) then
      raise exception 'Pick a valid section';
    end if;
    if v_campus is not null and not exists (select 1 from public.campuses c where c.id = v_campus) then
      raise exception 'Invalid campus';
    end if;

    -- Section matching: course sections encode the cohort in their name
    -- ('BSCS 2-A' = BSCS, 2nd year, block A), so match on the
    -- '<year>-<block>' suffix of that program's sections.
    select name into v_block from public.year_sections where id = v_block_id;

    insert into public.enrollments (section_id, user_id, role)
    select s.id, v_uid, 'teacher'
    from public.sections s
    join public.courses c on c.id = s.course_id
    where c.program_id = v_program
      and s.name like '% ' || v_year::text || '-' || v_block
    on conflict (section_id, user_id) do nothing;

    select count(*) into v_matched
    from public.sections s
    join public.courses c on c.id = s.course_id
    where c.program_id = v_program
      and s.name like '% ' || v_year::text || '-' || v_block;

    v_total := v_total + v_matched;
  end loop;

  -- Profile: align with the first submitted group (primary affiliation).
  -- Campus left empty falls back to the program's own campus (0012 made
  -- every program row campus-specific). Every group has been validated by
  -- now, so this can only run on good data.
  update public.profiles
    set program_id = v0_program::uuid,
        year_level = v0_year::integer,
        campus_id = coalesce(v0_campus::uuid,
                             (select p.campus_id from public.programs p where p.id = v0_program::uuid)),
        year_section_id = v0_block_id::uuid
  where id = v_uid;

  return v_total;
end $$;

-- Execute: authenticated callers only (auth.uid() inside does the rest).
revoke execute on function public.teacher_signup_multi(jsonb) from public, anon;
grant execute on function public.teacher_signup_multi(jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- Verify block (sandbox psql / Dashboard SQL Editor):
--   select public.teacher_signup_multi(
--     '[{"program_id":"e1000000-0000-4000-8000-000000000012","year_level":2,
--         "year_section_id":"<block-A-uuid>"}]'::jsonb);
-- should return 6 for the demo registry (BSCS 2-A x 6 courses).
-- ---------------------------------------------------------------------------
