-- ============================================================================
-- Student Codex migration 0012 — PROGRAM-CAMPUS ASSOCIATION
-- The official ParSU registry: every program belongs to the campus (or
-- campuses) that offer it. One row per (program, campus) pair:
--   Goa 28 · San Jose 2 · Lagonoy 3 · Sagñay 2 · Salogon 2 · Caramoan 2 ·
--   Tinambac 3 = 42 rows (39 distinct programs; BSHM, BSF and BSIT are each
--   offered at two campuses).
-- The sign-up form asks campus FIRST, then shows only that campus's
-- programs (no campus chosen yet = the all-campus list).
--
-- Data migration on existing databases:
--   1. programs gains campus_id (FK campuses, on delete cascade);
--   2. the 42 official rows are inserted with FIXED ids;
--   3. profiles + courses are remapped from the old flat rows to the new
--      campus rows — preferring the profile's own campus, then Goa, then
--      any campus offering the program (courses have no campus: Goa first);
--   4. the old flat rows (campus_id null) are deleted — references to
--      programs that no longer exist (e.g. BSES) become null via the
--      existing on-delete-set-null FKs;
--   5. campus_id becomes NOT NULL and the uniqueness moves from
--      (university_name, program_name) to include campus_id.
--
-- Idempotent: fixed ids + on-conflict-do-nothing + if-not-exists guards.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) campus_id column.
-- ---------------------------------------------------------------------------
alter table public.programs
  add column if not exists campus_id uuid references public.campuses (id) on delete cascade;

-- The old two-column unique must go before duplicate program names
-- (BSHM/BSF/BSIT at two campuses each) can exist as separate rows.
alter table public.programs
  drop constraint if exists programs_university_name_program_name_key;

-- ---------------------------------------------------------------------------
-- 2) The official registry: 42 (program, campus) rows with fixed ids.
--    e1000000-0000-4000-8000-0000000000XX — Goa 01..1c, San Jose 1d..1e,
--    Lagonoy 1f..21, Sagñay 22..23, Salogon 24..25, Caramoan 26..27,
--    Tinambac 28..2a.
-- ---------------------------------------------------------------------------
insert into public.programs (id, university_name, program_name, campus_id)
select v.id::uuid, 'Partido State University', v.program_name, c.id
from (values
  ('e1000000-0000-4000-8000-000000000001', 'Doctor of Philosophy (PhD)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000002', 'Master of Arts in Education (MAEd)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000003', 'Master of Education (MEd)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000004', 'Master in Business Administration (MBA)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000005', 'Master of Information Technology (MIT)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000006', 'Master in Public Administration (MPA)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000007', 'Bachelor of Elementary Education (BEEd)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000008', 'Bachelor of Secondary Education (BSEd)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000009', 'Bachelor of Early Childhood Education (BECEd)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000000a', 'Bachelor of Physical Education (BPEd)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000000b', 'Bachelor of Science in Accountancy (BSA)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000000c', 'Bachelor of Science in Business Administration (BSBA)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000000d', 'Bachelor of Science in Office Administration (BSOA)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000000e', 'Bachelor of Science in Entrepreneurship (BSEntrep)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000000f', 'Bachelor of Science in Economics (BSEcon)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000010', 'Bachelor of Science in Civil Engineering (BSCE)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000011', 'Bachelor of Science in Sanitary Engineering (BSSE)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000012', 'Bachelor of Science in Computer Science (BSCS)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000013', 'Bachelor of Science in Information Technology (BSIT)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000014', 'Bachelor of Science in Mathematics (BSMath)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000015', 'Bachelor of Science in Biology (BSBio)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000016', 'Bachelor of Science in Geology (BSGeo)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000017', 'Bachelor of Automotive Technology (BAT)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000018', 'Bachelor of Engineering Technology (BET)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-000000000019', 'Bachelor of Engineering Technology — Mechanical Engineering Technology (BET-ME)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000001a', 'CPE — Professional Education Certificate', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000001b', 'TCP — Professional Education Certificate', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000001c', 'Special Education (PM-SPED)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000001d', 'Bachelor of Science in Tourism Management (BSTM)', 'San Jose Campus'),
  ('e1000000-0000-4000-8000-00000000001e', 'Bachelor of Science in Hospitality Management (BSHM)', 'San Jose Campus'),
  ('e1000000-0000-4000-8000-00000000001f', 'Bachelor of Science in Criminology (BSCrim)', 'Lagonoy Campus'),
  ('e1000000-0000-4000-8000-000000000020', 'Bachelor of Science in Nutrition and Dietetics (BSND)', 'Lagonoy Campus'),
  ('e1000000-0000-4000-8000-000000000021', 'Bachelor of Science in Industrial Security Management (BSISM)', 'Lagonoy Campus'),
  ('e1000000-0000-4000-8000-000000000022', 'Bachelor of Science in Fisheries (BSFi)', 'Sagñay Campus'),
  ('e1000000-0000-4000-8000-000000000023', 'Bachelor of Science in Marine Biology (BSMB)', 'Sagñay Campus'),
  ('e1000000-0000-4000-8000-000000000024', 'Bachelor of Science in Agribusiness (BSAB)', 'Salogon Campus'),
  ('e1000000-0000-4000-8000-000000000025', 'Bachelor of Science in Community Development (BSCD)', 'Salogon Campus'),
  ('e1000000-0000-4000-8000-000000000026', 'Bachelor of Science in Hospitality Management (BSHM)', 'Caramoan Campus'),
  ('e1000000-0000-4000-8000-000000000027', 'Bachelor of Science in Forestry (BSF)', 'Caramoan Campus'),
  ('e1000000-0000-4000-8000-000000000028', 'Bachelor of Science in Environmental Planning (BSEP)', 'Tinambac Campus'),
  ('e1000000-0000-4000-8000-000000000029', 'Bachelor of Science in Forestry (BSF)', 'Tinambac Campus'),
  ('e1000000-0000-4000-8000-00000000002a', 'Bachelor of Science in Information Technology (BSIT)', 'Tinambac Campus')
) as v(id, program_name, campus_name)
join public.campuses c on c.name = v.campus_name
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 3) Remap references from the old flat rows to the new campus rows.
--    Preference: the profile's own campus (when it offers the program),
--    then Goa, then any campus offering it. Same name must match exactly
--    (the 0007 seed used identical program_name strings).
-- ---------------------------------------------------------------------------
update public.profiles p
set program_id = coalesce(
  (select n.id from public.programs n
    where n.campus_id is not null
      and n.program_name = o.program_name
      and n.campus_id = p.campus_id
    limit 1),
  (select n.id from public.programs n
    where n.campus_id is not null
      and n.program_name = o.program_name
      and n.campus_id = (select id from public.campuses
                          where name = 'Goa Campus (Main Campus)')
    limit 1),
  (select n.id from public.programs n
    where n.campus_id is not null
      and n.program_name = o.program_name
    order by n.id
    limit 1)
)
from public.programs o
where o.id = p.program_id
  and o.campus_id is null;

update public.courses crs
set program_id = coalesce(
  (select n.id from public.programs n
    where n.campus_id is not null
      and n.program_name = o.program_name
      and n.campus_id = (select id from public.campuses
                          where name = 'Goa Campus (Main Campus)')
    limit 1),
  (select n.id from public.programs n
    where n.campus_id is not null
      and n.program_name = o.program_name
    order by n.id
    limit 1)
)
from public.programs o
where o.id = crs.program_id
  and o.campus_id is null;

-- ---------------------------------------------------------------------------
-- 4) Drop the old flat rows. Profiles/courses still pointing at a program
--    that is not in the official registry (BSES) fall back to null through
--    the existing on-delete-set-null FKs.
-- ---------------------------------------------------------------------------
delete from public.programs where campus_id is null;

-- ---------------------------------------------------------------------------
-- 5) campus_id is now definitive: NOT NULL + uniqueness per campus.
-- ---------------------------------------------------------------------------
alter table public.programs alter column campus_id set not null;

create unique index if not exists programs_program_campus_uq
  on public.programs (university_name, program_name, campus_id);

comment on column public.programs.campus_id is
  'Campus offering this program; one row per (program, campus) pair per the official ParSU registry (migration 0012).';
