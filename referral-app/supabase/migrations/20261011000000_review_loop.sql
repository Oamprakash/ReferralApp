-- Referral Jobs App: Sprint 4 — referrer review loop
--   * jobs.skills + a match score computed on apply (never trusted from the client)
--   * fixed order of status changes for referrers
--   * a notification row for every status change and new applicant; pushed by the
--     send-push Edge Function through a Database Webhook on notifications INSERT
--   * device tokens registered through two small functions
--   * per-post counts and referrer stats (also shown to seekers on job detail)
--   * a withdrawn applicant's profile and resume are no longer visible to the referrer

-- ---------- Skills on jobs ----------
alter table public.jobs add column if not exists skills text[] not null default '{}'
  check (cardinality(skills) <= 10);

-- ---------- Match score (0-100) ----------
-- 50 for experience: inside the range 50, within 1 year 30, within 2 years 10.
-- 50 for skills: share of the job's skills the seeker lists (case-insensitive, exact words).
-- Jobs with no skills listed give everyone a neutral 25 for that half.
create or replace function public.match_score(
  p_job_skills text[], p_exp_min int, p_exp_max int, p_seeker_skills text[], p_years int)
returns smallint language sql immutable as $$
  with js as (select distinct lower(trim(s)) as s from unnest(coalesce(p_job_skills, '{}')) s where trim(s) <> ''),
       ss as (select distinct lower(trim(s)) as s from unnest(coalesce(p_seeker_skills, '{}')) s where trim(s) <> '')
  select (
    case
      when p_years is null then 0
      when p_years between p_exp_min and p_exp_max then 50
      when p_years between p_exp_min - 1 and p_exp_max + 1 then 30
      when p_years between p_exp_min - 2 and p_exp_max + 2 then 10
      else 0
    end
    + case
        when (select count(*) from js) = 0 then 25
        else round(50.0 * (select count(*) from js where s in (select s from ss)) / (select count(*) from js))
      end
  )::smallint;
$$;

-- ---------- Apply trigger: Sprint 3 rules + score ----------
create or replace function public.enforce_application_limits()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_seeker profiles%rowtype;
  v_count int;
  v_answers text[];
begin
  select * into v_job from jobs where id = new.job_id for update;

  if (select count(*) from applications
      where seeker_id = new.seeker_id and created_at > now() - interval '7 days') >= 10 then
    raise exception 'WEEKLY_LIMIT: max 10 applications per week';
  end if;

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

  select * into v_seeker from profiles where id = new.seeker_id;
  new.match_score := public.match_score(v_job.skills, v_job.exp_min, v_job.exp_max,
                                        v_seeker.skills, v_seeker.experience_years);
  return new;
end;
$$;

-- ---------- Status changes: fixed order for referrers ----------
-- Referrers (role "authenticated") may only move an application one step forward:
--   applied -> shortlisted | declined
--   shortlisted -> referred | declined
--   referred -> interview | not_selected
--   interview -> offer | not_selected
-- System paths (withdraw_application, waitlist promotion, admin) run as the function
-- owner and are not limited here.
create or replace function public.guard_application_status()
returns trigger language plpgsql as $$
begin
  if new.status = old.status or current_user <> 'authenticated' then
    return new;
  end if;
  if (old.status::text, new.status::text) not in (
       ('applied', 'shortlisted'), ('applied', 'declined'),
       ('shortlisted', 'referred'), ('shortlisted', 'declined'),
       ('referred', 'interview'), ('referred', 'not_selected'),
       ('interview', 'offer'), ('interview', 'not_selected')) then
    raise exception 'STATUS_TRANSITION: % -> % is not allowed', old.status, new.status;
  end if;
  return new;
end;
$$;
create trigger applications_status_guard before update of status on public.applications
  for each row execute function public.guard_application_status();

-- ---------- Notifications ----------
create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  kind text not null,
  title text not null,
  body text not null,
  data jsonb not null default '{}',
  created_at timestamptz not null default now(),
  sent_at timestamptz,             -- set by send-push
  read_at timestamptz
);
create index notifications_user_idx on public.notifications (user_id, created_at desc);
alter table public.notifications enable row level security;
create policy notifications_select_own on public.notifications for select to authenticated
  using (user_id = auth.uid());
create policy notifications_mark_read on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
revoke update on public.notifications from authenticated;
grant update (read_at) on public.notifications to authenticated;

create or replace function public.notify_application_change()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_poster text;
  v_title text;
  v_body text;
begin
  select * into v_job from jobs where id = new.job_id;

  if tg_op = 'INSERT' then
    if new.status::text = 'applied' then
      insert into notifications (user_id, kind, title, body, data) values (
        v_job.referrer_id, 'new_applicant', 'New applicant',
        'Someone applied for ' || v_job.title || '.',
        jsonb_build_object('job_id', new.job_id, 'application_id', new.id));
    end if;
    return new;
  end if;

  if new.status = old.status or new.status::text = 'withdrawn' then
    return new;
  end if;

  select split_part(coalesce(full_name, ''), ' ', 1) into v_poster from profiles where id = v_job.referrer_id;
  v_poster := coalesce(nullif(v_poster, ''), 'The referrer');

  case new.status::text
    when 'applied' then         -- moved up from the waitlist
      v_title := 'A spot opened up'; v_body := 'You''re off the waitlist for ' || v_job.title || '.';
    when 'shortlisted' then
      v_title := 'You''re shortlisted'; v_body := v_poster || ' shortlisted you for ' || v_job.title || '.';
    when 'referred' then
      v_title := 'You''ve been referred'; v_body := v_poster || ' referred you for ' || v_job.title || '. The company will contact you.';
    when 'interview' then
      v_title := 'Interview stage'; v_body := 'You''re interviewing for ' || v_job.title || '. Good luck.';
    when 'offer' then
      v_title := 'Offer received'; v_body := 'Congratulations on the offer for ' || v_job.title || '.';
    when 'declined' then
      v_title := 'Application update'; v_body := v_poster || ' won''t be taking your application for ' || v_job.title || ' forward.';
    when 'not_selected' then
      v_title := 'Application update'; v_body := 'The company didn''t move forward for ' || v_job.title || '.';
    else
      return new;
  end case;

  insert into notifications (user_id, kind, title, body, data) values (
    new.seeker_id, 'status_' || new.status::text, v_title, v_body,
    jsonb_build_object('job_id', new.job_id, 'application_id', new.id, 'status', new.status));
  return new;
end;
$$;
create trigger applications_notify after insert or update of status on public.applications
  for each row execute function public.notify_application_change();

-- ---------- Device tokens (FCM) ----------
create table public.device_tokens (
  token text primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);
create index device_tokens_user_idx on public.device_tokens (user_id);
alter table public.device_tokens enable row level security;   -- only through the functions below

-- A token belongs to whoever registered it last (a phone can change hands between accounts)
create or replace function public.register_device_token(p_token text, p_platform text default 'android')
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or coalesce(length(p_token), 0) not between 20 and 4096 then
    raise exception 'INVALID_TOKEN';
  end if;
  insert into device_tokens (token, user_id, platform, updated_at)
    values (p_token, auth.uid(), coalesce(p_platform, 'android'), now())
  on conflict (token) do update set user_id = auth.uid(), platform = excluded.platform, updated_at = now();
end;
$$;

create or replace function public.unregister_device_token(p_token text)
returns void language sql security definer set search_path = public as $$
  delete from device_tokens where token = p_token and user_id = auth.uid();
$$;

revoke all on function public.register_device_token(text, text) from public, anon;
revoke all on function public.unregister_device_token(text) from public, anon;
grant execute on function public.register_device_token(text, text) to authenticated;
grant execute on function public.unregister_device_token(text) to authenticated;
revoke all on public.device_tokens from anon, authenticated;

-- ---------- Withdrawn applicants: profile and resume locked again ----------
create or replace function public.applied_to_my_job(p_seeker text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from applications a join jobs j on j.id = a.job_id
    where a.seeker_id::text = p_seeker and j.referrer_id = auth.uid()
      and a.status::text <> 'withdrawn'
  );
$$;

-- ---------- Stats ----------
-- Public track record of a referrer: people referred and how many reached interview.
create or replace function public.referrer_stats(p_user uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'referred',   count(*) filter (where a.status::text in ('referred', 'interview', 'offer', 'not_selected')),
    'interviews', count(*) filter (where a.status::text in ('interview', 'offer')),
    'offers',     count(*) filter (where a.status::text = 'offer'))
  from applications a join jobs j on j.id = a.job_id
  where j.referrer_id = p_user;
$$;
revoke all on function public.referrer_stats(uuid) from public, anon;
grant execute on function public.referrer_stats(uuid) to authenticated;

-- Per-post counts for the signed-in referrer's My posts screen (runs with their own RLS).
create or replace function public.my_posts_summary()
returns table (job_id uuid, applicants int, new_count int, shortlisted int, referred int, waitlisted int)
language sql stable as $$
  select j.id,
         (count(a.id) filter (where a.status::text not in ('withdrawn', 'waitlisted')))::int,
         (count(a.id) filter (where a.status::text = 'applied'))::int,
         (count(a.id) filter (where a.status::text = 'shortlisted'))::int,
         (count(a.id) filter (where a.status::text in ('referred', 'interview', 'offer', 'not_selected')))::int,
         (count(a.id) filter (where a.status::text = 'waitlisted'))::int
  from public.jobs j
  left join public.applications a on a.job_id = j.id
  where j.referrer_id = auth.uid()
  group by j.id;
$$;
revoke all on function public.my_posts_summary() from public, anon;
grant execute on function public.my_posts_summary() to authenticated;

-- ---------- Job detail: add skills and the poster's track record ----------
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
    'profile_complete', (v_profile.full_name is not null and v_profile.experience_years is not null
                         and v_profile.resume_path is not null),
    'applications_this_week', (select count(*) from applications
                               where seeker_id = auth.uid() and created_at > now() - interval '7 days')
  );
end;
$$;
