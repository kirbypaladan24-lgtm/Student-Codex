-- ============================================================================
-- Student Codex migration 0007 — SCHOOL DETAILS
-- Lookup tables for the sign-up forms: campuses, year levels and year
-- sections (the programs table from migration 0001 gains the seed rows).
-- Sign-up (email + Google completion) collects the four choices as dropdown
-- values, never free text, so profiles stay clean relational data:
--   profiles.program_id / year_level (existing) + campus_id / year_section_id.
--
-- Naming note: `sections` already exists as course sections (migration
-- 0002); the registrar-style block labels (A..F, 1..3) live in
-- `year_sections` instead.
--
-- RLS: all four lookup tables are readable by anon AND authenticated — the
-- sign-up form needs them BEFORE any account exists. They are public facts
-- of the university; writes remain admin/service-only (no write policies).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Campuses: the seven campuses, seeded in registry order.
-- ---------------------------------------------------------------------------
create table if not exists public.campuses (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 80),
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (name)
);

drop trigger if exists campuses_set_updated_at on public.campuses;
create trigger campuses_set_updated_at before update on public.campuses
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Year levels: 1st..5th. `level` is both the PK and the FK target for
-- profiles.year_level (whose 1-8 check narrows to the seeded rows).
-- ---------------------------------------------------------------------------
create table if not exists public.year_levels (
  level integer primary key check (level between 1 and 10),
  label text not null check (char_length(label) between 1 and 8),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (label)
);

drop trigger if exists year_levels_set_updated_at on public.year_levels;
create trigger year_levels_set_updated_at before update on public.year_levels
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Year sections: registrar-style block labels (A..F, 1..3).
-- ---------------------------------------------------------------------------
create table if not exists public.year_sections (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 8),
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (name)
);

drop trigger if exists year_sections_set_updated_at on public.year_sections;
create trigger year_sections_set_updated_at before update on public.year_sections
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Profiles: campus + year section columns (program_id and year_level exist
-- since migration 0001). The year_level lookup FK is added further below,
-- AFTER the seeds, together with a normalization for pre-existing rows.
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column if not exists campus_id uuid references public.campuses (id) on delete set null,
  add column if not exists year_section_id uuid references public.year_sections (id) on delete set null;

-- ---------------------------------------------------------------------------
-- Seeds (from the School_Details registry; idempotent for re-runs).
-- ---------------------------------------------------------------------------
insert into public.campuses (name, sort_order) values
  ('Goa Campus (Main Campus)', 1),
  ('San Jose Campus', 2),
  ('Lagonoy Campus', 3),
  ('Sagñay Campus', 4),
  ('Salogon Campus', 5),
  ('Caramoan Campus', 6),
  ('Tinambac Campus', 7)
on conflict (name) do nothing;

insert into public.year_levels (level, label) values
  (1, '1st'), (2, '2nd'), (3, '3rd'), (4, '4th'), (5, '5th')
on conflict (level) do nothing;

insert into public.year_sections (name, sort_order) values
  ('A', 1), ('B', 2), ('C', 3), ('D', 4), ('E', 5), ('F', 6),
  ('1', 7), ('2', 8), ('3', 9)
on conflict (name) do nothing;

insert into public.programs (university_name, program_name) values
  ('Partido State University', 'Doctor of Philosophy (PhD)'),
  ('Partido State University', 'Master of Arts in Education (MAEd)'),
  ('Partido State University', 'Master of Education (MEd)'),
  ('Partido State University', 'Master in Business Administration (MBA)'),
  ('Partido State University', 'Master of Information Technology (MIT)'),
  ('Partido State University', 'Master in Public Administration (MPA)'),
  ('Partido State University', 'Bachelor of Elementary Education (BEEd)'),
  ('Partido State University', 'Bachelor of Secondary Education (BSEd)'),
  ('Partido State University', 'Bachelor of Early Childhood Education (BECEd)'),
  ('Partido State University', 'Bachelor of Physical Education (BPEd)'),
  ('Partido State University', 'Bachelor of Science in Accountancy (BSA)'),
  ('Partido State University', 'Bachelor of Science in Business Administration (BSBA)'),
  ('Partido State University', 'Bachelor of Science in Office Administration (BSOA)'),
  ('Partido State University', 'Bachelor of Science in Entrepreneurship (BSEntrep)'),
  ('Partido State University', 'Bachelor of Science in Economics (BSEcon)'),
  ('Partido State University', 'Bachelor of Science in Civil Engineering (BSCE)'),
  ('Partido State University', 'Bachelor of Science in Sanitary Engineering (BSSE)'),
  ('Partido State University', 'Bachelor of Science in Computer Science (BSCS)'),
  ('Partido State University', 'Bachelor of Science in Information Technology (BSIT)'),
  ('Partido State University', 'Bachelor of Science in Mathematics (BSMath)'),
  ('Partido State University', 'Bachelor of Science in Biology (BSBio)'),
  ('Partido State University', 'Bachelor of Science in Geology (BSGeo)'),
  ('Partido State University', 'Bachelor of Science in Environmental Science (BSES)'),
  ('Partido State University', 'Bachelor of Science in Environmental Planning (BSEP)'),
  ('Partido State University', 'Bachelor of Science in Forestry (BSF)'),
  ('Partido State University', 'Bachelor of Science in Tourism Management (BSTM)'),
  ('Partido State University', 'Bachelor of Science in Hospitality Management (BSHM)'),
  ('Partido State University', 'Bachelor of Science in Criminology (BSCrim)'),
  ('Partido State University', 'Bachelor of Science in Nutrition and Dietetics (BSND)'),
  ('Partido State University', 'Bachelor of Science in Industrial Security Management (BSISM)'),
  ('Partido State University', 'Bachelor of Science in Fisheries (BSFi)'),
  ('Partido State University', 'Bachelor of Science in Marine Biology (BSMB)'),
  ('Partido State University', 'Bachelor of Science in Agribusiness (BSAB)'),
  ('Partido State University', 'Bachelor of Science in Community Development (BSCD)'),
  ('Partido State University', 'Bachelor of Automotive Technology (BAT)'),
  ('Partido State University', 'Bachelor of Engineering Technology (BET)'),
  ('Partido State University', 'Bachelor of Engineering Technology — Mechanical Engineering Technology (BET-ME)'),
  ('Partido State University', 'CPE — Professional Education Certificate'),
  ('Partido State University', 'TCP — Professional Education Certificate'),
  ('Partido State University', 'Special Education (PM-SPED)')
on conflict (university_name, program_name) do nothing;

-- ---------------------------------------------------------------------------
-- year_level lookup FK: narrows the schema's 1-8 check to the seeded levels.
-- Any pre-FK row outside the registry (only possible from direct admin
-- writes) snaps to the default 1 so the validation scan passes.
-- ---------------------------------------------------------------------------
update public.profiles
  set year_level = 1
  where year_level not in (select level from public.year_levels);

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'profiles_year_level_fkey'
  ) then
    alter table public.profiles
      add constraint profiles_year_level_fkey
      foreign key (year_level) references public.year_levels (level);
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- RLS: public reads (the sign-up form runs before any account exists);
-- writes stay admin/service-only. `programs` keeps its authenticated policy
-- from migration 0001 and gains the anon policy alongside it.
-- ---------------------------------------------------------------------------
alter table public.campuses enable row level security;
drop policy if exists campuses_select_public on public.campuses;
create policy campuses_select_public on public.campuses
  for select to anon, authenticated
  using (true);

alter table public.year_levels enable row level security;
drop policy if exists year_levels_select_public on public.year_levels;
create policy year_levels_select_public on public.year_levels
  for select to anon, authenticated
  using (true);

alter table public.year_sections enable row level security;
drop policy if exists year_sections_select_public on public.year_sections;
create policy year_sections_select_public on public.year_sections
  for select to anon, authenticated
  using (true);

drop policy if exists programs_select_public on public.programs;
create policy programs_select_public on public.programs
  for select to anon
  using (true);

-- ---------------------------------------------------------------------------
-- Safe UUID parse for trigger inputs: raw_user_meta_data is client-supplied
-- text; a malformed id must be ignored, never crash the sign-up.
-- (CASE only evaluates the cast on the matched branch.)
-- ---------------------------------------------------------------------------
create or replace function public.try_uuid(value text) returns uuid
language sql immutable as $$
  select case when value ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
              then value::uuid
              else null end
$$;

revoke execute on function public.try_uuid(text) from public;

-- ---------------------------------------------------------------------------
-- Sign-up path (replaces the migration 0006 body): the names AND school
-- details arrive with the INSERT as user metadata. School values only apply
-- when they reference real registry rows — anything else (empty, malformed,
-- foreign ids) is ignored rather than failing the sign-up.
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
  return new;
end $$;

-- ---------------------------------------------------------------------------
-- Completion path (replaces the migration 0006 body): the names and school
-- details arrive later via updateUser (Google flow and pre-name-era
-- accounts), marked with profile_completed = true. Idempotent: names and
-- school details fill whenever provided; the username handle is claimed
-- only while none is set (profiles.username is immutable once set).
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
  return new;
end $$;
