-- =====================================================================
-- Company allowlist: 15 companies with large Chennai engineering teams.
-- Work emails at these domains (and their subdomains) can verify as referrers.
--
-- Run in: Supabase Dashboard -> SQL Editor. Safe to re-run: a company is
-- skipped if any of its domains is already on the list.
--
-- BEFORE GOING LIVE: confirm each domain with someone who works there.
-- These are the companies' public web domains; most use the same domain for
-- employee email, but a wrong entry would block real employees.
-- =====================================================================

insert into public.companies (name, domains, status)
select v.name, v.domains, 'approved'
from (values
  ('Movate',                    '{movate.com}'::text[]),
  ('Zoho',                      '{zohocorp.com}'::text[]),   -- not zoho.com: that's also free Zoho Mail
  ('Freshworks',                '{freshworks.com}'::text[]),
  ('Chargebee',                 '{chargebee.com}'::text[]),
  ('Kissflow',                  '{kissflow.com}'::text[]),
  ('LatentView Analytics',      '{latentview.com}'::text[]),
  ('Ramco Systems',             '{ramco.com}'::text[]),
  ('Hexaware Technologies',     '{hexaware.com}'::text[]),
  ('Tata Consultancy Services', '{tcs.com}'::text[]),
  ('Infosys',                   '{infosys.com}'::text[]),
  ('Cognizant',                 '{cognizant.com}'::text[]),
  ('HCLTech',                   '{hcltech.com,hcl.com}'::text[]),
  ('Wipro',                     '{wipro.com}'::text[]),
  ('Accenture',                 '{accenture.com}'::text[]),
  ('PayPal',                    '{paypal.com}'::text[])
) as v(name, domains)
where not exists (select 1 from public.companies c where c.domains && v.domains);

-- Check
select name, domains, status from public.companies
where status = 'approved' and domains[1] not like '%.test'
order by name;

-- ---------------------------------------------------------------------
-- Admin: review domains people tried that aren't on the list
-- ---------------------------------------------------------------------
-- select domain, request_count, last_requested_at from public.domain_requests
-- where status = 'pending' order by request_count desc;
--
-- Approve one (adds the company, marks the request):
-- insert into public.companies (name, domains, status) values ('Company Name', '{example.com}', 'approved');
-- update public.domain_requests set status = 'approved' where domain = 'example.com';
