-- Removes the fictional test data from seed_test_jobs.sql before real users arrive.
-- Run once in the SQL Editor. Safe to run again (it only matches the seed ids).
-- Deleting the test referrers' auth users cascades to their profiles, verifications, jobs,
-- and every application, notification and report attached to those jobs.

begin;

delete from auth.users where id::text like '5eed0000-%';
delete from public.companies where id::text like '5eedc000-%';

-- Check: all three should be 0
select
  (select count(*) from auth.users where id::text like '5eed0000-%') as seed_users,
  (select count(*) from public.companies where id::text like '5eedc000-%') as seed_companies,
  (select count(*) from public.jobs where company_id::text like '5eedc000-%') as seed_jobs;

commit;
