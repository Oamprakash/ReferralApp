// Integration tests for the Edge Function handlers against a real Postgres
// with both migrations applied. Email sending is faked; everything else is real.
//
// Run: TEST_DB_URL=postgres://postgres@localhost:5432/dbname node --test tests/otp_functions.test.ts
// (Node 22.18+ runs TypeScript directly; run_tests.sh sets this up.)
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHmac } from "node:crypto";
import { type Deps, generateCode, handleRequestOtp, handleVerifyOtp, hashCode } from "../functions/_shared/otp.ts";

const SECRET = "test-secret-that-is-at-least-32-characters-long";

/** Runs SQL through psql; variables are passed with -v and referenced as :'name'. */
function sql(query: string, vars: Record<string, string> = {}): string {
  const args = [...(process.env.TEST_DB_URL ? [process.env.TEST_DB_URL] : []), "-X", "-At", "-v", "ON_ERROR_STOP=1"];
  for (const [k, v] of Object.entries(vars)) args.push("-v", `${k}=${v}`);
  return execFileSync("psql", args, { input: query, encoding: "utf8" }).trim();
}

function makeDeps(over: Partial<Deps> & { tokens?: Record<string, string> } = {}) {
  const sent: { to: string; subject: string; text: string }[] = [];
  const tokens = over.tokens ?? {};
  const deps: Deps = {
    hmacSecret: SECRET,
    devMode: false,
    async userIdFromRequest(req) {
      const t = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
      return t ? tokens[t] ?? null : null;
    },
    async rpc(fn, args) {
      const keys = Object.keys(args);
      const call = `select public.${fn}(${keys.map((k) => `${k} => :'${k}'`).join(", ")})::text;`;
      const vars = Object.fromEntries(keys.map((k) => [k, String(args[k])]));
      return JSON.parse(sql(call, vars));
    },
    async sendEmail(msg) {
      sent.push(msg);
      return true;
    },
    ...over,
  };
  return { deps, sent };
}

function post(path: string, body: unknown, token?: string): Request {
  return new Request(`http://local/${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
}

const U1 = "bbbb0000-0000-0000-0000-000000000001";
const U2 = "bbbb0000-0000-0000-0000-000000000002";
const U3 = "bbbb0000-0000-0000-0000-000000000003";
const tokens = { t1: U1, t2: U2, t3: U3 };

sql(`
  insert into auth.users (id) values ('${U1}'), ('${U2}'), ('${U3}');
  insert into companies (name, domains, status) values ('Movate', '{movate.com}', 'approved');
`);

test("generateCode gives uniform-looking 6-digit strings", () => {
  const codes = Array.from({ length: 2000 }, generateCode);
  assert.ok(codes.every((c) => /^\d{6}$/.test(c)));
  assert.ok(new Set(codes).size > 1990, "codes should almost never repeat");
});

test("hashCode matches Node's HMAC-SHA256 and is bound to the user", async () => {
  const expected = createHmac("sha256", SECRET).update(`${U1}:123456`).digest("hex");
  assert.equal(await hashCode(SECRET, U1, "123456"), expected);
  assert.notEqual(await hashCode(SECRET, U2, "123456"), expected);
});

test("request: rejects wrong method, missing auth and a weak secret", async () => {
  const { deps } = makeDeps({ tokens });
  assert.equal((await handleRequestOtp(new Request("http://local/x"), deps)).status, 405);
  assert.equal((await handleRequestOtp(post("x", { email: "a@movate.com" }), deps)).status, 401);
  assert.equal((await handleRequestOtp(post("x", { email: "a@movate.com" }, "bad-token"), deps)).status, 401);
  const weak = makeDeps({ tokens, hmacSecret: "short" }).deps;
  assert.equal((await handleRequestOtp(post("x", { email: "a@movate.com" }, "t1"), weak)).status, 500);
});

test("request: business outcomes come back as statuses", async () => {
  const { deps, sent } = makeDeps({ tokens });
  const status = async (email: unknown) =>
    (await (await handleRequestOtp(post("x", { email }, "t1"), deps)).json()).status;
  assert.equal(await status(123), "invalid_email");
  assert.equal(await status("nope"), "invalid_email");
  assert.equal(await status("oam@gmail.com"), "free_domain");
  assert.equal(await status("oam@someunknownco.in"), "pending_review");
  assert.equal(sent.length, 0, "no email for rejected requests");
});

test("request + verify: full flow with the emailed code", async () => {
  const { deps, sent } = makeDeps({ tokens });

  const res = await handleRequestOtp(post("x", { email: " Oam@Movate.com " }, "t1"), deps);
  const body = await res.json();
  assert.equal(body.status, "sent");
  assert.equal(body.company_name, "Movate");
  assert.equal(body.dev_code, undefined, "code must not leak outside dev mode");
  assert.equal(sent.length, 1);
  assert.equal(sent[0].to, "oam@movate.com");
  const code = sent[0].text.match(/\b(\d{6})\b/)![1];
  assert.ok(!sql("select code_hash from work_email_otps").includes(code), "code itself is not stored");

  // Second request inside 60 s is refused
  const again = await (await handleRequestOtp(post("x", { email: "oam@movate.com" }, "t1"), deps)).json();
  assert.equal(again.status, "cooldown");

  const wrongCode = code === "000000" ? "111111" : "000000";
  assert.equal((await (await handleVerifyOtp(post("v", { code: "12ab" }, "t1"), deps)).json()).status, "invalid_format");
  const wrong = await (await handleVerifyOtp(post("v", { code: wrongCode }, "t1"), deps)).json();
  assert.deepEqual(wrong, { status: "invalid_code", attempts_left: 4 });

  // Another user can't use U1's code (hash is bound to the user)
  assert.equal((await (await handleVerifyOtp(post("v", { code }, "t2"), deps)).json()).status, "no_pending_code");

  const ok = await (await handleVerifyOtp(post("v", { code: ` ${code.slice(0, 3)} ${code.slice(3)} ` }, "t1"), deps)).json();
  assert.equal(ok.status, "verified");
  assert.equal(ok.company_name, "Movate");
  assert.equal(sql(`select work_email from referrer_verifications where user_id = '${U1}'`), "oam@movate.com");
});

test("dev mode returns the code and sends no email", async () => {
  const { deps, sent } = makeDeps({ tokens, devMode: true });
  const body = await (await handleRequestOtp(post("x", { email: "dev@movate.com" }, "t2"), deps)).json();
  assert.equal(body.status, "sent");
  assert.match(body.dev_code, /^\d{6}$/);
  assert.equal(sent.length, 0);
  const ok = await (await handleVerifyOtp(post("v", { code: body.dev_code }, "t2"), deps)).json();
  assert.equal(ok.status, "verified");
});

test("email failure is reported as send_failed", async () => {
  const { deps } = makeDeps({ tokens, sendEmail: async () => false });
  const body = await (await handleRequestOtp(post("x", { email: "fail@movate.com" }, "t3"), deps)).json();
  assert.equal(body.status, "send_failed");
});

test("database errors become a 500 without leaking details", async () => {
  const { deps } = makeDeps({ tokens, rpc: async () => { throw new Error("db down"); } });
  sql(`update work_email_otps set created_at = now() - interval '2 hours'`);
  const res = await handleRequestOtp(post("x", { email: "x@movate.com" }, "t3"), deps);
  assert.equal(res.status, 500);
  assert.deepEqual(await res.json(), { error: "server_error" });
});
