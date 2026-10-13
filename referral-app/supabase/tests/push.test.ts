// Tests for the send-push logic with a real RSA key and a fake Google/FCM endpoint.
// Run: node --test tests/push.test.ts   (Node 22.18+)
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, verify } from "node:crypto";
import {
  AccessTokenCache, handlePushWebhook, parseServiceAccount, signServiceAccountJwt,
  type PushDeps, type ServiceAccount,
} from "../functions/_shared/push.ts";

const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const SA: ServiceAccount = {
  project_id: "referral-test",
  client_email: "push@referral-test.iam.gserviceaccount.com",
  // Service-account files store the key with literal "\n" sequences
  private_key: (privateKey.export({ type: "pkcs8", format: "pem" }) as string).replace(/\n/g, "\\n"),
};
const SECRET = "webhook-secret-123";

const b64urlDecode = (s: string) => Buffer.from(s.replace(/-/g, "+").replace(/_/g, "/"), "base64");

/** Fake Google: token endpoint + FCM send endpoint with per-token behaviour. */
function fakeGoogle(opts: { fcm?: (token: string, call: number) => { status: number; body?: unknown } } = {}) {
  const calls = { token: 0, send: [] as { token: string; auth: string; body: any }[] };
  let n = 0;
  const f = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url === "https://oauth2.googleapis.com/token") {
      calls.token++;
      return new Response(JSON.stringify({ access_token: `access-${calls.token}`, expires_in: 3600 }), { status: 200 });
    }
    if (url === `https://fcm.googleapis.com/v1/projects/${SA.project_id}/messages:send`) {
      const body = JSON.parse(String(init!.body));
      const token = body.message.token;
      calls.send.push({ token, auth: (init!.headers as Record<string, string>).Authorization, body });
      const r = opts.fcm?.(token, n++) ?? { status: 200, body: { name: "projects/x/messages/1" } };
      return new Response(JSON.stringify(r.body ?? {}), { status: r.status });
    }
    return new Response("not found", { status: 404 });
  }) as typeof fetch;
  return { f, calls };
}

function deps(over: Partial<PushDeps> & { tokens?: string[] } = {}) {
  const state = { deleted: [] as string[], marked: [] as string[], now: 1_760_000_000_000 };
  const g = fakeGoogle();
  const d: PushDeps = {
    serviceAccount: SA,
    webhookSecret: SECRET,
    fetch: g.f,
    nowMs: () => state.now,
    tokensFor: async () => over.tokens ?? ["device-token-1"],
    deleteTokens: async (t) => { state.deleted.push(...t); },
    markSent: async (id) => { state.marked.push(id); },
    ...over,
  };
  return { d, state, calls: g.calls };
}

const record = { id: "n1", user_id: "u1", title: "You're shortlisted", body: "Priya shortlisted you", data: { job_id: "j1", status: "shortlisted", n: 5 } };
const hook = (body: unknown, secret = SECRET) => new Request("http://local/send-push", {
  method: "POST",
  headers: { "content-type": "application/json", "x-webhook-secret": secret },
  body: JSON.stringify(body),
});

test("JWT is RS256-signed for the service account and verifies with its public key", async () => {
  const jwt = await signServiceAccountJwt(SA, 1_760_000_000);
  const [h, c, s] = jwt.split(".");
  assert.deepEqual(JSON.parse(b64urlDecode(h).toString()), { alg: "RS256", typ: "JWT" });
  const claims = JSON.parse(b64urlDecode(c).toString());
  assert.equal(claims.iss, SA.client_email);
  assert.equal(claims.aud, "https://oauth2.googleapis.com/token");
  assert.equal(claims.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assert.equal(claims.exp - claims.iat, 3600);
  assert.ok(verify("sha256", Buffer.from(`${h}.${c}`), publicKey, b64urlDecode(s)), "signature must verify");
});

test("service account accepted as raw JSON or base64", () => {
  const raw = JSON.stringify(SA);
  assert.equal(parseServiceAccount(raw)!.project_id, "referral-test");
  assert.equal(parseServiceAccount(Buffer.from(raw).toString("base64"))!.client_email, SA.client_email);
  assert.equal(parseServiceAccount(""), null);
  assert.throws(() => parseServiceAccount('{"project_id":"x"}'));
});

test("rejects a wrong webhook secret and ignores non-notification events", async () => {
  const { d } = deps();
  assert.equal((await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }, "nope"), d, new AccessTokenCache())).status, 401);
  const r = await handlePushWebhook(hook({ type: "UPDATE", table: "notifications", record }), d, new AccessTokenCache());
  assert.deepEqual(await r.json(), { skipped: "not_a_notification_insert" });
});

test("not configured yet: skips with 200 so the webhook doesn't retry", async () => {
  const { d } = deps({ serviceAccount: null });
  const r = await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), d, new AccessTokenCache());
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { skipped: "push_not_configured", reason: "FCM_SERVICE_ACCOUNT is not set" });
  const bad = deps({ serviceAccount: null, configError: "FCM_SERVICE_ACCOUNT could not be read (12 chars): bad" }).d;
  const r2 = await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), bad, new AccessTokenCache());
  assert.equal((await r2.json()).reason, "FCM_SERVICE_ACCOUNT could not be read (12 chars): bad");
});

test("sends to every device with string data and marks the notification sent", async () => {
  const { d, state, calls } = deps({ tokens: ["phone-a", "phone-b"] });
  const r = await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), d, new AccessTokenCache());
  assert.deepEqual(await r.json(), { sent: 2, failed: 0, removed: 0 });
  assert.equal(calls.token, 1, "one token exchange for both sends");
  assert.deepEqual(calls.send.map((s) => s.token), ["phone-a", "phone-b"]);
  assert.equal(calls.send[0].auth, "Bearer access-1");
  assert.deepEqual(calls.send[0].body.message.data, { notification_id: "n1", job_id: "j1", status: "shortlisted", n: "5" });
  assert.equal(calls.send[0].body.message.android.notification.channel_id, "updates");
  assert.deepEqual(state.marked, ["n1"]);
});

test("dead tokens (404 / UNREGISTERED) are deleted; other failures are kept", async () => {
  const g = fakeGoogle({
    fcm: (token) => token === "gone" ? { status: 404, body: { error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } } }
      : token === "flaky" ? { status: 503, body: { error: { status: "UNAVAILABLE" } } }
      : { status: 200 },
  });
  const { d, state } = deps({ tokens: ["gone", "flaky", "ok"], fetch: g.f });
  const r = await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), d, new AccessTokenCache());
  assert.deepEqual(await r.json(), { sent: 1, failed: 1, removed: 1 });
  assert.deepEqual(state.deleted, ["gone"]);
});

test("access token is cached across calls and refreshed after a 401", async () => {
  let rejectOnce = true;
  const g = fakeGoogle({
    fcm: () => {
      if (rejectOnce) { rejectOnce = false; return { status: 401, body: { error: { status: "UNAUTHENTICATED" } } }; }
      return { status: 200 };
    },
  });
  const { d } = deps({ fetch: g.f });
  const cache = new AccessTokenCache();
  const r1 = await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), d, cache);
  assert.deepEqual(await r1.json(), { sent: 1, failed: 0, removed: 0 });
  assert.equal(g.calls.token, 2, "401 forces one refresh");
  assert.equal(g.calls.send.at(-1)!.auth, "Bearer access-2");

  await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), d, cache);
  assert.equal(g.calls.token, 2, "second notification reuses the cached token");
});

test("user with no devices: nothing sent, still marked handled", async () => {
  const { d, state, calls } = deps({ tokens: [] });
  const r = await handlePushWebhook(hook({ type: "INSERT", table: "notifications", record }), d, new AccessTokenCache());
  assert.deepEqual(await r.json(), { sent: 0, failed: 0, removed: 0, reason: "no_devices" });
  assert.equal(calls.token, 0);
  assert.deepEqual(state.marked, ["n1"]);
});
