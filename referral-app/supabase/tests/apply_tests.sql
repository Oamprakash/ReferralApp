-- Sprint 3 tests: applying, answers, waitlist, withdraw, job detail, visibility.
-- Run after all migrations (see run_tests.sh). Each block prints what it expects.
\set R '''dddd0000-0000-0000-0000-00000000000a'''
\set S1 '''dddd0000-0000-0000-0000-000000000001'''
\set S2 '''dddd0000-0000-0000-0000-000000000002'''
\set S3 '''dddd0000-0000-0000-0000-000000000003'''
\set J '''eeee0000-0000-0000-0000-000000000001'''
\set J2 '''eeee0000-0000-0000-0000-000000000002'''
\pset footer off

insert into auth.users (id) values (:R), (:S1), (:S2), (:S3);
update profiles set full_name = 'Priya Sundaram', experience_years = 8 where id = :R;
update profiles set full_name = 'Seeker ' || right(id::text, 1), experience_years = 3,
                    resume_path = id::text || '/resume.pdf'
  where id in (:S1, :S2, :S3);
insert into companies (id, name, domains, status)
  values ('cccc0000-0000-0000-0000-000000000001', 'Movate', '{movate.com}', 'approved');
insert into referrer_verifications (user_id, company_id, work_email)
  values (:R, 'cccc0000-0000-0000-0000-000000000001', 'priya@movate.com');
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref,
                  questions, applicant_cap) values
  (:J, 'cccc0000-0000-0000-0000-000000000001', :R, 'Android Dev', 'Chennai', 2, 5, 'hybrid', 'R1',
   '{"Notice period?","Compose experience?"}', 2),
  (:J2, 'cccc0000-0000-0000-0000-000000000001', :R, 'QA', 'Chennai', 1, 3, 'onsite', 'R2', '{}', 30);

set role authenticated;

\echo '--- A1 answers: none, one of two, a blank one (expect 3x ANSWERS_INVALID)'
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
insert into applications (job_id, seeker_id) values (:J, :S1);
insert into applications (job_id, seeker_id, answers) values (:J, :S1, '{"30 days"}');
insert into applications (job_id, seeker_id, answers) values (:J, :S1, '{"30 days","   "}');

\echo '--- A2 valid answers are trimmed; client-sent status/score ignored (expect applied, null, {"30 days",yes})'
insert into applications (job_id, seeker_id, answers, status, match_score)
  values (:J, :S1, '{" 30 days ","yes"}', 'offer', 99)
  returning status, match_score, answers;

\echo '--- A3 job without questions takes empty answers (expect applied)'
insert into applications (job_id, seeker_id) values (:J2, :S1) returning status;

\echo '--- A4 apply twice to the same job (expect unique violation)'
insert into applications (job_id, seeker_id, answers) values (:J, :S1, '{"a","b"}');

\echo '--- A5 cap 2: S2 gets the last spot, S3 is waitlisted (expect applied, waitlisted)'
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into applications (job_id, seeker_id, answers) values (:J, :S2, '{"a","b"}') returning status;
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
insert into applications (job_id, seeker_id, answers) values (:J, :S3, '{"a","b"}') returning status;

\echo '--- A6 detail as S3: 0 spots, 1 waiting, my status waitlisted, poster first name only'
select d->>'spots_left' as spots, d->>'waitlist_count' as waiting, d->>'my_application_status' as mine,
       d->>'poster_first_name' as poster, d->>'company_name' as company, d->>'is_mine' as is_mine
from (select get_job_detail(:J) as d) x;

\echo '--- A7 S3 calls withdraw with S1''s application id (expect not_found; S1 still applied)'
reset role;
select id as s1_app from applications where seeker_id = :S1 and job_id = :J \gset
set role authenticated;
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
select withdraw_application(:'s1_app')->>'status' as s3_withdrawing_s1;

\echo '--- A8 S1 withdraws: S3 promoted (expect withdrawn true; S3 applied)'
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select withdraw_application(id) as resp from applications where seeker_id = :S1 and job_id = :J;
reset role;
select p.full_name, a.status from applications a join profiles p on p.id = a.seeker_id
  where a.job_id = :J order by p.full_name;
set role authenticated;

\echo '--- A9 withdraw again (expect not_allowed)'
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select withdraw_application(id)->>'status' from applications where seeker_id = :S1 and job_id = :J;

\echo '--- A10 seekers cannot set status or delete (expect UPDATE 0; permission denied for the delete)'
update applications set status = 'withdrawn' where seeker_id = :S1 and job_id = :J2;
delete from applications where seeker_id = :S1;
select status as still_applied from applications where seeker_id = :S1 and job_id = :J2;

\echo '--- A11 job closes: S1 (applied) still sees it and its company; S-new (never applied) does not'
reset role;
update jobs set status = 'closed' where id = :J2;
insert into auth.users (id) values ('dddd0000-0000-0000-0000-0000000000ff');
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select j.title, c.name from jobs j join companies c on c.id = j.company_id where j.id = :J2;
select get_job_detail(:J2)->>'status' as detail_status;
select set_config('request.jwt.claim.sub', 'dddd0000-0000-0000-0000-0000000000ff', false) is not null as as_new;
select count(*) as new_user_sees from jobs where id = :J2;
select case when get_job_detail(:J2) is null then 'hidden' else 'VISIBLE' end as detail;

\echo '--- A12 weekly limit: withdrawn applications still count (expect 10th ok, 11th WEEKLY_LIMIT)'
reset role;
alter table jobs disable trigger jobs_post_limit;   -- test setup only: 9 open jobs from one referrer
insert into jobs (company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref)
  select 'cccc0000-0000-0000-0000-000000000001', :R, 'Bulk ' || g, 'Chennai', 0, 5, 'remote', 'B'
  from generate_series(1, 9) g;
alter table jobs enable trigger jobs_post_limit;
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select count(*) as s1_this_week from applications where seeker_id = :S1;
insert into applications (job_id, seeker_id)
  select id, :S1 from jobs where title in ('Bulk 1','Bulk 2','Bulk 3','Bulk 4','Bulk 5','Bulk 6','Bulk 7','Bulk 8');
select count(*) as s1_now from applications where seeker_id = :S1;
insert into applications (job_id, seeker_id) select id, :S1 from jobs where title = 'Bulk 9';
reset role;
