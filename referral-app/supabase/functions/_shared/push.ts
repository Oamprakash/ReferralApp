// Push delivery via Firebase Cloud Messaging HTTP v1. Web-standard APIs only
// (fetch, crypto.subtle, atob), so it runs on Supabase Edge (Deno) and in Node tests.
//
// Flow: Database Webhook on notifications INSERT -> POST {type, table, record} here
//       -> look up the user's device tokens -> send one FCM message per token
//       -> delete tokens FCM reports as gone -> mark the notification sent.

export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
}

export interface NotificationRecord {
  id: string;
  user_id: string;
  title: string;
  body: string;
  data?: Record<string, unknown> | null;
}

export interface PushDeps {
  serviceAccount: ServiceAccount | null;
  /** Why serviceAccount is null, shown in the response so setup problems are easy to spot. */
  configError?: string;
  /** Shared secret the Database Webhook sends in the x-webhook-secret header. */
  webhookSecret: string;
  fetch: typeof fetch;
  nowMs(): number;
  tokensFor(userId: string): Promise<string[]>;
  deleteTokens(tokens: string[]): Promise<void>;
  markSent(notificationId: string): Promise<void>;
  log?: (msg: string, extra?: unknown) => void;
}

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

/** Accepts the service-account JSON as-is or base64-encoded (easier to pass as a secret on Windows). */
export function parseServiceAccount(raw: string | undefined | null): ServiceAccount | null {
  if (!raw || !raw.trim()) return null;
  const text = raw.trim().startsWith("{") ? raw.trim() : new TextDecoder().decode(base64ToBytes(raw.trim()));
  const sa = JSON.parse(text) as ServiceAccount;
  if (!sa.project_id || !sa.client_email || !sa.private_key) throw new Error("service account JSON is missing fields");
  return sa;
}

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64.replace(/\s+/g, ""));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

function base64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** RS256-signed JWT that Google exchanges for an OAuth access token. */
export async function signServiceAccountJwt(sa: ServiceAccount, nowSec: number): Promise<string> {
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: sa.client_email,
    scope: SCOPE,
    aud: sa.token_uri ?? DEFAULT_TOKEN_URI,
    iat: nowSec,
    exp: nowSec + 3600,
  }));
  const pem = sa.private_key.replace(/\\n/g, "\n");
  const der = base64ToBytes(pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, ""));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  return `${header}.${claims}.${base64url(new Uint8Array(sig))}`;
}

/** Caches the OAuth token across requests on a warm instance; refreshes 5 minutes early. */
export class AccessTokenCache {
  private token: string | null = null;
  private expiresAtMs = 0;

  clear() { this.token = null; this.expiresAtMs = 0; }

  async get(deps: PushDeps): Promise<string> {
    const sa = deps.serviceAccount!;
    if (this.token && deps.nowMs() < this.expiresAtMs - 5 * 60_000) return this.token;
    const jwt = await signServiceAccountJwt(sa, Math.floor(deps.nowMs() / 1000));
    const res = await deps.fetch(sa.token_uri ?? DEFAULT_TOKEN_URI, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${jwt}`,
    });
    if (!res.ok) throw new Error(`token exchange failed: ${res.status} ${await res.text()}`);
    const body = await res.json() as { access_token: string; expires_in: number };
    this.token = body.access_token;
    this.expiresAtMs = deps.nowMs() + (body.expires_in ?? 3600) * 1000;
    return this.token;
  }
}

/** FCM data values must be strings. */
function stringData(data: Record<string, unknown> | null | undefined, n: NotificationRecord): Record<string, string> {
  const out: Record<string, string> = { notification_id: n.id };
  for (const [k, v] of Object.entries(data ?? {})) if (v !== null && v !== undefined) out[k] = String(v);
  return out;
}

type SendResult = "sent" | "dead" | "failed" | "auth";

async function sendOne(deps: PushDeps, accessToken: string, token: string, n: NotificationRecord): Promise<SendResult> {
  const sa = deps.serviceAccount!;
  const res = await deps.fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: n.title, body: n.body },
        data: stringData(n.data, n),
        android: { priority: "HIGH", notification: { channel_id: "updates" } },
      },
    }),
  });
  if (res.ok) return "sent";
  const text = await res.text();
  if (res.status === 401) return "auth";
  // The token is gone (app uninstalled, data cleared) or was never valid.
  if (res.status === 404 || text.includes("UNREGISTERED") ||
      (res.status === 400 && /registration token/i.test(text))) return "dead";
  deps.log?.("FCM send failed", { status: res.status, text: text.slice(0, 300) });
  return "failed";
}

export async function handlePushWebhook(req: Request, deps: PushDeps, cache: AccessTokenCache): Promise<Response> {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!deps.webhookSecret || req.headers.get("x-webhook-secret") !== deps.webhookSecret) {
    return json({ error: "unauthorized" }, 401);
  }

  let payload: { type?: string; table?: string; record?: NotificationRecord };
  try {
    payload = await req.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }
  const n = payload.record;
  if (payload.type !== "INSERT" || payload.table !== "notifications" || !n?.id || !n.user_id) {
    return json({ skipped: "not_a_notification_insert" });
  }
  // 200 so the webhook doesn't retry; the notification row still exists for an in-app list.
  if (!deps.serviceAccount) {
    return json({ skipped: "push_not_configured", reason: deps.configError ?? "FCM_SERVICE_ACCOUNT is not set" });
  }

  const tokens = await deps.tokensFor(n.user_id);
  if (tokens.length === 0) {
    await deps.markSent(n.id);
    return json({ sent: 0, failed: 0, removed: 0, reason: "no_devices" });
  }

  let access = await cache.get(deps);
  const results: SendResult[] = [];
  for (const token of tokens) {
    let r = await sendOne(deps, access, token, n);
    if (r === "auth") {                 // token expired early or was revoked: refresh once
      cache.clear();
      access = await cache.get(deps);
      r = await sendOne(deps, access, token, n);
      if (r === "auth") r = "failed";
    }
    results.push(r);
  }

  const dead = tokens.filter((_, i) => results[i] === "dead");
  if (dead.length) await deps.deleteTokens(dead);
  const sent = results.filter((r) => r === "sent").length;
  if (sent > 0) await deps.markSent(n.id);

  return json({ sent, failed: results.filter((r) => r === "failed").length, removed: dead.length });
}
