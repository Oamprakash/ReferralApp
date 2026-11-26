-- =====================================================================
-- New-match alerts: when a job goes live, people it suits get a push.
--   "New match: Backend Engineer at Zoho · 78% match. Ask Arjun for a referral."
-- Who gets one:
--   * match_score(...) >= 60 (the "strong" level and up), so it isn't every new post
--   * has skills or experience on their profile, isn't the poster, isn't suspended
--   * hasn't had 3 of these in the last 24 hours (no flood when many jobs land at once)
-- Sent when a job is inserted as active, and when a hidden post comes back to active
-- after more than 14 days (it's news again). Tapping the push opens the job (open_job).
-- =====================================================================

create or replace function public.notify_new_matches()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_company text;
  v_first text;
begin
  if new.status <> 'active' then
    return new;
  end if;
  if tg_op = 'UPDATE' then
    -- only a real comeback, not a "Still hiring" tap on a live post
    if old.status = 'active' or old.last_confirmed_at > now() - interval '14 days' then
      return new;
    end if;
  end if;

  select name into v_company from companies where id = new.company_id;
  select nullif(split_part(coalesce(full_name, ''), ' ', 1), '') into v_first from profiles where id = new.referrer_id;

  insert into notifications (user_id, kind, title, body, data)
  select p.id, 'new_match',
         'New match: ' || new.title || ' at ' || coalesce(v_company, 'a company'),
         s.score || '% match. ' ||
           case when v_first is not null then 'Ask ' || v_first || ' for a referral.' else 'Ask for a referral.' end,
         jsonb_build_object('job_id', new.id, 'open_job', 'true', 'score', s.score)
  from profiles p
  cross join lateral (
    select public.match_score(new.skills, new.exp_min, new.exp_max, p.skills, p.experience_years) as score
  ) s
  where p.id <> new.referrer_id
    and p.banned_at is null
    and (coalesce(array_length(p.skills, 1), 0) > 0 or p.experience_years is not null)
    and s.score >= 60
    and not exists (select 1 from applications a where a.job_id = new.id and a.seeker_id = p.id)
    and (select count(*) from notifications n
          where n.user_id = p.id and n.kind = 'new_match' and n.created_at > now() - interval '24 hours') < 3;

  return new;
end;
$$;

drop trigger if exists jobs_notify_new_matches on public.jobs;
create trigger jobs_notify_new_matches after insert or update of status on public.jobs
  for each row execute function public.notify_new_matches();
