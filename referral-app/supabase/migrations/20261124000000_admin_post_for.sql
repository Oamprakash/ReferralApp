-- =====================================================================
-- Admins can post a job on behalf of a verified employee ("Post for a referrer").
-- The post belongs to the employee: referrer_id is theirs, they get the applicants,
-- notifications and the My posts card. Nothing is posted under the admin's name.
--   * jobs.posted_by: who typed it in (the referrer themselves, or an admin)
--   * insert policy: an admin may insert a job for a user with a live verification at that company
--   * admin_referrers(): the picker list (unexpired verifications), admins only
--   * the employee is notified when a post is made for them
-- The 5-active-posts limit and the banned-user check still apply to the employee.
-- =====================================================================

alter table public.jobs add column if not exists posted_by uuid references public.profiles (id) on delete set null
  default auth.uid();

-- Same rule as is_verified_referrer(), for any user
create or replace function public.has_live_verification(p_user uuid, p_company uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from referrer_verifications
    where user_id = p_user and company_id = p_company and expires_at > now()
  );
$$;

drop policy if exists jobs_insert_admin_for on public.jobs;
create policy jobs_insert_admin_for on public.jobs for insert to authenticated
  with check (public.is_admin() and public.has_live_verification(referrer_id, company_id));

-- Admin picker: people who can have a job posted for them right now
create or replace function public.admin_referrers()
returns table (user_id uuid, full_name text, company_id uuid, company_name text,
               work_email text, expires_at timestamptz, active_posts int)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() then
    raise exception 'NOT_ADMIN';
  end if;
  return query
    select v.user_id, p.full_name, v.company_id, c.name, v.work_email, v.expires_at,
           (select count(*)::int from jobs j where j.referrer_id = v.user_id and j.status = 'active')
    from referrer_verifications v
    join profiles p on p.id = v.user_id
    join companies c on c.id = v.company_id
    where v.expires_at > now() and p.banned_at is null
    order by c.name, p.full_name;
end;
$$;
revoke all on function public.admin_referrers() from public, anon;
grant execute on function public.admin_referrers() to authenticated;

-- Tell the employee a post was made for them
create or replace function public.notify_posted_for()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.posted_by is not null and new.posted_by <> new.referrer_id then
    insert into notifications (user_id, kind, title, body, data)
    values (new.referrer_id, 'posted_for_you',
            'Your opening is live',
            '"' || new.title || '" was posted for you. Applicants will come to you in My posts.',
            jsonb_build_object('job_id', new.id));
  end if;
  return new;
end;
$$;
drop trigger if exists jobs_notify_posted_for on public.jobs;
create trigger jobs_notify_posted_for after insert on public.jobs
  for each row execute function public.notify_posted_for();
