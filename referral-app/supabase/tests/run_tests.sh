#!/usr/bin/env bash
# Runs all backend tests against a local Postgres (15 or 16). Each suite gets a fresh database.
#   1. policy_tests.sql        Sprint 1 RLS and limits (prints expected ERROR lines)
#   2. verification_tests.sql  Sprint 2 work-email rules (prints expected values)
#   3. apply_tests.sql         Sprint 3 apply, waitlist, withdraw, job detail (prints expected values)
#   4. review_tests.sql        Sprint 4 match score, status order, notifications, tokens, stats
#   4b. reapply_tests.sql      reapply once, back of the queue, notifications
#   4c. push_trigger_tests.sql  database -> send-push call (pg_net/Vault stand-ins)
#   4d. trust_safety_tests.sql  Sprint 5 maintenance, reports, moderation, bans, admin, export
#   4e. resume_match_tests.sql  contact privacy, applicant_contact after shortlist, best_matches, export
#   4f. job_description_tests.sql  description tidy-up, length limit, job detail
#   4g. grants_tests.sql       every public table has explicit grants (no Supabase defaults)
#   5. otp_functions.test.ts   Edge Function handlers against the real SQL (pass/fail; needs Node 22.18+)
#   6. push.test.ts            send-push logic: FCM auth, sending, dead-token cleanup (pass/fail)
#   7. account.test.ts         delete-account against the real schema: every cascade checked (pass/fail)
#
# Usage: PGURL=postgres://postgres@localhost:5432 ./run_tests.sh
set -euo pipefail
cd "$(dirname "$0")"
PGURL=${PGURL:-postgres://postgres@localhost:5432}

psql "$PGURL/postgres" -qc "do \$\$ begin
  if not exists (select from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists (select from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
  if not exists (select from pg_roles where rolname='service_role') then create role service_role nologin bypassrls; end if;
end \$\$"

fresh_db() {
  psql "$PGURL/postgres" -qc "drop database if exists $1" -c "create database $1"
  psql "$PGURL/$1" -v ON_ERROR_STOP=1 -qf 00_supabase_stub.sql
  # One transaction per file, like the CLI. pg_net isn't installable on plain Postgres; the stub provides net.http_post.
  for m in ../migrations/*.sql; do
    sed '/create extension if not exists pg_net/d' "$m" | psql "$PGURL/$1" -v ON_ERROR_STOP=1 -1 -q
  done
}

echo "=== 1. Sprint 1 policy tests"
fresh_db referral_test_policy
psql "$PGURL/referral_test_policy" -qf policy_tests.sql || true

echo; echo "=== 2. Sprint 2 verification tests"
fresh_db referral_test_verify
psql "$PGURL/referral_test_verify" -qf verification_tests.sql || true

echo; echo "=== 3. Sprint 3 apply tests"
fresh_db referral_test_apply
psql "$PGURL/referral_test_apply" -qf apply_tests.sql || true

echo; echo "=== 4. Sprint 4 review tests"
fresh_db referral_test_review
psql "$PGURL/referral_test_review" -qf review_tests.sql || true

echo; echo "=== 4b. Reapply tests"
fresh_db referral_test_reapply
psql "$PGURL/referral_test_reapply" -qf reapply_tests.sql || true

echo; echo "=== 4c. Push trigger tests"
fresh_db referral_test_push
psql "$PGURL/referral_test_push" -qf push_trigger_tests.sql || true

echo; echo "=== 4d. Trust and safety tests"
fresh_db referral_test_safety
psql "$PGURL/referral_test_safety" -qf trust_safety_tests.sql || true

echo; echo "=== 4e. Resume match tests"
fresh_db referral_test_match
psql "$PGURL/referral_test_match" -qf resume_match_tests.sql || true

echo; echo "=== 4f. Job description tests"
fresh_db referral_test_desc
psql "$PGURL/referral_test_desc" -qf job_description_tests.sql || true

echo; echo "=== 4g. Grant tests"
fresh_db referral_test_grants
psql "$PGURL/referral_test_grants" -qf grants_tests.sql || true

echo; echo "=== 5. Edge Function handler tests"
fresh_db referral_test_otp
TEST_DB_URL="$PGURL/referral_test_otp" node --test otp_functions.test.ts

echo; echo "=== 6. Push function tests"
node --test push.test.ts

echo; echo "=== 7. Account deletion tests"
fresh_db referral_test_account
TEST_DB_URL="$PGURL/referral_test_account" node --test account.test.ts
