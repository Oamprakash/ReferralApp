-- Referral Jobs App: Sprint 2 — work-email verification
--
-- Flow:
--   1. App calls Edge Function request-work-email-otp {email}
--      -> function generates a 6-digit code, HMACs it, calls request_work_email_otp()
--      -> on status 'sent' the function emails the code
--   2. App calls Edge Function verify-work-email-otp {code}
--      -> function HMACs the code, calls verify_work_email_otp()
--      -> on success the user gets a 90-day referrer_verifications row
--
-- The SQL functions below hold all the rules. Only the service role may call them.

-- ---------- Domains people can't verify with (free and disposable mail) ----------
create table public.blocked_email_domains (
  domain text primary key check (domain = lower(domain))
);
insert into public.blocked_email_domains (domain) values
  ('gmail.com'), ('googlemail.com'), ('yahoo.com'), ('yahoo.co.in'), ('ymail.com'),
  ('rocketmail.com'), ('outlook.com'), ('outlook.in'), ('hotmail.com'), ('live.com'),
  ('msn.com'), ('icloud.com'), ('me.com'), ('mac.com'), ('aol.com'), ('proton.me'),
  ('protonmail.com'), ('pm.me'), ('rediffmail.com'), ('zoho.com'), ('zohomail.com'),
  ('zohomail.in'), ('gmx.com'), ('gmx.net'), ('mail.com'), ('yandex.com'), ('yandex.ru'),
  ('tutanota.com'), ('tuta.io'), ('fastmail.com'), ('hey.com'), ('inbox.com'),
  ('mailinator.com'), ('yopmail.com'), ('guerrillamail.com'), ('10minutemail.com'),
  ('temp-mail.org'), ('tempmail.com'), ('sharklasers.com'), ('trashmail.com'),
  ('dispostable.com'), ('getnada.com'), ('maildrop.cc')
on conflict do nothing;
alter table public.blocked_email_domains enable row level security;   -- service role only

-- ---------- Unknown company domains waiting for admin review ----------
create table public.domain_requests (
  domain text primary key check (domain = lower(domain)),
  first_requested_by uuid references public.profiles (id) on delete set null,
  request_count int not null default 1,
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  created_at timestamptz not null default now(),
  last_requested_at timestamptz not null default now()
);
alter table public.domain_requests enable row level security;          -- service role only

-- ---------- One-time codes ----------
create table public.work_email_otps (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  company_id uuid not null references public.companies (id) on delete cascade,
  email text not null,
  code_hash text not null,                 -- HMAC-SHA256 hex; the code itself is never stored
  attempts smallint not null default 0,
  expires_at timestamptz not null default (now() + interval '10 minutes'),
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);
create index work_email_otps_user_idx on public.work_email_otps (user_id, created_at desc);
create index work_email_otps_email_idx on public.work_email_otps (email, created_at desc);
alter table public.work_email_otps enable row level security;          -- service role only

-- ---------- Integrity on existing tables ----------
-- One work email backs at most one account
create unique index referrer_verifications_email_uniq
  on public.referrer_verifications (lower(work_email));

-- Company domains are lowercase and never shared between two companies
create or replace function public.check_company_domains()
returns trigger language plpgsql as $$
begin
  new.domains := (select coalesce(array_agg(distinct lower(trim(d))), '{}') from unnest(new.domains) d
                  where trim(d) <> '');
  if exists (select 1 from public.companies c
             where c.id <> new.id and c.domains && new.domains) then
    raise exception 'DOMAIN_TAKEN: one of % already belongs to another company', new.domains;
  end if;
  return new;
end;
$$;
create trigger companies_domains_check before insert or update of domains on public.companies
  for each row execute function public.check_company_domains();

-- ---------- Helpers ----------
-- 'Name@Mail.Movate.com ' -> 'mail.movate.com'; null if not a plausible email
create or replace function public.email_domain(p_email text)
returns text language sql immutable as $$
  select case
    when lower(trim(p_email)) ~ '^[a-z0-9._%+''-]+@([a-z0-9-]+\.)+[a-z]{2,}$'
    then split_part(lower(trim(p_email)), '@', 2)
  end;
$$;

-- Approved company owning the domain, also matching subdomains (in.ibm.com -> ibm.com)
create or replace function public.company_for_domain(p_domain text)
returns uuid language sql stable security definer set search_path = public as $$
  select c.id from companies c, unnest(c.domains) d
  where c.status = 'approved' and (p_domain = d or p_domain like '%.' || d)
  order by length(d) desc
  limit 1;
$$;

-- ---------- request_work_email_otp ----------
-- Returns json {status, ...}. Statuses:
--   sent            -> code stored; caller must email it. Includes company_name.
--   invalid_email   -> not an email address
--   free_domain     -> gmail etc.; work email required
--   pending_review  -> company not on the allowlist yet; domain queued for admin
--   cooldown        -> asked again within 60 s; includes retry_after_seconds
--   rate_limited    -> too many codes this hour (per user or per address)
--   email_in_use    -> address already verifies another active account
create or replace function public.request_work_email_otp(p_user uuid, p_email text, p_code_hash text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_email text := lower(trim(p_email));
  v_domain text := public.email_domain(p_email);
  v_company uuid;
  v_company_name text;
  v_last timestamptz;
begin
  if v_domain is null then
    return jsonb_build_object('status', 'invalid_email');
  end if;

  if exists (select 1 from blocked_email_domains b
             where v_domain = b.domain or v_domain like '%.' || b.domain) then
    return jsonb_build_object('status', 'free_domain');
  end if;

  v_company := public.company_for_domain(v_domain);
  if v_company is null then
    insert into domain_requests (domain, first_requested_by) values (v_domain, p_user)
    on conflict (domain) do update
      set request_count = domain_requests.request_count + 1, last_requested_at = now();
    return jsonb_build_object('status', 'pending_review', 'domain', v_domain);
  end if;

  if exists (select 1 from referrer_verifications
             where lower(work_email) = v_email and user_id <> p_user and expires_at > now()) then
    return jsonb_build_object('status', 'email_in_use');
  end if;

  -- 60-second cooldown between codes for the same user
  select max(created_at) into v_last from work_email_otps where user_id = p_user;
  if v_last is not null and v_last > now() - interval '60 seconds' then
    return jsonb_build_object('status', 'cooldown',
      'retry_after_seconds', ceil(extract(epoch from (v_last + interval '60 seconds' - now())))::int);
  end if;

  -- 5 codes per user per hour; 3 per address per hour (stops inbox flooding)
  if (select count(*) from work_email_otps where user_id = p_user and created_at > now() - interval '1 hour') >= 5
     or (select count(*) from work_email_otps where email = v_email and created_at > now() - interval '1 hour') >= 3 then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  -- Only the newest code is valid
  update work_email_otps set consumed_at = now()
    where user_id = p_user and consumed_at is null;

  insert into work_email_otps (user_id, company_id, email, code_hash)
    values (p_user, v_company, v_email, p_code_hash);

  select name into v_company_name from companies where id = v_company;
  return jsonb_build_object('status', 'sent', 'company_name', v_company_name, 'email', v_email);
end;
$$;

-- ---------- verify_work_email_otp ----------
-- Returns json {status, ...}. Statuses:
--   verified           -> referrer_verifications row created/renewed; includes company_name,
--                         expires_at, closed_posts (active posts closed at a previous company)
--   no_pending_code    -> nothing to verify, or it expired; request a new code
--   invalid_code       -> wrong code; includes attempts_left
--   too_many_attempts  -> 5 wrong tries; request a new code
--   email_in_use       -> someone verified this address meanwhile
create or replace function public.verify_work_email_otp(p_user uuid, p_code_hash text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_otp work_email_otps%rowtype;
  v_expires timestamptz := now() + interval '90 days';
  v_closed int;
  v_company_name text;
begin
  select * into v_otp from work_email_otps
    where user_id = p_user and consumed_at is null and expires_at > now()
    order by created_at desc limit 1
    for update;

  if not found then
    return jsonb_build_object('status', 'no_pending_code');
  end if;

  if v_otp.attempts >= 5 then
    return jsonb_build_object('status', 'too_many_attempts');
  end if;

  if v_otp.code_hash <> p_code_hash then
    update work_email_otps set attempts = attempts + 1 where id = v_otp.id;
    if v_otp.attempts + 1 >= 5 then
      return jsonb_build_object('status', 'too_many_attempts');
    end if;
    return jsonb_build_object('status', 'invalid_code', 'attempts_left', 5 - (v_otp.attempts + 1));
  end if;

  update work_email_otps set consumed_at = now() where id = v_otp.id;

  if exists (select 1 from referrer_verifications
             where lower(work_email) = v_otp.email and user_id <> p_user and expires_at > now()) then
    return jsonb_build_object('status', 'email_in_use');
  end if;
  -- An expired verification by someone else for this address no longer counts
  delete from referrer_verifications where lower(work_email) = v_otp.email and user_id <> p_user;

  -- Moving company: close posts at the old one and drop the old verification
  update jobs set status = 'closed'
    where referrer_id = p_user and company_id <> v_otp.company_id and status <> 'closed';
  get diagnostics v_closed = row_count;
  delete from referrer_verifications where user_id = p_user and company_id <> v_otp.company_id;

  insert into referrer_verifications (user_id, company_id, work_email, verified_at, expires_at)
    values (p_user, v_otp.company_id, v_otp.email, now(), v_expires)
  on conflict (user_id, company_id) do update
    set work_email = excluded.work_email, verified_at = now(), expires_at = v_expires;

  select name into v_company_name from companies where id = v_otp.company_id;
  return jsonb_build_object('status', 'verified', 'company_name', v_company_name,
                            'expires_at', v_expires, 'closed_posts', v_closed);
end;
$$;

-- Only the Edge Functions (service role) may call these
revoke all on function public.request_work_email_otp(uuid, text, text) from public, anon, authenticated;
revoke all on function public.verify_work_email_otp(uuid, text) from public, anon, authenticated;
grant execute on function public.request_work_email_otp(uuid, text, text) to service_role;
grant execute on function public.verify_work_email_otp(uuid, text) to service_role;
revoke all on function public.company_for_domain(text) from public, anon, authenticated;
grant execute on function public.company_for_domain(text) to service_role;

revoke all on public.blocked_email_domains, public.domain_requests, public.work_email_otps
  from anon, authenticated;
