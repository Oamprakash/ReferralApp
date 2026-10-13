-- Referral Jobs App: reapply after withdrawing (once per job)
--   * applications.reapplied_at marks the one allowed reapply
--   * a reapply counts toward the weekly 10 and goes to the back of the waitlist
--   * the referrer is told about a new applicant; the seeker gets no "spot opened" message
--   * get_job_detail() reports can_reapply

alter table public.applications add column if not exists reapplied_at timestamptz;

-- Applications this week, counting first applies and reapplies
create or replace function public.applications_this_week(p_user uuid)
returns int language sql stable security definer set search_path = public as $$
  select (count(*) filter (where created_at > now() - interval '7 days')
        + count(*) filter (where reapplied_at > now() - interval '7 days'))::int
  from applications where seeker_id = p_user;
$$;
revoke all on function public.applications_this_week(uuid) from public, anon, authenticated;

-- Position in line for the waitlist: a reapply joins at the back
create or replace function public.queue_time(a public.applications)
returns timestamptz language sql immutable as $$
  select coalesce(a.reapplied_at, a.created_at);
$$;

-- ---------- Apply trigger: weekly count now includes reapplies ----------
create or replace function public.enforce_application_limits()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_seeker profiles%rowtype;
  v_count int;
  v_answers text[];
begin
  select * into v_job from jobs where id = new.job_id for update;

  if public.applications_this_week(new.seeker_id) >= 10 then
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
  new.reapplied_at := null;
  return new;
end;
$$;

-- ---------- Withdraw: promote by queue position ----------
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

  perform 1 from jobs where id = v_app.job_id for update;
  update applications set status = 'withdrawn' where id = v_app.id;

  if v_app.status::text = 'applied' then
    update applications set status = 'applied'
      where id = (select a.id from applications a
                  where a.job_id = v_app.job_id and a.status::text = 'waitlisted'
                  order by public.queue_time(a) limit 1)
      returning id into v_promoted;
  end if;

  return jsonb_build_object('status', 'withdrawn', 'promoted', v_promoted is not null,
                            'can_reapply', v_app.reapplied_at is null);
end;
$$;

-- ---------- Reapply ----------
-- Returns json {status}:
--   applied | waitlisted   -> back in
--   not_found              -> not your application
--   not_withdrawn          -> only withdrawn applications can be reapplied
--   already_reapplied      -> the one reapply was used
--   job_closed             -> the job isn't active any more
--   profile_incomplete     -> no resume on file
-- Raises WEEKLY_LIMIT / ANSWERS_INVALID like a first apply.
create or replace function public.reapply_application(p_application uuid, p_answers text[])
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_app applications%rowtype;
  v_job jobs%rowtype;
  v_seeker profiles%rowtype;
  v_answers text[];
  v_taken int;
  v_status application_status;
begin
  select * into v_app from applications where id = p_application and seeker_id = auth.uid() for update;
  if not found then return jsonb_build_object('status', 'not_found'); end if;
  if v_app.status::text <> 'withdrawn' then return jsonb_build_object('status', 'not_withdrawn'); end if;
  if v_app.reapplied_at is not null then return jsonb_build_object('status', 'already_reapplied'); end if;

  select * into v_job from jobs where id = v_app.job_id for update;
  if v_job.status <> 'active' then return jsonb_build_object('status', 'job_closed'); end if;

  select * into v_seeker from profiles where id = auth.uid();
  if v_seeker.resume_path is null then return jsonb_build_object('status', 'profile_incomplete'); end if;

  if public.applications_this_week(auth.uid()) >= 10 then
    raise exception 'WEEKLY_LIMIT: max 10 applications per week';
  end if;

  v_answers := coalesce((select array_agg(trim(a) order by i)
                         from unnest(p_answers) with ordinality as t(a, i)), '{}');
  if cardinality(v_answers) <> cardinality(v_job.questions)
     or exists (select 1 from unnest(v_answers) a where a = '' or length(a) > 1000) then
    raise exception 'ANSWERS_INVALID: answer each of the % question(s), up to 1000 characters each',
      cardinality(v_job.questions);
  end if;

  select count(*) into v_taken from applications
    where job_id = v_job.id and status::text not in ('waitlisted', 'withdrawn');
  v_status := case when v_taken >= v_job.applicant_cap
                   then 'waitlisted'::application_status else 'applied'::application_status end;

  update applications set
    status = v_status,
    answers = v_answers,
    match_score = public.match_score(v_job.skills, v_job.exp_min, v_job.exp_max,
                                     v_seeker.skills, v_seeker.experience_years),
    reapplied_at = now()
  where id = v_app.id;

  return jsonb_build_object('status', v_status);
end;
$$;
revoke all on function public.reapply_application(uuid, text[]) from public, anon;
grant execute on function public.reapply_application(uuid, text[]) to authenticated;

-- ---------- Notifications: a reapply is a new applicant, not a promotion ----------
create or replace function public.notify_application_change()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
  v_poster text;
  v_title text;
  v_body text;
begin
  select * into v_job from jobs where id = new.job_id;

  if tg_op = 'INSERT' or (old.status::text = 'withdrawn' and new.status::text <> 'withdrawn') then
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

-- ---------- Job detail: can_reapply + reapply-aware weekly count ----------
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
    'has_reapplied', v_mine.reapplied_at is not null,
    'can_reapply', coalesce(v_mine.status::text = 'withdrawn' and v_mine.reapplied_at is null
                            and v_job.status = 'active', false),
    'profile_complete', (v_profile.full_name is not null and v_profile.experience_years is not null
                         and v_profile.resume_path is not null),
    'applications_this_week', public.applications_this_week(auth.uid())
  );
end;
$$;
