-- Sprint 4 tests: match score, status order, notifications, device tokens, stats, resume lock.
-- Run after all migrations (see run_tests.sh). Each block prints what it expects.
\set R '''f0000000-0000-0000-0000-00000000000a'''
\set S1 '''f0000000-0000-0000-0000-000000000001'''
\set S2 '''f0000000-0000-0000-0000-000000000002'''
\set S3 '''f0000000-0000-0000-0000-000000000003'''
\set J '''f1000000-0000-0000-0000-000000000001'''
\pset footer off

insert into auth.users (id) values (:R), (:S1), (:S2), (:S3);
update profiles set full_name = 'Priya Sundaram', experience_years = 8 where id = :R;
update profiles set full_name = 'Arun K', experience_years = 4, skills = '{kotlin,Jetpack Compose,AWS}',
  resume_path = :S1 || '/resume.pdf' where id = :S1;
update profiles set full_name = 'Bala M', experience_years = 1, skills = '{Java}',
  resume_path = :S2 || '/resume.pdf' where id = :S2;
update profiles set full_name = 'Chitra R', experience_years = 6, skills = '{}',
  resume_path = :S3 || '/resume.pdf' where id = :S3;
insert into storage.objects (bucket_id, name) values
  ('resumes', :S1 || '/resume.pdf'), ('resumes', :S2 || '/resume.pdf'), ('resumes', :S3 || '/resume.pdf');
insert into companies (id, name, domains, status)
  values ('f2000000-0000-0000-0000-000000000001', 'Movate', '{movate.com}', 'approved');
insert into referrer_verifications (user_id, company_id, work_email)
  values (:R, 'f2000000-0000-0000-0000-000000000001', 'priya@movate.com');
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, skills)
  values (:J, 'f2000000-0000-0000-0000-000000000001', :R, 'Android Dev', 'Chennai', 3, 5, 'hybrid', 'R1',
          '{Kotlin,Jetpack Compose,Coroutines,Hilt}');

\echo '--- M1 match_score cases (expect 75, 10, 35 = 10 exp + 25 neutral skills, 50 exp-only)'
select match_score('{Kotlin,Jetpack Compose,Coroutines,Hilt}', 3, 5, '{kotlin,Jetpack Compose,AWS}', 4) as arun,
       match_score('{Kotlin,Jetpack Compose,Coroutines,Hilt}', 3, 5, '{Java}', 1) as bala,
       match_score('{}', 3, 5, '{}', 7) as chitra_no_job_skills,
       match_score('{Kotlin}', 3, 5, '{}', 4) as exp_only;

set role authenticated;
\echo '--- M2 scores computed on apply; client value ignored (expect Arun 75, Bala 10, Chitra 30)'
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
insert into applications (job_id, seeker_id, match_score) values (:J, :S1, 100);
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
insert into applications (job_id, seeker_id) values (:J, :S2);
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
insert into applications (job_id, seeker_id) values (:J, :S3);
reset role;
select p.full_name, a.match_score from applications a join profiles p on p.id = a.seeker_id
  where a.job_id = :J order by a.match_score desc;

\echo '--- N1 referrer notified once per applicant (expect 3 new_applicant for Priya)'
select kind, count(*) from notifications where user_id = :R group by kind;

\echo '--- T1 referrer walks Arun forward: shortlisted, referred, interview, offer (expect 4 OKs)'
set role authenticated;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
update applications set status = 'shortlisted' where seeker_id = :S1 returning status;
update applications set status = 'referred'    where seeker_id = :S1 returning status;
update applications set status = 'interview'   where seeker_id = :S1 returning status;
update applications set status = 'offer'       where seeker_id = :S1 returning status;

\echo '--- T2 illegal moves (expect 4x STATUS_TRANSITION)'
update applications set status = 'referred'   where seeker_id = :S2;   -- skips shortlist
update applications set status = 'withdrawn'  where seeker_id = :S2;   -- only the seeker withdraws
update applications set status = 'applied'    where seeker_id = :S1;   -- backwards
update applications set status = 'waitlisted' where seeker_id = :S3;

\echo '--- T3 decline Bala (expect declined)'
update applications set status = 'declined' where seeker_id = :S2 returning status;
reset role;

\echo '--- N2 seeker notifications (expect Arun: 4 updates ending with offer; Bala: declined wording)'
select n.kind, n.title, n.body from notifications n where n.user_id = :S1 order by n.created_at, n.kind;
select title, body from notifications where user_id = :S2;

\echo '--- S1 stats: Priya referred 1, interviews 1 (via referrer_stats and job detail)'
select referrer_stats(:R);
set role authenticated;
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
select d->>'poster_referred' as referred, d->>'poster_interviews' as interviews, d->'skills' as skills
from (select get_job_detail(:J) d) x;

\echo '--- S2 my_posts_summary as Priya (expect applicants 3, new 1 (Chitra), shortlisted 0, referred 1)'
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
select applicants, new_count, shortlisted, referred, waitlisted from my_posts_summary();
\echo '--- S3 my_posts_summary as a seeker (expect 0 rows)'
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
select count(*) as rows_for_seeker from my_posts_summary();

\echo '--- R1 resume lock: Priya sees Chitra''s profile+resume before withdraw (1,1), not after (0,0)'
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
select (select count(*) from profiles where id = :S3) as profile,
       (select count(*) from storage.objects where name = :S3 || '/resume.pdf') as resume;
select set_config('request.jwt.claim.sub', :S3, false) is not null as as_s3;
select withdraw_application(id)->>'status' as withdraw from applications where seeker_id = :S3;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
select (select count(*) from profiles where id = :S3) as profile,
       (select count(*) from storage.objects where name = :S3 || '/resume.pdf') as resume;
\echo '--- R2 no notification for the withdrawal itself (expect Chitra has 0)'
reset role;
select count(*) as chitra_notifications from notifications where user_id = :S3;

\echo '--- D1 device tokens: register, re-register by another user moves it, unregister'
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select register_device_token('tok_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
select register_device_token('tok_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
reset role;
select p.full_name as token_owner from device_tokens t join profiles p on p.id = t.user_id;
set role authenticated;
select set_config('request.jwt.claim.sub', :S1, false) is not null as as_s1;
select unregister_device_token('tok_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');   -- not S1's any more: no effect
reset role;
select count(*) as still_there from device_tokens;
set role authenticated;
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
select unregister_device_token('tok_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
reset role;
select count(*) as after_owner_unregisters from device_tokens;

\echo '--- D2 app users cannot read tokens or others'' notifications (expect permission denied, 0)'
set role authenticated;
select set_config('request.jwt.claim.sub', :S2, false) is not null as as_s2;
select * from device_tokens;
select count(*) as others_notifications from notifications where user_id <> :S2;
\echo '--- D3 seeker can mark own notification read but not change its text (expect UPDATE ok, then permission denied)'
update notifications set read_at = now() where user_id = :S2 returning read_at is not null as marked;
update notifications set body = 'hacked' where user_id = :S2;
reset role;
