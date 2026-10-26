-- ============================================================================
-- Student Codex migration 0010 — DEMO SEED (Phase 12 rehearsal data)
--
-- Clearly labeled DEMO data so the app, the teacher dashboard and the
-- end-to-end rehearsal have real-looking content: one demo teacher, three
-- demo students, six BSCS courses with one section each, section-scoped
-- teacher-confirmed activities spread over the coming two weeks, two
-- "imported" activities carrying google_classroom source rows (proving the
-- Phase 5/6 import path with data), and a small community feed with
-- comments, replies and likes.
--
-- Everything uses FIXED uuids and idempotent guards, so re-running is safe.
-- DEMO ACCOUNTS (documented for the developer, never for production):
--   demo.teacher@studentcodex.app        / demo-teacher-123  (teacher role)
--   maria.santos@studentcodex.app        / demo-student-123
--   juan.delacruz@studentcodex.app       / demo-student-123
--   angela.reyes@studentcodex.app        / demo-student-123
-- Cleanup: delete these auth.users (cascades profiles/enrollments/posts)
-- plus the fixed-uuid courses/sections/activities below.
--
-- SQL-created GoTrue users MUST mirror GoTrue's own row shape: token
-- columns are EMPTY STRINGS (never NULL - GoTrue scans them as non-nullable
-- strings and a NULL makes every password grant 500), bcrypt cost 10, and
-- an auth.identities row must exist for the email provider.
-- ============================================================================

-- bcrypt hashes for the demo passwords (pgcrypto). Hosted Supabase installs
-- pgcrypto into the "extensions" schema; the local sandbox into public. Put
-- both on the search path so crypt()/gen_salt() resolve everywhere.
create extension if not exists pgcrypto;
set search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Demo accounts (auth.users). Email is pre-confirmed so sign-in works
-- immediately; passwords are bcrypt hashes set directly — the same storage
-- GoTrue itself uses.
-- ---------------------------------------------------------------------------
insert into auth.users (
  id, instance_id, aud, role, email, encrypted_password,
  email_confirmed_at, confirmation_sent_at, created_at, updated_at,
  confirmation_token, recovery_token, email_change, email_change_token_new,
  raw_app_meta_data, raw_user_meta_data
) values
  ('a1000000-0000-4000-8000-000000000001', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'demo.teacher@studentcodex.app',
   crypt('demo-teacher-123', gen_salt('bf', 10)), now(), now(), now(), now(),
   '', '', '', '',
   '{"provider":"email","providers":["email"]}', '{"first_name":"Teodoro","last_name":"Garcia","demo":true}'),
  ('a1000000-0000-4000-8000-000000000002', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'maria.santos@studentcodex.app',
   crypt('demo-student-123', gen_salt('bf', 10)), now(), now(), now(), now(),
   '', '', '', '',
   '{"provider":"email","providers":["email"]}', '{"first_name":"Maria","last_name":"Santos","demo":true}'),
  ('a1000000-0000-4000-8000-000000000003', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'juan.delacruz@studentcodex.app',
   crypt('demo-student-123', gen_salt('bf', 10)), now(), now(), now(), now(),
   '', '', '', '',
   '{"provider":"email","providers":["email"]}', '{"first_name":"Juan","last_name":"Dela Cruz","demo":true}'),
  ('a1000000-0000-4000-8000-000000000004', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'angela.reyes@studentcodex.app',
   crypt('demo-student-123', gen_salt('bf', 10)), now(), now(), now(), now(),
   '', '', '', '',
   '{"provider":"email","providers":["email"]}', '{"first_name":"Angela","last_name":"Reyes","demo":true}')
on conflict (id) do nothing;

-- GoTrue requires an auth.identities row per login identity (its GORM model
-- joins on it during the password grant). provider_id is the user's uuid as
-- text, identity_data carries the same sub/email GoTrue itself would write.
insert into auth.identities (
  id, user_id, provider_id, provider, identity_data,
  last_sign_in_at, created_at, updated_at
)
select gen_random_uuid(), u.id, u.id::text, 'email',
       jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
       now(), now(), now()
from auth.users u
where u.email like '%@studentcodex.app'
  and not exists (
    select 1 from auth.identities i
    where i.user_id = u.id and i.provider = 'email'
  );

-- The on_auth_user_created trigger has created bare profiles rows; fill in
-- names/username/school details (registry ids looked up BY NAME so this
-- works on any environment that ran migration 0007).
update public.profiles set
  first_name = 'Teodoro', last_name = 'Garcia',
  program_id = (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1),
  year_level = 2,
  campus_id = (select id from public.campuses where name = 'Goa Campus (Main Campus)' limit 1),
  year_section_id = (select id from public.year_sections where name = 'A' limit 1)
where id = 'a1000000-0000-4000-8000-000000000001';

update public.profiles set
  first_name = 'Maria', last_name = 'Santos',
  program_id = (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1),
  year_level = 2,
  campus_id = (select id from public.campuses where name = 'Goa Campus (Main Campus)' limit 1),
  year_section_id = (select id from public.year_sections where name = 'A' limit 1)
where id = 'a1000000-0000-4000-8000-000000000002';

update public.profiles set
  first_name = 'Juan', last_name = 'Dela Cruz',
  program_id = (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1),
  year_level = 2,
  campus_id = (select id from public.campuses where name = 'Goa Campus (Main Campus)' limit 1),
  year_section_id = (select id from public.year_sections where name = 'A' limit 1)
where id = 'a1000000-0000-4000-8000-000000000003';

update public.profiles set
  first_name = 'Angela', last_name = 'Reyes',
  program_id = (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1),
  year_level = 2,
  campus_id = (select id from public.campuses where name = 'Goa Campus (Main Campus)' limit 1),
  year_section_id = (select id from public.year_sections where name = 'A' limit 1)
where id = 'a1000000-0000-4000-8000-000000000004';

-- Teacher role grant (trusted table — written only here, by the service).
insert into public.user_roles (user_id, role)
values ('a1000000-0000-4000-8000-000000000001', 'teacher')
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Demo courses + one section each (BSCS 2-A, AY 2026-2027).
-- ---------------------------------------------------------------------------
insert into public.courses (id, program_id, code, title) values
  ('b2000000-0000-4000-8000-000000000001', (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1), 'CS 101', 'Introduction to Computing'),
  ('b2000000-0000-4000-8000-000000000002', (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1), 'CS 102', 'Computer Programming 1'),
  ('b2000000-0000-4000-8000-000000000003', (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1), 'CS 201', 'Data Structures and Algorithms'),
  ('b2000000-0000-4000-8000-000000000004', (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1), 'CS 211', 'Discrete Mathematics'),
  ('b2000000-0000-4000-8000-000000000005', (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1), 'GE 101', 'Mathematics in the Modern World'),
  ('b2000000-0000-4000-8000-000000000006', (select id from public.programs where program_name = 'Bachelor of Science in Computer Science (BSCS)' limit 1), 'CS 221', 'Database Management Systems')
on conflict (id) do nothing;

insert into public.sections (id, course_id, name, term)
select c.id_candidate::uuid, c.course_id::uuid, 'BSCS 2-A', 'AY 2026-2027'
from (
  values
    ('c3000000-0000-4000-8000-000000000001', 'b2000000-0000-4000-8000-000000000001'),
    ('c3000000-0000-4000-8000-000000000002', 'b2000000-0000-4000-8000-000000000002'),
    ('c3000000-0000-4000-8000-000000000003', 'b2000000-0000-4000-8000-000000000003'),
    ('c3000000-0000-4000-8000-000000000004', 'b2000000-0000-4000-8000-000000000004'),
    ('c3000000-0000-4000-8000-000000000005', 'b2000000-0000-4000-8000-000000000005'),
    ('c3000000-0000-4000-8000-000000000006', 'b2000000-0000-4000-8000-000000000006')
) as c(id_candidate, course_id)
where not exists (
  select 1 from public.sections s
  where s.course_id = c.course_id::uuid
    and s.name = 'BSCS 2-A'
    and coalesce(s.term, '') = 'AY 2026-2027'
);

-- ---------------------------------------------------------------------------
-- Demo enrollments: the demo teacher teaches all six sections; the three
-- demo students and the developer's test account (Kirby) study in them.
-- ---------------------------------------------------------------------------
insert into public.enrollments (section_id, user_id, role)
select s.id, u.user_id::uuid, u.role
from public.sections s
join (
  values
    ('a1000000-0000-4000-8000-000000000001', 'teacher'),
    ('a1000000-0000-4000-8000-000000000002', 'student'),
    ('a1000000-0000-4000-8000-000000000003', 'student'),
    ('a1000000-0000-4000-8000-000000000004', 'student'),
    ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'student')
) as u(user_id, role) on true
where s.name = 'BSCS 2-A' and coalesce(s.term, '') = 'AY 2026-2027'
  and not exists (
    select 1 from public.enrollments e
    where e.section_id = s.id and e.user_id = u.user_id::uuid
  );

-- ---------------------------------------------------------------------------
-- Demo activities: teacher-confirmed section work over the coming two
-- weeks, plus two "imported" items with google_classroom source rows
-- (the Phase 5/6 import path demonstrated with data — source_meta carries
-- {"demo": true}).
-- ---------------------------------------------------------------------------
insert into public.activities (
  id, title, description, activity_type, course_id, section_id,
  due_at, estimated_effort_minutes, scope, verification_status, created_by_user_id
) values
  ('d4000000-0000-4000-8000-000000000001',
   'Machine Problem 1 — Arrays and Pointers',
   'Implement insert, delete, and search on a dynamic integer array. Submit source + sample run.',
   'assignment', 'b2000000-0000-4000-8000-000000000002', 'c3000000-0000-4000-8000-000000000002',
   now() + interval '2 days', 180, 'section', 'teacher-confirmed', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000002',
   'Quiz 3 — Recursion',
   'Coverage: recursive definitions, tracing, base cases.',
   'quiz', 'b2000000-0000-4000-8000-000000000003', 'c3000000-0000-4000-8000-000000000003',
   now() + interval '3 days', 60, 'section', 'teacher-confirmed', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000003',
   'Laboratory Report — Linked Lists',
   'Compare singly vs doubly linked list operations; include complexity table.',
   'project', 'b2000000-0000-4000-8000-000000000003', 'c3000000-0000-4000-8000-000000000003',
   now() + interval '6 days', 240, 'section', 'teacher-confirmed', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000004',
   'Problem Set 4 — Logical Equivalences',
   'Show truth tables for the listed equivalences.',
   'assignment', 'b2000000-0000-4000-8000-000000000004', 'c3000000-0000-4000-8000-000000000004',
   now() + interval '4 days', 90, 'section', 'teacher-confirmed', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000005',
   'Preliminary Examination',
   'Coverage: weeks 1-6. Closed book; one formula sheet allowed.',
   'exam', 'b2000000-0000-4000-8000-000000000005', 'c3000000-0000-4000-8000-000000000005',
   now() + interval '9 days', 120, 'section', 'teacher-confirmed', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000006',
   'Normalization Exercise (1NF to 3NF)',
   'Normalize the given enrollment table to third normal form; justify each step.',
   'assignment', 'b2000000-0000-4000-8000-000000000006', 'c3000000-0000-4000-8000-000000000006',
   now() + interval '12 days', 150, 'section', 'teacher-confirmed', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000007',
   'Classroom announcement: consultation hours moved',
   'Posted in Google Classroom by the teacher.',
   'announcement', 'b2000000-0000-4000-8000-000000000002', 'c3000000-0000-4000-8000-000000000002',
   now() + interval '1 day', null, 'section', 'imported', 'a1000000-0000-4000-8000-000000000001'),
  ('d4000000-0000-4000-8000-000000000008',
   'Classroom coursework: Chapter 4 exercises',
   'Imported from Google Classroom (demo item).',
   'assignment', 'b2000000-0000-4000-8000-000000000005', 'c3000000-0000-4000-8000-000000000005',
   now() + interval '7 days', 100, 'section', 'imported', 'a1000000-0000-4000-8000-000000000001')
on conflict (id) do nothing;

-- Provider source rows for the two imported demo items (unique source_key
-- makes repeated application converge to one row each).
insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key, reported_by_user_id, source_meta)
select v.activity_id::uuid, 'google_classroom', v.external_course_id, v.external_item_id,
       v.source_key, v.reported_by::uuid, v.meta::jsonb
from (
  values
    ('d4000000-0000-4000-8000-000000000007', 'demo-course-cs102', 'demo-ann-1',
     'google_classroom:demo-course-cs102:demo-ann-1', 'a1000000-0000-4000-8000-000000000001',
     '{"demo": true, "title": "consultation hours moved"}'),
    ('d4000000-0000-4000-8000-000000000008', 'demo-course-ge101', 'demo-cw-1',
     'google_classroom:demo-course-ge101:demo-cw-1', 'a1000000-0000-4000-8000-000000000001',
     '{"demo": true, "title": "Chapter 4 exercises"}')
) as v(activity_id, external_course_id, external_item_id, source_key, reported_by, meta)
where not exists (
  select 1 from public.activity_sources s
  where s.source_key = v.source_key
);

-- ---------------------------------------------------------------------------
-- Demo community feed: five posts (one anonymous), comments with a nested
-- reply, and likes.
-- ---------------------------------------------------------------------------
insert into public.posts (id, author_id, content, is_anonymous, created_at) values
  ('e5000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002',
   'Study group for the CS 201 quiz tomorrow? Library 2nd floor, 4 PM. Bring your recursion notes!', false,
   now() - interval '3 hours'),
  ('e5000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000003',
   'Finished the linked list lab early — the complexity table took longer than the code. Anyone else?', false,
   now() - interval '9 hours'),
  ('e5000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000004',
   'Reminder: prof announced the GE 101 prelim is moved to next week. Check the syllabus page!', false,
   now() - interval '1 day'),
  ('e5000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000002',
   'How do you people actually balance org duties and Machine Problems... asking anonymously for a reason.', true,
   now() - interval '2 days'),
  ('e5000000-0000-4000-8000-000000000005', '3c8c2276-ca5a-428b-86df-5062f1ddb1a0',
   'Testing the Student Codex community feed — hello BSCS 2-A!', false,
   now() - interval '5 hours')
on conflict (id) do nothing;

insert into public.comments (id, post_id, parent_comment_id, author_id, content, is_anonymous, created_at) values
  ('f6000000-0000-4000-8000-000000000001', 'e5000000-0000-4000-8000-000000000001', null,
   'a1000000-0000-4000-8000-000000000003', 'I''m in — I''ll bring the tracing exercises.', false,
   now() - interval '2 hours'),
  ('f6000000-0000-4000-8000-000000000002', 'e5000000-0000-4000-8000-000000000001',
   'f6000000-0000-4000-8000-000000000001',
   'a1000000-0000-4000-8000-000000000002', 'Perfect, see you both there.', false,
   now() - interval '1 hour'),
  ('f6000000-0000-4000-8000-000000000003', 'e5000000-0000-4000-8000-000000000002', null,
   'a1000000-0000-4000-8000-000000000004', 'Same. The tables are 90% of the grade honestly.', false,
   now() - interval '8 hours'),
  ('f6000000-0000-4000-8000-000000000004', 'e5000000-0000-4000-8000-000000000004', null,
   'a1000000-0000-4000-8000-000000000003', 'Calendar app + pomodoros. Still behind, but behind ON SCHEDULE.', true,
   now() - interval '1 day')
on conflict (id) do nothing;

insert into public.post_likes (post_id, user_id)
select p.post_id::uuid, p.user_id::uuid
from (
  values
    ('e5000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003'),
    ('e5000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000004'),
    ('e5000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000002'),
    ('e5000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000002'),
    ('e5000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000003'),
    ('e5000000-0000-4000-8000-000000000003', '3c8c2276-ca5a-428b-86df-5062f1ddb1a0'),
    ('e5000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000002')
) as p(post_id, user_id)
where not exists (
  select 1 from public.post_likes pl
  where pl.post_id = p.post_id::uuid and pl.user_id = p.user_id::uuid
);

insert into public.comment_likes (comment_id, user_id)
select c.comment_id::uuid, c.user_id::uuid
from (
  values
    ('f6000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002'),
    ('f6000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000002')
) as c(comment_id, user_id)
where not exists (
  select 1 from public.comment_likes cl
  where cl.comment_id = c.comment_id::uuid and cl.user_id = c.user_id::uuid
);
