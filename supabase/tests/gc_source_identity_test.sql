-- ============================================================================
-- Student Codex test — GOOGLE CLASSROOM SOURCE IDENTITY
--
-- Verifies the database-level deduplication guarantees for Google Classroom
-- imports (plan §4 dedup rule 1):
--   1. a repeated sync of the same coursework converges to ONE source row
--      via an atomic, retry-safe upsert;
--   2. the same provider identity cannot sneak in under a different
--      source_key (composite unique index);
--   3. distinct coursework identities stay separate (no over-merging).
--
-- Plain SQL in the same style as policies_test.sql: run it after the
-- migrations on the same database, e.g. `supabase test db` (pg_prove runs
-- the files in this directory) or via psql. It is hermetic: distinctive
-- identifiers, everything wrapped in begin/rollback, so it leaves no
-- residue and can run in any order relative to policies_test.sql.
--
-- Classroom imports are a TRUSTED server-side operation (Edge Function with
-- the service role, Phase 5/6), so this script runs in the no-JWT context
-- (auth.uid() is null) exactly like the future sync worker.
--
-- NOTE for the Edge Function implementation: the upsert MUST specify the
-- partial-index predicate —
--     on conflict (source_key) where source_key is not null do update
-- — because the unique index on source_key is partial (source_key is
-- nullable for manual sources). A bare `on conflict (source_key)` fails
-- with "no unique or exclusion constraint matching the ON CONFLICT
-- specification".
-- ============================================================================

begin;

-- scratch identity + academic structure (distinctive IDs; rolled back)
insert into auth.users (id, email)
values ('99999999-9999-9999-9999-999999999999', 'gcsync-9001@test.dev');

insert into public.programs (university_name, program_name)
values ('GCSync Test University 9001', 'GCSync Test Program 9001');

insert into public.courses (program_id, code, title)
select id, 'GCSYNC9', 'GCSync Test Course 9001' from public.programs limit 1;

insert into public.sections (course_id, name)
select id, 'SYNC-S1' from public.courses limit 1;

insert into public.activities (title, activity_type, course_id, section_id, scope, verification_status, created_by_user_id)
select 'gcsync-9001 coursework A', 'assignment', c.id, s.id, 'section', 'imported',
       '99999999-9999-9999-9999-999999999999'
from public.courses c join public.sections s on s.course_id = c.id limit 1;

-- 1) first sync: insert the source identity
insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key, source_meta)
select a.id, 'google_classroom', 'gcsync-9001-course', 'gcsync-9001-cw-A',
       'google_classroom:gcsync-9001-course:gcsync-9001-cw-A', '{"title": "v1"}'
from public.activities a where a.title = 'gcsync-9001 coursework A';

-- 2) repeated sync of the SAME coursework: atomic retry-safe upsert. The
--    unique constraint makes this converge to ONE row with the newest
--    metadata — never a duplicate. (Predicate required: partial index.)
insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key, source_meta)
select a.id, 'google_classroom', 'gcsync-9001-course', 'gcsync-9001-cw-A',
       'google_classroom:gcsync-9001-course:gcsync-9001-cw-A', '{"title": "v2"}'
from public.activities a where a.title = 'gcsync-9001 coursework A'
on conflict (source_key) where source_key is not null do update
  set source_meta = excluded.source_meta,
      updated_at = now();

do $gcx$
declare
  cnt int;
  meta text;
begin
  select count(*), max(source_meta ->> 'title') into cnt, meta
  from public.activity_sources
  where source_key = 'google_classroom:gcsync-9001-course:gcsync-9001-cw-A';
  if cnt <> 1 then
    raise exception 'GC-IDENTITY FAIL: expected exactly 1 source row after a repeated sync, got %', cnt;
  end if;
  if meta is distinct from 'v2' then
    raise exception 'GC-IDENTITY FAIL: upsert did not converge to the newest metadata (got %)', meta;
  end if;
end
$gcx$;

-- 3) the same coursework cannot sneak in under a different source_key: the
--    composite (provider, external_course_id, external_item_id) unique
--    index must reject it.
do $gcx$
begin
  begin
    insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key)
    select a.id, 'google_classroom', 'gcsync-9001-course', 'gcsync-9001-cw-A',
           'google_classroom:gcsync-9001:some-other-key'
    from public.activities a where a.title = 'gcsync-9001 coursework A';
    raise exception 'GC-IDENTITY FAIL: duplicate provider identity was accepted (composite unique constraint missing)';
  exception
    when unique_violation then null; -- expected
  end;
end
$gcx$;

-- 4) a DIFFERENT coursework in the same course is a separate source row
--    (no over-merging of distinct identities).
insert into public.activity_sources (activity_id, provider, external_course_id, external_item_id, source_key)
select a.id, 'google_classroom', 'gcsync-9001-course', 'gcsync-9001-cw-B',
       'google_classroom:gcsync-9001-course:gcsync-9001-cw-B'
from public.activities a where a.title = 'gcsync-9001 coursework A';

do $gcx$
declare
  cnt int;
begin
  select count(*) into cnt
  from public.activity_sources
  where external_item_id in ('gcsync-9001-cw-A', 'gcsync-9001-cw-B');
  if cnt <> 2 then
    raise exception 'GC-IDENTITY FAIL: expected 2 distinct coursework identities, got %', cnt;
  end if;
end
$gcx$;

rollback;

select 'GC SOURCE IDENTITY TESTS PASSED' as result;
