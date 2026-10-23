-- Explicit Data API grants for every table in "public".
--
-- From 30 Oct 2026 Supabase stops granting new public tables to anon/authenticated/service_role
-- automatically. That affects anything that rebuilds the schema from these migrations (a new
-- project, a preview branch, `supabase db reset`). Existing tables keep their grants, so on the
-- live project this migration only tightens them.
--
-- Rules:
--   * anon gets nothing: every screen needs a signed-in user, and no RLS policy targets anon.
--   * authenticated gets only what the app does directly through PostgREST. Row access is still
--     decided by RLS; writes that go through security-definer functions or Edge Functions
--     (service_role) need no grant here.
--   * service_role keeps full access (Edge Functions, admin SQL).
--
-- New tables: add the same three-part block (revoke, authenticated grants, service_role) in the
-- migration that creates the table.

-- profiles: read (own row, or applicants to my jobs, via RLS); edit descriptive fields only.
revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant update (full_name, city, skills, experience_years, resume_path) on public.profiles to authenticated;
grant all on public.profiles to service_role;

-- companies: read approved companies. Writes are admin functions.
revoke all on public.companies from anon, authenticated;
grant select on public.companies to authenticated;
grant all on public.companies to service_role;

-- referrer_verifications: read own. Writes are the work-email Edge Functions.
revoke all on public.referrer_verifications from anon, authenticated;
grant select on public.referrer_verifications to authenticated;
grant all on public.referrer_verifications to service_role;

-- jobs: browse, post, confirm/close own posts. No deletes (posts are closed, not deleted).
revoke all on public.jobs from anon, authenticated;
grant select, insert, update on public.jobs to authenticated;
grant all on public.jobs to service_role;

-- applications: seekers apply and read their own; referrers move the status forward.
revoke all on public.applications from anon, authenticated;
grant select, insert on public.applications to authenticated;
grant update (status) on public.applications to authenticated;
grant all on public.applications to service_role;

-- reports: file and read your own.
revoke all on public.reports from anon, authenticated;
grant select, insert on public.reports to authenticated;
grant all on public.reports to service_role;

-- notifications: read own, mark as read. Rows are written by triggers.
revoke all on public.notifications from anon, authenticated;
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;
grant all on public.notifications to service_role;

-- profile_contacts: the seeker manages their own row (saving an empty form deletes it).
revoke all on public.profile_contacts from anon, authenticated;
grant select, insert, update, delete on public.profile_contacts to authenticated;
grant all on public.profile_contacts to service_role;

-- Server-only tables: reached through security-definer functions or Edge Functions.
revoke all on public.device_tokens, public.admins, public.blocked_email_domains,
              public.domain_requests, public.work_email_otps
  from anon, authenticated;
grant all on public.device_tokens, public.admins, public.blocked_email_domains,
             public.domain_requests, public.work_email_otps
  to service_role;
