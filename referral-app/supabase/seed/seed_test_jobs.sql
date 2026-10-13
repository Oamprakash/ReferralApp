-- =====================================================================
-- Test data for the job feed: 5 fictional companies, 5 verified test
-- referrers, 20 jobs covering every feed state.
--
-- Run in: Supabase Dashboard -> SQL Editor (runs as postgres, so RLS is
-- bypassed). Safe to re-run: it removes previous seed data first.
-- Cleanup only: run the "REMOVE SEED DATA" block at the bottom alone.
--
-- All seed rows are marked by:
--   users     -> email ends in @seed.test
--   companies -> domain ends in .test
-- =====================================================================

begin;

-- ---------- 0. Remove previous seed data (cascades to profiles, jobs, applications) ----------
delete from auth.users where email like '%@seed.test';
delete from public.companies where domains[1] like '%.test';

-- ---------- 1. Companies (fictional) ----------
insert into public.companies (id, name, domains, status) values
  ('5eedc000-0000-0000-0000-000000000001', 'Kaveri Cloud Systems', '{kavericloud.test}', 'approved'),
  ('5eedc000-0000-0000-0000-000000000002', 'Marina Fintech',       '{marinafintech.test}', 'approved'),
  ('5eedc000-0000-0000-0000-000000000003', 'Tidel Product Labs',   '{tidellabs.test}', 'approved'),
  ('5eedc000-0000-0000-0000-000000000004', 'Adyar Health Tech',    '{adyarhealth.test}', 'approved'),
  ('5eedc000-0000-0000-0000-000000000005', 'OMR Digital Services', '{omrdigital.test}', 'approved');

-- ---------- 2. Test referrers (auth users; the trigger creates their profiles) ----------
-- Empty-string token columns keep the Auth > Users dashboard page from erroring on these rows.
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, recovery_token, email_change_token_new, email_change
)
select '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated',
       u.email, '', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now(),
       '', '', '', ''
from (values
  ('5eed0000-0000-0000-0000-000000000001'::uuid, 'priya@seed.test'),
  ('5eed0000-0000-0000-0000-000000000002'::uuid, 'arun@seed.test'),
  ('5eed0000-0000-0000-0000-000000000003'::uuid, 'divya@seed.test'),
  ('5eed0000-0000-0000-0000-000000000004'::uuid, 'karthik@seed.test'),
  ('5eed0000-0000-0000-0000-000000000005'::uuid, 'meena@seed.test')
) as u(id, email);

update public.profiles p set
  full_name = v.name, city = 'Chennai', skills = v.skills, experience_years = v.exp
from (values
  ('5eed0000-0000-0000-0000-000000000001'::uuid, 'Priya S',   '{Kotlin,Android,AWS}'::text[], 8),
  ('5eed0000-0000-0000-0000-000000000002'::uuid, 'Arun K',    '{Java,Spring Boot,Kafka}'::text[], 10),
  ('5eed0000-0000-0000-0000-000000000003'::uuid, 'Divya R',   '{React,TypeScript,Figma}'::text[], 6),
  ('5eed0000-0000-0000-0000-000000000004'::uuid, 'Karthik M', '{Python,Django,PostgreSQL}'::text[], 9),
  ('5eed0000-0000-0000-0000-000000000005'::uuid, 'Meena V',   '{Selenium,Appium,QA}'::text[], 7)
) as v(id, name, skills, exp)
where p.id = v.id;

-- Each referrer verified at one company (90-day expiry from now)
insert into public.referrer_verifications (user_id, company_id, work_email) values
  ('5eed0000-0000-0000-0000-000000000001', '5eedc000-0000-0000-0000-000000000001', 'priya@kavericloud.test'),
  ('5eed0000-0000-0000-0000-000000000002', '5eedc000-0000-0000-0000-000000000002', 'arun@marinafintech.test'),
  ('5eed0000-0000-0000-0000-000000000003', '5eedc000-0000-0000-0000-000000000003', 'divya@tidellabs.test'),
  ('5eed0000-0000-0000-0000-000000000004', '5eedc000-0000-0000-0000-000000000004', 'karthik@adyarhealth.test'),
  ('5eed0000-0000-0000-0000-000000000005', '5eedc000-0000-0000-0000-000000000005', 'meena@omrdigital.test');

-- ---------- 3. Jobs ----------
-- n = referrer/company number (1-5); days_ago sets last_confirmed_at.
-- Feed states covered:
--   fresh (0-11 days)      -> should show
--   due soon (12-13 days)  -> should show; referrer gets a confirm reminder in Sprint 5
--   stale (16-20 days)     -> status still 'active' but older than 14 days; feed must hide these
--   hidden / closed        -> never show in the feed
-- Inserted closed/hidden first so the max-5-active-posts trigger is never hit.
insert into public.jobs (
  company_id, referrer_id, title, team, location, exp_min, exp_max, work_mode,
  salary_range, official_url, job_ref, questions, applicant_cap, status,
  last_confirmed_at, created_at
)
select ('5eedc000-0000-0000-0000-00000000000' || j.n)::uuid,
       ('5eed0000-0000-0000-0000-00000000000' || j.n)::uuid,
       j.title, j.team, j.location, j.exp_min, j.exp_max, j.mode::work_mode,
       j.salary, j.url, j.ref, j.questions, j.cap, j.status::job_status,
       now() - make_interval(days => j.days_ago),
       now() - make_interval(days => j.days_ago + 3)
from (values
  -- n, title, team, location, exp_min, exp_max, mode, salary, url, ref, questions, cap, status, days_ago

  -- Not in feed: closed / hidden
  (1, 'iOS Developer', 'Mobile', 'Chennai', 3, 5, 'hybrid', null, null, 'KCS-IOS-07', '{}'::text[], 30, 'closed', 2),
  (3, 'Product Analyst', 'Growth', 'Chennai', 2, 4, 'onsite', null, null, 'TPL-PA-03', '{}'::text[], 30, 'hidden', 1),

  -- Kaveri Cloud Systems (Priya): 4 active
  (1, 'Senior Android Developer', 'Mobile Platform', 'Chennai', 5, 8, 'hybrid', '₹25–35 LPA',
     'https://careers.kavericloud.test/jobs/and-112', null,
     '{"Have you shipped a Compose app to production?","What is your notice period?"}'::text[], 30, 'active', 0),
  (1, 'Android Developer', 'Payments SDK', 'Chennai', 2, 4, 'hybrid', '₹12–18 LPA',
     null, 'KCS-AND-118', '{"Rate your Kotlin coroutines experience (1-5)"}'::text[], 25, 'active', 3),
  (1, 'DevOps Engineer', 'Platform', 'Chennai', 4, 7, 'onsite', '₹18–26 LPA',
     'https://careers.kavericloud.test/jobs/ops-41', null, '{}'::text[], 30, 'active', 12),
  (1, 'Engineering Manager, Mobile', 'Mobile', 'Chennai', 10, 15, 'hybrid', '₹55–70 LPA',
     'https://careers.kavericloud.test/jobs/em-9', null,
     '{"How many engineers have you managed?"}'::text[], 15, 'active', 18),   -- stale

  -- Marina Fintech (Arun): 4 active
  (2, 'Backend Engineer (Java)', 'Core Banking', 'Chennai', 3, 6, 'onsite', '₹16–24 LPA',
     'https://jobs.marinafintech.test/be-java-22', null,
     '{"Have you worked with Kafka in production?"}'::text[], 30, 'active', 1),
  (2, 'Senior Backend Engineer', 'Lending', 'Chennai', 6, 10, 'hybrid', '₹30–42 LPA',
     null, 'MF-SBE-301', '{"What is your notice period?","Are you open to Chennai onsite 3 days a week?"}'::text[], 30, 'active', 5),
  (2, 'Site Reliability Engineer', 'Infra', 'Bengaluru', 4, 8, 'hybrid', null,
     'https://jobs.marinafintech.test/sre-14', null, '{}'::text[], 20, 'active', 9),
  (2, 'Security Engineer', 'AppSec', 'Chennai', 3, 7, 'onsite', '₹20–30 LPA',
     null, 'MF-SEC-077', '{}'::text[], 25, 'active', 20),                       -- stale

  -- Tidel Product Labs (Divya): 4 active
  (3, 'Frontend Developer (React)', 'Web', 'Chennai', 2, 5, 'remote', '₹14–22 LPA',
     'https://tidellabs.test/careers/fe-react', null,
     '{"Share a link to something you built with React"}'::text[], 30, 'active', 0),
  (3, 'Senior UI Engineer', 'Design Systems', 'Chennai', 5, 9, 'hybrid', '₹28–38 LPA',
     null, 'TPL-UI-12', '{"Have you built or maintained a component library?"}'::text[], 20, 'active', 4),
  (3, 'Product Designer', 'Design', 'Chennai', 3, 6, 'hybrid', null,
     'https://tidellabs.test/careers/pd-5', null, '{}'::text[], 30, 'active', 7),
  (3, 'QA Engineer, Web', 'Quality', 'Chennai', 1, 3, 'onsite', '₹6–10 LPA',
     null, 'TPL-QA-21', '{}'::text[], 3, 'active', 13),                          -- cap 3: test waitlist

  -- Adyar Health Tech (Karthik): 3 active
  (4, 'Python Developer', 'Data Platform', 'Chennai', 2, 5, 'hybrid', '₹12–20 LPA',
     'https://adyarhealth.test/jobs/py-33', null,
     '{"Have you worked with Django REST Framework?"}'::text[], 30, 'active', 2),
  (4, 'Data Engineer', 'Analytics', 'Chennai', 4, 7, 'remote', '₹20–28 LPA',
     null, 'AHT-DE-08', '{}'::text[], 30, 'active', 6),
  (4, 'Fresher Software Engineer', 'Graduate Program', 'Chennai', 0, 1, 'onsite', '₹4.5–6 LPA',
     'https://adyarhealth.test/jobs/grad-2026', null,
     '{"Which year did you graduate?","Share your GitHub profile"}'::text[], 50, 'active', 10),

  -- OMR Digital Services (Meena): 3 active
  (5, 'Automation Test Engineer', 'QA', 'Chennai', 3, 6, 'onsite', '₹10–16 LPA',
     'https://omrdigital.test/careers/ate-19', null,
     '{"Selenium or Appium: which have you used more?"}'::text[], 30, 'active', 1),
  (5, 'Flutter Developer', 'Mobile', 'Coimbatore', 2, 4, 'hybrid', '₹9–14 LPA',
     null, 'OMR-FL-04', '{}'::text[], 30, 'active', 8),
  (5, 'Technical Lead, Mobile', 'Mobile', 'Chennai', 8, 12, 'hybrid', '₹38–48 LPA',
     'https://omrdigital.test/careers/tl-mob', null,
     '{"Have you led a team through a Play Store release?"}'::text[], 20, 'active', 16) -- stale
) as j(n, title, team, location, exp_min, exp_max, mode, salary, url, ref, questions, cap, status, days_ago);

commit;

-- ---------- 4. Check: what the feed should show ----------
-- Expect 15 rows: 20 jobs minus 1 closed, 1 hidden and 3 stale.
select c.name as company, j.title, j.exp_min || '-' || j.exp_max || ' yrs' as exp,
       j.work_mode, j.salary_range,
       extract(day from now() - j.last_confirmed_at)::int as confirmed_days_ago
from public.jobs j join public.companies c on c.id = j.company_id
where j.status = 'active' and j.last_confirmed_at > now() - interval '14 days'
order by j.last_confirmed_at desc;

-- Counts by feed state
select case
         when status <> 'active' then status::text
         when last_confirmed_at <= now() - interval '14 days' then 'stale (hide)'
         else 'in feed'
       end as state,
       count(*)
from public.jobs
where company_id in (select id from public.companies where domains[1] like '%.test')
group by 1 order by 1;


-- =====================================================================
-- REMOVE SEED DATA (run this block on its own when you're done)
-- =====================================================================
-- begin;
-- delete from auth.users where email like '%@seed.test';
-- delete from public.companies where domains[1] like '%.test';
-- commit;
