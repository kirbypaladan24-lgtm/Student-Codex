-- ============================================================================
-- Student Codex migration 0011 — DEMO SEED FIX: email identities
--
-- GoTrue requires an auth.identities row for every login identity. The
-- demo users created in migration 0010 by direct SQL insert (bypassing
-- GoTrue's signup endpoint) had auth.users rows and bcrypt passwords but
-- NO identities row, which makes the password grant fail with a 500
-- "Database error querying schema". This migration creates the missing
-- 'email' identity rows exactly as GoTrue itself would, re-hashes the
-- demo passwords at GoTrue's own cost (10), and drops the temporary
-- debug tables used while diagnosing the issue.
--
-- Idempotent: safe to re-run.
-- ============================================================================

set search_path = public, extensions;

-- The email login identities for the demo accounts.
insert into auth.identities (
  id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at
)
select gen_random_uuid(), u.id, u.id::text, 'email',
       jsonb_build_object(
         'sub', u.id::text,
         'email', u.email,
         'email_verified', true
       ),
       now(), now(), now()
from auth.users u
where u.email like '%@studentcodex.app'
  and not exists (
    select 1 from auth.identities i
    where i.user_id = u.id and i.provider = 'email'
  );

-- Re-hash the demo passwords at GoTrue's bcrypt cost so the rows mirror
-- GoTrue-created users byte-for-byte in format.
update auth.users set
  encrypted_password = extensions.crypt(
    case email
      when 'demo.teacher@studentcodex.app' then 'demo-teacher-123'
      else 'demo-student-123'
    end, extensions.gen_salt('bf', 10))
where email like '%@studentcodex.app';

-- Temporary debug artifacts from the diagnosis (absent on fresh replays).
drop table if exists public.__debug_users;
drop table if exists public.__debug_users2;
