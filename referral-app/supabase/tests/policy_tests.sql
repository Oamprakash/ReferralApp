\set A '''aaaaaaaa-0000-0000-0000-000000000001'''
\set B '''bbbbbbbb-0000-0000-0000-000000000002'''
\set C '''cccccccc-0000-0000-0000-000000000003'''
insert into auth.users (id) values (:A),(:B),(:C);
select count(*) as profiles_autocreated from profiles;
insert into companies (id,name,domains,status) values ('11111111-0000-0000-0000-000000000000','Movate','{movate.com}','approved');
insert into referrer_verifications (user_id,company_id,work_email) values (:A,'11111111-0000-0000-0000-000000000000','a@movate.com');

\echo '--- T1 verified referrer A posts a job (expect OK)'
set role authenticated; select set_config('request.jwt.claim.sub', :A, false);
insert into jobs (id,company_id,referrer_id,title,location,exp_min,exp_max,work_mode,job_ref,applicant_cap)
 values ('22222222-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000000',:A,'Android Dev','Chennai',3,6,'hybrid','REQ-1',1);
\echo '--- T2 A posts 5 more (expect 4 OK then ACTIVE_POST_LIMIT)'
insert into jobs (company_id,referrer_id,title,location,exp_min,exp_max,work_mode,job_ref) select '11111111-0000-0000-0000-000000000000',:A,'J'||g,'Chennai',1,2,'remote','R' from generate_series(1,4) g;
insert into jobs (company_id,referrer_id,title,location,exp_min,exp_max,work_mode,job_ref) values ('11111111-0000-0000-0000-000000000000',:A,'J6','Chennai',1,2,'remote','R');
\echo '--- T3 unverified B posts (expect RLS error)'
select set_config('request.jwt.claim.sub', :B, false);
insert into jobs (company_id,referrer_id,title,location,exp_min,exp_max,work_mode,job_ref) values ('11111111-0000-0000-0000-000000000000',:B,'X','Chennai',1,2,'remote','R');
\echo '--- T4 job without link or ref (expect check violation)'
select set_config('request.jwt.claim.sub', :A, false);
update jobs set status='closed' where title='J4';
insert into jobs (company_id,referrer_id,title,location,exp_min,exp_max,work_mode) values ('11111111-0000-0000-0000-000000000000',:A,'X','Chennai',1,2,'remote');
\echo '--- T5 B applies without resume (expect RLS error)'
select set_config('request.jwt.claim.sub', :B, false);
insert into applications (job_id,seeker_id) values ('22222222-0000-0000-0000-000000000001',:B);
\echo '--- T6 B uploads resume, sets path, applies (expect status applied)'
insert into storage.objects (bucket_id,name) values ('resumes','bbbbbbbb-0000-0000-0000-000000000002/resume.pdf');
update profiles set resume_path='bbbbbbbb-0000-0000-0000-000000000002/resume.pdf', full_name='Bala' where id=:B;
insert into applications (job_id,seeker_id) values ('22222222-0000-0000-0000-000000000001',:B) returning status;
\echo '--- T7 B writes into C folder (expect RLS error)'
insert into storage.objects (bucket_id,name) values ('resumes','cccccccc-0000-0000-0000-000000000003/resume.pdf');
\echo '--- T8 B tries to self-shortlist (expect UPDATE 0)'
update applications set status='shortlisted' where seeker_id=:B;
\echo '--- T9 C applies past cap of 1 (expect waitlisted)'
select set_config('request.jwt.claim.sub', :C, false);
update profiles set resume_path='x' where id=:C;
insert into applications (job_id,seeker_id) values ('22222222-0000-0000-0000-000000000001',:C) returning status;
\echo '--- T10 C reads B profile and resume (expect 0, 0)'
select count(*) from profiles where id=:B; select count(*) from storage.objects where name like 'bbbbbbbb%';
\echo '--- T11 A reads applicant B profile and resume (expect 1, 1)'
select set_config('request.jwt.claim.sub', :A, false);
select count(*) from profiles where id=:B; select count(*) from storage.objects where name like 'bbbbbbbb%';
\echo '--- T12 A shortlists B (expect UPDATE 1), then edits match_score (expect permission denied)'
update applications set status='shortlisted' where seeker_id=:B;
update applications set match_score=99 where seeker_id=:B;
\echo '--- T13 A applies to own job (expect RLS error)'
update profiles set resume_path='y' where id=:A;
insert into applications (job_id,seeker_id) values ('22222222-0000-0000-0000-000000000001',:A);
\echo '--- T14 expired verification blocks posting (expect RLS error)'
reset role; update referrer_verifications set expires_at=now()-interval '1 day';
set role authenticated; select set_config('request.jwt.claim.sub', :A, false);
update jobs set status='closed' where title='J3';
insert into jobs (company_id,referrer_id,title,location,exp_min,exp_max,work_mode,job_ref) values ('11111111-0000-0000-0000-000000000000',:A,'Late','Chennai',1,2,'remote','R');
\echo '--- T15 B edits own priority_until (expect permission denied)'
select set_config('request.jwt.claim.sub', :B, false);
update profiles set priority_until = now() + interval '1 year' where id=:B;
