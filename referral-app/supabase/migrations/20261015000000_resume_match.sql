-- Resume parser & auto-match
--   * profile_contacts: the email/phone a seeker wants referrers to use for the referral form.
--     Private to the seeker; a referrer sees it only once they've shortlisted that person (applicant_contact).
--   * best_matches(): open jobs ranked by the same match_score the referrer sees, with the skills
--     the seeker has and is missing for each one.
--   * export_my_data() now includes the contact details.
-- The resume itself is parsed on the phone; no resume text is stored or sent anywhere new.

-- ---------- Contact details for referrals ----------
create table if not exists public.profile_contacts (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  email text check (email is null or (length(email) <= 254 and email ~* '^[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$')),
  phone text check (phone is null or phone ~ '^\+91[6-9][0-9]{9}$'),
  updated_at timestamptz not null default now()
);

alter table public.profile_contacts enable row level security;

create policy contacts_select_own on public.profile_contacts for select to authenticated
  using (user_id = auth.uid());
create policy contacts_insert_own on public.profile_contacts for insert to authenticated
  with check (user_id = auth.uid());
create policy contacts_update_own on public.profile_contacts for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy contacts_delete_own on public.profile_contacts for delete to authenticated
  using (user_id = auth.uid());

revoke all on public.profile_contacts from anon;
grant select, insert, update, delete on public.profile_contacts to authenticated;

create or replace function public.touch_profile_contacts()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  new.email := nullif(lower(trim(new.email)), '');
  new.phone := nullif(trim(new.phone), '');
  return new;
end;
$$;

drop trigger if exists profile_contacts_touch on public.profile_contacts;
create trigger profile_contacts_touch before insert or update on public.profile_contacts
  for each row execute function public.touch_profile_contacts();

-- A referrer gets an applicant's contact details only after shortlisting them (they need it for the
-- company's referral form). Before that, or for anyone else, this returns null.
create or replace function public.applicant_contact(p_application uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('email', c.email, 'phone', c.phone)
  from applications a
  join jobs j on j.id = a.job_id
  join profile_contacts c on c.user_id = a.seeker_id
  where a.id = p_application
    and j.referrer_id = auth.uid()
    and a.status::text in ('shortlisted', 'referred', 'interview', 'offer');
$$;
revoke all on function public.applicant_contact(uuid) from public, anon;
grant execute on function public.applicant_contact(uuid) to authenticated;

-- ---------- Best matches for the signed-in seeker ----------
-- Same score the referrer sees on the application (match_score): up to 50 for experience fit,
-- up to 50 for the share of the job's skills the seeker has. Only fresh, open jobs they haven't
-- applied to and didn't post. Jobs scoring 0 are left out.
create or replace function public.best_matches(p_limit int default 20)
returns jsonb language sql stable security definer set search_path = public as $$
  with me as (
    select coalesce(skills, '{}') as skills, experience_years from profiles where id = auth.uid()
  ),
  mine as (select array(select lower(trim(s)) from me, unnest(me.skills) s) as lskills),
  scored as (
    select j.id, j.title, j.team, j.location, j.exp_min, j.exp_max, j.work_mode, j.salary_range,
           j.last_confirmed_at, c.name as company_name,
           match_score(j.skills, j.exp_min, j.exp_max, me.skills, me.experience_years) as score,
           array(select s from unnest(coalesce(j.skills, '{}')) s
                 where lower(trim(s)) = any (mine.lskills)) as have,
           array(select s from unnest(coalesce(j.skills, '{}')) s
                 where trim(s) <> '' and not lower(trim(s)) = any (mine.lskills)) as missing
    from jobs j
    join companies c on c.id = j.company_id
    cross join me cross join mine
    where j.status = 'active'
      and j.last_confirmed_at > now() - interval '14 days'
      and j.referrer_id <> auth.uid()
      and not exists (select 1 from applications a where a.job_id = j.id and a.seeker_id = auth.uid())
  )
  select coalesce(jsonb_agg(jsonb_build_object(
           'id', id, 'title', title, 'team', team, 'location', location,
           'exp_min', exp_min, 'exp_max', exp_max, 'work_mode', work_mode, 'salary_range', salary_range,
           'last_confirmed_at', last_confirmed_at, 'company_name', company_name,
           'score', score, 'have', to_jsonb(have), 'missing', to_jsonb(missing))
         order by score desc, last_confirmed_at desc), '[]')
  from (select * from scored where score > 0 order by score desc, last_confirmed_at desc
        limit least(greatest(coalesce(p_limit, 20), 1), 50)) t;
$$;
revoke all on function public.best_matches(int) from public, anon;
grant execute on function public.best_matches(int) to authenticated;

-- ---------- Export now includes contact details ----------
create or replace function public.export_my_data()
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'phone', u.phone, 'email', u.email, 'created_at', u.created_at)
                from auth.users u where u.id = auth.uid()),
    'profile', (select to_jsonb(p) - 'priority_until' from profiles p where p.id = auth.uid()),
    'contact_for_referrals', (select jsonb_build_object('email', email, 'phone', phone, 'updated_at', updated_at)
                              from profile_contacts where user_id = auth.uid()),
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
