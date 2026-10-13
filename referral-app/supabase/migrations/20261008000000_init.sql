-- Referral Jobs App: Sprint 1 schema
-- Tables: profiles, companies, referrer_verifications, jobs, applications, reports
-- Phone and email stay in auth.users; profiles hold no contact details.

-- ---------- Enums ----------
create type company_status as enum ('approved', 'pending', 'rejected');
create type work_mode as enum ('onsite', 'hybrid', 'remote');
create type job_status as enum ('active', 'hidden', 'closed');
create type application_status as enum (
  'applied', 'shortlisted', 'declined', 'referred',
  'interview', 'offer', 'not_selected', 'waitlisted'
);
create type report_target as enum ('job', 'user');
create type report_reason as enum ('payment_request', 'closed_role', 'fake', 'abuse', 'other');
create type report_status as enum ('open', 'actioned', 'dismissed');

-- ---------- Shared trigger: updated_at ----------
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------- profiles ----------
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  full_name text,
  city text default 'Chennai',
  skills text[] not null default '{}',
  experience_years smallint check (experience_years between 0 and 50),
  resume_path text,                       -- path inside the private 'resumes' bucket
  priority_until timestamptz,             -- set when a seeker ignores a shortlist
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create trigger profiles_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();

-- Create an empty profile for every new auth user
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id) values (new.id) on conflict do nothing;
  return new;
end;
$$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- companies ----------
create table public.companies (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  domains text[] not null default '{}',   -- e.g. {'movate.com'}
  status company_status not null default 'pending',
  created_at timestamptz not null default now()
);
create index companies_domains_idx on public.companies using gin (domains);

-- ---------- referrer_verifications ----------
create table public.referrer_verifications (
  user_id uuid not null references public.profiles (id) on delete cascade,
  company_id uuid not null references public.companies (id) on delete cascade,
  work_email text not null,
  verified_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '90 days'),
  primary key (user_id, company_id)
);

-- True when the current user holds an unexpired verification for the company
create or replace function public.is_verified_referrer(p_company uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from referrer_verifications
    where user_id = auth.uid() and company_id = p_company and expires_at > now()
  );
$$;

-- ---------- jobs ----------
create table public.jobs (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references public.companies (id),
  referrer_id uuid not null references public.profiles (id) on delete cascade,
  title text not null,
  team text,
  location text not null,
  exp_min smallint not null check (exp_min >= 0),
  exp_max smallint not null check (exp_max >= exp_min),
  work_mode work_mode not null,
  salary_range text,
  official_url text,
  job_ref text,
  questions text[] not null default '{}' check (cardinality(questions) <= 3),
  applicant_cap smallint not null default 30 check (applicant_cap between 1 and 50),
  status job_status not null default 'active',
  last_confirmed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint jobs_link_or_ref check (official_url is not null or job_ref is not null)
);
create index jobs_feed_idx on public.jobs (status, last_confirmed_at desc);
create index jobs_referrer_idx on public.jobs (referrer_id);
create trigger jobs_updated_at before update on public.jobs
  for each row execute function public.set_updated_at();

-- Max 5 active posts per referrer
create or replace function public.enforce_active_post_limit()
returns trigger language plpgsql as $$
begin
  if new.status = 'active' and (
    select count(*) from public.jobs
    where referrer_id = new.referrer_id and status = 'active' and id <> new.id
  ) >= 5 then
    raise exception 'ACTIVE_POST_LIMIT: max 5 active posts per referrer';
  end if;
  return new;
end;
$$;
create trigger jobs_post_limit before insert or update of status on public.jobs
  for each row execute function public.enforce_active_post_limit();

-- ---------- applications ----------
create table public.applications (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs (id) on delete cascade,
  seeker_id uuid not null references public.profiles (id) on delete cascade,
  answers text[] not null default '{}',
  match_score smallint,
  status application_status not null default 'applied',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (job_id, seeker_id)
);
create index applications_seeker_idx on public.applications (seeker_id, created_at desc);
create index applications_job_idx on public.applications (job_id, match_score desc);
create trigger applications_updated_at before update on public.applications
  for each row execute function public.set_updated_at();

-- On apply: 10 per seeker per rolling week; past the job's cap, the application is waitlisted
create or replace function public.enforce_application_limits()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_cap smallint;
  v_count int;
begin
  if (select count(*) from applications
      where seeker_id = new.seeker_id and created_at > now() - interval '7 days') >= 10 then
    raise exception 'WEEKLY_LIMIT: max 10 applications per week';
  end if;

  select applicant_cap into v_cap from jobs where id = new.job_id;
  select count(*) into v_count from applications
    where job_id = new.job_id and status <> 'waitlisted';
  new.status := case when v_count >= v_cap
                     then 'waitlisted'::application_status
                     else 'applied'::application_status end;
  return new;
end;
$$;
create trigger applications_limits before insert on public.applications
  for each row execute function public.enforce_application_limits();

-- ---------- reports ----------
create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target_type report_target not null,
  target_id uuid not null,
  reason report_reason not null,
  details text,
  status report_status not null default 'open',
  created_at timestamptz not null default now(),
  unique (reporter_id, target_type, target_id)
);

-- ---------- Policy helpers ----------
-- Security definer so policies on one table can check another without
-- triggering that table's own policies (avoids RLS recursion).
create or replace function public.owns_job(p_job uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from jobs where id = p_job and referrer_id = auth.uid());
$$;

create or replace function public.can_apply_to(p_job uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from jobs
                 where id = p_job and status = 'active' and referrer_id <> auth.uid())
     and exists (select 1 from profiles
                 where id = auth.uid() and resume_path is not null);
$$;

-- True when p_seeker has applied to any job owned by the current user
create or replace function public.applied_to_my_job(p_seeker text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from applications a join jobs j on j.id = a.job_id
    where a.seeker_id::text = p_seeker and j.referrer_id = auth.uid()
  );
$$;

-- ---------- Row-level security ----------
alter table public.profiles enable row level security;
alter table public.companies enable row level security;
alter table public.referrer_verifications enable row level security;
alter table public.jobs enable row level security;
alter table public.applications enable row level security;
alter table public.reports enable row level security;

-- profiles: own row; a referrer can read profiles of people who applied to their jobs
create policy profiles_select_own on public.profiles for select to authenticated
  using (id = auth.uid());
create policy profiles_select_applicants on public.profiles for select to authenticated
  using (public.applied_to_my_job(id::text));
create policy profiles_update_own on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- companies: anyone signed in sees approved companies; writes via service role (admin)
create policy companies_select_approved on public.companies for select to authenticated
  using (status = 'approved');

-- referrer_verifications: read own; writes only via the verification Edge Function (service role)
create policy verifications_select_own on public.referrer_verifications for select to authenticated
  using (user_id = auth.uid());

-- jobs: active posts visible to all signed-in users; owners see and edit their own
create policy jobs_select on public.jobs for select to authenticated
  using (status = 'active' or referrer_id = auth.uid());
create policy jobs_insert on public.jobs for insert to authenticated
  with check (referrer_id = auth.uid() and public.is_verified_referrer(company_id));
create policy jobs_update_own on public.jobs for update to authenticated
  using (referrer_id = auth.uid())
  -- closing or hiding is always allowed; keeping a post active needs a live verification
  with check (referrer_id = auth.uid()
              and (status <> 'active' or public.is_verified_referrer(company_id)));

-- applications: seeker sees own; job owner sees and updates applications to their jobs
create policy applications_select on public.applications for select to authenticated
  using (seeker_id = auth.uid() or public.owns_job(job_id));
create policy applications_insert on public.applications for insert to authenticated
  with check (seeker_id = auth.uid() and public.can_apply_to(job_id));
create policy applications_update_owner on public.applications for update to authenticated
  using (public.owns_job(job_id))
  with check (public.owns_job(job_id));

-- reports: file and read your own
create policy reports_insert_own on public.reports for insert to authenticated
  with check (reporter_id = auth.uid());
create policy reports_select_own on public.reports for select to authenticated
  using (reporter_id = auth.uid());

-- Seekers must not change status/score on their own applications; owners only touch status
revoke update on public.applications from authenticated;
grant update (status) on public.applications to authenticated;

-- Users edit only their own descriptive fields; priority_until is system-managed
revoke update on public.profiles from authenticated;
grant update (full_name, city, skills, experience_years, resume_path) on public.profiles to authenticated;

-- ---------- Storage: private resumes bucket ----------
-- Path convention: <user_id>/resume.pdf
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('resumes', 'resumes', false, 5242880, array['application/pdf'])
on conflict (id) do nothing;

create policy resumes_owner_all on storage.objects for all to authenticated
  using (bucket_id = 'resumes' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'resumes' and (storage.foldername(name))[1] = auth.uid()::text);

create policy resumes_referrer_read on storage.objects for select to authenticated
  using (
    bucket_id = 'resumes'
    and public.applied_to_my_job((storage.foldername(name))[1])
  );
