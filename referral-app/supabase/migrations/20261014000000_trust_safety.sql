-- Referral Jobs App: Sprint 5 — trust, safety and housekeeping
--   * run_daily_maintenance(): confirm reminders (day 12), auto-hide (day 14), verification
--     reminders (7 days before) and expiry, cleanup. Scheduled with pg_cron (see README).
--   * jobs.hidden_reason: why a post is hidden; moderation holds can't be undone by the referrer
--   * reports: validation + rate limit; 2 reporters (or 1 "asked for money") hide a post for review
--   * admins + bans, and admin_* functions for the in-app admin section
--   * export_my_data() and prepare_account_deletion() for the DPDP requirements

-- ---------- New columns ----------
alter table public.jobs add column if not exists reminded_at timestamptz;
alter table public.jobs add column if not exists hidden_reason text
  check (hidden_reason in ('stale', 'verification', 'reports', 'admin'));
alter table public.referrer_verifications add column if not exists reminded_at timestamptz;
alter table public.profiles add column if not exists banned_at timestamptz;
alter table public.profiles add column if not exists ban_reason text;
-- (users can't edit these: profile updates are limited to their own descriptive columns since Sprint 1)

-- ---------- Admins and bans ----------
create table public.admins (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.admins enable row level security;   -- managed in the SQL Editor only
revoke all on public.admins from anon, authenticated;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from admins where user_id = auth.uid());
$$;

create or replace function public.is_banned(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select banned_at is not null from profiles where id = p_user), false);
$$;

create or replace function public.am_i_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_admin();
$$;
revoke all on function public.am_i_admin() from public, anon;
grant execute on function public.am_i_admin() to authenticated;

-- Banned accounts can't post, apply or reapply.
-- Two functions: a trigger function can only reference columns of its own table's row.
create or replace function public.block_banned_job()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if public.is_banned(new.referrer_id) then
    raise exception 'BANNED: this account is suspended';
  end if;
  return new;
end;
$$;

create or replace function public.block_banned_application()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' or (old.status::text = 'withdrawn' and new.status::text <> 'withdrawn') then
    if public.is_banned(new.seeker_id) then
      raise exception 'BANNED: this account is suspended';
    end if;
  end if;
  return new;
end;
$$;

create trigger jobs_block_banned before insert on public.jobs
  for each row execute function public.block_banned_job();
create trigger applications_block_banned before insert or update of status on public.applications
  for each row execute function public.block_banned_application();

-- ---------- Moderation guard on jobs ----------
-- Referrers ("authenticated") can't reactivate a post held for reports or removed by an admin,
-- can't reactivate anything while banned, and never set hidden_reason themselves.
create or replace function public.guard_job_moderation()
returns trigger language plpgsql as $$
begin
  if current_user <> 'authenticated' then
    return new;                                   -- system paths set hidden_reason freely
  end if;
  if new.status = 'active' and old.status <> 'active' then
    if old.hidden_reason in ('reports', 'admin') then
      raise exception 'UNDER_REVIEW: this post is held for review';
    end if;
    if public.is_banned(new.referrer_id) then
      raise exception 'BANNED: this account is suspended';
    end if;
  end if;
  new.hidden_reason := case when new.status = 'active' then null else old.hidden_reason end;
  return new;
end;
$$;
create trigger jobs_moderation_guard before update on public.jobs
  for each row execute function public.guard_job_moderation();

-- ---------- Daily maintenance ----------
create or replace function public.run_daily_maintenance()
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_reminded int; v_hidden int; v_verif_reminded int; v_verif_hidden int; v_otps int; v_notifs int;
begin
  -- 1. Day 12-13: ask the referrer to confirm (once per confirmation cycle)
  with due as (
    update jobs set reminded_at = now()
    where status = 'active'
      and last_confirmed_at <= now() - interval '12 days'
      and last_confirmed_at >  now() - interval '14 days'
      and (reminded_at is null or reminded_at < last_confirmed_at)
    returning id, referrer_id, title
  )
  insert into notifications (user_id, kind, title, body, data)
  select referrer_id, 'confirm_reminder', 'Still hiring?',
         'Confirm "' || title || '" is still open, or it hides from the feed in 2 days.',
         jsonb_build_object('job_id', id)
  from due;
  get diagnostics v_reminded = row_count;

  -- 2. Day 14: hide unconfirmed posts
  with gone as (
    update jobs set status = 'hidden', hidden_reason = 'stale'
    where status = 'active' and last_confirmed_at <= now() - interval '14 days'
    returning id, referrer_id, title
  )
  insert into notifications (user_id, kind, title, body, data)
  select referrer_id, 'post_hidden', 'Post hidden',
         '"' || title || '" was hidden because it wasn''t confirmed for 14 days. Tap Still hiring in My posts to bring it back.',
         jsonb_build_object('job_id', id)
  from gone;
  get diagnostics v_hidden = row_count;

  -- 3. Verification ends within 7 days: remind once per verification
  with due as (
    update referrer_verifications set reminded_at = now()
    where expires_at > now() and expires_at <= now() + interval '7 days'
      and (reminded_at is null or reminded_at < verified_at)
    returning user_id, expires_at
  )
  insert into notifications (user_id, kind, title, body, data)
  select user_id, 'verification_expiring', 'Verify your work email again',
         'Your verification ends on ' || to_char(expires_at at time zone 'Asia/Kolkata', 'DD Mon') ||
         '. Verify again to keep your posts live.', '{}'::jsonb
  from due;
  get diagnostics v_verif_reminded = row_count;

  -- 4. Verification expired: hide that referrer's active posts at that company
  with gone as (
    update jobs j set status = 'hidden', hidden_reason = 'verification'
    where j.status = 'active'
      and not exists (select 1 from referrer_verifications v
                      where v.user_id = j.referrer_id and v.company_id = j.company_id and v.expires_at > now())
    returning j.id, j.referrer_id, j.title
  )
  insert into notifications (user_id, kind, title, body, data)
  select referrer_id, 'post_hidden', 'Post hidden',
         '"' || title || '" was hidden because your work-email verification expired. Verify again, then tap Still hiring.',
         jsonb_build_object('job_id', id)
  from gone;
  get diagnostics v_verif_hidden = row_count;

  -- 5. Cleanup
  delete from work_email_otps where created_at < now() - interval '1 day';
  get diagnostics v_otps = row_count;
  delete from notifications where created_at < now() - interval '180 days';
  get diagnostics v_notifs = row_count;

  return jsonb_build_object('confirm_reminders', v_reminded, 'posts_hidden_stale', v_hidden,
    'verification_reminders', v_verif_reminded, 'posts_hidden_verification', v_verif_hidden,
    'otps_deleted', v_otps, 'notifications_deleted', v_notifs);
end;
$$;
revoke all on function public.run_daily_maintenance() from public, anon, authenticated;

-- ---------- Reports ----------
create index if not exists reports_target_idx on public.reports (target_type, target_id, status);

create or replace function public.validate_report()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.reporter_id <> auth.uid() then
    raise exception 'REPORT_INVALID: reporter must be you';
  end if;
  if (select count(*) from reports where reporter_id = new.reporter_id and created_at > now() - interval '1 day') >= 10 then
    raise exception 'REPORT_LIMIT: max 10 reports a day';
  end if;
  if new.target_type = 'job' then
    if not exists (select 1 from jobs where id = new.target_id) then raise exception 'REPORT_INVALID: no such post'; end if;
    if exists (select 1 from jobs where id = new.target_id and referrer_id = new.reporter_id) then
      raise exception 'REPORT_INVALID: you can''t report your own post';
    end if;
  else
    if not exists (select 1 from profiles where id = new.target_id) then raise exception 'REPORT_INVALID: no such user'; end if;
    if new.target_id = new.reporter_id then raise exception 'REPORT_INVALID: you can''t report yourself'; end if;
  end if;
  new.details := left(nullif(trim(new.details), ''), 500);
  new.status := 'open';
  return new;
end;
$$;
create trigger reports_validate before insert on public.reports
  for each row execute function public.validate_report();

create or replace function public.after_report()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_reporters int;
  v_job jobs%rowtype;
begin
  -- Tell every admin (they get a push like everyone else)
  insert into notifications (user_id, kind, title, body, data)
  select a.user_id, 'admin_report', 'New report',
         'A ' || new.target_type::text || ' was reported: ' || replace(new.reason::text, '_', ' ') || '.',
         jsonb_build_object('target_type', new.target_type, 'target_id', new.target_id)
  from admins a;

  if new.target_type = 'job' then
    select count(distinct reporter_id) into v_reporters from reports
      where target_type = 'job' and target_id = new.target_id and status = 'open';
    if v_reporters >= 2 or new.reason = 'payment_request' then
      update jobs set status = 'hidden', hidden_reason = 'reports'
        where id = new.target_id and status = 'active'
        returning * into v_job;
      if found then
        insert into notifications (user_id, kind, title, body, data) values (
          v_job.referrer_id, 'post_under_review', 'Post under review',
          '"' || v_job.title || '" was reported and is hidden while we review it.',
          jsonb_build_object('job_id', v_job.id));
      end if;
    end if;
  end if;
  return new;
end;
$$;
create trigger reports_after_insert after insert on public.reports
  for each row execute function public.after_report();

-- ---------- Admin functions (each checks is_admin) ----------
create or replace function public.require_admin()
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'NOT_ADMIN'; end if;
end;
$$;

-- Open reports grouped by what was reported
create or replace function public.admin_open_reports()
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_admin();
  return coalesce((
    select jsonb_agg(x order by x->>'last_at' desc) from (
      select jsonb_build_object(
        'target_type', r.target_type,
        'target_id', r.target_id,
        'label', case when r.target_type = 'job'
                   then (select j.title || ' · ' || c.name from jobs j join companies c on c.id = j.company_id where j.id = r.target_id)
                   else (select coalesce(full_name, 'User') from profiles where id = r.target_id) end,
        'target_status', case when r.target_type = 'job'
                   then (select status::text || coalesce(' (' || hidden_reason || ')', '') from jobs where id = r.target_id)
                   else (select case when banned_at is null then 'active' else 'banned' end from profiles where id = r.target_id) end,
        'report_count', count(*),
        'reasons', jsonb_agg(distinct r.reason),
        'details', jsonb_agg(r.details) filter (where r.details is not null),
        'last_at', max(r.created_at)
      ) as x
      from reports r where r.status = 'open'
      group by r.target_type, r.target_id
    ) s), '[]'::jsonb);
end;
$$;

-- dismiss: reports closed; a post held for reports goes back live (or stays hidden as stale)
-- uphold:  post closed for good / user banned
create or replace function public.admin_resolve_reports(p_target_type report_target, p_target_id uuid, p_action text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_job jobs%rowtype;
begin
  perform public.require_admin();
  if p_action not in ('dismiss', 'uphold') then raise exception 'BAD_ACTION'; end if;

  update reports set status = case when p_action = 'dismiss' then 'dismissed'::report_status else 'actioned'::report_status end
    where target_type = p_target_type and target_id = p_target_id and status = 'open';

  if p_target_type = 'job' then
    select * into v_job from jobs where id = p_target_id;
    if not found then
      return jsonb_build_object('status', 'ok', 'note', 'post no longer exists');
    end if;
    if p_action = 'uphold' then
      update jobs set status = 'closed', hidden_reason = 'admin' where id = p_target_id;
      insert into notifications (user_id, kind, title, body, data) values (v_job.referrer_id, 'post_removed',
        'Post removed', '"' || v_job.title || '" was removed after review.', jsonb_build_object('job_id', v_job.id));
    elsif v_job.hidden_reason = 'reports' then
      begin
        update jobs set status = case when last_confirmed_at > now() - interval '14 days'
                                      then 'active'::job_status else 'hidden'::job_status end,
                        hidden_reason = case when last_confirmed_at > now() - interval '14 days' then null else 'stale' end
          where id = p_target_id;
      exception when raise_exception then
        -- only the 5-active-posts limit lands here: leave it hidden; the referrer can tap Still hiring later
        update jobs set hidden_reason = 'stale' where id = p_target_id;
      end;
    end if;
  elsif p_action = 'uphold' then
    perform public.admin_ban_user(p_target_id, 'Reported and reviewed');
  end if;
  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.admin_ban_user(p_user uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_jobs int; v_apps int;
begin
  perform public.require_admin();
  if p_user = auth.uid() then raise exception 'BAD_ACTION: you can''t ban yourself'; end if;
  update profiles set banned_at = now(), ban_reason = left(p_reason, 200) where id = p_user;
  update jobs set status = 'closed', hidden_reason = 'admin' where referrer_id = p_user and status <> 'closed';
  get diagnostics v_jobs = row_count;
  update applications set status = 'withdrawn' where seeker_id = p_user and status::text in ('applied', 'waitlisted');
  get diagnostics v_apps = row_count;
  delete from device_tokens where user_id = p_user;
  return jsonb_build_object('status', 'banned', 'jobs_closed', v_jobs, 'applications_withdrawn', v_apps);
end;
$$;

create or replace function public.admin_unban_user(p_user uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform public.require_admin();
  update profiles set banned_at = null, ban_reason = null where id = p_user;
  return jsonb_build_object('status', 'unbanned');
end;
$$;

create or replace function public.admin_domain_requests()
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  perform public.require_admin();
  return coalesce((select jsonb_agg(jsonb_build_object(
      'domain', domain, 'request_count', request_count, 'last_requested_at', last_requested_at)
      order by request_count desc, last_requested_at desc)
    from domain_requests where status = 'pending'), '[]'::jsonb);
end;
$$;

-- Adds the domain to an existing company with that name, or creates the company
create or replace function public.admin_approve_domain(p_domain text, p_company_name text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_company uuid;
begin
  perform public.require_admin();
  if coalesce(trim(p_company_name), '') = '' then raise exception 'BAD_ACTION: company name required'; end if;
  select id into v_company from companies where lower(name) = lower(trim(p_company_name));
  if v_company is null then
    insert into companies (name, domains, status) values (trim(p_company_name), array[lower(p_domain)], 'approved')
      returning id into v_company;
  else
    update companies set domains = array_append(domains, lower(p_domain)), status = 'approved' where id = v_company;
  end if;
  update domain_requests set status = 'approved' where domain = lower(p_domain);
  return jsonb_build_object('status', 'approved', 'company_id', v_company);
end;
$$;

create or replace function public.admin_reject_domain(p_domain text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform public.require_admin();
  update domain_requests set status = 'rejected' where domain = lower(p_domain);
  return jsonb_build_object('status', 'rejected');
end;
$$;

do $$
declare f text;
begin
  foreach f in array array[
    'admin_open_reports()', 'admin_resolve_reports(report_target, uuid, text)', 'admin_ban_user(uuid, text)',
    'admin_unban_user(uuid)', 'admin_domain_requests()', 'admin_approve_domain(text, text)', 'admin_reject_domain(text)']
  loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
revoke all on function public.require_admin() from public, anon, authenticated;

-- ---------- Data export (DPDP) ----------
create or replace function public.export_my_data()
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'phone', u.phone, 'email', u.email, 'created_at', u.created_at)
                from auth.users u where u.id = auth.uid()),
    'profile', (select to_jsonb(p) - 'priority_until' from profiles p where p.id = auth.uid()),
    'work_email_verifications', coalesce((select jsonb_agg(jsonb_build_object(
        'company', c.name, 'work_email', v.work_email, 'verified_at', v.verified_at, 'expires_at', v.expires_at))
      from referrer_verifications v join companies c on c.id = v.company_id where v.user_id = auth.uid()), '[]'),
    'jobs_posted', coalesce((select jsonb_agg(to_jsonb(j) - 'referrer_id') from jobs j where j.referrer_id = auth.uid()), '[]'),
    'applications', coalesce((select jsonb_agg(jsonb_build_object(
        'job', j.title, 'company', c.name, 'status', a.status, 'answers', a.answers,
        'applied_at', a.created_at, 'reapplied_at', a.reapplied_at))
      from applications a join jobs j on j.id = a.job_id join companies c on c.id = j.company_id
      where a.seeker_id = auth.uid()), '[]'),
    'reports_filed', coalesce((select jsonb_agg(jsonb_build_object(
        'target_type', target_type, 'reason', reason, 'details', details, 'status', status, 'created_at', created_at))
      from reports where reporter_id = auth.uid()), '[]'),
    'notifications', coalesce((select jsonb_agg(jsonb_build_object('title', title, 'body', body, 'created_at', created_at)
      order by created_at desc) from notifications where user_id = auth.uid()), '[]')
  );
$$;
revoke all on function public.export_my_data() from public, anon;
grant execute on function public.export_my_data() to authenticated;

-- ---------- Account deletion (called by the delete-account Edge Function, service role only) ----------
-- Tells applicants to this user's open posts that the post closed, and returns the resume path
-- so the function can remove the file. Deleting the auth user afterwards cascades everything else.
create or replace function public.prepare_account_deletion(p_user uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_resume text;
begin
  insert into notifications (user_id, kind, title, body, data)
  select a.seeker_id, 'post_closed', 'Post closed',
         'The employee who posted "' || j.title || '" left the app, so this application was closed.',
         jsonb_build_object('job_id', j.id)
  from applications a join jobs j on j.id = a.job_id
  where j.referrer_id = p_user and a.status::text not in ('withdrawn', 'declined', 'not_selected', 'offer');

  select resume_path into v_resume from profiles where id = p_user;
  return jsonb_build_object('resume_path', v_resume);
end;
$$;
revoke all on function public.prepare_account_deletion(uuid) from public, anon, authenticated;
grant execute on function public.prepare_account_deletion(uuid) to service_role;
