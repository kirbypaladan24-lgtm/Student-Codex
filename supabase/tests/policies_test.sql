-- ============================================================================
-- Student Codex — RLS / policy verification tests (plain SQL, runs with
-- `supabase test db` or any psql against the migrated database).
--
-- Every test follows the plan's testing rules: verify ALLOWED and DENIED
-- paths for select/insert/update/delete as applicable. Identity is always
-- simulated the way Supabase does it: role switch + request.jwt.claims.
--
-- Test identity map:
--   u1, u2  — students enrolled in section s1 (course c1)
--   u3      — student enrolled in section s2 (course c1, other section)
--   t1      — teacher of section s1
--   t2      — teacher of section s2
--   mod1    — moderator (user_roles)
--   adm1    — admin (user_roles)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Fixture (runs as postgres == service context: no JWT, bypasses RLS)
-- ----------------------------------------------------------------------------
do $fixture$
declare
  prog uuid;
begin
  insert into auth.users (id, email) values
    (gen_random_uuid(), 'u1@test.dev'),
    (gen_random_uuid(), 'u2@test.dev'),
    (gen_random_uuid(), 'u3@test.dev'),
    (gen_random_uuid(), 't1@test.dev'),
    (gen_random_uuid(), 't2@test.dev'),
    (gen_random_uuid(), 'mod1@test.dev'),
    (gen_random_uuid(), 'adm1@test.dev');

  select id into prog from public.programs
  where university_name = 'Test University' and program_name = 'BS Computer Science';
  if prog is null then
    insert into public.programs (university_name, program_name)
    values ('Test University', 'BS Computer Science')
    returning id into prog;
  end if;

  update public.profiles set username = 'user_one', program_id = prog, year_level = 2
  where id = (select id from auth.users where email = 'u1@test.dev');
  update public.profiles set username = 'user_two', program_id = prog, year_level = 2
  where id = (select id from auth.users where email = 'u2@test.dev');
  update public.profiles set username = 'user_three', program_id = prog, year_level = 1
  where id = (select id from auth.users where email = 'u3@test.dev');
  update public.profiles set username = 'teach_one', program_id = prog, year_level = 4
  where id = (select id from auth.users where email = 't1@test.dev');
  update public.profiles set username = 'teach_two', program_id = prog, year_level = 4
  where id = (select id from auth.users where email = 't2@test.dev');
  update public.profiles set username = 'mod_one', program_id = prog, year_level = 5
  where id = (select id from auth.users where email = 'mod1@test.dev');
  update public.profiles set username = 'adm_one', program_id = prog, year_level = 5
  where id = (select id from auth.users where email = 'adm1@test.dev');

  insert into public.user_roles (user_id, role)
  values ((select id from auth.users where email = 'mod1@test.dev'), 'moderator'),
         ((select id from auth.users where email = 'adm1@test.dev'), 'admin');

  insert into public.courses (code, title) values ('CS11', 'Intro to Computing');
  insert into public.sections (course_id, name, term)
  values ((select id from public.courses where code = 'CS11'), 'S1', '2026-1');
  insert into public.sections (course_id, name, term)
  values ((select id from public.courses where code = 'CS11'), 'S2', '2026-1');

  insert into public.enrollments (section_id, user_id, role)
  values
    ((select id from public.sections where name = 'S1'), (select id from auth.users where email = 'u1@test.dev'), 'student'),
    ((select id from public.sections where name = 'S1'), (select id from auth.users where email = 'u2@test.dev'), 'student'),
    ((select id from public.sections where name = 'S1'), (select id from auth.users where email = 't1@test.dev'), 'teacher'),
    ((select id from public.sections where name = 'S2'), (select id from auth.users where email = 'u3@test.dev'), 'student'),
    ((select id from public.sections where name = 'S2'), (select id from auth.users where email = 't2@test.dev'), 'teacher');
end $fixture$;

-- ----------------------------------------------------------------------------
-- T01 — RLS is enabled on every API-exposed table
-- ----------------------------------------------------------------------------
do $t01$
declare tbl text;
begin
  foreach tbl in array array[
    'programs','profiles','user_roles','courses','sections','enrollments',
    'activities','activity_sources','activity_reports','activity_groups',
    'activity_group_members','activity_participants',
    'posts','post_images','comments','post_likes','comment_likes','user_blocks',
    'content_reports'
  ] loop
    if not exists (
      select 1 from pg_tables
      where schemaname = 'public' and tablename = tbl and rowsecurity
    ) then
      raise exception 'T01 FAIL: RLS not enabled on public.%', tbl;
    end if;
  end loop;
  if not exists (
    select 1 from pg_tables
    where schemaname = 'storage' and tablename = 'objects' and rowsecurity
  ) then
    raise exception 'T01 FAIL: RLS not enabled on storage.objects';
  end if;
end $t01$;

-- ----------------------------------------------------------------------------
-- T02 — new auth user automatically gets a profile row (auth trigger)
-- ----------------------------------------------------------------------------
do $t02$
declare newu uuid := gen_random_uuid(); cnt int;
begin
  insert into auth.users (id, email) values (newu, 'newbie@test.dev');
  select count(*) into cnt from public.profiles where id = newu;
  if cnt <> 1 then raise exception 'T02 FAIL: profile row not auto-created'; end if;
end $t02$;

-- ----------------------------------------------------------------------------
-- T03 — anonymous access is denied everywhere
-- ----------------------------------------------------------------------------
do $t03$
declare cnt int;
begin
  perform set_config('request.jwt.claims', '', true);
  execute 'set local role anon';

  select count(*) into cnt from public.profiles;
  if cnt <> 0 then raise exception 'T03.1 FAIL: anon must not read profiles'; end if;
  select count(*) into cnt from public.activities;
  if cnt <> 0 then raise exception 'T03.2 FAIL: anon must not read activities'; end if;
  select count(*) into cnt from public.posts;
  if cnt <> 0 then raise exception 'T03.3 FAIL: anon must not read posts'; end if;
  select count(*) into cnt from storage.objects where bucket_id = 'community-images';
  if cnt <> 0 then raise exception 'T03.4 FAIL: anon must not read storage objects'; end if;

  begin
    insert into public.posts (author_id, content) values (gen_random_uuid(), 'anon post');
    raise exception 'T03.5 FAIL: anon insert into posts must be rejected';
  exception when insufficient_privilege then null; end;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t03$;

-- ----------------------------------------------------------------------------
-- T04 — profiles: own-row updates only; username set-once + uniqueness;
--       id immutable; other users' rows untouchable
-- ----------------------------------------------------------------------------
do $t04$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  -- allowed: edit own year_level
  update public.profiles set year_level = 3 where id = u1;
  if (select year_level from public.profiles where id = u1) <> 3 then
    raise exception 'T04.1 FAIL: owner should be able to update own profile';
  end if;

  -- denied (silent no-op): update someone else's profile
  update public.profiles set year_level = 1 where id = u2;
  if (select year_level from public.profiles where id = u2) <> 2 then
    raise exception 'T04.2 FAIL: updating another profile must be a no-op';
  end if;

  -- denied: change own id
  begin
    update public.profiles set id = gen_random_uuid() where id = u1;
    raise exception 'T04.3 FAIL: profiles.id must be immutable';
  exception when raise_exception then null; end;

  -- allowed: set username exactly once (was null for new users is tested in T02 world;
  -- u1 already has one, so a change must be rejected)
  begin
    update public.profiles set username = 'renamed_one' where id = u1;
    raise exception 'T04.4 FAIL: username must be immutable once set';
  exception when raise_exception then null; end;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- username uniqueness (set as postgres/service for the fresh user from T02)
  begin
    update public.profiles set username = 'user_one'
    where id = (select id from auth.users where email = 'newbie@test.dev');
    raise exception 'T04.5 FAIL: duplicate username must be rejected';
  exception when unique_violation then null; end;
end $t04$;

-- ----------------------------------------------------------------------------
-- T05 — trusted data is not client-writable: user_roles, enrollments,
--       courses, sections, programs
-- ----------------------------------------------------------------------------
do $t05$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  begin
    insert into public.user_roles (user_id, role) values (u1, 'admin');
    raise exception 'T05.1 FAIL: student must not grant themselves admin';
  exception when insufficient_privilege then null; end;

  begin
    insert into public.enrollments (section_id, user_id, role)
    values ((select id from public.sections where name = 'S1'), u1, 'teacher');
    raise exception 'T05.2 FAIL: self-enrollment as teacher must be rejected';
  exception when insufficient_privilege then null; end;

  begin
    insert into public.courses (code, title) values ('HAX', 'Hax 101');
    raise exception 'T05.3 FAIL: course creation must be service/admin only';
  exception when insufficient_privilege then null; end;

  begin
    insert into public.sections (course_id, name) values ((select id from public.courses where code = 'CS11'), 'S9');
    raise exception 'T05.4 FAIL: section creation must be service/admin only';
  exception when insufficient_privilege then null; end;

  -- denied (silent no-op): enrollment role change
  update public.enrollments set role = 'teacher'
  where user_id = u1 and section_id = (select id from public.sections where name = 'S1');
  if exists (select 1 from public.enrollments where user_id = u1 and role = 'teacher') then
    raise exception 'T05.5 FAIL: enrollment role change must be a no-op';
  end if;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t05$;

-- ----------------------------------------------------------------------------
-- T06 — user_roles visibility: own rows; moderator sees the queue
-- ----------------------------------------------------------------------------
do $t06$
declare mod1 uuid := (select id from auth.users where email = 'mod1@test.dev');
        u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        cnt int;
begin
  -- u1 has no platform roles: sees none
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.user_roles;
  if cnt <> 0 then raise exception 'T06.1 FAIL: u1 should see zero user_roles rows'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- moderator sees all role rows
  perform set_config('request.jwt.claims', json_build_object('sub', mod1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.user_roles;
  if cnt < 2 then raise exception 'T06.2 FAIL: moderator should see all user_roles rows'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t06$;

-- ----------------------------------------------------------------------------
-- T07 — activity visibility (the core privacy rule):
--        personal → owner only; +participants; section → enrolled members
-- ----------------------------------------------------------------------------
do $t07$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        u3 uuid := (select id from auth.users where email = 'u3@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        a_personal uuid; a_section uuid; cnt int;
begin
  -- service creates the canonical rows (imported + teacher-confirmed)
  insert into public.activities (title, activity_type, scope, section_id, due_at,
                                 verification_status, created_by_user_id)
  values ('S1 Midterm Exam', 'exam', 'section', s1, now() + interval '7 days',
          'teacher-confirmed', t1)
  returning id into a_section;

  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.activities (title, activity_type, scope, course_id, due_at,
                                 verification_status, created_by_user_id)
  values ('Draft thesis outline review', 'project', 'personal',
          (select id from public.courses where code = 'CS11'), now() + interval '3 days',
          'student-reported', u1)
  returning id into a_personal;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- owner sees own personal activity
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.activities where id = a_personal) then
    raise exception 'T07.1 FAIL: owner must see own personal activity';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- other student in the SAME section does NOT see the personal activity
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if exists (select 1 from public.activities where id = a_personal) then
    raise exception 'T07.2 FAIL: classmate must not see a personal activity';
  end if;
  -- ... but sees the section activity
  if not exists (select 1 from public.activities where id = a_section) then
    raise exception 'T07.3 FAIL: enrolled student must see section activity';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- student from ANOTHER section sees neither
  perform set_config('request.jwt.claims', json_build_object('sub', u3::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.activities where id in (a_personal, a_section);
  if cnt <> 0 then raise exception 'T07.4 FAIL: non-member must see zero of these activities'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- teacher of the section sees the section activity
  perform set_config('request.jwt.claims', json_build_object('sub', t1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.activities where id = a_section) then
    raise exception 'T07.5 FAIL: section teacher must see the section activity';
  end if;
  if exists (select 1 from public.activities where id = a_personal) then
    raise exception 'T07.6 FAIL: teacher must not see a student private activity by default';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- soft-deleted activities disappear for everyone
  update public.activities set deleted_at = now() where id = a_section;
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if exists (select 1 from public.activities where id = a_section) then
    raise exception 'T07.7 FAIL: deleted activity must be hidden';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
  update public.activities set deleted_at = null where id = a_section;
end $t07$;

-- ----------------------------------------------------------------------------
-- T08 — activity insert rules: students create personal only; teachers may
--        create section/course-scoped activities
-- ----------------------------------------------------------------------------
do $t08$
declare u3 uuid := (select id from auth.users where email = 'u3@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        c1 uuid := (select id from public.courses where code = 'CS11');
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u3::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  begin
    insert into public.activities (title, scope, section_id, created_by_user_id)
    values ('Fake section activity', 'section', s1, u3);
    raise exception 'T08.1 FAIL: non-member student must not create a section activity';
  exception when insufficient_privilege then null; end;

  begin
    insert into public.activities (title, scope, course_id, created_by_user_id)
    values ('Fake course activity', 'course', c1, u3);
    raise exception 'T08.2 FAIL: student must not create a course-wide activity';
  exception when insufficient_privilege then null; end;

  -- forged ownership must fail
  begin
    insert into public.activities (title, scope, created_by_user_id)
    values ('Forged', 'personal', t1);
    raise exception 'T08.3 FAIL: activity with forged created_by must be rejected';
  exception when insufficient_privilege then null; end;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- teacher creates a section activity fine
  perform set_config('request.jwt.claims', json_build_object('sub', t1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.activities (title, activity_type, scope, section_id, created_by_user_id)
  values ('S1 Quiz 2', 'quiz', 'section', s1, t1);
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t08$;

-- ----------------------------------------------------------------------------
-- T09 — verification_status guards: students cannot self-verify;
--        created_by immutable
-- ----------------------------------------------------------------------------
do $t09$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        a uuid;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  begin
    insert into public.activities (title, scope, verification_status, created_by_user_id)
    values ('Self-verified', 'personal', 'teacher-confirmed', u1);
    raise exception 'T09.1 FAIL: student must not create teacher-confirmed activity';
  exception when raise_exception then null; end;

  begin
    insert into public.activities (title, scope, verification_status, created_by_user_id)
    values ('Fake import', 'personal', 'imported', u1);
    raise exception 'T09.2 FAIL: student must not create imported activity';
  exception when raise_exception then null; end;

  insert into public.activities (title, scope, created_by_user_id)
  values ('Legit personal', 'personal', u1) returning id into a;

  begin
    update public.activities set verification_status = 'teacher-confirmed' where id = a;
    raise exception 'T09.3 FAIL: student must not escalate verification_status';
  exception when raise_exception then null; end;

  begin
    update public.activities set created_by_user_id = t1 where id = a;
    raise exception 'T09.4 FAIL: created_by_user_id must be immutable';
  exception when raise_exception then null; end;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- teacher CAN confirm an activity in their section
  update public.activities set section_id = s1, scope = 'section' where id = a;
  perform set_config('request.jwt.claims', json_build_object('sub', t1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.activities set verification_status = 'teacher-confirmed' where id = a;
  if (select verification_status from public.activities where id = a) <> 'teacher-confirmed' then
    raise exception 'T09.5 FAIL: section teacher should be able to confirm';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t09$;

-- ----------------------------------------------------------------------------
-- T10 — activity_sources: visibility follows parent; student_manual source
--        only for own activities; provider identity unique (dedup rule 1)
-- ----------------------------------------------------------------------------
do $t10$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        a1 uuid; a2 uuid; src uuid;
begin
  -- service inserts a Classroom-imported activity + source
  insert into public.activities (title, scope, section_id, verification_status, created_by_user_id)
  values ('Classroom: Lab 3', 'section', s1, 'imported', t1) returning id into a1;
  insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key)
  values (a1, 'google_classroom', 'gc-course-1', 'gc-cw-1', 'google_classroom:gc-course-1:gc-cw-1')
  returning id into src;

  -- duplicate provider identity must be impossible (dedup rule 1)
  begin
    insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key)
    values (a1, 'google_classroom', 'gc-course-1', 'gc-cw-1', 'google_classroom:gc-course-1:gc-cw-1');
    raise exception 'T10.1 FAIL: duplicate source_key must be rejected';
  exception when unique_violation then null; end;

  -- clients cannot fabricate Classroom sources
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  begin
    insert into public.activity_sources (activity_id, provider, source_key, reported_by_user_id)
    values (a1, 'google_classroom', 'google_classroom:x:y', u1);
    raise exception 'T10.2 FAIL: client must not fabricate google_classroom source';
  exception when raise_exception then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u2 (enrolled in S1) can SEE the source because the parent is visible
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.activity_sources where id = src) then
    raise exception 'T10.3 FAIL: source of a visible activity must be visible';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u2 cannot attach their manual source to u1's personal activity
  insert into public.activities (title, scope, created_by_user_id)
  values ('u1 private', 'personal', u1) returning id into a2;
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  begin
    insert into public.activity_sources (activity_id, provider, reported_by_user_id)
    values (a2, 'student_manual', u2);
    raise exception 'T10.4 FAIL: manual source on someone else''s activity must be rejected';
  exception when insufficient_privilege then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u1 CAN attach their own manual source to their own activity
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.activity_sources (activity_id, provider, reported_by_user_id)
  values (a2, 'student_manual', u1);
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t10$;

-- ----------------------------------------------------------------------------
-- T11 — activity_reports: filed as pending only; reporter may withdraw;
--        only staff confirm/reject/link; reporters see only their own
-- ----------------------------------------------------------------------------
do $t11$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u3 uuid := (select id from auth.users where email = 'u3@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        c1 uuid := (select id from public.courses where code = 'CS11');
        r uuid; cnt int;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  begin
    insert into public.activity_reports (reporter_user_id, title, section_id, status)
    values (u1, 'Oral exam schedule', s1, 'confirmed');
    raise exception 'T11.1 FAIL: reports must be filed as pending';
  exception when insufficient_privilege then null; end;

  insert into public.activity_reports (reporter_user_id, title, section_id)
  values (u1, 'Oral exam schedule', s1) returning id into r;

  -- reporter cannot confirm their own report
  begin
    update public.activity_reports set status = 'confirmed' where id = r;
    raise exception 'T11.2 FAIL: reporter must not confirm own report';
  exception when raise_exception then null; end;

  -- reporter CAN withdraw
  update public.activity_reports set status = 'withdrawn' where id = r;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
  update public.activity_reports set status = 'pending' where id = r;

  -- section teacher confirms and links the report to the canonical activity
  perform set_config('request.jwt.claims', json_build_object('sub', t1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.activity_reports
  set status = 'confirmed', activity_id = (select id from public.activities where title = 'S1 Midterm Exam' limit 1)
  where id = r;
  if (select status from public.activity_reports where id = r) <> 'confirmed' then
    raise exception 'T11.3 FAIL: section teacher should be able to confirm + link';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- a teacher from ANOTHER section cannot even see this report
  perform set_config('request.jwt.claims', json_build_object('sub', u3::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.activity_reports where id = r;
  if cnt <> 0 then raise exception 'T11.4 FAIL: unrelated student must not see the report'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t11$;

-- ----------------------------------------------------------------------------
-- T12 — activity_participants: sharing a personal activity makes it visible
--        to the participant; only the owner/teacher manages participants
-- ----------------------------------------------------------------------------
do $t12$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        u3 uuid := (select id from auth.users where email = 'u3@test.dev');
        a uuid;
begin
  select id into a from public.activities where title = 'u1 private';
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.activity_participants (activity_id, user_id) values (a, u2);
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- now u2 sees it
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.activities where id = a) then
    raise exception 'T12.1 FAIL: participant must see the shared activity';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u3 still cannot
  perform set_config('request.jwt.claims', json_build_object('sub', u3::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if exists (select 1 from public.activities where id = a) then
    raise exception 'T12.2 FAIL: non-participant must still not see it';
  end if;
  -- and u3 cannot force themselves in
  begin
    insert into public.activity_participants (activity_id, user_id) values (a, u3);
    raise exception 'T12.3 FAIL: self-adding as participant must be rejected';
  exception when insufficient_privilege then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t12$;

-- ----------------------------------------------------------------------------
-- T13 — posts: ownership enforced; author & anonymity immutable
-- ----------------------------------------------------------------------------
do $t13$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p uuid;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.posts (author_id, content) values (u1, 'Tips for the CS11 midterm?')
  returning id into p;

  -- forged authorship must fail
  begin
    insert into public.posts (author_id, content) values (u2, 'not really');
    raise exception 'T13.1 FAIL: forged author_id must be rejected';
  exception when insufficient_privilege then null; end;

  update public.posts set content = 'Tips for the CS11 midterm? (edited)' where id = p;
  if (select edited_at from public.posts where id = p) is null then
    raise exception 'T13.2 FAIL: content edit must set edited_at';
  end if;

  begin
    update public.posts set is_anonymous = true where id = p;
    raise exception 'T13.3 FAIL: is_anonymous must be immutable';
  exception when raise_exception then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u2 cannot edit or delete u1's post (silent no-ops under RLS)
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.posts set content = 'hax' where id = p;
  if (select content from public.posts where id = p) <> 'Tips for the CS11 midterm? (edited)' then
    raise exception 'T13.4 FAIL: editing another user''s post must be a no-op';
  end if;
  delete from public.posts where id = p;
  if not exists (select 1 from public.posts where id = p) then
    raise exception 'T13.5 FAIL: deleting another user''s post must be a no-op';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t13$;

-- ----------------------------------------------------------------------------
-- T14 — post_images: max 3 enforced by the database; only on own posts
-- ----------------------------------------------------------------------------
do $t14$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p uuid := (select id from public.posts where content like 'Tips for the CS11 midterm?%' limit 1);
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  insert into public.post_images (post_id, storage_path, position)
  values (p, u1::text || '/' || p || '/img1.jpg', 1),
         (p, u1::text || '/' || p || '/img2.jpg', 2),
         (p, u1::text || '/' || p || '/img3.jpg', 3);

  -- 4th image: impossible no matter the client
  begin
    insert into public.post_images (post_id, storage_path, position)
    values (p, u1::text || '/' || p || '/img4.jpg', 4);
    raise exception 'T14.1 FAIL: 4th image must be rejected';
  exception when check_violation then null; end;

  -- sneaky attempt: reuse an existing position
  begin
    insert into public.post_images (post_id, storage_path, position)
    values (p, u1::text || '/' || p || '/extra.jpg', 1);
    raise exception 'T14.2 FAIL: duplicate position must be rejected';
  exception when unique_violation then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u2 cannot attach an image to u1's post
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  begin
    insert into public.post_images (post_id, storage_path, position)
    values (p, u2::text || '/' || p || '/evil.jpg', 3);
    raise exception 'T14.3 FAIL: image on another user''s post must be rejected';
  exception when insufficient_privilege then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t14$;

-- ----------------------------------------------------------------------------
-- T15 — comments: nesting rules (same post, live parent, depth limit),
--        immutability of linkage, ownership
-- ----------------------------------------------------------------------------
do $t15$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p1 uuid; p2 uuid; c_root uuid; c5 uuid; node uuid;
begin
  -- u1 creates two posts
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.posts (author_id, content) values (u1, 'Post A')
  returning id into p1;
  insert into public.posts (author_id, content) values (u1, 'Post B')
  returning id into p2;

  -- u2 comments on Post A
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  insert into public.comments (post_id, author_id, content) values (p1, u2, 'root comment')
  returning id into c_root;

  -- parent from a different post must be rejected
  begin
    insert into public.comments (post_id, parent_comment_id, author_id, content)
    values (p2, c_root, u2, 'wrong post');
    raise exception 'T15.1 FAIL: cross-post parent must be rejected';
  exception when raise_exception then null; end;

  -- build a 5-deep chain (root + four replies)
  node := c_root;
  for i in 1..4 loop
    insert into public.comments (post_id, parent_comment_id, author_id, content)
    values (p1, node, u2, 'reply level ' || i) returning id into node;
  end loop;
  c5 := node;

  -- one more reply would exceed the limit
  begin
    insert into public.comments (post_id, parent_comment_id, author_id, content)
    values (p1, c5, u2, 'too deep');
    raise exception 'T15.2 FAIL: depth limit must be enforced';
  exception when raise_exception then null; end;

  -- parent linkage immutable
  begin
    update public.comments set parent_comment_id = null where id = c5;
    raise exception 'T15.3 FAIL: parent linkage must be immutable';
  exception when raise_exception then null; end;

  -- deleted parent cannot be replied to
  update public.comments set deleted_at = now() where id = c_root;
  begin
    insert into public.comments (post_id, parent_comment_id, author_id, content)
    values (p1, c_root, u2, 'reply to deleted');
    raise exception 'T15.4 FAIL: replying to a deleted comment must be rejected';
  exception when raise_exception then null; end;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u1 cannot edit u2's comment (silent no-op under RLS)
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.comments set content = 'hax' where id = c5;
  if (select content from public.comments where id = c5) <> 'reply level 4' then
    raise exception 'T15.5 FAIL: editing another user''s comment must be a no-op';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t15$;

-- ----------------------------------------------------------------------------
-- T16 — likes: unique per (user, target); cannot like invisible content
-- ----------------------------------------------------------------------------
do $t16$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p1 uuid := (select id from public.posts where content = 'Post A');
        c_live uuid := (select id from public.comments where content = 'reply level 1');
        c_dead uuid := (select id from public.comments where content = 'root comment');
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  insert into public.post_likes (post_id, user_id) values (p1, u1);

  begin
    insert into public.post_likes (post_id, user_id) values (p1, u1);
    raise exception 'T16.1 FAIL: duplicate post like must be rejected';
  exception when unique_violation then null; end;

  insert into public.comment_likes (comment_id, user_id) values (c_live, u1);
  begin
    insert into public.comment_likes (comment_id, user_id) values (c_live, u1);
    raise exception 'T16.2 FAIL: duplicate comment like must be rejected';
  exception when unique_violation then null; end;

  -- liking a soft-deleted comment must be rejected: it is invisible
  begin
    insert into public.comment_likes (comment_id, user_id) values (c_dead, u1);
    raise exception 'T16.3 FAIL: liking a deleted comment must be rejected';
  exception when insufficient_privilege then null; end;

  -- forged like (user_id != auth.uid()) must fail
  begin
    insert into public.post_likes (post_id, user_id) values (p1, u2);
    raise exception 'T16.4 FAIL: forged like user_id must be rejected';
  exception when insufficient_privilege then null; end;

  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t16$;

-- ----------------------------------------------------------------------------
-- T17 — anonymity: author identity never leaks through the feed views,
--        while the author keeps full ownership via the base tables
-- ----------------------------------------------------------------------------
do $t17$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p_anon uuid; c_anon uuid;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.posts (author_id, content, is_anonymous)
  values (u1, 'Anonymous struggles with proofs', true)
  returning id into p_anon;
  insert into public.comments (post_id, author_id, content, is_anonymous)
  values (p_anon, u1, 'me too (anon)', true) returning id into c_anon;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- other users: no author identity through the views
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if (select author_id from public.feed_posts where id = p_anon) is not null then
    raise exception 'T17.1 FAIL: feed_posts must null the anonymous author';
  end if;
  if (select author_id from public.feed_comments where id = c_anon) is not null then
    raise exception 'T17.2 FAIL: feed_comments must null the anonymous author';
  end if;
  -- but is_anonymous flag is present so the UI can label it
  if (select is_anonymous from public.feed_posts where id = p_anon) is not true then
    raise exception 'T17.3 FAIL: is_anonymous flag must be exposed';
  end if;
  -- counts work through the view
  if (select like_count from public.feed_posts where id = p_anon) <> 0 then
    raise exception 'T17.4 FAIL: like_count expected 0';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- the author still sees their own authorship on the base table
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if (select author_id from public.posts where id = p_anon) is distinct from u1 then
    raise exception 'T17.5 FAIL: author must see own authorship on base table';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t17$;

-- ----------------------------------------------------------------------------
-- T18 — blocking: the blocker no longer sees the blocked user's content
-- ----------------------------------------------------------------------------
do $t18$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p2 uuid; c uuid;
begin
  -- u2 wrote 'Post B'?? no: u1 wrote Post B; create a u2 post + comment
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.posts (author_id, content) values (u2, 'u2 announcement')
  returning id into p2;
  insert into public.comments (post_id, author_id, content)
  values ((select id from public.posts where content = 'Post A'), u2, 'u2 comment on A')
  returning id into c;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u1 blocks u2
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.user_blocks (blocker, blocked) values (u1, u2);

  if exists (select 1 from public.posts where id = p2) then
    raise exception 'T18.1 FAIL: blocked user''s post must be hidden from the blocker';
  end if;
  if exists (select 1 from public.comments where id = c) then
    raise exception 'T18.2 FAIL: blocked user''s comment must be hidden from the blocker';
  end if;
  -- self-block is impossible
  begin
    insert into public.user_blocks (blocker, blocked) values (u1, u1);
    raise exception 'T18.3 FAIL: self-block must be rejected';
  exception when check_violation then null; end;
  -- unblock restores visibility
  delete from public.user_blocks where blocker = u1 and blocked = u2;
  if not exists (select 1 from public.posts where id = p2) then
    raise exception 'T18.4 FAIL: unblocking must restore visibility';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t18$;

-- ----------------------------------------------------------------------------
-- T19 — storage: private bucket, own-folder writes only, authenticated reads
-- ----------------------------------------------------------------------------
do $t19$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        p1 uuid := (select id from public.posts where content = 'Post A');
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  insert into storage.objects (bucket_id, name, owner)
  values ('community-images', u1::text || '/' || p1::text || '/photo.jpg', u1);

  -- writing into someone else's folder must fail
  begin
    insert into storage.objects (bucket_id, name, owner)
    values ('community-images', u2::text || '/stolen/file.jpg', u1);
    raise exception 'T19.1 FAIL: upload into another user''s folder must be rejected';
  exception when insufficient_privilege then null; end;

  -- fixture: an object owned by u2 (created by the service context)
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
  insert into storage.objects (bucket_id, name, owner)
  values ('community-images', u2::text || '/own/file.jpg', u2);
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  -- deleting someone else's object must be a silent no-op
  delete from storage.objects where name = u2::text || '/own/file.jpg';
  if not exists (select 1 from storage.objects where name = u2::text || '/own/file.jpg') then
    raise exception 'T19.2 FAIL: deleting another user''s object must be a no-op';
  end if;

  -- reads work for authenticated users on the community bucket
  if not exists (select 1 from storage.objects where bucket_id = 'community-images') then
    raise exception 'T19.3 FAIL: authenticated read of community images must work';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- anon cannot read
  perform set_config('request.jwt.claims', '', true);
  execute 'set local role anon';
  if exists (select 1 from storage.objects where bucket_id = 'community-images') then
    raise exception 'T19.4 FAIL: anon must not read community images';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t19$;

-- ----------------------------------------------------------------------------
-- T20 — enrollments: member sees own; section teacher sees their section;
--        unrelated teacher does not
-- ----------------------------------------------------------------------------
do $t20$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        t2 uuid := (select id from auth.users where email = 't2@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        cnt int;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.enrollments where section_id = s1;
  if cnt <> 1 then raise exception 'T20.1 FAIL: member should see own enrollment row (got %)', cnt; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  perform set_config('request.jwt.claims', json_build_object('sub', t1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.enrollments where section_id = s1;
  if cnt < 3 then raise exception 'T20.2 FAIL: section teacher should see the section''s enrollments'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  perform set_config('request.jwt.claims', json_build_object('sub', t2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.enrollments where section_id = s1;
  if cnt <> 0 then raise exception 'T20.3 FAIL: unrelated teacher must not see another section''s enrollments'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t20$;

-- ----------------------------------------------------------------------------
-- T21 — content_reports: anyone can report; only moderators triage;
--        reporters see their own only
-- ----------------------------------------------------------------------------
do $t21$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u2 uuid := (select id from auth.users where email = 'u2@test.dev');
        mod1 uuid := (select id from auth.users where email = 'mod1@test.dev');
        p1 uuid := (select id from public.posts where content = 'Post A');
        rep uuid; cnt int;
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.content_reports (reporter_id, target_type, target_id, reason)
  values (u1, 'post', p1, 'spam') returning id into rep;

  -- reporter cannot triage their own report (silent no-op: the update
  -- policy only matches moderators/admins)
  update public.content_reports set status = 'dismissed' where id = rep;
  if (select status from public.content_reports where id = rep) <> 'open' then
    raise exception 'T21.1 FAIL: reporter must not change report status';
  end if;

  -- forged reporter must fail
  begin
    insert into public.content_reports (reporter_id, target_type, target_id, reason)
    values (u2, 'post', p1, 'spam');
    raise exception 'T21.2 FAIL: forged reporter_id must be rejected';
  exception when insufficient_privilege then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u2 (who filed nothing) sees no reports at all — u1's report is invisible
  perform set_config('request.jwt.claims', json_build_object('sub', u2::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into cnt from public.content_reports;
  if cnt <> 0 then raise exception 'T21.3 FAIL: u2 must not see u1''s report'; end if;
  -- u2 files their own report and immediately sees exactly that one
  insert into public.content_reports (reporter_id, target_type, target_id, reason)
  values (u2, 'post', p1, 'off-topic');
  select count(*) into cnt from public.content_reports;
  if cnt <> 1 then raise exception 'T21.3b FAIL: u2 should see exactly their own report'; end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- moderator sees and triages all
  perform set_config('request.jwt.claims', json_build_object('sub', mod1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.content_reports where id = rep) then
    raise exception 'T21.4 FAIL: moderator must see the report queue';
  end if;
  update public.content_reports set status = 'actioned' where id = rep;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t21$;

-- ----------------------------------------------------------------------------
-- T22 — feed view aggregation sanity: like_count / comment_count correct
-- ----------------------------------------------------------------------------
do $t22$
declare p1 uuid := (select id from public.posts where content = 'Post A');
        p_img uuid := (select id from public.posts where content like 'Tips for the CS11 midterm?%');
begin
  if (select like_count from public.feed_posts where id = p1) <> 1 then
    raise exception 'T22.1 FAIL: like_count expected 1';
  end if;
  if (select comment_count from public.feed_posts where id = p1) < 1 then
    raise exception 'T22.2 FAIL: comment_count expected >= 1';
  end if;
  if (select image_count from public.feed_posts where id = p_img) <> 3 then
    raise exception 'T22.3 FAIL: image_count expected 3';
  end if;
end $t22$;

-- ----------------------------------------------------------------------------
-- T23 — activity_groups: visible only through visible member activities;
--        not client-writable
-- ----------------------------------------------------------------------------
do $t23$
declare u1 uuid := (select id from auth.users where email = 'u1@test.dev');
        u3 uuid := (select id from auth.users where email = 'u3@test.dev');
        t1 uuid := (select id from auth.users where email = 't1@test.dev');
        s1 uuid := (select id from public.sections where name = 'S1');
        s2 uuid := (select id from public.sections where name = 'S2');
        g uuid; a1 uuid; a2 uuid; cnt int;
begin
  -- service creates the same assignment published separately in two sections
  insert into public.activities (title, scope, section_id, due_at, verification_status, created_by_user_id)
  values ('Lab 5 report', 'section', s1, now() + interval '5 days', 'teacher-confirmed', t1)
  returning id into a1;
  insert into public.activities (title, scope, section_id, due_at, verification_status, created_by_user_id)
  values ('Lab 5 report', 'section', s2, now() + interval '5 days', 'teacher-confirmed',
          (select id from auth.users where email = 't2@test.dev'))
  returning id into a2;

  -- both keep their own row (dedup rule 4: no blind cross-section merge)
  if (select count(*) from public.activities where title = 'Lab 5 report') <> 2 then
    raise exception 'T23.1 FAIL: cross-section copies must remain separate rows';
  end if;

  insert into public.activity_groups (title, created_by_user_id) values ('Lab 5 (all sections)', t1)
  returning id into g;
  insert into public.activity_group_members (group_id, activity_id) values (g, a1), (g, a2);

  -- u1 (S1 member) sees the group via the visible member activity
  perform set_config('request.jwt.claims', json_build_object('sub', u1::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.activity_groups where id = g) then
    raise exception 'T23.2 FAIL: group with a visible member must be visible';
  end if;
  -- u1 sees only the S1 member row, not the S2 one
  select count(*) into cnt from public.activity_group_members where group_id = g;
  if cnt <> 1 then raise exception 'T23.3 FAIL: expected 1 visible member row, got %', cnt; end if;

  -- clients cannot create groups or memberships (trusted operation)
  begin
    insert into public.activity_groups (title, created_by_user_id) values ('hax', u1);
    raise exception 'T23.4 FAIL: group creation must be service-only';
  exception when insufficient_privilege then null; end;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);

  -- u3 sees the group too (via S2 member row)
  perform set_config('request.jwt.claims', json_build_object('sub', u3::text, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  if not exists (select 1 from public.activity_groups where id = g) then
    raise exception 'T23.5 FAIL: u3 should see the group via S2 member';
  end if;
  execute 'set local role postgres';
  perform set_config('request.jwt.claims', '', true);
end $t23$;

-- ----------------------------------------------------------------------------
-- Done
-- ----------------------------------------------------------------------------
select 'ALL STUDENT CODEX POLICY TESTS PASSED' as result;
