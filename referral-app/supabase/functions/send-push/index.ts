// Called by a Database Webhook on notifications INSERT. See README "Push notifications".
import { createClient } from "npm:@supabase/supabase-js@2";
import { AccessTokenCache, handlePushWebhook, parseServiceAccount, type PushDeps } from "../_shared/push.ts";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

let serviceAccount = null;
let configError: string | undefined;
const rawSa = Deno.env.get("FCM_SERVICE_ACCOUNT");
try {
  serviceAccount = parseServiceAccount(rawSa);
  if (!serviceAccount) configError = "FCM_SERVICE_ACCOUNT is not set (or empty)";
} catch (e) {
  configError = `FCM_SERVICE_ACCOUNT could not be read (${rawSa?.length ?? 0} chars): ${(e as Error).message}`;
  console.error(configError);
}

const deps: PushDeps = {
  serviceAccount,
  configError,
  webhookSecret: Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "",
  fetch: (input, init) => fetch(input, init),
  nowMs: () => Date.now(),
  log: (msg, extra) => console.error(msg, extra ?? ""),

  async tokensFor(userId) {
    const { data, error } = await admin.from("device_tokens").select("token").eq("user_id", userId);
    if (error) throw error;
    return (data ?? []).map((r: { token: string }) => r.token);
  },
  async deleteTokens(tokens) {
    const { error } = await admin.from("device_tokens").delete().in("token", tokens);
    if (error) console.error("deleteTokens failed", error);
  },
  async markSent(id) {
    const { error } = await admin.from("notifications").update({ sent_at: new Date().toISOString() }).eq("id", id);
    if (error) console.error("markSent failed", error);
  },
};

const cache = new AccessTokenCache();
Deno.serve(async (req) => {
  try {
    return await handlePushWebhook(req, deps, cache);
  } catch (e) {
    console.error("send-push failed", e);
    return new Response(JSON.stringify({ error: "server_error" }), { status: 500 });
  }
});
