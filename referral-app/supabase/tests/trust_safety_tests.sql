-- Sprint 5 tests: daily maintenance, moderation guard, reports, bans, admin functions, export.
-- Run after all migrations (see run_tests.sh). Each block prints what it expects.
\set R '''c1000000-0000-0000-0000-00000000000a'''
\set S1 '''c1000000-0000-0000-0000-000000000001'''
\set S2 '''c1000000-0000-0000-0000-000000000002'''
\set A '''c1000000-0000-0000-0000-0000000000ad'''
\set CO '''c2000000-0000-0000-0000-000000000001'''
\pset footer off

insert into auth.users (id, phone) values (:R, '919000000001'), (:S1, '919000000002'), (:S2, '919000000003'), (:A, '919000000004');
update profiles set full_name = 'Priya S', experience_years = 8 where id = :R;
update profiles set full_name = 'Seeker ' || right(id::text, 1), experience_years = 3, resume_path = id::text || '/resume.pdf'
  where id in (:S1, :S2);
update profiles set full_name = 'Admin Oam' where id = :A;
insert into admins (user_id) values (:A);
insert into companies (id, name, domains, status) values (:CO, 'Movate', '{movate.com}', 'approved');
insert into referrer_verifications (user_id, company_id, work_email) values (:R, :CO, 'priya@movate.com');
alter table jobs disable trigger jobs_post_limit;
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, last_confirmed_at) values
  ('c3000000-0000-0000-0000-000000000001', :CO, :R, 'Fresh',   'Chennai', 1, 5, 'remote', 'R1', now()),
  ('c3000000-0000-0000-0000-000000000002', :CO, :R, 'Day12',   'Chennai', 1, 5, 'remote', 'R2', now() - interval '12 days 2 hours'),
  ('c3000000-0000-0000-0000-000000000003', :CO, :R, 'Day15',   'Chennai', 1, 5, 'remote', 'R3', now() - interval '15 days'),
  ('c3000000-0000-0000-0000-000000000004', :CO, :R, 'Reported','Chennai', 1, 5, 'remote', 'R4', now()),
  ('c3000000-0000-0000-0000-000000000005', :CO, :R, 'Scam',    'Chennai', 1, 5, 'remote', 'R5', now());
alter table jobs enable trigger jobs_post_limit;

\echo '=== MAINTENANCE'
\echo '--- M1 first run: 1 reminder (Day12), 1 hidden (Day15), no verification work yet'
select run_daily_maintenance() - 'otps_deleted' - 'notifications_deleted' as run1;
select title, status, hidden_reason, reminded_at is not null as reminded from jobs order by title;
\echo '--- M2 second run the same day: nothing new (reminders are once per confirmation cycle)'
select run_daily_maintenance() - 'otps_deleted' - 'notifications_deleted' as run2;
\echo '--- M3 referrer confirms Day12 and Day15 (Still hiring) -> both active, hidden_reason cleared'
set role authenticated;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
update jobs set status = 'active', last_confirmed_at = now() where title in ('Day12', 'Day15');
reset role;
select title, status, hidden_reason from jobs where title in ('Day12', 'Day15') order by title;
\echo '--- M4 verification ends in 5 days -> 1 reminder; after expiry -> all 5 posts hidden as verification'
update referrer_verifications set expires_at = now() + interval '5 days';
select run_daily_maintenance()->'verification_reminders' as verif_reminders;
update referrer_verifications set expires_at = now() - interval '1 minute';
select run_daily_maintenance()->'posts_hidden_verification' as hidden_for_verification;
\echo '--- M5 expired referrer cannot reactivate (expect RLS error); after re-verifying they can'
set role authenticated;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
update jobs set status = 'active', last_confirmed_at = now() where title = 'Fresh';
reset role;
update referrer_verifications set expires_at = now() + interval '90 days', verified_at = now();
set role authenticated;
update jobs set status = 'active', last_confirmed_at = now() where title in ('Fresh', 'Reported', 'Scam') returning title, status;
reset role;
\echo '--- M6 notifications Priya got (expect confirm_reminder, post_hidden x2 kinds, verification_expiring)'
select kind, count(*) from notifications where user_id = :R group by kind order by kind;

\echo '=== REPORTS'
set role authenticated;
\echo '--- P1 one "fake" report: post stays active; admin notified'
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
insert into reports (reporter_id, target_type, target_id, reason, details)
  values (:S1, 'job', 'c3000000-0000-0000-0000-000000000004', 'fake', '  Not on their careers page  ');
select status from jobs where title = 'Reported';
\echo '--- P2 same person again (expect unique violation); report own self / bad target (expect REPORT_INVALID x2)'
insert into reports (reporter_id, target_type, target_id, reason) values (:S1, 'job', 'c3000000-0000-0000-0000-000000000004', 'fake');
insert into reports (reporter_id, target_type, target_id, reason) values (:S1, 'user', :S1, 'abuse');
insert into reports (reporter_id, target_type, target_id, reason) values (:S1, 'job', gen_random_uuid(), 'fake');
\echo '--- P3 second reporter -> post hidden for reports, Priya told'
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into reports (reporter_id, target_type, target_id, reason) values (:S2, 'job', 'c3000000-0000-0000-0000-000000000004', 'closed_role');
reset role;
select status, hidden_reason from jobs where title = 'Reported';
\echo '--- P4 a single "asked for money" report hides immediately'
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
insert into reports (reporter_id, target_type, target_id, reason) values (:S1, 'job', 'c3000000-0000-0000-0000-000000000005', 'payment_request');
reset role;
select status, hidden_reason from jobs where title = 'Scam';
\echo '--- P5 referrer cannot bring a reported post back (expect UNDER_REVIEW) or set hidden_reason (stays reports)'
set role authenticated;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
update jobs set status = 'active', last_confirmed_at = now() where title = 'Reported';
update jobs set hidden_reason = null where title = 'Reported';
reset role;
select hidden_reason from jobs where title = 'Reported';
\echo '--- P6 report limit: 10 a day (expect REPORT_LIMIT on the 11th)'
alter table reports disable trigger reports_after_insert;
alter table reports disable trigger reports_validate;
insert into reports (reporter_id, target_type, target_id, reason)
  select :S2, 'user', gen_random_uuid(), 'other' from generate_series(1, 9);
alter table reports enable trigger reports_validate;
alter table reports enable trigger reports_after_insert;
set role authenticated;
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into reports (reporter_id, target_type, target_id, reason) values (:S2, 'user', :R, 'abuse');
reset role;
delete from reports where reporter_id = :S2 and target_type = 'user' and target_id <> :R;
\echo '--- P7 admin got a notification per report (expect admin_report 4)'
select kind, count(*) from notifications where user_id = :A group by kind;

\echo '=== ADMIN'
\echo '--- A1 non-admin calls admin functions (expect NOT_ADMIN x2)'
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select admin_open_reports();
select admin_ban_user(:R, 'x');
select am_i_admin() as s1_is_admin;
\echo '--- A2 admin sees 3 targets (Reported x2, Scam x1, Priya user x1)'
select set_config('request.jwt.claim.sub', :A, false) is not null as as_admin;
select am_i_admin() as admin_is_admin;
select x->>'label' as label, x->>'target_status' as status, x->>'report_count' as n, x->'reasons' as reasons, x->'details' as details
from jsonb_array_elements(admin_open_reports()) x order by 1;
\echo '--- A3 dismiss "Reported" -> back to active; uphold "Scam" -> closed by admin'
select admin_resolve_reports('job', 'c3000000-0000-0000-0000-000000000004', 'dismiss')->>'status';
select admin_resolve_reports('job', 'c3000000-0000-0000-0000-000000000005', 'uphold')->>'status';
reset role;
select title, status, hidden_reason from jobs where title in ('Reported', 'Scam') order by title;
select status, count(*) from reports group by status order by status;

\echo '=== BANS'
\echo '--- B1 S2 applies to Fresh; admin bans S2 -> application withdrawn, tokens gone'
set role authenticated;
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into applications (job_id, seeker_id) values ('c3000000-0000-0000-0000-000000000001', :S2);
select register_device_token('tok_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb');
select set_config('request.jwt.claim.sub', :A, false) is not null as as_admin;
select admin_ban_user(:S2, 'Spam') - 'status' as ban;
reset role;
select (select count(*) from device_tokens where user_id = :S2) as tokens,
       (select status from applications where seeker_id = :S2) as app_status,
       (select banned_at is not null from profiles where id = :S2) as banned;
\echo '--- B2 banned S2 cannot apply or reapply (expect BANNED x2)'
set role authenticated;
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into applications (job_id, seeker_id) values ('c3000000-0000-0000-0000-000000000002', :S2);
select reapply_application(id, '{}') from applications where seeker_id = :S2;
\echo '--- B3 banned users cannot unban themselves via profile update (expect permission denied)'
update profiles set banned_at = null where id = :S2;
\echo '--- B4 admin cannot ban themselves; unban works'
select set_config('request.jwt.claim.sub', :A, false) is not null as as_admin;
select admin_ban_user(:A, 'oops');
select admin_unban_user(:S2)->>'status';

\echo '=== DOMAINS'
reset role;
insert into domain_requests (domain, request_count) values ('newco.in', 3), ('acme.io', 1), ('movate.co.in', 2);
set role authenticated;
select set_config('request.jwt.claim.sub', :A, false) is not null as as_admin;
\echo '--- D1 pending list, most requested first'
select x->>'domain' as domain, x->>'request_count' as n from jsonb_array_elements(admin_domain_requests()) x;
\echo '--- D2 approve newco.in as a new company; movate.co.in into existing Movate (case-insensitive); reject acme.io'
select admin_approve_domain('newco.in', 'NewCo') ->> 'status';
select admin_approve_domain('movate.co.in', 'movate') ->> 'status';
select admin_reject_domain('acme.io') ->> 'status';
reset role;
select name, domains, status from companies order by name;
select domain, status from domain_requests order by domain;

\echo '=== EXPORT'
\echo '--- E1 S1 export has account, profile, applications/reports arrays; no other users'' data'
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select e->'account'->>'phone' as phone, e->'profile'->>'full_name' as name,
       jsonb_array_length(e->'reports_filed') as reports, jsonb_array_length(e->'applications') as apps,
       e->'profile' ? 'priority_until' as leaks_internal
from (select export_my_data() e) x;
\echo '--- E2 app users cannot run maintenance or deletion prep (expect permission denied x2)'
select run_daily_maintenance();
select prepare_account_deletion(:S1);
reset role;

\echo '=== DISMISS WHEN AT THE POST LIMIT'
\echo '--- X1 Priya has 5 active posts; dismissing a report on a 6th held post leaves it hidden as stale (no error)'
alter table jobs disable trigger jobs_post_limit;
insert into jobs (company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref)
  select :CO, :R, 'Filler ' || g, 'Chennai', 1, 5, 'remote', 'F' from generate_series(1, 5) g;
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, status, hidden_reason)
  values ('c3000000-0000-0000-0000-000000000009', :CO, :R, 'Held', 'Chennai', 1, 5, 'remote', 'H', 'hidden', 'reports');
alter table jobs enable trigger jobs_post_limit;
insert into reports (reporter_id, target_type, target_id, reason) values (:S1, 'job', 'c3000000-0000-0000-0000-000000000009', 'other');
set role authenticated;
select set_config('request.jwt.claim.sub', :A, false) is not null as as_admin;
select admin_resolve_reports('job', 'c3000000-0000-0000-0000-000000000009', 'dismiss')->>'status' as dismissed;
reset role;
select status, hidden_reason from jobs where title = 'Held';
