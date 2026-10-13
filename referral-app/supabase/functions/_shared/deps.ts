// Deno / Supabase Edge wiring for the OTP handlers.
import { createClient } from "npm:@supabase/supabase-js@2";
import type { Deps } from "./otp.ts";

export function makeDeps(): Deps {
  const url = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;   // injected by Supabase
  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const resendKey = Deno.env.get("RESEND_API_KEY") ?? "";
  const from = Deno.env.get("EMAIL_FROM") ?? "";

  return {
    hmacSecret: Deno.env.get("OTP_HMAC_SECRET") ?? "",
    devMode: Deno.env.get("OTP_DEV_MODE") === "true",
    log: (msg, extra) => console.error(msg, extra ?? ""),

    async userIdFromRequest(req) {
      const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
      if (!token) return null;
      const { data, error } = await admin.auth.getUser(token);
      return error || !data.user ? null : data.user.id;
    },

    async rpc(fn, args) {
      const { data, error } = await admin.rpc(fn, args);
      if (error) throw error;
      return data as Record<string, unknown>;
    },

    async sendEmail({ to, subject, text, html }) {
      if (!resendKey || !from) {
        console.error("RESEND_API_KEY or EMAIL_FROM not set; cannot send email");
        return false;
      }
      const res = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { Authorization: `Bearer ${resendKey}`, "Content-Type": "application/json" },
        body: JSON.stringify({ from, to: [to], subject, text, html }),
      });
      if (!res.ok) console.error("Resend error", res.status, await res.text());
      return res.ok;
    },
  };
}
