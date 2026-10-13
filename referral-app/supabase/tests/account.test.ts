// Account deletion against a real Postgres with all migrations: checks every table is cleaned up.
// Run: TEST_DB_URL=postgres://postgres@localhost:5432/dbname node --test tests/account.test.ts
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { type AccountDeps, handleDeleteAccount } from "../functions/_shared/account.ts";

function sql(query: string, vars: Record<string, string> = {}): string {
  const args = [...(process.env.TEST_DB_URL ? [process.env.TEST_DB_URL] : []), "-X", "-At", "-v", "ON_ERROR_STOP=1"];
  for (const [k, v] of Object.entries(vars)) args.push("-v", `${k}=${v}`);
  return execFileSync("psql", args, { input: query, encoding: "utf8" }).trim();
}

const R = "d1000000-0000-0000-0000-00000000000a";   // referrer being deleted (also applied somewhere)
const S = "d1000000-0000-0000-0000-000000000001";   // seeker who applied to R's post
const O = "d1000000-0000-0000-0000-000000000002";   // other referrer whose post R applied to
const CO = "d2000000-0000-0000-0000-000000000001";

sql(`
  insert into auth.users (id) values ('${R}'), ('${S}'), ('${O}');
  update profiles set full_name = 'Priya', experience_years = 5, resume_path = '${R}/resume.pdf' where id = '${R}';
  update profiles set full_name = 'Seeker', experience_years = 3, resume_path = '${S}/resume.pdf' where id = '${S}';
  update profiles set full_name = 'Other', experience_years = 9 where id = '${O}';
  insert into companies (id, name, domains, status) values ('${CO}', 'Movate', '{movate.com}', 'approved');
  insert into referrer_verifications (user_id, company_id, work_email) values
    ('${R}', '${CO}', 'priya@movate.com'), ('${O}', '${CO}', 'other@movate.com');
  insert into jobs (id, company_id, referrer_id, title, location, exp_min, exp_max, work_mode, job_ref) values
    ('d3000000-0000-0000-0000-000000000001', '${CO}', '${R}', 'Priya post', 'Chennai', 1, 5, 'remote', 'A'),
    ('d3000000-0000-0000-0000-000000000002', '${CO}', '${O}', 'Other post', 'Chennai', 1, 9, 'remote', 'B');
  insert into applications (job_id, seeker_id) values
    ('d3000000-0000-0000-0000-000000000001', '${S}'),
    ('d3000000-0000-0000-0000-000000000002', '${R}');
  insert into device_tokens (token, user_id) values ('tok_rrrrrrrrrrrrrrrrrrrrrrrrrrrrrr', '${R}');
  insert into domain_requests (domain, first_requested_by) values ('someco.in', '${R}');
  insert into admins (user_id) values ('${R}');
`);
// A report by R and a work-email code for R
sql(`
  alter table reports disable trigger reports_validate;
  insert into reports (reporter_id, target_type, target_id, reason) values ('${R}', 'job', 'd3000000-0000-0000-0000-000000000002', 'other');
  alter table reports enable trigger reports_validate;
  insert into work_email_otps (user_id, company_id, email, code_hash) values ('${R}', '${CO}', 'priya@movate.com', 'h');
`);

function deps(over: Partial<AccountDeps> = {}) {
  const removed: string[] = [];
  const d: AccountDeps = {
    userIdFromRequest: async (req) => (req.headers.get("Authorization") === "Bearer r" ? R : null),
    rpc: async (fn, args) => JSON.parse(sql(`select public.${fn}(:'u')::text;`, { u: String(args.p_user) })),
    removeResume: async (p) => { removed.push(p); },
    deleteAuthUser: async (uid) => { sql(`delete from auth.users where id = :'u';`, { u: uid }); },
    ...over,
  };
  return { d, removed };
}

const req = (body: unknown, auth = "Bearer r") =>
  new Request("http://local/delete-account", {
    method: "POST", headers: { "content-type": "application/json", Authorization: auth }, body: JSON.stringify(body),
  });

test("refuses without auth or without the typed confirmation", async () => {
  const { d } = deps();
  assert.equal((await handleDeleteAccount(req({ confirm: "DELETE" }, "Bearer nope"), d)).status, 401);
  assert.equal((await handleDeleteAccount(req({}), d)).status, 400);
  assert.equal((await handleDeleteAccount(req({ confirm: "delete" }), d)).status, 400);
  assert.equal(sql(`select count(*) from profiles where id = '${R}'`), "1", "nothing deleted yet");
});

test("a failing auth deletion reports an error and keeps the account", async () => {
  const { d } = deps({ deleteAuthUser: async () => { throw new Error("auth down"); } });
  const res = await handleDeleteAccount(req({ confirm: "DELETE" }), d);
  assert.equal(res.status, 500);
  assert.equal(sql(`select count(*) from profiles where id = '${R}'`), "1");
  sql(`delete from notifications where kind = 'post_closed'`);   // reset the side effect for the next test
});

test("deletes everything about the user and tells their applicants", async () => {
  const { d, removed } = deps();
  const res = await handleDeleteAccount(req({ confirm: "DELETE" }), d);
  assert.deepEqual(await res.json(), { status: "deleted" });
  assert.deepEqual(removed, [`${R}/resume.pdf`], "resume file removed");

  const left = sql(`
    select json_build_object(
      'auth', (select count(*) from auth.users where id = '${R}'),
      'profile', (select count(*) from profiles where id = '${R}'),
      'jobs', (select count(*) from jobs where referrer_id = '${R}'),
      'applications_as_seeker', (select count(*) from applications where seeker_id = '${R}'),
      'applications_to_their_jobs', (select count(*) from applications where job_id = 'd3000000-0000-0000-0000-000000000001'),
      'verifications', (select count(*) from referrer_verifications where user_id = '${R}'),
      'device_tokens', (select count(*) from device_tokens where user_id = '${R}'),
      'notifications', (select count(*) from notifications where user_id = '${R}'),
      'reports_filed', (select count(*) from reports where reporter_id = '${R}'),
      'otps', (select count(*) from work_email_otps where user_id = '${R}'),
      'admin', (select count(*) from admins where user_id = '${R}'),
      'domain_request_kept_anonymised', (select first_requested_by is null from domain_requests where domain = 'someco.in')
    )`);
  assert.deepEqual(JSON.parse(left), {
    auth: 0, profile: 0, jobs: 0, applications_as_seeker: 0, applications_to_their_jobs: 0, verifications: 0,
    device_tokens: 0, notifications: 0, reports_filed: 0, otps: 0, admin: 0, domain_request_kept_anonymised: true,
  });

  // The seeker who applied to Priya's post was told, and their own account is untouched
  assert.equal(sql(`select count(*) from notifications where user_id = '${S}' and kind = 'post_closed'`), "1");
  assert.equal(sql(`select count(*) from profiles where id in ('${S}', '${O}')`), "2");
  assert.equal(sql(`select count(*) from jobs where referrer_id = '${O}'`), "1");
});

test("a resume that's already gone doesn't block deletion", async () => {
  sql(`insert into auth.users (id) values ('d1000000-0000-0000-0000-0000000000ee');
       update profiles set resume_path = 'x/resume.pdf' where id = 'd1000000-0000-0000-0000-0000000000ee';`);
  const { d } = deps({
    userIdFromRequest: async () => "d1000000-0000-0000-0000-0000000000ee",
    removeResume: async () => { throw new Error("Object not found"); },
  });
  const res = await handleDeleteAccount(req({ confirm: "DELETE" }), d);
  assert.deepEqual(await res.json(), { status: "deleted" });
  assert.equal(sql(`select count(*) from profiles where id = 'd1000000-0000-0000-0000-0000000000ee'`), "0");
});
