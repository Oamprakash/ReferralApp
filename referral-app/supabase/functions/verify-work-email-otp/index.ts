import { handleVerifyOtp } from "../_shared/otp.ts";
import { makeDeps } from "../_shared/deps.ts";

const deps = makeDeps();
Deno.serve((req) => handleVerifyOtp(req, deps));
