-- Resume parser & auto-match backend: contact details privacy, applicant_contact, best_matches, export.
\set R '''d1000000-0000-0000-0000-00000000000a'''
\set R2 '''d1000000-0000-0000-0000-00000000000b'''
\set S '''d1000000-0000-0000-0000-000000000001'''
\set O '''d1000000-0000-0000-0000-000000000002'''
\set CO '''d2000000-0000-0000-0000-000000000001'''
\pset footer off

insert into auth.users (id, phone) values (:R, '919100000001'), (:R2, '919100000005'), (:S, '919100000002'), (:O, '919100000003');
update profiles set full_name = 'Ref', experience_years = 9 where id in (:R, :R2);
update profiles set full_name = 'Seeker', experience_years = 4, skills = '{Kotlin,Jetpack Compose,Hilt}',
  resume_path = id::text || '/resume.pdf' where id = :S;
update profiles set full_name = 'Other', experience_years = 2, resume_path = id::text || '/resume.pdf' where id = :O;
insert into companies (id, name, domains, status) values (:CO, 'Movate', '{movate.com}', 'approved');
insert into referrer_verifications (user_id, company_id, work_email) values (:R, :CO, 'r@movate.com'), (:R2, :CO, 'r2@movate.com');
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, skills, last_confirmed_at) values
  ('d3000000-0000-0000-0000-000000000001', :CO, :R, 'Android perfect', 'Chennai', 3, 6, 'hybrid', 'J1', '{kotlin,"Jetpack Compose"}', now()),
  ('d3000000-0000-0000-0000-000000000002', :CO, :R, 'Android partial', 'Chennai', 3, 6, 'hybrid', 'J2', '{Kotlin,Coroutines,Room,Retrofit}', now() - interval '1 day'),
  ('d3000000-0000-0000-0000-000000000003', :CO, :R, 'Senior far',      'Chennai', 10, 14, 'onsite', 'J3', '{Java}', now()),
  ('d3000000-0000-0000-0000-000000000004', :CO, :R, 'Stale perfect',   'Chennai', 3, 6, 'remote', 'J4', '{Kotlin}', now() - interval '15 days'),
  ('d3000000-0000-0000-0000-000000000005', :CO, :R2, 'Applied already','Chennai', 3, 6, 'remote', 'J5', '{Kotlin}', now());

\echo '=== CONTACTS'
set role authenticated;
select set_config('request.jwt.claim.sub', :S, false) is not null as as_s;
\echo '--- C1 seeker saves contact; email lower-cased and trimmed'
insert into profile_contacts (user_id, email, phone) values (:S, '  Seeker@Gmail.COM ', '+919876543210') returning email, phone;
\echo '--- C2 bad phone / bad email / someone else''s row (expect check x2, RLS x1)'
update profile_contacts set phone = '9876543210' where user_id = :S;
update profile_contacts set email = 'not-an-email' where user_id = :S;
insert into profile_contacts (user_id, email) values (:O, 'x@y.com');
\echo '--- C3 other users and the referrer cannot read the table (expect 0 rows each)'
select set_config('request.jwt.claim.sub', :O, false) is not null as as_o;
select count(*) as other_sees from profile_contacts;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
select count(*) as referrer_sees from profile_contacts;

\echo '=== MATCHES'
select set_config('request.jwt.claim.sub', :S, false) is not null as as_s;
insert into applications (job_id, seeker_id, answers) values ('d3000000-0000-0000-0000-000000000005', :S, '{}');
\echo '--- M1 best_matches: perfect (100) then partial (50+13=63); not senior-far (0), stale, or applied'
select m->>'title' as title, (m->>'score')::int as score, m->'have' as have, m->'missing' as missing, m->>'company_name' as company
from jsonb_array_elements(best_matches()) m;
\echo '--- M2 limit is respected and clamped'
select jsonb_array_length(best_matches(1)) as one, jsonb_array_length(best_matches(0)) as clamped_to_one;
\echo '--- M3 a referrer with a fitting profile sees only the other referrer''s post, never their own'
reset role;
update profiles set experience_years = 4, skills = '{Kotlin}' where id = :R;
set role authenticated;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
select m->>'title' as title from jsonb_array_elements(best_matches()) m;
\echo '--- M4 anon cannot call it (expect permission denied)'
reset role;
set role anon;
select best_matches();
reset role;

\echo '=== APPLICANT CONTACT'
set role authenticated;
select set_config('request.jwt.claim.sub', :S, false) is not null as as_s;
insert into applications (job_id, seeker_id, answers) values ('d3000000-0000-0000-0000-000000000001', :S, '{}');
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
\echo '--- A1 before shortlisting: null'
select applicant_contact((select id from applications where job_id = 'd3000000-0000-0000-0000-000000000001')) as before_shortlist;
\echo '--- A2 after shortlisting: email and phone'
update applications set status = 'shortlisted' where job_id = 'd3000000-0000-0000-0000-000000000001';
select applicant_contact((select id from applications where job_id = 'd3000000-0000-0000-0000-000000000001')) as after_shortlist;
\echo '--- A3 a different referrer gets null'
select set_config('request.jwt.claim.sub', :R2, false) is not null as as_r2;
select applicant_contact((select id from applications where job_id = 'd3000000-0000-0000-0000-000000000001')) as other_referrer;
\echo '--- A4 declined applicants are hidden again'
select set_config('request.jwt.claim.sub', :R2, false) is not null as as_r2;
update applications set status = 'declined' where job_id = 'd3000000-0000-0000-0000-000000000005';
select applicant_contact((select id from applications where job_id = 'd3000000-0000-0000-0000-000000000005')) as declined;

\echo '=== EXPORT AND DELETE'
select set_config('request.jwt.claim.sub', :S, false) is not null as as_s;
\echo '--- E1 export includes contact_for_referrals'
select export_my_data()->'contact_for_referrals'->>'email' as exported_email;
reset role;
\echo '--- E2 deleting the user removes the contact row'
delete from auth.users where id = :S;
select count(*) as contacts_left from profile_contacts;
