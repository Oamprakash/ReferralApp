-- =====================================================================
-- Experience half of the match score: being over the range no longer drops to 0.
--   in range                 50
--   1 year outside           30   (either side)
--   more than 1 year above   25   (overqualified: half, however far over)
--   2 years below            10
--   further below             0
-- Skills half unchanged. Used by best_matches() and stored on each new application.
-- Scores already stored on applications keep the value they were given.
-- =====================================================================

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
      when p_years > p_exp_max + 1 then 25
      when p_years >= p_exp_min - 2 then 10
      else 0
    end
    + case
        when (select count(*) from js) = 0 then 25
        else round(50.0 * (select count(*) from js where s in (select s from ss)) / (select count(*) from js))
      end
  )::smallint;
$$;
