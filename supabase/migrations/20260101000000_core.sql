-- ============================================================================
-- Student Codex migration 0001 — CORE
-- programs, profiles, user_roles + shared helper functions and auth trigger.
--
-- Conventions used across all Student Codex migrations:
--   * ids are uuid (gen_random_uuid(), core since PG13)
--   * timestamps are timestamptz, updated_at maintained by trigger
--   * every API-exposed table enables RLS with explicit policies
--   * policy helpers are SECURITY DEFINER with pinned empty search_path and
--     fully-qualified object references (plan §2 security rules)
--   * trusted data (roles, structure) is admin/service-managed: no client
--     insert/update/delete policies exist for it
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Shared trigger: keep updated_at current
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

-- ---------------------------------------------------------------------------
-- Programs: university + program/course pairs (minimal profile model).
-- Readable by authenticated users; maintained by admins/service only.
-- ---------------------------------------------------------------------------
create table if not exists public.programs (
  id uuid primary key default gen_random_uuid(),
  university_name text not null check (char_length(university_name) between 1 and 160),
  program_name text not null check (char_length(program_name) between 1 and 160),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (university_name, program_name)
);

create trigger programs_set_updated_at before update on public.programs
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Profiles: app profile fields only. Identity lives in auth.users (Supabase
-- Auth). No bio field. Trusted role data lives in user_roles (admin-managed).
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text check (username ~ '^[a-z0-9_]{3,24}$'),
  program_id uuid references public.programs (id) on delete set null,
  year_level integer not null default 1 check (year_level between 1 and 8),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists profiles_username_key
  on public.profiles (username) where username is not null;

create trigger profiles_set_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();

-- Profile row is created automatically when the Supabase Auth user is
-- created, so clients never need insert privileges on profiles.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id) values (new.id)
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Profiles: only id is immutable; username is immutable once set (it is
-- referenced by community @mentions and must not be reused for identity
-- tricks). year_level / program_id remain user-editable.
create or replace function public.guard_profile_update() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.id is distinct from old.id then
    raise exception 'profiles.id is immutable';
  end if;
  if old.username is not null and new.username is distinct from old.username then
    raise exception 'profiles.username is immutable once set';
  end if;
  if new.username is not null then
    new.username := lower(new.username);
    if new.username !~ '^[a-z0-9_]{3,24}$' then
      raise exception 'invalid username: 3-24 chars, lowercase letters, digits, underscore';
    end if;
  end if;
  return new;
end $$;

create trigger profiles_guard_update before update on public.profiles
  for each row execute function public.guard_profile_update();

-- ---------------------------------------------------------------------------
-- user_roles: trusted, admin-managed role assignments. A student must never
-- be able to make themselves a teacher/moderator/admin. There are no client
-- write policies on this table (service role bypasses RLS).
-- ---------------------------------------------------------------------------
create table if not exists public.user_roles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null check (role in ('student', 'teacher', 'moderator', 'admin')),
  granted_by uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  unique (user_id, role)
);

-- ---------------------------------------------------------------------------
-- RLS helper functions (SECURITY DEFINER, pinned empty search_path, fully
-- qualified references). They let policies check trusted data without
-- recursive RLS evaluation. (Enrollment helpers live in migration 0002 —
-- they reference the enrollments table created there.)
-- ---------------------------------------------------------------------------
create or replace function public.has_role(target_role text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.user_roles ur
    where ur.user_id = auth.uid() and ur.role = target_role
  )
$$;

revoke execute on function public.has_role(text) from public;
grant execute on function public.has_role(text) to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- RLS: programs
-- ---------------------------------------------------------------------------
alter table public.programs enable row level security;

create policy programs_select on public.programs
  for select to authenticated
  using (true);
-- no insert/update/delete policies: admin/service-managed only

-- ---------------------------------------------------------------------------
-- RLS: profiles — everyone signed-in can read the minimal public profile
-- (username, program, year level are intentionally public information);
-- users can update only their own row (guard trigger protects id/username).
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;

create policy profiles_select on public.profiles
  for select to authenticated
  using (true);

create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());
-- no insert policy: rows are created by the auth trigger / service only
-- no delete policy: profile deletion follows account deletion (cascade)

-- ---------------------------------------------------------------------------
-- RLS: user_roles — users can read their own roles (the app needs to know
-- whether the current user has teacher/moderator capabilities); moderators
-- and admins can read all; nobody writes through the client API.
-- ---------------------------------------------------------------------------
alter table public.user_roles enable row level security;

create policy user_roles_select_own on public.user_roles
  for select to authenticated
  using (
    user_id = auth.uid()
    or public.has_role('moderator')
    or public.has_role('admin')
  );
-- no client write policies: role grants are trusted, service/admin-managed
