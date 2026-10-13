-- Referral Jobs App: Sprint 3 — apply flow
--   * withdrawn status; withdrawing frees a spot and promotes the oldest waitlisted applicant
--   * screening answers validated on apply; concurrent applies can't overshoot the cap
--   * seekers keep seeing jobs they applied to after those close
--   * get_job_detail(): one call for the detail screen (spots left, poster first name, my status)
--
-- Functions here are plpgsql so the new enum value is only used at run time
-- (Postgres forbids using an enum value in the same transaction that adds it).

alter type application_status add value if not exists 'withdrawn';

-- ---------- Apply: limits, cap, answers ----------
create or replace function public.enforce_application_limits()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_count int;
  v_answers text[];
begin
  -- Lock the job row so two applies at once can't both take the last spot
  select * into v_job from jobs where id = new.job_id for update;

  if (select count(*) from applications
      where seeker_id = new.seeker_id and created_at > now() - interval '7 days') >= 10 then
    raise exception 'WEEKLY_LIMIT: max 10 applications per week';
  end if;

  -- One trimmed, non-empty answer (max 1000 chars) per screening question
  v_answers := coalesce((select array_agg(trim(a) order by i)
                         from unnest(new.answers) with ordinality as t(a, i)), '{}');
  if cardinality(v_answers) <> cardinality(v_job.questions)
     or exists (select 1 from unnest(v_answers) a where a = '' or length(a) > 1000) then
    raise exception 'ANSWERS_INVALID: answer each of the % question(s), up to 1000 characters each',
      cardinality(v_job.questions);
  end if;
  new.answers := v_answers;

  select count(*) into v_count from applications
    where job_id = new.job_id and status::text not in ('waitlisted', 'withdrawn');
  new.status := case when v_count >= v_job.applicant_cap
                     then 'waitlisted'::application_status
                     else 'applied'::application_status end;
  new.match_score := null;   -- computed server-side in Sprint 4, never trusted from the client
  return new;
end;
$$;

-- ---------- Withdraw ----------
-- Seeker withdraws their own applied/waitlisted application. If it held a spot,
-- the oldest waitlisted applicant on that job moves up. Returns json {status}:
--   withdrawn | not_found | not_allowed (already reviewed)
create or replace function public.withdraw_application(p_application uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_app applications%rowtype;
  v_promoted uuid;
begin
  select * into v_app from applications
    where id = p_application and seeker_id = auth.uid()
    for update;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;
  if v_app.status::text not in ('applied', 'waitlisted') then
    return jsonb_build_object('status', 'not_allowed', 'current', v_app.status);
  end if;

  perform 1 from jobs where id = v_app.job_id for update;   -- same lock as apply
  update applications set status = 'withdrawn' where id = v_app.id;

  if v_app.status::text = 'applied' then
    update applications set status = 'applied'
      where id = (select id from applications
                  where job_id = v_app.job_id and status::text = 'waitlisted'
                  order by created_at limit 1)
      returning id into v_promoted;
  end if;

  return jsonb_build_object('status', 'withdrawn', 'promoted', v_promoted is not null);
end;
$$;
revoke all on function public.withdraw_application(uuid) from public, anon;
grant execute on function public.withdraw_application(uuid) to authenticated;

-- ---------- Seekers keep seeing jobs they applied to ----------
create or replace function public.has_applied(p_job uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from applications where job_id = p_job and seeker_id = auth.uid());
$$;

drop policy if exists jobs_select on public.jobs;
create policy jobs_select on public.jobs for select to authenticated
  using (status = 'active' or referrer_id = auth.uid() or public.has_applied(id));

-- Company names stay readable for jobs a seeker applied to, even if the company is later suspended
drop policy if exists companies_select_approved on public.companies;
create policy companies_select on public.companies for select to authenticated
  using (status = 'approved'
         or exists (select 1 from public.jobs j where j.company_id = companies.id
                    and public.has_applied(j.id)));

-- ---------- Job detail ----------
-- Everything the detail screen needs in one call. Returns null if the caller
-- can't see the job (not active, not theirs, never applied).
create or replace function public.get_job_detail(p_job uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_taken int;
  v_waiting int;
  v_mine applications%rowtype;
  v_profile profiles%rowtype;
begin
  select * into v_job from jobs where id = p_job;
  if not found
     or not (v_job.status = 'active' or v_job.referrer_id = auth.uid() or public.has_applied(p_job)) then
    return null;
  end if;

  select count(*) filter (where status::text not in ('waitlisted', 'withdrawn')),
         count(*) filter (where status::text = 'waitlisted')
    into v_taken, v_waiting
    from applications where job_id = p_job;

  select * into v_mine from applications where job_id = p_job and seeker_id = auth.uid();
  select * into v_profile from profiles where id = auth.uid();

  return jsonb_build_object(
    'id', v_job.id,
    'title', v_job.title,
    'team', v_job.team,
    'location', v_job.location,
    'exp_min', v_job.exp_min,
    'exp_max', v_job.exp_max,
    'work_mode', v_job.work_mode,
    'salary_range', v_job.salary_range,
    'official_url', v_job.official_url,
    'job_ref', v_job.job_ref,
    'questions', to_jsonb(v_job.questions),
    'status', v_job.status,
    'last_confirmed_at', v_job.last_confirmed_at,
    'company_name', (select name from companies where id = v_job.company_id),
    -- First name only: enough to trust, not enough to contact outside the app
    'poster_first_name', (select split_part(coalesce(full_name, ''), ' ', 1)
                          from profiles where id = v_job.referrer_id),
    'is_mine', v_job.referrer_id = auth.uid(),
    'applicant_cap', v_job.applicant_cap,
    'spots_left', greatest(v_job.applicant_cap - v_taken, 0),
    'waitlist_count', v_waiting,
    'my_application_id', v_mine.id,
    'my_application_status', v_mine.status,
    'profile_complete', (v_profile.full_name is not null and v_profile.experience_years is not null
                         and v_profile.resume_path is not null),
    'applications_this_week', (select count(*) from applications
                               where seeker_id = auth.uid() and created_at > now() - interval '7 days')
  );
end;
$$;
revoke all on function public.get_job_detail(uuid) from public, anon;
grant execute on function public.get_job_detail(uuid) to authenticated;
