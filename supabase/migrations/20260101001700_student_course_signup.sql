-- ============================================================================
-- Student Codex migration 0017 — STUDENT COURSE SIGN-UP (course codes)
--
-- Product decision (2026-10-25, user request): students are asked for their
-- courses (subjects) AT ACCOUNT CREATION — on the email sign-up form AND on
-- the Google completion form. Entry is by COURSE CODE ONLY (e.g. 'CS 101'):
-- the class group (program / year level / block / campus) stays the four
-- registry dropdowns; the course codes say WHICH SUBJECTS the student
-- takes. This is what powers the manual "add activity" flow: its audience
-- picker lists the student's enrolled sections, and until now a freshly
-- signed-up student had ZERO enrollments — the editor dead-ended on "No
-- classes yet", and any forced save failed the enrollment guard with:
--   verification_status 'student-reported' on a section activity requires
--   the creator to be enrolled in that section.
--
-- How it works:
--   * The course codes travel in the SAME auth user metadata as the school
--     details (key 'course_codes', a JSON array of strings), so both
--     existing metadata doors pick them up:
--       - handle_new_user (email sign-up insert trigger)
--       - handle_user_profile_sync (Google completion / Settings update)
--     Enrollment therefore happens atomically with account creation — even
--     when the hosted project requires email confirmation and no session
--     exists yet, and automatically for accounts edited later in Settings.
--   * Matching: a code names a courses row (case-insensitive); the
--     student's OWN cohort — the year level + block they picked — selects
--     which section of that course is theirs. Course sections encode the
--     cohort in their name ('BSCS 2-A' = program, 2nd year, block A), the
--     same convention teacher_signup_multi matches against. At most one
--     enrollment per code (own program's section preferred on ambiguity).
--   * Unknown codes — and codes whose course has no section for the
--     student's cohort — are SKIPPED, never fatal: the app validates codes
--     against the registry before submit (see the anon policy below), so
--     this is a safety net, not the primary gate. Re-runs are idempotent
--     (ON CONFLICT DO NOTHING) — enrollments are append-only by design.
--   * courses becomes anon-readable: the sign-up form validates codes
--     BEFORE any account exists — same rationale as programs/campuses in
--     migration 0007: they are public facts of the university.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. The shared enrollment helper. SECURITY DEFINER (the enrollments table
--    has no client INSERT policy by design — membership writes go through
--    guarded doors like this one), pinned search_path, validates every
--    input against the registry. Callable directly by authenticated users
--    (idempotent), and from both metadata triggers below.
-- ---------------------------------------------------------------------------
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
  v_code text;
  v_course uuid;
  v_section uuid;
  v_enrolled integer := 0;
  v_matched text[] := '{}';
  v_unknown text[] := '{}';
  v_no_section text[] := '{}';
  r record;
begin
  if p_user is null then
    raise exception 'enroll_student_courses: missing user';
  end if;

  -- The cohort comes from the student's own school details. Without year +
  -- block there is nothing to match against (legacy account, or a metadata
  -- payload that skipped the dropdowns) — report and exit, never fail.
  if p_year is null or p_block_id is null then
    return jsonb_build_object(
      'enrolled', 0, 'matched', to_jsonb(v_matched),
      'unknown', to_jsonb(v_unknown), 'no_section', to_jsonb(v_no_section),
      'skipped', 'no cohort'
    );
  end if;

  select name into v_block from public.year_sections where id = p_block_id;
  if v_block is null then
    raise exception 'enroll_student_courses: invalid block';
  end if;

  -- Program short name ('BSCS' from '… (BSCS)') — preferred section prefix
  -- when one course has several sections with the same cohort suffix.
  select substring(program_name from '\(([^)]+)\)\s*$') into v_prog_short
  from public.programs where id = p_program;

  for r in
    select distinct btrim(code_text) as code
    from jsonb_array_elements_text(
      case when jsonb_typeof(p_codes) = 'array' then p_codes else '[]'::jsonb end
    ) as code_text
  loop
    v_code := nullif(r.code, '');
    if v_code is null then continue; end if;

    -- The course behind the code (case-insensitive; the student's own
    -- program wins if the same code is listed under several programs).
    select c.id into v_course
    from public.courses c
    where upper(btrim(c.code)) = upper(v_code)
    order by (c.program_id = p_program) desc, c.code
    limit 1;
    if v_course is null then
      v_unknown := v_unknown || v_code;
      continue;
    end if;

    -- The student's section of that course: the cohort suffix (' 2-A')
    -- with the own-program section preferred, then name order — always
    -- deterministic.
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
    'no_section', to_jsonb(v_no_section)
  );
end $$;

revoke execute on function public.enroll_student_courses(uuid, uuid, integer, uuid, jsonb) from public, anon;
grant execute on function public.enroll_student_courses(uuid, uuid, integer, uuid, jsonb) to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 2. Email sign-up door: handle_new_user gains the course enrollment call.
--    Body is byte-identical to the migration 0008 version except the final
--    PERFORM: the school-detail variables are already the VALIDATED values
--    (nulled when they reference nothing), which is exactly what the helper
--    wants — bad or missing details skip enrollment instead of failing the
--    sign-up.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_given text := nullif(btrim(coalesce(new.raw_user_meta_data->>'first_name', '')), '');
  v_family text := nullif(btrim(coalesce(new.raw_user_meta_data->>'last_name', '')), '');
  v_program uuid := public.try_uuid(new.raw_user_meta_data->>'program_id');
  v_year integer;
  v_campus uuid := public.try_uuid(new.raw_user_meta_data->>'campus_id');
  v_section uuid := public.try_uuid(new.raw_user_meta_data->>'year_section_id');
  v_username text;
begin
  insert into public.profiles (id) values (new.id)
    on conflict (id) do nothing;

  if new.raw_user_meta_data->>'year_level' ~ '^[0-9]{1,2}$' then
    v_year := (new.raw_user_meta_data->>'year_level')::integer;
  end if;
  if v_program is not null and not exists (select 1 from public.programs p where p.id = v_program) then
    v_program := null;
  end if;
  if v_year is not null and not exists (select 1 from public.year_levels y where y.level = v_year) then
    v_year := null;
  end if;
  if v_campus is not null and not exists (select 1 from public.campuses c where c.id = v_campus) then
    v_campus := null;
  end if;
  if v_section is not null and not exists (select 1 from public.year_sections s where s.id = v_section) then
    v_section := null;
  end if;

  if v_given is not null or v_family is not null
     or v_program is not null or v_year is not null
     or v_campus is not null or v_section is not null then
    begin
      v_username := public.claim_username(
        public.derive_username(coalesce(v_given, ''), coalesce(v_family, ''))
      );
      update public.profiles set
        first_name = coalesce(v_given, first_name),
        last_name = coalesce(v_family, last_name),
        program_id = coalesce(v_program, program_id),
        year_level = coalesce(v_year, year_level),
        campus_id = coalesce(v_campus, campus_id),
        year_section_id = coalesce(v_section, year_section_id),
        username = coalesce(
          case when username is null then v_username else username end,
          username
        )
      where id = new.id;
    exception when unique_violation then
      -- Two same-named sign-ups raced us to the handle: keep the profile
      -- (with names + school details, without username) instead of failing
      -- the sign-up; the user can claim a handle later in-app.
      update public.profiles set
        first_name = coalesce(v_given, first_name),
        last_name = coalesce(v_family, last_name),
        program_id = coalesce(v_program, program_id),
        year_level = coalesce(v_year, year_level),
        campus_id = coalesce(v_campus, campus_id),
        year_section_id = coalesce(v_section, year_section_id)
      where id = new.id;
    end;
  end if;

  -- NEW (migration 0017): enroll the student in the sections behind the
  -- submitted course codes. Skips silently when the codes are absent,
  -- unknown, or the school details never made it through — a broken code
  -- must never break the account.
  perform public.enroll_student_courses(
    new.id, v_program, v_year, v_section,
    new.raw_user_meta_data->'course_codes'
  );

  return new;
end $$;

-- ---------------------------------------------------------------------------
-- 3. Google completion / Settings door: handle_user_profile_sync gains the
--    same call. The function only acts when profile_completed=true, so a
--    password-only updateUser (metadata untouched) re-runs this with the
--    OLD metadata — which is fine: enrollment is idempotent and
--    append-only, and the app always re-sends the full course list.
-- ---------------------------------------------------------------------------
create or replace function public.handle_user_profile_sync() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_given text := nullif(btrim(coalesce(new.raw_user_meta_data->>'first_name', '')), '');
  v_family text := nullif(btrim(coalesce(new.raw_user_meta_data->>'last_name', '')), '');
  v_program uuid := public.try_uuid(new.raw_user_meta_data->>'program_id');
  v_year integer;
  v_campus uuid := public.try_uuid(new.raw_user_meta_data->>'campus_id');
  v_section uuid := public.try_uuid(new.raw_user_meta_data->>'year_section_id');
  v_username text;
begin
  if coalesce(new.raw_user_meta_data->>'profile_completed', 'false') <> 'true' then
    return new;
  end if;

  if new.raw_user_meta_data->>'year_level' ~ '^[0-9]{1,2}$' then
    v_year := (new.raw_user_meta_data->>'year_level')::integer;
  end if;
  if v_program is not null and not exists (select 1 from public.programs p where p.id = v_program) then
    v_program := null;
  end if;
  if v_year is not null and not exists (select 1 from public.year_levels y where y.level = v_year) then
    v_year := null;
  end if;
  if v_campus is not null and not exists (select 1 from public.campuses c where c.id = v_campus) then
    v_campus := null;
  end if;
  if v_section is not null and not exists (select 1 from public.year_sections s where s.id = v_section) then
    v_section := null;
  end if;

  if v_given is null and v_family is null and v_program is null
     and v_year is null and v_campus is null and v_section is null then
    return new;
  end if;

  begin
    v_username := public.claim_username(
      public.derive_username(coalesce(v_given, ''), coalesce(v_family, ''))
    );
    update public.profiles set
      first_name = coalesce(v_given, first_name),
      last_name = coalesce(v_family, last_name),
      program_id = coalesce(v_program, program_id),
      year_level = coalesce(v_year, year_level),
      campus_id = coalesce(v_campus, campus_id),
      year_section_id = coalesce(v_section, year_section_id),
      username = coalesce(
        case when username is null then v_username else username end,
        username
      )
    where id = new.id;
  exception when unique_violation then
    update public.profiles set
      first_name = coalesce(v_given, first_name),
      last_name = coalesce(v_family, last_name),
      program_id = coalesce(v_program, program_id),
      year_level = coalesce(v_year, year_level),
      campus_id = coalesce(v_campus, campus_id),
      year_section_id = coalesce(v_section, year_section_id)
    where id = new.id;
  end;

  -- NEW (migration 0017): same enrollment door for Google completions and
  -- Settings school-detail edits. Idempotent by construction.
  perform public.enroll_student_courses(
    new.id, v_program, v_year, v_section,
    new.raw_user_meta_data->'course_codes'
  );

  return new;
end $$;

-- ---------------------------------------------------------------------------
-- 4. Course codes are a public fact: the sign-up form (pre-account, anon)
--    validates typed codes against the courses registry before submit.
--    The authenticated policy from migration 0002 stays; this adds anon.
-- ---------------------------------------------------------------------------
drop policy if exists courses_select_public on public.courses;
create policy courses_select_public on public.courses
  for select to anon, authenticated
  using (true);

commit;

-- ---------------------------------------------------------------------------
-- Verify block (sandbox psql / Dashboard SQL Editor):
--   select public.enroll_student_courses(
--     '<student-uuid>',
--     (select id from public.programs where program_name like '%Computer Science%' limit 1),
--     2, (select id from public.year_sections where name = 'A'),
--     '["CS 101", "CS 102", "GE 101", "XX 999"]'::jsonb);
-- should report enrolled=3, matched={CS 101, CS 102, GE 101}, unknown={XX 999}
-- for the demo registry (every course has one 'BSCS 2-A' section).
-- ---------------------------------------------------------------------------
