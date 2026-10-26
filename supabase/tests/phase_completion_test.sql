-- Behavioral tests for migration 0008 (phase 5/6-ready/9 completion) and
-- 0009 (demo seed). PASSED markers printed by q.mjs. Statement-by-statement
-- with session-level set_config for auth.uid() emulation.


-- Helper: assert a statement RAISES with the expected message fragment.
create or replace function pg_temp.expect_error(p_sql text, p_fragment text) returns text
language plpgsql as $$
begin
  execute p_sql;
  return 'FAILED statement succeeded but should have raised: ' || p_fragment;
exception when others then
  if position(p_fragment in SQLERRM) > 0 then
    return 'PASSED expected error: ' || p_fragment;
  end if;
  return 'FAILED wrong error (' || SQLERRM || ')';
end $$;

-- ------------------------------------------------------------- seed sanity
select case when (select count(*) from auth.users where email like '%@studentcodex.app') = 4
            then 'PASSED 4 demo users' else 'FAILED demo users' end;
select case when (select count(*) from public.user_roles where role = 'teacher') = 1
            then 'PASSED teacher role granted' else 'FAILED teacher role' end;
select case when (select count(*) from public.courses) = 6
            then 'PASSED 6 courses' else 'FAILED courses' end;
select case when (select count(*) from public.sections) = 6
            then 'PASSED 6 sections' else 'FAILED sections' end;
select case when (select count(*) from public.enrollments) = 30
            then 'PASSED 30 enrollments (5 x 6 sections)' else 'FAILED enrollments: '||(select count(*) from public.enrollments) end;
select case when (select count(*) from public.enrollments e join auth.users u on u.id=e.user_id where u.email='kirbypaladan24@gmail.com') = 6
            then 'PASSED Kirby enrolled in all 6 sections' else 'FAILED Kirby enrollments' end;
select case when (select count(*) from public.activities) = 8
            then 'PASSED 8 demo activities' else 'FAILED activities' end;
select case when (select count(*) from public.activity_sources) = 2
            then 'PASSED 2 provider source rows' else 'FAILED source rows' end;
select case when (select count(*) from public.posts) = 5
            then 'PASSED 5 demo posts' else 'FAILED posts' end;
select case when (select count(*) from public.comments) = 4
            then 'PASSED 4 demo comments (1 nested)' else 'FAILED comments' end;
select case when (select count(*) from public.post_likes) = 7
            then 'PASSED 7 post likes' else 'FAILED post likes' end;
select case when (select count(*) from public.profiles where username = 'teodoro_garcia') = 1
            then 'PASSED teacher username auto-derived by sign-up trigger' else 'FAILED teacher username' end;
select case when (select count(*) from public.profiles p join auth.users u on u.id = p.id
                  where u.email = 'demo.teacher@studentcodex.app' and p.first_name = 'Teodoro') = 1
            then 'PASSED teacher profile named' else 'FAILED teacher profile' end;
select case when (select count(*) from public.profiles p join auth.users u on u.id = p.id
                  where u.email = 'maria.santos@studentcodex.app' and p.program_id is not null and p.campus_id is not null) = 1
            then 'PASSED demo student school details' else 'FAILED student details' end;

-- ------------------------------------------- Kirby sees section activities
set role authenticated;
select set_config('request.jwt.claims', '{"sub":"3c8c2276-ca5a-428b-86df-5062f1ddb1a0"}', false);
select case when (select count(*) from public.activities) = 8
            then 'PASSED Kirby sees all 8 section activities' else 'FAILED Kirby activity visibility: '||(select count(*) from public.activities) end;
select case when (select count(*) from public.feed_posts) = 5
            then 'PASSED Kirby feed has 5 posts' else 'FAILED feed count' end;
select case when (select count(*) from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000004') = 1
       and (select author_id from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000004') is null
       and (select author_name from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000004') is null
            then 'PASSED anonymous post leaks no author id or name' else 'FAILED anonymous leak' end;
select case when (select author_name from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000001') = 'Maria Santos'
            then 'PASSED named post shows author name' else 'FAILED author name' end;
select case when (select liked_by_me from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000003')
            then 'PASSED liked_by_me true for post Kirby liked' else 'FAILED liked_by_me' end;
select case when (select like_count from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000003') = 3
            then 'PASSED like_count = 3' else 'FAILED like_count' end;
select case when (select comment_count from public.feed_posts where id = 'e5000000-0000-4000-8000-000000000001') = 2
            then 'PASSED comment_count = 2 (incl nested reply)' else 'FAILED comment_count' end;
select case when (select count(*) from public.my_posts) = 1
       and (select content from public.my_posts) like '%hello BSCS 2-A%'
            then 'PASSED my_posts shows Kirby own post' else 'FAILED my_posts' end;
select case when (select jsonb_array_length(image_paths) from public.feed_posts limit 1) = 0
            then 'PASSED image_paths empty array default' else 'FAILED image_paths' end;

-- Kirby report workflow (Phase 5 student side)
insert into public.activity_reports (reporter_user_id, title, due_at, notes)
values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'Face-to-face quiz announced in class', now() + interval '2 days', 'Prof announced verbally');
select case when (select status from public.activity_reports order by created_at desc limit 1) = 'pending'
            then 'PASSED report filed as pending' else 'FAILED report status' end;
select pg_temp.expect_error(
  $$update public.activity_reports set status = 'confirmed' where reporter_user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0'$$,
  'only teachers or admins can change a report status');
update public.activity_reports set status = 'withdrawn'
where reporter_user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0';
select case when (select status from public.activity_reports order by created_at desc limit 1) = 'withdrawn'
            then 'PASSED reporter can withdraw own report' else 'FAILED withdraw' end;

-- Empty-content post is allowed now (photo-only posts); guest-free RLS: Kirby authors
insert into public.posts (author_id, content, is_anonymous)
values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', '', false);
select case when (select count(*) from public.posts where content = '') = 1
            then 'PASSED image-only (empty content) post allowed' else 'FAILED empty post' end;

-- Rate limit: Kirby has 1 fresh post; 7 more are allowed, the 8th in-window fails.
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 1');
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 2');
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 3');
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 4');
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 5');
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 6');
insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 7');
select case when (select count(*) from public.posts where author_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0') = 9
            then 'PASSED 8 posts within the rate window accepted' else 'FAILED rate window count' end;
select pg_temp.expect_error(
  $$insert into public.posts (author_id, content) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'rl 9 - should fail')$$,
  'posting too quickly');

-- google_connections: owner reads own, cannot see others', cannot insert
select pg_temp.expect_error(
  $$insert into public.google_connections (user_id, google_email, status) values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'kirby@gmail.com', 'connected')$$,
  'row-level security');

-- cleanup Kirby's test posts/reports before service-context tests
delete from public.posts where author_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0' and content like 'rl %';
delete from public.posts where author_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0' and content = '';
delete from public.activity_reports where reporter_user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0';

-- Maria context
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-4000-8000-000000000002"}', false);
select case when (select count(*) from public.google_connections) = 0
            then 'PASSED other user cannot read someone elses connection row' else 'FAILED connection visibility' end;
select case when (select count(*) from public.my_posts) = 2
            then 'PASSED my_posts includes Maria anonymous post' else 'FAILED Maria my_posts' end;
select case when (select count(*) from public.feed_comments where post_id = 'e5000000-0000-4000-8000-000000000001') = 2
            then 'PASSED comments view lists nested thread' else 'FAILED comments view' end;
select case when (select author_name from public.feed_comments where id = 'f6000000-0000-4000-8000-000000000004') is null
            then 'PASSED anonymous comment hides author name' else 'FAILED anonymous comment leak' end;
reset role;
select set_config('request.jwt.claims', '', false);

-- --------------------------------------- service context: upsert RPC (Phase 5)
select case when public.upsert_activity_from_source(
  'google_classroom:demo-course-cs201:demo-cw-9', 'google_classroom', 'demo-course-cs201', 'demo-cw-9',
  'a1000000-0000-4000-8000-000000000002', 'RPC-created coursework', null, 'assignment',
  'b2000000-0000-4000-8000-000000000003', 'c3000000-0000-4000-8000-000000000003',
  now() + interval '5 days', 60, 'section', '{"title":"RPC-created coursework"}') is not null
            then 'PASSED service upsert creates activity' else 'FAILED upsert create' end;
select case when (select count(*) from public.activity_sources where source_key = 'google_classroom:demo-course-cs201:demo-cw-9') = 1
            then 'PASSED one source row for new item' else 'FAILED source count' end;
select case when (select verification_status from public.activities where title = 'RPC-created coursework') = 'imported'
            then 'PASSED RPC activity is imported' else 'FAILED rpc verification' end;
select case when public.upsert_activity_from_source(
  'google_classroom:demo-course-cs201:demo-cw-9', 'google_classroom', 'demo-course-cs201', 'demo-cw-9',
  'a1000000-0000-4000-8000-000000000002', 'RPC-created coursework v2', null, 'assignment',
  'b2000000-0000-4000-8000-000000000003', 'c3000000-0000-4000-8000-000000000003',
  now() + interval '5 days', 90, 'section', '{"title":"v2"}') is not null
            then 'PASSED retried upsert executed' else 'FAILED upsert retry call' end;
select case when (select s.activity_id from public.activity_sources s
                  where s.source_key = 'google_classroom:demo-course-cs201:demo-cw-9')
       = (select a.id from public.activities a where a.title = 'RPC-created coursework v2')
            then 'PASSED retried upsert converges to same activity' else 'FAILED upsert convergence' end;
select case when (select count(*) from public.activity_sources where source_key = 'google_classroom:demo-course-cs201:demo-cw-9') = 1
       and (select count(*) from public.activities where title like 'RPC-created coursework%') = 1
            then 'PASSED retry did not duplicate activity or source' else 'FAILED upsert dedup' end;
select case when (select estimated_effort_minutes from public.activities where title like 'RPC-created coursework%') = 90
            then 'PASSED retry refreshed content (effort 90)' else 'FAILED upsert refresh' end;

-- authenticated caller is rejected
set role authenticated;
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-4000-8000-000000000002"}', false);
select pg_temp.expect_error(
  $$select public.upsert_activity_from_source('google_classroom:x:y', 'google_classroom', 'x', 'y', 'a1000000-0000-4000-8000-000000000002', 'hack', null, 'assignment', null, null, null, null, 'personal', '{}')$$,
  'permission denied for function upsert_activity_from_source');
reset role;
select set_config('request.jwt.claims', '', false);
-- defense in depth: even WITH execute permission (service_role) a JWT caller is rejected
set role service_role;
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-4000-8000-000000000002"}', false);
select pg_temp.expect_error(
  $$select public.upsert_activity_from_source('google_classroom:x:y', 'google_classroom', 'x', 'y', 'a1000000-0000-4000-8000-000000000002', 'hack', null, 'assignment', null, null, null, null, 'personal', '{}')$$,
  'service-only operation');
reset role;
select set_config('request.jwt.claims', '', false);

-- --------------------------------- service writes google_connections + owner
insert into public.google_connections (user_id, google_email, status, last_sync_at, last_sync_status)
values ('3c8c2276-ca5a-428b-86df-5062f1ddb1a0', 'kirbypaladan24@gmail.com', 'connected', now(), 'ok');
set role authenticated;
select set_config('request.jwt.claims', '{"sub":"3c8c2276-ca5a-428b-86df-5062f1ddb1a0"}', false);
select case when (select status from public.google_connections where user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0') = 'connected'
            then 'PASSED owner reads own connection state' else 'FAILED connection read' end;
select pg_temp.expect_error(
  $$update public.google_connections set google_email = 'evil@evil.dev' where user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0'$$,
  'managed by the sync service');
select pg_temp.expect_error(
  $$update public.google_connections set status = 'revoked' where user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0'$$,
  'you can only disconnect');
update public.google_connections set status = 'disconnected' where user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0';
select case when (select status from public.google_connections where user_id = '3c8c2276-ca5a-428b-86df-5062f1ddb1a0') = 'disconnected'
            then 'PASSED owner can disconnect' else 'FAILED disconnect' end;
reset role;
select set_config('request.jwt.claims', '', false);

-- ------------------------------ teacher workload view (Phase 11 contract)
set role authenticated;
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-4000-8000-000000000001"}', false);
select case when (select count(*) from public.section_workload_daily) >= 6
            then 'PASSED teacher sees workload rows for his sections' else 'FAILED teacher workload rows' end;
select case when (select count(*) from public.activities a join public.sections s on s.id = a.section_id) >= 9
            then 'PASSED teacher can read section activities' else 'FAILED teacher activities' end;
select case when (select count(*) from public.activity_reports) = 0
            then 'PASSED teacher sees empty report queue (all withdrawn)' else 'FAILED teacher report queue' end;
reset role;
select set_config('request.jwt.claims', '', false);

-- a student with no enrollments sees no workload rows
set role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-1111-1111-111111111101"}', false);
select case when (select count(*) from public.section_workload_daily) = 0
            then 'PASSED unenrolled user sees no workload rows' else 'FAILED workload isolation' end;
reset role;
select set_config('request.jwt.claims', '', false);

-- -------------------------------------------------- cleanup test artifacts
delete from public.activity_sources where source_key = 'google_classroom:demo-course-cs201:demo-cw-9';
delete from public.activities where title like 'RPC-created coursework%';
delete from public.google_connections;
insert into public.activity_reports (reporter_user_id, title, due_at, notes)
values ('a1000000-0000-4000-8000-000000000002', 'Face-to-face quiz announced in class', now() + interval '2 days', 'Prof announced verbally');
select case when (select count(*) from public.activity_reports) = 1
            then 'PASSED cleanup done + one demo report left for teacher triage' else 'FAILED cleanup' end;
