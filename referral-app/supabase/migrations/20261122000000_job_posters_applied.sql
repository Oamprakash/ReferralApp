-- =====================================================================
-- job_posters(): also answer for jobs the caller has applied to, even after the post
-- closes or hides, so the Applied tab can say "Sent to Arjun · Nov 13".
-- Still first name + referral count only, and never for jobs the caller can't see.
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
    and (j.status = 'active'
         or exists (select 1 from applications a where a.job_id = j.id and a.seeker_id = auth.uid()));
$$;

revoke all on function public.job_posters(uuid[]) from public, anon;
grant execute on function public.job_posters(uuid[]) to authenticated;
