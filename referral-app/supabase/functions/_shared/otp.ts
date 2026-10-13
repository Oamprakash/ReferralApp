// Work-email OTP handlers. Plain TypeScript with web-standard APIs only
// (Request, Response, crypto.subtle), so they run on Supabase Edge (Deno)
// and in Node tests. Deno-specific wiring lives in deps.ts.

export interface Deps {
  /** Resolves the signed-in user's id from the request's bearer token, or null. */
  userIdFromRequest(req: Request): Promise<string | null>;
  /** Calls a Postgres function as the service role and returns its JSON result. */
  rpc(fn: string, args: Record<string, unknown>): Promise<Record<string, unknown>>;
  /** Sends an email; resolves false on failure. */
  sendEmail(msg: { to: string; subject: string; text: string; html: string }): Promise<boolean>;
  /** Server-side secret for HMAC-ing codes (OTP_HMAC_SECRET). */
  hmacSecret: string;
  /** When true, no email is sent and the code is returned as dev_code. Never enable in production. */
  devMode: boolean;
  log?: (msg: string, extra?: unknown) => void;
}

export const CODE_LENGTH = 6;

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

/** Uniform 6-digit code from a CSPRNG (rejection sampling avoids modulo bias). */
export function generateCode(): string {
  const limit = Math.floor(0x1_0000_0000 / 1_000_000) * 1_000_000;
  const buf = new Uint32Array(1);
  do {
    crypto.getRandomValues(buf);
  } while (buf[0] >= limit);
  return String(buf[0] % 1_000_000).padStart(CODE_LENGTH, "0");
}

/** HMAC-SHA256(secret, "<userId>:<code>") as hex. Binding the user stops cross-account reuse. */
export async function hashCode(secret: string, userId: string, code: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${userId}:${code}`));
  return Array.from(new Uint8Array(sig), (b) => b.toString(16).padStart(2, "0")).join("");
}

export function otpEmail(code: string, companyName: string) {
  const subject = `${code} is your verification code`;
  const text =
    `Your code to verify your ${companyName} work email is ${code}.\n\n` +
    `It expires in 10 minutes. If you didn't ask for this, ignore this email.\n` +
    `We never ask for money. Report anyone who does.`;
  const html =
    `<p>Your code to verify your <b>${escapeHtml(companyName)}</b> work email is:</p>` +
    `<p style="font-size:28px;font-weight:700;letter-spacing:6px">${code}</p>` +
    `<p>It expires in 10 minutes. If you didn't ask for this, ignore this email.</p>` +
    `<p style="color:#666">We never ask for money. Report anyone who does.</p>`;
  return { subject, text, html };
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!
  );
}

async function readJson(req: Request): Promise<Record<string, unknown> | null> {
  try {
    const body = await req.json();
    return body && typeof body === "object" ? (body as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

async function guard(req: Request, deps: Deps): Promise<string | Response> {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!deps.hmacSecret || deps.hmacSecret.length < 32) {
    deps.log?.("OTP_HMAC_SECRET missing or shorter than 32 characters");
    return json({ error: "server_misconfigured" }, 500);
  }
  const userId = await deps.userIdFromRequest(req);
  if (!userId) return json({ error: "unauthorized" }, 401);
  return userId;
}

/**
 * POST {email}. Business outcomes are HTTP 200 with a status field
 * (sent, invalid_email, free_domain, pending_review, cooldown, rate_limited,
 * email_in_use, send_failed); only real failures use 4xx/5xx.
 */
export async function handleRequestOtp(req: Request, deps: Deps): Promise<Response> {
  const userId = await guard(req, deps);
  if (userId instanceof Response) return userId;

  const body = await readJson(req);
  const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
  if (!email || email.length > 254) return json({ status: "invalid_email" });

  const code = generateCode();
  let result: Record<string, unknown>;
  try {
    result = await deps.rpc("request_work_email_otp", {
      p_user: userId,
      p_email: email,
      p_code_hash: await hashCode(deps.hmacSecret, userId, code),
    });
  } catch (e) {
    deps.log?.("request_work_email_otp failed", e);
    return json({ error: "server_error" }, 500);
  }

  if (result.status !== "sent") return json(result);

  if (deps.devMode) {
    deps.log?.("OTP_DEV_MODE is on: code returned in response, no email sent");
    return json({ ...result, dev_code: code });
  }

  const msg = otpEmail(code, String(result.company_name ?? "your company"));
  const ok = await deps.sendEmail({ to: email, ...msg });
  if (!ok) return json({ status: "send_failed" });
  return json(result);
}

/** POST {code}. Statuses: verified, invalid_code, too_many_attempts, no_pending_code, email_in_use. */
export async function handleVerifyOtp(req: Request, deps: Deps): Promise<Response> {
  const userId = await guard(req, deps);
  if (userId instanceof Response) return userId;

  const body = await readJson(req);
  const code = typeof body?.code === "string" ? body.code.replace(/\D/g, "") : "";
  if (code.length !== CODE_LENGTH) return json({ status: "invalid_format" });

  try {
    const result = await deps.rpc("verify_work_email_otp", {
      p_user: userId,
      p_code_hash: await hashCode(deps.hmacSecret, userId, code),
    });
    return json(result);
  } catch (e) {
    deps.log?.("verify_work_email_otp failed", e);
    return json({ error: "server_error" }, 500);
  }
}
