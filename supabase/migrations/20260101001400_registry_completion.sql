-- ============================================================================
-- Student Codex migration 0014 — REGISTRY COMPLETION (programs)
--
-- WHY: the sign-up dropdowns (Android app + Teacher Dashboard WebView) render
-- campuses / programs / year levels / year sections straight from these
-- registry tables — nothing is hard-coded in any client. The registry itself
-- was missing several programs that ParSU officially offers today, so the
-- program dropdown was incomplete. This migration adds every program-campus
-- row that is live on the official offerings list
-- (https://www.parsu.edu.ph/academics/program-offerings, ICTMO-maintained)
-- but not yet in `programs`:
--
--   Goa       + Bachelor of Arts in Communication                 (new)
--   Goa       + Bachelor of Public Administration                 (newly offered)
--   San Jose  + Master of Science in Tourism Management           (newly offered)
--   Tinambac  + Bachelor of Science in Environmental Science      (restored)
--   Caramoan  + Bachelor of Automotive Technology                 (2nd campus)
--   Caramoan  + Bachelor of Science in Biology                    (2nd campus)
--   Caramoan  + Bachelor of Science in Tourism Management         (2nd campus)
--   Caramoan  + Bachelor of Engineering Technology                (2nd campus)
--
-- Registry after this migration: 50 program-campus rows
--   Goa 30 · San Jose 3 · Lagonoy 3 · Sagñay 2 · Salogon 2 ·
--   Caramoan 6 · Tinambac 4.
--
-- Campuses (7), year levels (1st–5th) and year sections (A–F, 1–3) already
-- match the official registry; no rows are removed — ADD ONLY, so existing
-- profile/course references stay valid. Future registry edits are plain
-- INSERTs here: every client picks them up automatically on next load.
--
-- Idempotent: fixed ids + on-conflict-do-nothing + a not-exists guard for
-- equivalent (program, campus) rows that may already exist with another id.
-- ============================================================================

insert into public.programs (id, university_name, program_name, campus_id)
select v.id::uuid, 'Partido State University', v.program_name, c.id
from (values
  -- Goa Campus (Main Campus) -----------------------------------------------
  ('e1000000-0000-4000-8000-00000000002b', 'Bachelor of Arts in Communication (BAC)', 'Goa Campus (Main Campus)'),
  ('e1000000-0000-4000-8000-00000000002c', 'Bachelor of Public Administration (BPA)', 'Goa Campus (Main Campus)'),
  -- San Jose Campus ---------------------------------------------------------
  ('e1000000-0000-4000-8000-00000000002d', 'Master of Science in Tourism Management (MSTM)', 'San Jose Campus'),
  -- Tinambac Campus ---------------------------------------------------------
  ('e1000000-0000-4000-8000-00000000002e', 'Bachelor of Science in Environmental Science (BSES)', 'Tinambac Campus'),
  -- Caramoan Campus ---------------------------------------------------------
  ('e1000000-0000-4000-8000-00000000002f', 'Bachelor of Automotive Technology (BAT)', 'Caramoan Campus'),
  ('e1000000-0000-4000-8000-000000000030', 'Bachelor of Science in Biology (BSBio)', 'Caramoan Campus'),
  ('e1000000-0000-4000-8000-000000000031', 'Bachelor of Science in Tourism Management (BSTM)', 'Caramoan Campus'),
  ('e1000000-0000-4000-8000-000000000032', 'Bachelor of Engineering Technology (BET)', 'Caramoan Campus')
) as v(id, program_name, campus_name)
join public.campuses c on c.name = v.campus_name
where not exists (
  select 1 from public.programs p
  where p.program_name = v.program_name
    and p.campus_id = c.id
)
on conflict (id) do nothing;
