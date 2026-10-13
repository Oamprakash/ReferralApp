-- Job description: optional free text on a post (what the team does, what the role involves).
-- Shown on the job detail screen. Plain text, up to 4000 characters; blank becomes null.
alter table public.jobs add column if not exists description text
  check (description is null or length(description) <= 4000);

create or replace function public.tidy_job_description()
returns trigger language plpgsql as $$
begin
  -- trim, collapse runs of 3+ blank lines, and store blank as null
  new.description := nullif(regexp_replace(btrim(coalesce(new.description, '')), '\n{3,}', E'\n\n', 'g'), '');
  return new;
end;
$$;

drop trigger if exists jobs_tidy_description on public.jobs;
create trigger jobs_tidy_description before insert or update of description on public.jobs
  for each row execute function public.tidy_job_description();

-- get_job_detail: same as before (20261012000000_reapply.sql) plus 'description'.
create or replace function public.get_job_detail(p_job uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_taken int;
  v_waiting int;
  v_mine applications%rowtype;
  v_profile profiles%rowtype;
  v_stats jsonb;
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
  v_stats := public.referrer_stats(v_job.referrer_id);

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
    'skills', to_jsonb(v_job.skills),
    'description', v_job.description,
    'status', v_job.status,
    'last_confirmed_at', v_job.last_confirmed_at,
    'company_name', (select name from companies where id = v_job.company_id),
    'poster_first_name', (select split_part(coalesce(full_name, ''), ' ', 1)
                          from profiles where id = v_job.referrer_id),
    'poster_referred', (v_stats->>'referred')::int,
    'poster_interviews', (v_stats->>'interviews')::int,
    'is_mine', v_job.referrer_id = auth.uid(),
    'applicant_cap', v_job.applicant_cap,
    'spots_left', greatest(v_job.applicant_cap - v_taken, 0),
    'waitlist_count', v_waiting,
    'my_application_id', v_mine.id,
    'my_application_status', v_mine.status,
    'has_reapplied', v_mine.reapplied_at is not null,
    'can_reapply', coalesce(v_mine.status::text = 'withdrawn' and v_mine.reapplied_at is null
                            and v_job.status = 'active', false),
    'profile_complete', (v_profile.full_name is not null and v_profile.experience_years is not null
                         and v_profile.resume_path is not null),
    'applications_this_week', public.applications_this_week(auth.uid())
  );
end;
$$;
