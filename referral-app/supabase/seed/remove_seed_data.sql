-- =====================================================================
-- Production cleanup: removes ALL test data before real users arrive.
-- Run in: Supabase Dashboard -> SQL Editor. Safe to run again.
--
-- Removes
--   * the 5 fictional companies (Kaveri Cloud Systems, Marina Fintech, Tidel Product Labs,
--     Adyar Health Tech, OMR Digital Services) and every job posted under them, by anyone,
--     including real accounts' test posts from screenshot_data.sql or the app itself
--   * every application to those jobs (testers' included), and the notifications and
--     reports that point at them
--   * the 5 fictional seed users (priya@seed.test ...), with their profiles
--   * real accounts' "verified referrer" status at the fictional companies, so the
--     My posts tab disappears for them
--
-- Keeps
--   * every real account (yours, the Play reviewer's, testers'), their profiles and resumes
--   * the company allowlist (seed_company_allowlist.sql) and anything at real companies
--
-- Step 1 is a preview and changes nothing. Read its numbers, then run step 2.
-- =====================================================================

-- ---------- Step 1: preview (run alone first) ----------
select
  (select count(*) from public.companies where id::text like '5eedc000-%')                as fake_companies,
  (select count(*) from public.jobs      where company_id::text like '5eedc000-%')        as jobs_to_delete,
  (select count(*) from public.jobs      where company_id::text like '5eedc000-%'
                                           and referrer_id::text not like '5eed0000-%')   as of_which_by_real_accounts,
  (select count(*) from public.applications a join public.jobs j on j.id = a.job_id
     where j.company_id::text like '5eedc000-%')                                           as applications_to_delete,
  (select count(*) from public.referrer_verifications
     where company_id::text like '5eedc000-%' and user_id::text not like '5eed0000-%')    as real_accounts_losing_fake_verification,
  (select count(*) from auth.users where id::text like '5eed0000-%')                       as fake_users;


-- ---------- Step 2: delete ----------
begin;

create temp table _seed_jobs on commit drop as
  select id from public.jobs where company_id::text like '5eedc000-%';

-- Things that point at those jobs or the fake users without a foreign key
delete from public.notifications
  where data ->> 'job_id' in (select id::text from _seed_jobs)
     or user_id::text like '5eed0000-%';
delete from public.reports
  where target_id in (select id from _seed_jobs)
     or target_id::text like '5eed0000-%'
     or reporter_id::text like '5eed0000-%';

-- Jobs first: jobs.company_id has no cascade. Applications go with them (cascade).
delete from public.jobs where id in (select id from _seed_jobs);

-- Fake users: profiles, verifications, device tokens cascade
delete from auth.users where id::text like '5eed0000-%';

-- Fake companies: remaining verifications (real accounts') cascade
delete from public.companies where id::text like '5eedc000-%';

-- Check: every number should be 0
select
  (select count(*) from public.companies where id::text like '5eedc000-%')         as fake_companies,
  (select count(*) from public.jobs where company_id::text like '5eedc000-%')      as fake_jobs,
  (select count(*) from public.referrer_verifications
     where company_id::text like '5eedc000-%')                                      as fake_verifications,
  (select count(*) from auth.users where id::text like '5eed0000-%')                as fake_users;

commit;

-- What's left in the feed (should be only real posts, or none yet)
select c.name as company, j.title, j.status, j.created_at
from public.jobs j join public.companies c on c.id = j.company_id
order by j.created_at desc;
