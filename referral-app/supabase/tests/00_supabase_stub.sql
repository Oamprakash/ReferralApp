-- Minimal stand-ins for Supabase auth/storage so the migration runs on plain Postgres.
-- Run once per fresh database (roles anon/authenticated/service_role must exist; see run_tests.sh).
create extension if not exists pgcrypto;
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
create schema auth;
grant usage on schema auth to anon, authenticated, service_role;
create table auth.users (id uuid primary key default gen_random_uuid(), email text, phone text, created_at timestamptz default now());
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
grant execute on function auth.uid() to anon, authenticated, service_role;
create schema storage;
grant usage on schema storage to anon, authenticated, service_role;
create table storage.buckets (id text primary key, name text, public bool, file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects (id uuid primary key default gen_random_uuid(), bucket_id text references storage.buckets, name text, owner uuid);
alter table storage.objects enable row level security;
grant all on storage.objects to authenticated, service_role; grant select on storage.buckets to authenticated;
create function storage.foldername(name text) returns text[] language sql immutable as $$
  select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'),1)-1] $$;
grant execute on function storage.foldername(text) to authenticated;

-- pg_net stand-in: records requests instead of sending them (tests read net.calls)
create schema if not exists extensions;
create schema if not exists net;
create table net.calls (id serial primary key, url text, body jsonb, headers jsonb, timeout_ms int);
create function net.http_post(url text, body jsonb default '{}', params jsonb default '{}',
                              headers jsonb default '{}', timeout_milliseconds int default 1000)
returns bigint language sql as $$
  insert into net.calls (url, body, headers, timeout_ms) values (url, body, headers, timeout_milliseconds) returning id;
$$;

-- Vault stand-in: plain table with the same name and columns as vault.decrypted_secrets
create schema if not exists vault;
create table vault.decrypted_secrets (name text primary key, decrypted_secret text);
