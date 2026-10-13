// Account deletion handler. Web-standard APIs only, so it runs on Supabase Edge and in Node tests.
//
// Order matters: (1) tell applicants to this user's posts while the data still exists,
// (2) remove the resume file, (3) delete the auth user, which cascades every table row.

export interface AccountDeps {
  userIdFromRequest(req: Request): Promise<string | null>;
  rpc(fn: string, args: Record<string, unknown>): Promise<Record<string, unknown>>;
  removeResume(path: string): Promise<void>;
  deleteAuthUser(userId: string): Promise<void>;
  log?: (msg: string, extra?: unknown) => void;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

/** POST {confirm: "DELETE"} with the user's bearer token. */
export async function handleDeleteAccount(req: Request, deps: AccountDeps): Promise<Response> {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const userId = await deps.userIdFromRequest(req);
  if (!userId) return json({ error: "unauthorized" }, 401);

  let body: { confirm?: unknown } = {};
  try {
    body = await req.json();
  } catch { /* handled below */ }
  if (body?.confirm !== "DELETE") return json({ error: "confirmation_required" }, 400);

  try {
    const prep = await deps.rpc("prepare_account_deletion", { p_user: userId });
    const resume = typeof prep?.resume_path === "string" ? prep.resume_path : null;
    if (resume) {
      // A missing file must not block deletion
      try { await deps.removeResume(resume); } catch (e) { deps.log?.("resume removal failed", e); }
    }
    await deps.deleteAuthUser(userId);
    return json({ status: "deleted" });
  } catch (e) {
    deps.log?.("delete-account failed", e);
    return json({ error: "server_error" }, 500);
  }
}
