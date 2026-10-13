-- Referral Jobs App: call send-push from the database (replaces the dashboard Database Webhook)
--
-- Every new notifications row triggers an async HTTP POST (pg_net) to the send-push
-- Edge Function, in the same payload shape a Database Webhook sends.
--
-- One-time setup, run in the SQL Editor (values stay encrypted in Vault, never in git):
--   select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
--   select vault.create_secret('<same value as PUSH_WEBHOOK_SECRET>', 'push_webhook_secret');
-- Until both exist, inserts work normally and nothing is sent.
--
-- If you created a Database Webhook in the dashboard for notifications, delete it,
-- or every notification is pushed twice.

create extension if not exists pg_net with schema extensions;

create or replace function public.push_on_notification()
returns trigger language plpgsql security definer set search_path = public, extensions as $$
declare
  v_url text;
  v_secret text;
begin
  select decrypted_secret into v_url from vault.decrypted_secrets where name = 'project_url';
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'push_webhook_secret';
  if v_url is null or v_secret is null then
    return new;                          -- push not configured yet
  end if;

  -- Asynchronous: queued and sent after the transaction commits; never blocks or fails the insert
  perform net.http_post(
    url := rtrim(v_url, '/') || '/functions/v1/send-push',
    body := jsonb_build_object('type', 'INSERT', 'table', 'notifications', 'schema', 'public',
                               'record', to_jsonb(new)),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-webhook-secret', v_secret),
    timeout_milliseconds := 5000
  );
  return new;
exception when others then
  raise warning 'push_on_notification: %', sqlerrm;   -- a push problem must never lose the notification
  return new;
end;
$$;
revoke all on function public.push_on_notification() from public, anon, authenticated;

drop trigger if exists notifications_push on public.notifications;
create trigger notifications_push after insert on public.notifications
  for each row execute function public.push_on_notification();
