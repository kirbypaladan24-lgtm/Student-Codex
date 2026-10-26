-- Profile names + automatic username derivation (Phase 4).
--
-- Sign-up (email or Google) now collects first/last name in the app and
-- stores them in auth.users.raw_user_meta_data; the database derives the
-- profiles.username handle from them so the row is never empty:
--   * handle_new_user (INSERT on auth.users): the manual email sign-up,
--     which sends the names together with the initial sign-up request;
--   * handle_user_profile_sync (UPDATE on auth.users): the Google flow,
--     where the names arrive later via updateUser once the user completes
--     the in-app form (marked with profile_completed = true).
-- Both paths claim a UNIQUE handle: "Juan Dela Cruz" -> juan_dela_cruz,
-- appending _2, _3, ... when the base is taken, and giving up (username
-- stays NULL, claimable later in-app) when no candidate fits.
--
-- No RLS changes: clients never write profiles directly; the triggers are
-- SECURITY DEFINER like the existing handle_new_user.

alter table public.profiles
  add column if not exists first_name text,
  add column if not exists last_name text;

-- Lowercase, common Latin-1 accents folded to ASCII, every non-[a-z0-9]
-- run collapsed to a single underscore, underscores trimmed from the ends,
-- truncated to the schema's 24-char limit; NULL when the names cannot
-- produce a valid 3-24 char handle (the column's check constraint).
create or replace function public.derive_username(given_name text, family_name text)
returns text
language sql immutable as $$
  with folded as (
    select left(
      btrim(
        regexp_replace(
          regexp_replace(
            translate(
              lower(coalesce(given_name, '') || ' ' || coalesce(family_name, '')),
              'áàâäãåçéèêëíìîïñóòôöõúùûüýÿ',
              'aaaaaaceeeeiiiinooooouuuuyy'
            ),
            '[^a-z0-9]+', '_', 'g'
          ),
          '_+', '_', 'g'
        ),
        '_'
      ),
      24
    ) as handle
  )
  select case when length(handle) >= 3 then handle else null end
  from folded;
$$;

-- Returns a free username based on [base], or NULL when none is available.
-- Tries the base itself, then base_2, base_3, ... (shortening the base when
-- the suffix would push it past 24 chars), up to 20 attempts.
create or replace function public.claim_username(base text)
returns text
language plpgsql security definer set search_path = '' as $$
declare
  candidate text;
  suffix_text text;
begin
  if base is null or base !~ '^[a-z0-9_]{3,24}$' then
    return null;
  end if;
  for i in 0..20 loop
    if i = 0 then
      candidate := base;
    else
      suffix_text := '_' || (i + 1)::text;
      candidate := left(base, 24 - length(suffix_text)) || suffix_text;
    end if;
    if length(candidate) >= 3
       and candidate ~ '^[a-z0-9_]{3,24}$'
       and not exists (select 1 from public.profiles where username = candidate) then
      return candidate;
    end if;
  end loop;
  return null;
end $$;

-- Sign-up path: the names arrive with the INSERT (email sign-up metadata).
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_given text := nullif(btrim(coalesce(new.raw_user_meta_data->>'first_name', '')), '');
  v_family text := nullif(btrim(coalesce(new.raw_user_meta_data->>'last_name', '')), '');
  v_username text;
begin
  insert into public.profiles (id) values (new.id)
    on conflict (id) do nothing;

  if v_given is not null or v_family is not null then
    begin
      v_username := public.claim_username(
        public.derive_username(coalesce(v_given, ''), coalesce(v_family, ''))
      );
      update public.profiles set
        first_name = coalesce(v_given, first_name),
        last_name = coalesce(v_family, last_name),
        username = coalesce(v_username, username)
      where id = new.id;
    exception when unique_violation then
      -- Two same-named sign-ups raced us to the handle: keep the profile
      -- (with names, without username) instead of failing the sign-up; the
      -- user can claim a handle later in-app.
      update public.profiles set
        first_name = coalesce(v_given, first_name),
        last_name = coalesce(v_family, last_name)
      where id = new.id;
    end;
  end if;
  return new;
end $$;

-- Completion path: the names arrive later via updateUser (Google flow and
-- pre-name-era accounts), marked with profile_completed = true. Idempotent:
-- fills names when present and claims a username only while none is set
-- (profiles.username is immutable once set).
create or replace function public.handle_user_profile_sync() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_given text := nullif(btrim(coalesce(new.raw_user_meta_data->>'first_name', '')), '');
  v_family text := nullif(btrim(coalesce(new.raw_user_meta_data->>'last_name', '')), '');
  v_username text;
begin
  if (v_given is null and v_family is null)
     or coalesce(new.raw_user_meta_data->>'profile_completed', 'false') <> 'true' then
    return new;
  end if;

  begin
    v_username := public.claim_username(
      public.derive_username(coalesce(v_given, ''), coalesce(v_family, ''))
    );
    update public.profiles set
      first_name = coalesce(v_given, first_name),
      last_name = coalesce(v_family, last_name),
      username = coalesce(v_username, username)
    where id = new.id and username is null;
  exception when unique_violation then
    update public.profiles set
      first_name = coalesce(v_given, first_name),
      last_name = coalesce(v_family, last_name)
    where id = new.id;
  end;
  return new;
end $$;

drop trigger if exists on_auth_user_profile_sync on auth.users;
create trigger on_auth_user_profile_sync
  after update on auth.users
  for each row
  when (new.raw_user_meta_data is distinct from old.raw_user_meta_data)
  execute function public.handle_user_profile_sync();
