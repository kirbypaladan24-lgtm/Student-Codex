-- ============================================================================
-- Student Codex migration 0019 — STUDENT COURSE SELF-SERVICE
--
-- Product reversal of 0017's matching assumption (user report 2026-10-25:
-- "why can't I add my real course code? no course is fixed, we learn
-- different courses each semester/year"). 0017 assumed the courses registry
-- is COMPLETE — it is not and never will be: the hosted catalog holds only
-- the six demo seed rows, and a real university's offerings change every
-- term. The result: the app-side validation rejected every real code
-- ("Unknown course code…") and the enrollment helper skipped them anyway.
-- A registry nobody maintains cannot be ground truth.
--
-- New rule: the student's typed course code IS the authority.
--   * unknown code             -> the course is CREATED (code doubles as the
--                                 placeholder title; attached to the
--                                 student's own program)
--   * course w/o cohort        -> the section '<SHORT> <year>-<block>' is
--      section                    CREATED — the same 'BSCS 2-A' naming
--                                 convention teacher_signup_multi sweeps,
--                                 so a real class forms itself
--   * then enroll the student (unchanged, idempotent)
--
-- Consequences worth knowing:
--   * Classmates typing the same code land on the SAME course
--     (case-insensitive) and the SAME cohort section — shared activities
--     work immediately.
--   * Teachers pick the new sections up on their next school-details
--     re-save: teacher_signup_multi sweeps ALL of the cohort's sections and
--     is idempotent. (Student-created sections appear after the teacher's
--     last save — re-saving enrolls them; no code change needed.)
--   * Cross-program courses (GE electives) attach to the first program
--     that named them. Other programs' students still MATCH the course
--     (own-program preference is only a tie-break) and get their own
--     cohort section on it — but a teacher of the other program will not
--     sweep onto it, because the teacher sweep filters courses by
--     program_id. Known limitation, acceptable at this scale.
--
-- Safety rails:
--   * format guard  ^[A-Za-z][A-Za-z0-9 -]{0,31}$  — junk ('!!!') lands in
--     the new 'invalid' return array and is skipped, never fatal (the app
--     enforces the same shape before submit).
--   * creation is serialized per code (transaction advisory lock keyed on
--     the upper-cased code; loop order is globally sorted so concurrent
--     calls cannot deadlock) and every insert has a unique-violation
--     re-select as belt-and-braces.
--   * at most 32 codes per call.
--   * everything stays inside this SECURITY DEFINER helper — courses and
--     sections still have NO client INSERT policy; the only membership
--     door remains the metadata path.
-- ============================================================================

begin;

create or replace function public.enroll_student_courses(
  p_user uuid,
  p_program uuid,
  p_year integer,
  p_block_id uuid,
  p_codes jsonb
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_block text;
  v_prog_short text;
  v_prog_name text;
  v_section_name text;
  v_code text;
  v_course uuid;
  v_section uuid;
  v_enrolled integer := 0;
  v_matched text[] := '{}';
  v_unknown text[] := '{}';
  v_no_section text[] := '{}';
  v_invalid text[] := '{}';
  v_created_courses text[] := '{}';
  v_created_sections text[] := '{}';
  r record;
begin
  if p_user is null then
    raise exception 'enroll_student_courses: missing user';
  end if;

  -- The cohort comes from the student's own school details. Without year +
  -- block there is nothing to create or match against — report and exit.
  if p_year is null or p_block_id is null then
    return jsonb_build_object(
      'enrolled', 0, 'matched', to_jsonb(v_matched),
      'unknown', to_jsonb(v_unknown), 'no_section', to_jsonb(v_no_section),
      'invalid', to_jsonb(v_invalid),
      'created_courses', to_jsonb(v_created_courses),
      'created_sections', to_jsonb(v_created_sections),
      'skipped', 'no cohort'
    );
  end if;

  select name into v_block from public.year_sections where id = p_block_id;
  if v_block is null then
    raise exception 'enroll_student_courses: invalid block';
  end if;

  -- Program short name ('BSCS' from '… (BSCS)') doubles as the cohort
  -- prefix when a section has to be created; full name is the fallback.
  select substring(program_name from '\(([^)]+)\)\s*$'), btrim(program_name)
    into v_prog_short, v_prog_name
  from public.programs where id = p_program;
  v_section_name := left(coalesce(nullif(v_prog_short, ''), v_prog_name, 'CLASS'), 72)
                    || ' ' || p_year::text || '-' || v_block;

  -- Sorted by upper-cased code: deterministic order (same input = same
  -- result) AND a global lock order, so two concurrent sign-ups sharing
  -- two codes can never deadlock on the advisory locks below.
  for r in
    select code from (
      select distinct btrim(code_text) as code
      from jsonb_array_elements_text(
        case when jsonb_typeof(p_codes) = 'array' then p_codes else '[]'::jsonb end
      ) as code_text
      limit 32
    ) d
    order by upper(code)
  loop
    v_code := nullif(r.code, '');
    if v_code is null then continue; end if;

    -- junk guard (the app guards pre-submit too; defense in depth)
    if v_code !~ '^[A-Za-z][A-Za-z0-9 -]{0,31}$' then
      v_invalid := v_invalid || v_code;
      continue;
    end if;

    -- serialize creation per code: concurrent sign-ups typing 'CC1'/'cc1'
    -- must converge on ONE course, and unique(code,title) is case-SENSITIVE
    perform pg_catalog.pg_advisory_xact_lock(
      pg_catalog.hashtext('courses:' || upper(v_code)));

    -- The course behind the code (case-insensitive; the student's own
    -- program wins if the same code is listed under several programs).
    select c.id into v_course
    from public.courses c
    where upper(btrim(c.code)) = upper(v_code)
    order by (c.program_id = p_program) desc, c.code
    limit 1;

    if v_course is null then
      begin
        insert into public.courses (program_id, code, title)
        values (p_program, v_code, v_code)
        returning id into v_course;
        v_created_courses := v_created_courses || v_code;
      exception when unique_violation then
        -- lost a creation race (lock guards case variants; this is the
        -- belt-and-braces path) — take the winner's course
        select c.id into v_course
        from public.courses c
        where upper(btrim(c.code)) = upper(v_code)
        order by (c.program_id = p_program) desc, c.code
        limit 1;
      end;
    end if;
    if v_course is null then
      v_unknown := v_unknown || v_code;
      continue;
    end if;

    -- The student's section of that course: the cohort suffix (' 2-A').
    select s.id into v_section
    from public.sections s
    where s.course_id = v_course
      and s.name like '% ' || p_year::text || '-' || v_block
    order by
      (case when v_prog_short is null then false
            else s.name like v_prog_short || ' %' end) desc,
      s.name
    limit 1;

    if v_section is null then
      -- no cohort section yet -> create it under the same naming
      -- convention the teacher sweep matches ('BSCS 2-A')
      begin
        insert into public.sections (course_id, name)
        values (v_course, v_section_name);
        v_created_sections := v_created_sections || v_section_name;
      exception when unique_violation then
        null; -- lost a race; the re-select below finds the winner's row
      end;
      select s.id into v_section
      from public.sections s
      where s.course_id = v_course
        and s.name like '% ' || p_year::text || '-' || v_block
      order by
        (case when v_prog_short is null then false
              else s.name like v_prog_short || ' %' end) desc,
        s.name
      limit 1;
    end if;
    if v_section is null then
      v_no_section := v_no_section || v_code;
      continue;
    end if;

    insert into public.enrollments (section_id, user_id, role)
    values (v_section, p_user, 'student')
    on conflict (section_id, user_id) do nothing;
    if found then
      v_enrolled := v_enrolled + 1;
    end if;
    v_matched := v_matched || v_code;
  end loop;

  return jsonb_build_object(
    'enrolled', v_enrolled,
    'matched', to_jsonb(v_matched),
    'unknown', to_jsonb(v_unknown),
    'no_section', to_jsonb(v_no_section),
    'invalid', to_jsonb(v_invalid),
    'created_courses', to_jsonb(v_created_courses),
    'created_sections', to_jsonb(v_created_sections)
  );
end $$;

revoke execute on function public.enroll_student_courses(uuid, uuid, integer, uuid, jsonb) from public, anon;
grant execute on function public.enroll_student_courses(uuid, uuid, integer, uuid, jsonb) to authenticated, service_role;

commit;

-- ---------------------------------------------------------------------------
-- Verify block (sandbox psql / Dashboard SQL Editor):
--   select public.enroll_student_courses(
--     '<student-uuid>',
--     (select id from public.programs where program_name like '%Computer Science%' limit 1),
--     2, (select id from public.year_sections where name = 'A'),
--     '["CC1", "cs 101", "!!!"]'::jsonb);
-- CC1   -> course + 'BSCS 2-A' section CREATED, enrolled
-- cs 101-> matches the seeded CS 101 (case-insensitive), enrolled
-- !!!   -> reported in 'invalid', skipped
-- re-run the same call: enrolled=0 second time, nothing duplicated.
-- ---------------------------------------------------------------------------
