-- =====================================================================
-- Data for Play Store screenshots. Run AFTER seed_test_jobs.sql (and seed_job_skills.sql)
-- have been run once. Safe to run again.
--
-- Run in: Supabase Dashboard -> SQL Editor.
-- Put YOUR Google sign-in email on the line marked "<-- your email" first.
--
-- What it does:
--   1. Makes every seed job fresh again (the daily job hides posts after 14 days).
--   2. Gives your account a few applications to seed jobs at different stages,
--      so the Applied tab has something to show.
--   3. Makes your account a verified referrer at "Kaveri Cloud Systems" with two posts,
--      each with applicants from the seed users, so My posts and Applicants have data.
--
-- You'll get a few push notifications while it runs; that's the app working.
-- Remove everything later with remove_seed_data.sql: it deletes the seed companies,
-- which also removes your seed posts, your verification there and your applications to seed jobs.
-- =====================================================================

do $$
declare
  my_email text := 'oamprakash@gmail.com';   -- <-- your email (the Google account you sign in with)
  me uuid;
  kaveri uuid := '5eedc000-0000-0000-0000-000000000001';
  post_a uuid;
  post_b uuid;
begin
  select id into me from auth.users where email = my_email;
  if me is null then
    raise exception 'No user with email %. Sign in to the app once with that Google account, then run this again.', my_email;
  end if;

  -- ---------- 1. Fresh seed jobs ----------
  -- Spread "confirmed N days ago" over 0-11 days so the feed looks natural.
  update public.jobs j set
    status = 'active',
    hidden_reason = null,
    last_confirmed_at = now() - make_interval(days => (abs(hashtext(j.id::text)) % 12)),
    reminded_at = null
  from public.companies c
  where j.company_id = c.id
    and c.id::text like '5eedc000-%'
    and j.referrer_id::text like '5eed0000-%'
    and j.title not in ('iOS Developer', 'Product Analyst');   -- keep the closed/hidden examples as they are

  -- Seed referrers stay verified
  update public.referrer_verifications set verified_at = now(), expires_at = now() + interval '90 days'
  where user_id::text like '5eed0000-%';

  -- ---------- 2. Your applications to seed jobs ----------
  insert into public.applications (job_id, seeker_id, answers, match_score)
  select j.id, me, v.answers, v.score
  from (values
    ('Senior Android Developer', '{"Yes, two apps on Play with Compose","30 days"}'::text[], 92::smallint),
    ('Android Developer',        '{"5"}'::text[],                                            85::smallint),
    ('Flutter Developer',        '{}'::text[],                                               64::smallint),
    ('Data Engineer',            '{}'::text[],                                               41::smallint)
  ) as v(title, answers, score)
  join public.jobs j on j.title = v.title and j.referrer_id::text like '5eed0000-%'
  on conflict (job_id, seeker_id) do nothing;

  -- Move them along the pipeline (runs as postgres, so the status rules don't apply)
  update public.applications a set status = v.status::application_status
  from (values
    ('Senior Android Developer', 'interview'),
    ('Android Developer',        'referred'),
    ('Flutter Developer',        'shortlisted'),
    ('Data Engineer',            'applied')
  ) as v(title, status), public.jobs j
  where a.job_id = j.id and j.title = v.title and a.seeker_id = me
    and j.referrer_id::text like '5eed0000-%';

  -- ---------- 3. You as a referrer with two posts ----------
  insert into public.referrer_verifications (user_id, company_id, work_email)
  values (me, kaveri, 'you@kavericloud.test')
  on conflict (user_id, company_id) do update set verified_at = now(), expires_at = now() + interval '90 days';

  -- Replace earlier screenshot posts
  delete from public.jobs where referrer_id = me and company_id = kaveri;

  insert into public.jobs (company_id, referrer_id, title, team, location, exp_min, exp_max, work_mode,
                           salary_range, job_ref, questions, applicant_cap, status, skills, last_confirmed_at, created_at)
  values (kaveri, me, 'Staff Android Engineer', 'Mobile Platform', 'Chennai', 8, 12, 'hybrid', '₹45–60 LPA',
          'KCS-AND-140', '{"What is your notice period?"}', 30, 'active',
          '{Kotlin,Android,Jetpack Compose,Clean Architecture}', now() - interval '2 days', now() - interval '6 days')
  returning id into post_a;

  insert into public.jobs (company_id, referrer_id, title, team, location, exp_min, exp_max, work_mode,
                           salary_range, job_ref, questions, applicant_cap, status, skills, last_confirmed_at, created_at)
  values (kaveri, me, 'Backend Engineer (Java)', 'Billing', 'Chennai', 3, 6, 'onsite', '₹18–26 LPA',
          'KCS-BE-77', '{}', 30, 'active',
          '{Java,Spring Boot,Kafka,PostgreSQL}', now() - interval '9 days', now() - interval '12 days')
  returning id into post_b;

  -- Applicants: the seed users
  insert into public.applications (job_id, seeker_id, answers, match_score)
  values
    (post_a, '5eed0000-0000-0000-0000-000000000001', '{"30 days"}', 88),
    (post_a, '5eed0000-0000-0000-0000-000000000003', '{"Immediate"}', 52),
    (post_a, '5eed0000-0000-0000-0000-000000000005', '{"60 days"}', 37),
    (post_b, '5eed0000-0000-0000-0000-000000000002', '{}', 94),
    (post_b, '5eed0000-0000-0000-0000-000000000004', '{}', 58);

  update public.applications set status = 'shortlisted'
    where job_id = post_a and seeker_id = '5eed0000-0000-0000-0000-000000000001';
  update public.applications set status = 'referred'
    where job_id = post_b and seeker_id = '5eed0000-0000-0000-0000-000000000002';
end $$;

-- ---------- Check ----------
-- Feed: should be about 18 live jobs
select c.name as company, j.title, extract(day from now() - j.last_confirmed_at)::int as confirmed_days_ago
from public.jobs j join public.companies c on c.id = j.company_id
where j.status = 'active' and j.last_confirmed_at > now() - interval '14 days'
order by j.last_confirmed_at desc;
