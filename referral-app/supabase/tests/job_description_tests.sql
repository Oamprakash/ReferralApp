-- Job description: tidy-up trigger, length limit, shown by get_job_detail.
\set R '''e1000000-0000-0000-0000-00000000000a'''
\set S '''e1000000-0000-0000-0000-000000000001'''
\set CO '''e2000000-0000-0000-0000-000000000001'''
\pset footer off
insert into auth.users (id, phone) values (:R, '919200000001'), (:S, '919200000002');
insert into companies (id, name, domains, status) values (:CO, 'Movate', '{movate.com}', 'approved');
insert into referrer_verifications (user_id, company_id, work_email) values (:R, :CO, 'r@movate.com');

set role authenticated;
select set_config('request.jwt.claim.sub', :R, false) is not null as as_r;
\echo '--- D1 referrer posts with a messy description: trimmed, 3+ blank lines collapsed to one'
insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, description)
values ('e3000000-0000-0000-0000-000000000001', :CO, :R, 'Android Dev', 'Chennai', 3, 6, 'hybrid', 'J1',
        E'  Build the payments app.\n\n\n\n\nMust know Kotlin.  ')
returning description = E'Build the payments app.\n\nMust know Kotlin.' as tidied;
\echo '--- D2 blank description is stored as null; no description is fine'
insert into jobs (company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, description)
values (:CO, :R, 'QA', 'Chennai', 1, 3, 'onsite', 'J2', '   ') returning description is null as blank_is_null;
\echo '--- D3 over 4000 characters (expect check violation)'
insert into jobs (company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref, description)
values (:CO, :R, 'Long', 'Chennai', 1, 3, 'onsite', 'J3', repeat('x', 4001));
\echo '--- D4 a seeker sees it in get_job_detail'
select set_config('request.jwt.claim.sub', :S, false) is not null as as_s;
select get_job_detail('e3000000-0000-0000-0000-000000000001')->>'description' as detail_description;
reset role;
