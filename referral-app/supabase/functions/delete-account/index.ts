import { createClient } from "npm:@supabase/supabase-js@2";
import { handleDeleteAccount } from "../_shared/account.ts";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

Deno.serve((req) =>
  handleDeleteAccount(req, {
    log: (msg, extra) => console.error(msg, extra ?? ""),
    async userIdFromRequest(r) {
      const token = r.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
      if (!token) return null;
      const { data, error } = await admin.auth.getUser(token);
      return error || !data.user ? null : data.user.id;
    },
    async rpc(fn, args) {
      const { data, error } = await admin.rpc(fn, args);
      if (error) throw error;
      return data as Record<string, unknown>;
    },
    async removeResume(path) {
      const { error } = await admin.storage.from("resumes").remove([path]);
      if (error) throw error;
    },
    async deleteAuthUser(userId) {
      const { error } = await admin.auth.admin.deleteUser(userId);
      if (error) throw error;
    },
  })
);
