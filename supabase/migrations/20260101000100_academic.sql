-- ============================================================================
-- Student Codex migration 0002 — ACADEMIC STRUCTURE
-- courses, sections, enrollments.
-- Structure and membership are trusted data: created/maintained by admins or
-- the service role. Students read what they are allowed to see.
-- ============================================================================

create table if not exists public.courses (
  id uuid primary key default gen_random_uuid(),
  program_id uuid references public.programs (id) on delete set null,
  code text not null check (char_length(code) between 1 and 32),
  title text not null check (char_length(title) between 1 and 200),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (code, title)
);

create trigger courses_set_updated_at before update on public.courses
  for each row execute function public.set_updated_at();

create table if not exists public.sections (
  id uuid primary key default gen_random_uuid(),
  course_id uuid not null references public.courses (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  term text check (char_length(term) between 1 and 40),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- one section name per course+term (a null term counts as '')
create unique index if not exists sections_course_name_term_key
  on public.sections (course_id, name, coalesce(term, ''));

create trigger sections_set_updated_at before update on public.sections
  for each row execute function public.set_updated_at();

create table if not exists public.enrollments (
  id uuid primary key default gen_random_uuid(),
  section_id uuid not null references public.sections (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'student' check (role in ('student', 'teacher')),
  enrolled_at timestamptz not null default now(),
  unique (section_id, user_id)
);

-- ---------------------------------------------------------------------------
-- RLS helper functions for membership checks (SECURITY DEFINER, pinned
-- search_path). They let policies check trusted data without recursive RLS
-- evaluation.
-- ---------------------------------------------------------------------------
create or replace function public.is_enrolled(target_section uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.enrollments e
    where e.section_id = target_section and e.user_id = auth.uid()
  )
$$;

create or replace function public.is_section_teacher(target_section uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.enrollments e
    where e.section_id = target_section
      and e.user_id = auth.uid()
      and e.role = 'teacher'
  )
$$;

create or replace function public.is_course_teacher(target_course uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
    from public.enrollments e
    join public.sections s on s.id = e.section_id
    where s.course_id = target_course
      and e.user_id = auth.uid()
      and e.role = 'teacher'
  )
$$;

revoke execute on function public.is_enrolled(uuid) from public;
revoke execute on function public.is_section_teacher(uuid) from public;
revoke execute on function public.is_course_teacher(uuid) from public;
grant execute on function public.is_enrolled(uuid) to authenticated, service_role;
grant execute on function public.is_section_teacher(uuid) to authenticated, service_role;
grant execute on function public.is_course_teacher(uuid) to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- RLS: courses / sections — visible to authenticated users (needed for
-- course pickers and activity labels); structure is admin/service-managed.
-- ---------------------------------------------------------------------------
alter table public.courses enable row level security;

create policy courses_select on public.courses
  for select to authenticated
  using (true);

alter table public.sections enable row level security;

create policy sections_select on public.sections
  for select to authenticated
  using (true);

-- ---------------------------------------------------------------------------
-- RLS: enrollments — a member sees their own membership (the app needs "my
-- sections"); a section teacher sees their section's members; admins see
-- all. Nobody inserts/updates/deletes through the client API: membership is
-- trusted, admin-managed data (plan §2 security rules).
-- ---------------------------------------------------------------------------
alter table public.enrollments enable row level security;

create policy enrollments_select on public.enrollments
  for select to authenticated
  using (
    user_id = auth.uid()
    or public.is_section_teacher(section_id)
    or public.has_role('admin')
  );
-- no client write policies: membership is admin/service-managed
