-- ============================================================================
-- Student Codex migration 0012 — DEMO USER SHAPE FIX (consolidated)
--
-- Consolidates the live fixes applied while diagnosing SQL-created GoTrue
-- users (see migration 0011's header for the full lesson): restores the
-- demo teacher's intended @studentcodex.app address, guarantees the four
-- demo accounts carry GoTrue's exact row shape (empty-string token columns,
-- email identities, bcrypt cost 10), and removes every diagnostic artifact
-- (debug tables + the throwaway probe account).
--
-- Idempotent: safe on fresh replays (everything is a no-op there).
-- ============================================================================

set search_path = public, extensions;

-- Restore the teacher's intended address (diagnosis renamed it to
-- example.com at one point) and keep the identity row in sync.
update auth.users set email = 'demo.teacher@studentcodex.app'
where email = 'demo.teacher@example.com'
  and id = 'a1000000-0000-4000-8000-000000000001';

update auth.identities set
  identity_data = jsonb_set(identity_data, '{email}', '"demo.teacher@studentcodex.app"')
where user_id = 'a1000000-0000-4000-8000-000000000001'
  and provider = 'email'
  and identity_data->>'email' = 'demo.teacher@example.com';

-- Belt and braces: every demo account gets GoTrue's row shape.
update auth.users set
  confirmation_token = '',
  recovery_token = '',
  email_change = '',
  email_change_token_new = '',
  confirmation_sent_at = coalesce(confirmation_sent_at, now()),
  email_confirmed_at = coalesce(email_confirmed_at, now())
where email like '%@studentcodex.app';

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

-- Diagnostic artifacts (absent on fresh replays).
drop table if exists public.__debug_users;
drop table if exists public.__debug_users2;
drop table if exists public.__dbg;
delete from auth.users where email = 'studentcodex.probe.9x2@gmail.com';
