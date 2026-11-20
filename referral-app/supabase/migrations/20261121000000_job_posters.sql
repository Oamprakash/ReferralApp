-- =====================================================================
-- Who posted each job, for the referrer line on feed cards:
--   "Posted by Priya, verified employee · 12 referred"
-- Same details the job screen already shows (get_job_detail): first name only, and how many
-- people they've referred. Profiles are private, so this runs as security definer and only
-- answers for open jobs.
-- =====================================================================

create or replace function public.job_posters(p_jobs uuid[])
returns table (job_id uuid, first_name text, referred int)
language sql stable security definer set search_path = public as $$
  select j.id,
         nullif(split_part(coalesce(p.full_name, ''), ' ', 1), ''),
         coalesce((public.referrer_stats(j.referrer_id) ->> 'referred')::int, 0)
  from jobs j
  left join profiles p on p.id = j.referrer_id
  where j.id = any (p_jobs[1:100])
    and j.status = 'active';
$$;

revoke all on function public.job_posters(uuid[]) from public, anon;
grant execute on function public.job_posters(uuid[]) to authenticated;
