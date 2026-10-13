-- Push trigger tests: each new notification queues one POST to send-push once Vault is set up.
\set U '''b1000000-0000-0000-0000-000000000001'''
\pset footer off
insert into auth.users (id) values (:U);

\echo '--- K1 Vault not set up: notification saved, nothing sent (expect 1 row, 0 calls)'
insert into notifications (user_id, kind, title, body) values (:U, 'test', 'One', 'first');
select (select count(*) from notifications) as notifications, (select count(*) from net.calls) as calls;

\echo '--- K2 Vault set up (URL with trailing slash): one call per insert with the webhook payload'
insert into vault.decrypted_secrets values ('project_url', 'https://abcd.supabase.co/'), ('push_webhook_secret', 's3cret');
insert into notifications (user_id, kind, title, body, data) values (:U, 'test', 'Two', 'second', '{"job_id":"j1"}');
select url, headers->>'x-webhook-secret' as secret, timeout_ms,
       body->>'type' as type, body->>'table' as tbl, body->'record'->>'title' as title,
       body->'record'->>'user_id' = :U as right_user, body->'record'->'data'->>'job_id' as data_job
from net.calls;

\echo '--- K3 a status change notification (from the app flow) also triggers a call (expect 2 calls total)'
insert into notifications (user_id, kind, title, body) values (:U, 'status_shortlisted', 'You''re shortlisted', 'x');
select count(*) as calls from net.calls;

\echo '--- K4 if the HTTP call itself errors, the notification is still saved (expect WARNING, 4 notifications)'
create or replace function net.http_post(url text, body jsonb default '{}', params jsonb default '{}',
                                         headers jsonb default '{}', timeout_milliseconds int default 1000)
returns bigint language plpgsql as $$ begin raise exception 'network down'; end $$;
insert into notifications (user_id, kind, title, body) values (:U, 'test', 'Four', 'fourth');
select count(*) as notifications from notifications;

\echo '--- K5 app users cannot call the trigger function directly (expect permission denied)'
set role authenticated;
select public.push_on_notification();
reset role;
