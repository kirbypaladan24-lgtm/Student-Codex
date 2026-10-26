-- ============================================================================
-- One-time data repair: pull UTC-shifted student deadlines back onto their
-- intended day.
--
-- Until this point the Android app interpreted the picked due DATE in the
-- device's local zone (ZoneId.systemDefault()). On devices set to GMT/UTC
-- (Android emulators default to GMT) an end-of-day deadline was saved as
-- 23:59 UTC instead of 23:59 Asia/Manila — eight hours late — so "due
-- Nov 6" landed on Nov 7 for every viewer in the Philippines (teacher
-- dashboard, classmates' phones). The app now anchors due dates to
-- Asia/Manila (domain/SchoolTime.kt); this migration repairs the rows
-- already saved the shifted way.
--
-- Signature of such a write: UTC wall clock exactly 23:59:00 (a Philippine
-- device writes 15:59:00Z for the same end-of-day deadline, and seeded
-- demo rows carry sub-second times). The guard on the old value keeps the
-- update idempotent — re-running it can never double-shift a row.
-- ============================================================================

do $$
declare
    shifted int;
begin
    update public.activities
    set due_at = due_at - interval '8 hours'
    where (due_at at time zone 'UTC')::time = '23:59:00'
      and verification_status = 'student-reported'
      and created_by_user_id = '04232397-2b95-4a32-bfc1-7dad80c50215';

    get diagnostics shifted = ROW_COUNT;
    raise notice 'due_at Manila repair: shifted % row(s) back 8 hours', shifted;
end $$;
