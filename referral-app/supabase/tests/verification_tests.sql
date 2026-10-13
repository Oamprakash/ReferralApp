-- Sprint 2 tests: work-email verification rules.
-- Run after both migrations (see run_tests.sh). Each block prints what it expects.
\set U1 '''aaaa0000-0000-0000-0000-000000000001'''
\set U2 '''aaaa0000-0000-0000-0000-000000000002'''
\pset footer off
insert into auth.users (id) values (:U1), (:U2);
insert into companies (id, name, domains, status) values
  ('c0000000-0000-0000-0000-000000000001', 'Movate', '{Movate.com}', 'approved'),
  ('c0000000-0000-0000-0000-000000000002', 'Zoho',   '{zohocorp.com}', 'approved'),
  ('c0000000-0000-0000-0000-000000000003', 'Pending Co', '{pendingco.com}', 'pending');

\echo '--- V1 domains stored lowercase (expect {movate.com})'
select domains from companies where name = 'Movate';
\echo '--- V2 second company claiming movate.com (expect DOMAIN_TAKEN)'
insert into companies (name, domains, status) values ('Copycat', '{movate.com}', 'approved');

\echo '--- V3 statuses: invalid, free, free subdomain, unknown, pending company, subdomain match'
select request_work_email_otp(:U1, 'not-an-email', 'h')->>'status' as invalid;
select request_work_email_otp(:U1, 'oam@Gmail.com', 'h')->>'status' as free;
select request_work_email_otp(:U1, 'x@mail.yahoo.com', 'h')->>'status' as free_sub;
select request_work_email_otp(:U1, 'x@unknownco.in', 'h')->>'status' as unknown;
select request_work_email_otp(:U2, 'y@unknownco.in', 'h')->>'status' as unknown_again;
select domain, request_count from domain_requests;
select request_work_email_otp(:U1, 'x@pendingco.com', 'h')->>'status' as pending_company;
\echo '--- V4 U1 requests code at a subdomain of movate.com (expect sent, Movate)'
select request_work_email_otp(:U1, ' Oam@In.Movate.com ', 'hash-1') as resp;
\echo '--- V5 immediate second request (expect cooldown ~60)'
select request_work_email_otp(:U1, 'oam@in.movate.com', 'hash-2') as resp;

\echo '--- V6 wrong code x4 then right code (expect 4,3,2,1 left then verified)'
select verify_work_email_otp(:U1, 'wrong')->>'attempts_left' as left1;
select verify_work_email_otp(:U1, 'wrong')->>'attempts_left' as left2;
select verify_work_email_otp(:U1, 'wrong')->>'attempts_left' as left3;
select verify_work_email_otp(:U1, 'wrong')->>'attempts_left' as left4;
select verify_work_email_otp(:U1, 'hash-1')->>'status' as status;
select work_email, expires_at > now() + interval '89 days' as ninety_days from referrer_verifications;
\echo '--- V7 reuse the consumed code (expect no_pending_code)'
select verify_work_email_otp(:U1, 'hash-1')->>'status';

\echo '--- V8 five wrong tries lock the code (expect too_many_attempts twice)'
update work_email_otps set created_at = now() - interval '2 minutes';
select request_work_email_otp(:U1, 'oam@in.movate.com', 'hash-3')->>'status' as sent;
select verify_work_email_otp(:U1, 'w') ->>'status' from generate_series(1,4);
select verify_work_email_otp(:U1, 'w') ->>'status' as fifth;
select verify_work_email_otp(:U1, 'hash-3')->>'status' as right_code_after_lock;

\echo '--- V9 U2 tries the address U1 already verified (expect email_in_use)'
select request_work_email_otp(:U2, 'oam@in.movate.com', 'hash-x')->>'status';

\echo '--- V10 expired code (expect no_pending_code)'
update work_email_otps set created_at = now() - interval '2 minutes';
select request_work_email_otp(:U2, 'colleague@movate.com', 'hash-u2')->>'status' as sent;
update work_email_otps set expires_at = now() - interval '1 second' where user_id = :U2;
select verify_work_email_otp(:U2, 'hash-u2')->>'status';

\echo '--- V11 rate limits: 3 per address per hour (expect sent then rate_limited)'
update work_email_otps set created_at = now() - interval '2 minutes';
select request_work_email_otp(:U2, 'colleague@movate.com', 'h2')->>'status' as second_for_address;
update work_email_otps set created_at = now() - interval '2 minutes' where user_id = :U2;
select request_work_email_otp(:U2, 'colleague@movate.com', 'h3')->>'status' as third_for_address;
update work_email_otps set created_at = now() - interval '2 minutes' where user_id = :U2;
select request_work_email_otp(:U2, 'colleague@movate.com', 'h4')->>'status' as fourth_for_address;

\echo '--- V12 U1 moves to Zoho: Movate posts close, verification switches (expect verified, 2 closed)'
insert into jobs (company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref) values
  ('c0000000-0000-0000-0000-000000000001', :U1, 'A', 'Chennai', 1, 2, 'remote', 'R1'),
  ('c0000000-0000-0000-0000-000000000001', :U1, 'B', 'Chennai', 1, 2, 'remote', 'R2');
update work_email_otps set created_at = now() - interval '2 hours';
select request_work_email_otp(:U1, 'oam@zohocorp.com', 'hz')->>'status' as sent;
select verify_work_email_otp(:U1, 'hz') - 'expires_at' as resp;
select c.name, v.work_email from referrer_verifications v join companies c on c.id = v.company_id where v.user_id = :U1;
select count(*) filter (where status = 'closed') as closed_movate_posts from jobs where referrer_id = :U1;

\echo '--- V13 app users cannot call the functions or read OTP tables (expect 3 permission errors)'
set role authenticated;
select set_config('request.jwt.claim.sub', :U2, false) is not null as as_u2;
select request_work_email_otp(:U2, 'x@movate.com', 'h');
select verify_work_email_otp(:U2, 'h');
select * from work_email_otps;
reset role;
