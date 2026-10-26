-- ============================================================================
-- Student Codex migration 0006 — STORAGE
-- Community images bucket + storage policies.
--
-- Model (plan §2/§7): image bytes live in Supabase Storage; PostgreSQL rows
-- (post_images) store object paths and metadata only.
--
-- Access rules:
--   * bucket is PRIVATE (never make private images public for convenience);
--     reads are granted to authenticated users because the community feed
--     is visible to signed-in students only
--   * uploads must go into the uploader's own folder: {user_id}/... — the
--     folder prefix is enforced by RLS, so a modified client still cannot
--     write into someone else's folder
--   * path convention: {auth.uid()}/{post_id}/{filename}
-- ============================================================================

insert into storage.buckets (id, name, public)
values ('community-images', 'community-images', false)
on conflict (id) do nothing;

-- Read: any authenticated user can read community images (private feed).
create policy community_images_read on storage.objects
  for select to authenticated
  using (bucket_id = 'community-images');

-- Create: only into the uploader's own folder.
create policy community_images_insert_own_folder on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'community-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- Update: only the uploader's own objects.
create policy community_images_update_own_folder on storage.objects
  for update to authenticated
  using (
    bucket_id = 'community-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  )
  with check (
    bucket_id = 'community-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- Delete: only the uploader's own objects.
create policy community_images_delete_own_folder on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'community-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
