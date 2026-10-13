-- Reapply tests. Run after all migrations (see run_tests.sh). Each block prints what it expects.
\set R '''a1000000-0000-0000-0000-00000000000a'''
\set S1 '''a1000000-0000-0000-0000-000000000001'''
\set S2 '''a1000000-0000-0000-0000-000000000002'''
\set S3 '''a1000000-0000-0000-0000-000000000003'''
\set J '''a2000000-0000-0000-0000-000000000001'''
\pset footer off

insert into auth.users (id) values (:R), (:S1), (:S2), (:S3);
update profiles set full_name = 'Priya S', experience_years = 8 where id = :R;
update profiles set full_name = 'Seeker ' || right(id::text, 1), experience_years = 3,
  resume_path = id::text || '/resume.pdf' where id in (:S1, :S2, :S3);
insert into companies (id, name, domains, status)
  values ('a3000000-0000-0000-0000-000000000001', 'Movate', '{movate.com}', 'approved');
insert into referrer_verifications (user_id, company_id, work_email)
  values (:R, 'a3000000-0000-0000-0000-000000000001', 'priya@movate.com');
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref,
                  questions, applicant_cap)
  values (:J, 'a3000000-0000-0000-0000-000000000001', :R, 'Android Dev', 'Chennai', 2, 5, 'hybrid', 'R1',
          '{"Notice period?"}', 2);

set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
insert into applications (job_id, seeker_id, answers) values (:J, :S1, '{"30 days"}');
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into applications (job_id, seeker_id, answers) values (:J, :S2, '{"60 days"}');
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
insert into applications (job_id, seeker_id, answers) values (:J, :S3, '{"15 days"}') returning status as s3_first;

\echo '--- P1 S1 withdraws: S3 promoted; detail says S1 can reapply (expect withdrawn, can_reapply true)'
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
reset role;
select id as s1_app from applications where seeker_id = :S1 \gset
set role authenticated;
select withdraw_application(:'s1_app') - 'promoted' as resp;
select d->>'my_application_status' as mine, d->>'can_reapply' as can_reapply from (select get_job_detail(:J) d) x;

\echo '--- P2 reapply with a blank answer (expect ANSWERS_INVALID)'
select reapply_application(:'s1_app', '{"  "}');

\echo '--- P3 reapply while full: back of the line (expect waitlisted, answers updated)'
select reapply_application(:'s1_app', '{" 90 days "}')->>'status' as reapply;
reset role;
select answers, reapplied_at is not null as marked from applications where id = :'s1_app';

\echo '--- P4 referrer pings: only S1 and S2 first applies (expect new_applicant 2; S3 and the reapply went to the waitlist)'
select kind, count(*) from notifications where user_id = :R group by kind;
\echo '--- P5 S1 got no "spot opened" message from the reapply (expect 0)'
select count(*) as s1_notifications from notifications where user_id = :S1;

\echo '--- P6 second reapply refused (withdraw again first; expect withdrawn, can_reapply false, already_reapplied)'
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select withdraw_application(:'s1_app') - 'promoted' as resp;
select reapply_application(:'s1_app', '{"90 days"}')->>'status' as second_reapply;
select get_job_detail(:J)->>'can_reapply' as can_reapply;

\echo '--- P7 queue order: S2 withdraws a spot; the earlier waitlister, not a reapplier, moves up'
reset role;
-- New scenario on a fresh job with cap 1: A applies, B waitlisted, A withdraws -> B promoted;
-- A reapplies (waitlisted, behind C who joined earlier), B withdraws -> C promoted, not A.
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, applicant_cap)
  values ('a2000000-0000-0000-0000-000000000002', 'a3000000-0000-0000-0000-000000000001', :R, 'QA', 'Chennai', 1, 3, 'onsite', 'R2', 1);
\set J2 '''a2000000-0000-0000-0000-000000000002'''
insert into auth.users (id) values ('a1000000-0000-0000-0000-0000000000a1'), ('a1000000-0000-0000-0000-0000000000b1'), ('a1000000-0000-0000-0000-0000000000c1');
update profiles set full_name = 'A', resume_path = 'x', experience_years = 2 where id = 'a1000000-0000-0000-0000-0000000000a1';
update profiles set full_name = 'B', resume_path = 'x', experience_years = 2 where id = 'a1000000-0000-0000-0000-0000000000b1';
update profiles set full_name = 'C', resume_path = 'x', experience_years = 2 where id = 'a1000000-0000-0000-0000-0000000000c1';
set role authenticated;
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-0000000000a1', false) is not null as as_a;
insert into applications (job_id, seeker_id) values (:J2, 'a1000000-0000-0000-0000-0000000000a1');
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-0000000000b1', false) is not null as as_b;
insert into applications (job_id, seeker_id) values (:J2, 'a1000000-0000-0000-0000-0000000000b1');
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-0000000000a1', false) is not null as as_a;
select withdraw_application(id)->>'status' as a_withdraws from applications where job_id = :J2 and seeker_id = 'a1000000-0000-0000-0000-0000000000a1';
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-0000000000c1', false) is not null as as_c;
insert into applications (job_id, seeker_id) values (:J2, 'a1000000-0000-0000-0000-0000000000c1') returning status as c_status;
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-0000000000a1', false) is not null as as_a;
select reapply_application(id, '{}')->>'status' as a_reapplies from applications where job_id = :J2 and seeker_id = 'a1000000-0000-0000-0000-0000000000a1';
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-0000000000b1', false) is not null as as_b;
select withdraw_application(id)->>'status' as b_withdraws from applications where job_id = :J2 and seeker_id = 'a1000000-0000-0000-0000-0000000000b1';
reset role;
\echo '    (expect A waitlisted, B withdrawn, C applied)'
select p.full_name, a.status from applications a join profiles p on p.id = a.seeker_id where a.job_id = :J2 order by 1;

\echo '--- P8 reapply into an open spot notifies the referrer as a new applicant (expect +1 new_applicant)'
select count(*) as before from notifications where user_id = :R and kind = 'new_applicant' \gset
set role authenticated;
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
select withdraw_application(id)->>'status' from applications where seeker_id = :S2 and job_id = :J;
reset role;
-- reset S2's reapply allowance is untouched; spot is now open
set role authenticated;
select reapply_application(id, '{"45 days"}')->>'status' as s2_reapply from applications where seeker_id = :S2 and job_id = :J;
reset role;
select count(*) - :before as new_pings from notifications where user_id = :R and kind = 'new_applicant';

\echo '--- P9 closed job: no reapply (expect job_closed)'
set role authenticated;
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
select withdraw_application(id)->>'status' from applications where seeker_id = :S3 and job_id = :J;
reset role;
update jobs set status = 'closed' where id = :J;
set role authenticated;
select reapply_application(id, '{"x"}')->>'status' as closed_job from applications where seeker_id = :S3 and job_id = :J;

\echo '--- P10 weekly limit counts reapplies (S1: 1 apply + 1 reapply = 2)'
reset role;
select applications_this_week(:S1) as s1_week;

\echo '--- P11 someone else cannot reapply S2''s application (expect not_found)'
set role authenticated;
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
reset role;
select id as s2_app from applications where seeker_id = :S2 and job_id = :J \gset
set role authenticated;
select reapply_application(:'s2_app', '{"x"}')->>'status' as other_user;
reset role;
