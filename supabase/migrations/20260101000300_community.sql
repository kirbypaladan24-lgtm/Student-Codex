-- ============================================================================
-- Student Codex migration 0004 — STUDENT COMMUNITY
-- posts, post_images, comments (self-referencing for nested replies),
-- post_likes, comment_likes, user_blocks, and invoker-safe feed views.
--
-- Safety model (plan §7):
--   * ownership enforced server-side: only authors edit/delete their content
--   * max 3 images per post enforced by the DATABASE (unique position 1-3),
--     not only in the UI
--   * likes are unique per (user, target)
--   * replies reference a real, non-deleted parent in the same post and are
--     depth-limited; parent linkage is immutable (no cycle is possible)
--   * anonymous = author hidden in the public interface (feed views null
--     the author) while moderation stays possible on the underlying tables
--   * blocking hides the blocked user's posts/comments from the blocker
--     through RLS itself
-- ============================================================================

create table if not exists public.posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references auth.users (id) on delete cascade,
  content text not null check (char_length(content) between 1 and 5000),
  is_anonymous boolean not null default false,
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists posts_created_at_idx on public.posts (created_at desc);
create index if not exists posts_author_idx on public.posts (author_id);

-- Server-side 3-image limit: unique (post_id, position) with position in
-- 1..3 makes a 4th image impossible regardless of the client.
create table if not exists public.post_images (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts (id) on delete cascade,
  storage_path text not null check (char_length(storage_path) between 1 and 500),
  position integer not null check (position between 1 and 3),
  created_at timestamptz not null default now(),
  unique (post_id, position)
);

create index if not exists post_images_post_idx on public.post_images (post_id);

-- One comments table: a reply is a comment with a nullable parent_comment_id.
create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts (id) on delete cascade,
  parent_comment_id uuid references public.comments (id) on delete cascade,
  author_id uuid not null references auth.users (id) on delete cascade,
  content text not null check (char_length(content) between 1 and 2000),
  is_anonymous boolean not null default false,
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists comments_post_idx on public.comments (post_id);
create index if not exists comments_parent_idx on public.comments (parent_comment_id);

-- One user cannot like the same target twice: database uniqueness.
create table if not exists public.post_likes (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (post_id, user_id)
);

create index if not exists post_likes_post_idx on public.post_likes (post_id);

create table if not exists public.comment_likes (
  id uuid primary key default gen_random_uuid(),
  comment_id uuid not null references public.comments (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (comment_id, user_id)
);

create index if not exists comment_likes_comment_idx on public.comment_likes (comment_id);

create table if not exists public.user_blocks (
  id uuid primary key default gen_random_uuid(),
  blocker uuid not null references auth.users (id) on delete cascade,
  blocked uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (blocker, blocked),
  constraint user_blocks_no_self check (blocker <> blocked)
);

-- ---------------------------------------------------------------------------
-- Guard: posts integrity. Author and anonymity are immutable (switching a
-- post from anonymous to named would retroactively reveal history).
-- ---------------------------------------------------------------------------
create or replace function public.guard_post_update() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.author_id is distinct from old.author_id then
    raise exception 'posts.author_id is immutable';
  end if;
  if new.is_anonymous is distinct from old.is_anonymous then
    raise exception 'posts.is_anonymous is immutable';
  end if;
  if new.content is distinct from old.content then
    new.edited_at = now();
  end if;
  return new;
end $$;

create trigger posts_guard_update before update on public.posts
  for each row execute function public.guard_post_update();

create trigger posts_set_updated_at before update on public.posts
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Guard: comments integrity on insert. The parent (when present) must:
--   * exist
--   * belong to the same post
--   * not be deleted
--   * keep total reply depth within the limit (5)
-- Runs as SECURITY DEFINER so the parent lookup is a trusted read; the
-- deleted-parent check is explicit here.
-- ---------------------------------------------------------------------------
create or replace function public.guard_comment_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  parent public.comments;
  depth integer := 1;
begin
  if new.parent_comment_id is not null then
    select * into parent from public.comments where id = new.parent_comment_id;
    if parent.id is null then
      raise exception 'parent comment does not exist';
    end if;
    if parent.post_id is distinct from new.post_id then
      raise exception 'parent comment belongs to a different post';
    end if;
    if parent.deleted_at is not null then
      raise exception 'cannot reply to a deleted comment';
    end if;
    while parent.parent_comment_id is not null and depth <= 5 loop
      select * into parent from public.comments where id = parent.parent_comment_id;
      depth := depth + 1;
    end loop;
    -- depth counts the ancestor chain of the new reply; a thread may nest
    -- five levels total (root + four replies)
    if depth >= 5 then
      raise exception 'reply depth limit (5) exceeded';
    end if;
  end if;
  return new;
end $$;

create trigger comments_guard_insert before insert on public.comments
  for each row execute function public.guard_comment_insert();

-- ---------------------------------------------------------------------------
-- Guard: comments integrity on update. Author, post, parent linkage and
-- anonymity are immutable — parent immutability makes cycles impossible.
-- ---------------------------------------------------------------------------
create or replace function public.guard_comment_update() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.author_id is distinct from old.author_id then
    raise exception 'comments.author_id is immutable';
  end if;
  if new.post_id is distinct from old.post_id then
    raise exception 'comments.post_id is immutable';
  end if;
  if new.parent_comment_id is distinct from old.parent_comment_id then
    raise exception 'comments.parent_comment_id is immutable';
  end if;
  if new.is_anonymous is distinct from old.is_anonymous then
    raise exception 'comments.is_anonymous is immutable';
  end if;
  if new.content is distinct from old.content then
    new.edited_at = now();
  end if;
  return new;
end $$;

create trigger comments_guard_update before update on public.comments
  for each row execute function public.guard_comment_update();

create trigger comments_set_updated_at before update on public.comments
  for each row execute function public.set_updated_at();

-- ===========================================================================
-- RLS
-- ===========================================================================

-- posts -------------------------------------------------------------------------
alter table public.posts enable row level security;

-- Feed visibility: live posts (deleted posts stay visible ONLY to their
-- author — Postgres enforces SELECT policies as WITH CHECK on UPDATE, so
-- this exception is what lets authors soft-delete their own content while
-- nobody else ever sees it), minus anything authored by a user the caller
-- has blocked. The caller always sees their own posts.
create policy posts_select on public.posts
  for select to authenticated
  using (
    (deleted_at is null or author_id = auth.uid())
    and (
      author_id = auth.uid()
      or not exists (
        select 1 from public.user_blocks ub
        where ub.blocker = auth.uid() and ub.blocked = posts.author_id
      )
    )
  );

create policy posts_insert on public.posts
  for insert to authenticated
  with check (author_id = auth.uid());

create policy posts_update_own on public.posts
  for update to authenticated
  using (author_id = auth.uid() and deleted_at is null)
  with check (author_id = auth.uid());

create policy posts_delete_own on public.posts
  for delete to authenticated
  using (author_id = auth.uid());

-- Moderators and admins may remove content as a moderation action.
create policy posts_delete_moderator on public.posts
  for delete to authenticated
  using (public.has_role('moderator') or public.has_role('admin'));

-- post_images ---------------------------------------------------------------------
alter table public.post_images enable row level security;

create policy post_images_select on public.post_images
  for select to authenticated
  using (
    exists (
      select 1 from public.posts p
      where p.id = post_images.post_id
    )
  );

create policy post_images_insert on public.post_images
  for insert to authenticated
  with check (
    exists (
      select 1 from public.posts p
      where p.id = post_images.post_id
        and p.author_id = auth.uid()
        and p.deleted_at is null
    )
  );

create policy post_images_delete_own on public.post_images
  for delete to authenticated
  using (
    exists (
      select 1 from public.posts p
      where p.id = post_images.post_id
        and p.author_id = auth.uid()
    )
  );
-- no update policy: image rows are replaced, not mutated

-- comments ------------------------------------------------------------------------
alter table public.comments enable row level security;

create policy comments_select on public.comments
  for select to authenticated
  using (
    -- deleted comments stay visible only to their own author (see the
    -- posts_select note about UPDATE + SELECT-policy WITH CHECK semantics)
    (deleted_at is null or author_id = auth.uid())
    and exists (
      select 1 from public.posts p
      where p.id = comments.post_id
    )
    and (
      author_id = auth.uid()
      or not exists (
        select 1 from public.user_blocks ub
        where ub.blocker = auth.uid() and ub.blocked = comments.author_id
      )
    )
  );

-- The post must be visible (RLS applies inside the subquery). Parent
-- validation (exists, same post, not deleted, depth limit) is enforced by
-- the SECURITY DEFINER guard trigger — the policy must NOT subquery
-- comments itself, or PostgreSQL rejects it with "infinite recursion
-- detected in policy".
create policy comments_insert on public.comments
  for insert to authenticated
  with check (
    author_id = auth.uid()
    and exists (
      select 1 from public.posts p where p.id = comments.post_id
    )
  );

create policy comments_update_own on public.comments
  for update to authenticated
  using (author_id = auth.uid() and deleted_at is null)
  with check (author_id = auth.uid());

create policy comments_delete_own on public.comments
  for delete to authenticated
  using (author_id = auth.uid());

-- Moderators and admins may remove comments as a moderation action.
create policy comments_delete_moderator on public.comments
  for delete to authenticated
  using (public.has_role('moderator') or public.has_role('admin'));

-- post_likes ------------------------------------------------------------------------
alter table public.post_likes enable row level security;

create policy post_likes_select on public.post_likes
  for select to authenticated
  using (true);

create policy post_likes_insert on public.post_likes
  for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.posts p where p.id = post_likes.post_id)
  );

create policy post_likes_delete_own on public.post_likes
  for delete to authenticated
  using (user_id = auth.uid());
-- no update policy: a like is inserted or removed, never mutated

-- comment_likes -----------------------------------------------------------------------
alter table public.comment_likes enable row level security;

create policy comment_likes_select on public.comment_likes
  for select to authenticated
  using (true);

create policy comment_likes_insert on public.comment_likes
  for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.comments c where c.id = comment_likes.comment_id)
  );

create policy comment_likes_delete_own on public.comment_likes
  for delete to authenticated
  using (user_id = auth.uid());

-- user_blocks ---------------------------------------------------------------------------
alter table public.user_blocks enable row level security;

create policy user_blocks_select_own on public.user_blocks
  for select to authenticated
  using (blocker = auth.uid());

create policy user_blocks_insert_own on public.user_blocks
  for insert to authenticated
  with check (blocker = auth.uid());

create policy user_blocks_delete_own on public.user_blocks
  for delete to authenticated
  using (blocker = auth.uid());
-- no update policy: a block is created or removed

-- ===========================================================================
-- Invoker-safe feed views.
-- security_invoker = true keeps RLS active for the selecting user (plan §2:
-- prefer invoker-safe views). The views null the author for anonymous
-- content so identity never leaks through the public interface.
-- ===========================================================================
create or replace view public.feed_posts with (security_invoker = true) as
select
  p.id,
  p.content,
  p.is_anonymous,
  case when p.is_anonymous then null else p.author_id end as author_id,
  p.created_at,
  p.edited_at,
  (select count(*) from public.post_likes pl where pl.post_id = p.id) as like_count,
  (select count(*) from public.comments c where c.post_id = p.id) as comment_count,
  (select count(*) from public.post_images pi where pi.post_id = p.id) as image_count
from public.posts p
where p.deleted_at is null;

create or replace view public.feed_comments with (security_invoker = true) as
select
  c.id,
  c.post_id,
  c.parent_comment_id,
  c.content,
  c.is_anonymous,
  case when c.is_anonymous then null else c.author_id end as author_id,
  c.created_at,
  c.edited_at,
  (select count(*) from public.comment_likes cl where cl.comment_id = c.id) as like_count
from public.comments c
where c.deleted_at is null;

comment on view public.feed_posts is
  'Public community feed. Anonymous posts never expose author_id through this view; RLS applies to the selecting user (security_invoker).';
comment on view public.feed_comments is
  'Public comment thread view. Anonymous comments never expose author_id through this view; RLS applies to the selecting user (security_invoker).';
