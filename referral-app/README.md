# Referral Jobs App — Sprints 1–4

> **Releasing?** Follow [RELEASE.md](RELEASE.md): signing, Supabase production settings, privacy pages, Play Console.


Every job is posted by a verified employee and re-confirmed open every 14 days. This repo holds the Supabase backend and the Android app.

## What's in Sprint 1

| Area | Done |
| --- | --- |
| Database | All MVP tables (`profiles`, `companies`, `referrer_verifications`, `jobs`, `applications`, `reports`), enums, triggers |
| Rules in the database | Max 5 active posts per referrer · max 10 applications per seeker per week · applicant cap with automatic waitlist · official link or job ID required · expired verification can't keep posts active |
| Security | Row-level security on every table · private `resumes` bucket · referrers can read only their own applicants' profiles and resumes · seekers can't change their application status · `priority_until` is system-only |
| Android | Compose + Hilt + type-safe navigation · Supabase client · Google sign-in · profile form · PDF resume upload |

`profiles` is the spec's `users` table, renamed so it doesn't clash with Supabase's `auth.users`. Phone and email stay in `auth.users`, so referrers never see them through `profiles`.

## Project layout

```
supabase/
  migrations/20261008000000_init.sql   schema, triggers, RLS, storage bucket + policies
  migrations/20261009000000_work_email_verification.sql   OTP rules, allowlist checks
  migrations/20261010000000_apply_flow.sql                 apply rules, withdraw + waitlist, job detail
  migrations/20261011000000_review_loop.sql                match score, status order, notifications, stats
  migrations/20261012000000_reapply.sql                    reapply once after withdrawing
  migrations/20261013000000_push_trigger.sql               notifications -> send-push via pg_net
  functions/                           Edge Functions (work-email OTP) + shared handlers
  seed/                                test jobs, company allowlist
  tests/                               policy + verification SQL tests, handler tests, runner
android/
  app/src/main/java/com/referral/app/
    di/SupabaseModule.kt               Supabase client (Auth, Postgrest, Storage)
    data/                              AuthRepository, ProfileRepository, models
    ui/navigation/                     RootViewModel decides sign-in / profile / home
    ui/auth/                           Google sign-in (Credential Manager, nonce)
    ui/profile/                        profile form + resume picker/upload
    ui/home/                           placeholder until the Sprint 3 feed
```

## Setup

### 1. Supabase

1. Create a project at supabase.com (pick the Mumbai region for India latency).
2. Apply the migration: either `supabase link` then `supabase db push`, or paste `supabase/migrations/20261008000000_init.sql` into the SQL editor and run it.
3. **Auth → Providers → Phone:** enable it and connect an SMS provider. Indian numbers need a DLT-registered sender ID and template with your provider, so start that registration early; it can take days. For development, add test phone numbers with fixed OTPs under Phone settings.
4. **Auth → Providers → Google:** enable it and paste the Web client ID and secret (step 2).

### 2. Google Cloud (for "Continue with Google")

1. Create an OAuth **Web application** client. Its ID goes into Supabase and into `GOOGLE_WEB_CLIENT_ID`.
2. Create an OAuth **Android** client with package `com.referral.app` and your debug keystore's SHA-1 (`./gradlew signingReport`). The app doesn't use this ID directly, but Google needs it registered.

### 3. Android

1. Open the `android/` folder in Android Studio. If it asks to create a Gradle wrapper, accept (or run `gradle wrapper` once).
2. Copy the keys from `local.properties.example` into `android/local.properties`.
3. Run on a device or emulator with Google Play services.
4. Library versions are a pinned, known-compatible set; use Android Studio's dependency upgrade suggestions to bump them together.

To use your shared Compose design system, add it as a module and replace `ui/theme/Theme.kt`.

## Running the database tests

Needs a local Postgres (15 or 16) and `psql`:

```bash
PGURL=postgres://postgres@localhost:5432 supabase/tests/run_tests.sh
```

Each SQL test prints what it expects. In suite 1, nine lines should be `ERROR`s: the rules correctly rejecting bad writes. In suite 2, four `ERROR`s are expected (a duplicate domain and three blocked direct calls). In suite 3, five (three invalid answer sets, a duplicate apply, the 11th application in a week). In suite 4, six (four blocked status jumps, two blocked direct writes). In suite 4b, one (a blank answer on reapply). Suites 5 and 6 need Node 22.18 or newer and should each end with `# fail 0`.

## Sprint 2: Verify + post

| Area | Done |
| --- | --- |
| Verification rules (SQL) | Company allowlist with subdomain matching · free and disposable domains blocked · unknown domains queued in `domain_requests` · 6-digit codes valid 10 min, 5 tries · 60 s cooldown, 5 codes/user/hour, 3 codes/address/hour · one work email per account · moving company closes old posts |
| Edge Functions | `request-work-email-otp`, `verify-work-email-otp`: codes are HMAC-hashed with a server secret and never stored or logged; email via Resend; dev mode for testing without email |
| Android | Verify work email · Post a job (form mirrors database checks) · My posts (Still hiring / Close, 5-active limit) · feed refreshes when you come back |
| Allowlist | `seed/seed_company_allowlist.sql`: 15 Chennai companies |

### Sprint 2 setup

You need the Supabase CLI for the Edge Functions. Install it with Scoop on Windows (`scoop bucket add supabase https://github.com/supabase/scoop-bucket.git`, then `scoop install supabase`) or Homebrew on macOS (`brew install supabase/tap/supabase`), or run it through `npx supabase@latest`. A global npm install is not supported. Then, **from the repo root** (the folder containing `supabase/`), run `supabase init` once to create `supabase/config.toml`, followed by `supabase login` and `supabase link --project-ref <ref>`.

1. **Apply the migration.** `supabase db push`, or paste `migrations/20261009000000_work_email_verification.sql` into the SQL Editor.
   If you applied Sprint 1 through the SQL Editor and now switch to `db push`, first run
   `supabase migration repair --status applied 20261008000000` so the CLI doesn't re-run it.
2. **Load the allowlist.** Run `seed/seed_company_allowlist.sql` in the SQL Editor. Confirm each domain with someone who works there before launch.
3. **Set secrets.** For development, use dev mode, where the code comes back in the app instead of by email:
   ```bash
   supabase secrets set OTP_HMAC_SECRET=$(openssl rand -hex 32) OTP_DEV_MODE=true
   ```
   On Windows PowerShell (no `openssl`):
   ```powershell
   $bytes = New-Object byte[] 32
   [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
   $secret = -join ($bytes | ForEach-Object { $_.ToString("x2") })
   supabase secrets set "OTP_HMAC_SECRET=$secret" "OTP_DEV_MODE=true"
   ```
4. **Deploy the functions.** They check the user's token themselves, so gateway JWT checking is off:
   ```bash
   supabase functions deploy request-work-email-otp --no-verify-jwt
   supabase functions deploy verify-work-email-otp --no-verify-jwt
   ```
5. **Try it.** In the app: ⋮ → Post a job → Verify work email. With the test-job seed loaded, any address at a seed company works, e.g. `you@kavericloud.test`; the code shows on screen in dev mode.

### Going live with real email

1. Create a [Resend](https://resend.com) account and verify a sending domain you own.
2. Set the secrets and switch dev mode off:
   ```bash
   supabase secrets set RESEND_API_KEY=re_xxx EMAIL_FROM="ReferralApp <verify@yourdomain.com>"
   supabase secrets unset OTP_DEV_MODE
   ```
   **Never leave `OTP_DEV_MODE` on in production.** Anyone could verify any work email.
3. Functions read secrets when they start, so running instances can keep old values for a few minutes. Redeploy both functions to switch over immediately.

### Admin: approving a new company

Domains people tried that aren't on the list collect in `domain_requests`. From Sprint 5, approve or reject them in the app's Admin screen (Company requests tab). The SQL at the bottom of `seed/seed_company_allowlist.sql` still works.

## Sprint 3: Feed + apply

| Area | Done |
| --- | --- |
| Database | `get_job_detail()` returns spots left, waitlist size, poster's first name and your status in one call · every screening question must be answered (trimmed, max 1,000 chars) · the job row is locked during apply so two people can't take the last spot · `withdraw_application()` frees your spot and moves the oldest waitlisted applicant up · withdrawn applications still count toward the weekly 10 · you keep seeing jobs you applied to after they close |
| Feed | Title search, Onsite/Hybrid/Remote, company, "Fits my N yrs" filters (server-side) · "Applied" badge |
| Job detail | Verified-poster line, careers-page link, spots left, one main button that adapts: Apply, Join waitlist, Complete profile, weekly limit reached, your own post, closed, or your application status with Withdraw |
| Apply | Screening questions, waitlist notice, applications used this week, confirmation screen |
| My applications | Every application with plain-language status; withdraw while it's still Applied or Waitlisted |

### Sprint 3 setup

Run `supabase db push` (or paste `migrations/20261010000000_apply_flow.sql` into the SQL Editor). No new functions or secrets.

To try it: with the test-job seed loaded, open any seed job and apply as yourself. "QA Engineer, Web" has a cap of 3, so a few test accounts can fill it and the next applicant joins the waitlist.

## Sprint 4: Review loop

| Area | Done |
| --- | --- |
| Match score | 0–100, computed on apply: 50 for experience (in range 50, ±1 yr 30, ±2 yrs 10) + 50 for the share of the job's skills the seeker lists (case-insensitive exact words; jobs with no skills give a neutral 25) |
| Status order | Referrers can only move one step: Applied → Shortlisted/Declined → Referred → Interview → Offer/Not selected. Anything else is refused by the database |
| Notifications | A row in `notifications` for every status change (to the seeker) and every new applicant (to the referrer); `send-push` delivers it through FCM and removes dead device tokens |
| Privacy | A withdrawn applicant's profile and resume are no longer visible to the referrer |
| Stats | `my_posts_summary()` per post; `referrer_stats()` shown on My posts and to seekers on job detail ("Has referred 4 people · 2 reached interviews") |
| Android | Applicants screen with To review / In progress / Done / Waitlist tabs, match %, screening answers, resume link (valid 5 min), confirm before decline · skills field on Post a job · counts on My posts · push notifications |

### Sprint 4 setup

1. `supabase db push` (or paste `migrations/20261011000000_review_loop.sql` into the SQL Editor).
2. Everything except push works now. Push needs Firebase; follow the next section when you're ready. Until then the app builds and runs without it, and notifications simply wait in the `notifications` table.

### Reapplying after a withdrawal

A seeker can reapply **once** per job, while it's open, with fresh screening answers. The reapply counts toward the weekly 10, joins at the back of the waitlist if the job is full, and pings the referrer as a new applicant only when it lands in an open spot. Withdrawing a second time is final. Setup: `supabase db push` (or paste `migrations/20261012000000_reapply.sql`).

### Push notifications (Firebase)

**1. Firebase project.** At console.firebase.google.com, add a project (you can pick the Google Cloud project you made for Google sign-in). Add an Android app with package `com.referral.app`, download `google-services.json` and put it in `android/app/`. It's git-ignored.

**2. Service account key.** Firebase console → Project settings → Service accounts → *Generate new private key*. This file can send pushes as your app: keep it out of git and out of the app.

**3. Secrets** (PowerShell, from the repo root):
```powershell
$sa = [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\service-account.json"))
$bytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$hook = -join ($bytes | ForEach-Object { $_.ToString("x2") })
supabase secrets set "FCM_SERVICE_ACCOUNT=$sa" "PUSH_WEBHOOK_SECRET=$hook"
$hook   # copy this; the webhook in step 5 needs it
```
On macOS/Linux: `supabase secrets set FCM_SERVICE_ACCOUNT="$(base64 < service-account.json | tr -d '\n')" PUSH_WEBHOOK_SECRET=$(openssl rand -hex 24)`.

**4. Deploy the function** (it checks the webhook secret itself):
```bash
supabase functions deploy send-push --no-verify-jwt
```

**5. Let the database call send-push.** Run once in the SQL Editor, with your project URL and the same value you used for `PUSH_WEBHOOK_SECRET` (both are stored encrypted in Vault, never in git):
```sql
select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
select vault.create_secret('<your PUSH_WEBHOOK_SECRET value>', 'push_webhook_secret');
```
Then `supabase db push` to apply `migrations/20261013000000_push_trigger.sql`. It adds a trigger that POSTs every new notification to `send-push` through `pg_net`, so no dashboard webhook is needed. If you made one in the dashboard, delete it, or pushes arrive twice. To change a value later: `select vault.update_secret((select id from vault.secrets where name = 'push_webhook_secret'), '<new value>');`

**6. Check it.** Rebuild and run the app, sign in and allow notifications. A row for your account should appear in `device_tokens`. Then in the SQL Editor:
```sql
insert into notifications (user_id, kind, title, body)
select id, 'test', 'Test push', 'If you can read this, push works.' from auth.users where email = 'you@gmail.com';
```
If nothing arrives, open Edge Functions → send-push → Logs. `sent_at` is filled in on the row once FCM accepts it.

The notification icon is `res/drawable/ic_notification.xml`, a white silhouette of the app icon.

## Sprint 5: Trust & safety

- **Freshness job**, daily at 03:00 IST: reminds referrers on day 12, hides posts not confirmed for 14 days, reminds 7 days before a work-email verification expires, hides posts when it expires, and clears old OTPs (1 day) and notifications (180 days).
- **Reports**: "Report this post" on job details, "Report" on applicant cards. A post is hidden for review after 2 different people report it, or after a single "Asked for money" report. 10 reports per person per day.
- **Admin screen** (menu → Admin, only for admins): open reports grouped by post or user (dismiss, close post, suspend user) and company domain requests (approve with a company name, or reject).
- **Suspended accounts** see a suspension screen. The database also refuses their posts, applications and reactivations, closes their posts and withdraws their open applications.
- **My posts** shows why a post is hidden: under review, removed by moderators, verification expired (with "Verify again"), or not confirmed.
- **Profile & privacy**: "Download my data" saves a JSON file; "Delete my account" (type DELETE) removes the account, resume, posts, applications and notifications. Applicants to the deleted user's open posts are told the post closed.
- A consent line on the sign-in screen.

### Sprint 5 setup

1. Apply the migration: `supabase db push` (adds `20261014000000_trust_safety.sql`).
2. Deploy the delete function (it checks the user's token itself):
   ```bash
   supabase functions deploy delete-account --no-verify-jwt
   ```
3. Turn on scheduling: Dashboard → Integrations → Cron → Enable (this installs `pg_cron`). Then in the SQL Editor:
   ```sql
   select cron.schedule('daily-maintenance', '30 21 * * *', $$select public.run_daily_maintenance()$$);
   ```
   `30 21 * * *` is 21:30 UTC, which is 03:00 IST. To run it once now and see what it did: `select public.run_daily_maintenance();` (it returns counts). Past runs: `select * from cron.job_run_details order by start_time desc limit 5;`
4. Make yourself an admin (the Google account you sign in with):
   ```sql
   insert into admins (user_id) select id from auth.users where email = 'you@gmail.com';
   ```
   Restart the app; "Admin" appears in the menu. To unsuspend someone: `select admin_unban_user('<user id>');` while signed in as admin, or in the SQL Editor `update profiles set banned_at = null, ban_reason = null where id = '<user id>';`.

### Trying it

- Report a seed post from two different test accounts (or once with "Asked for money"). It disappears from the feed, the referrer gets a notification, and it shows in Admin → Reports. Dismiss puts it back if it was confirmed in the last 14 days.
- To test freshness without waiting: `update jobs set last_confirmed_at = now() - interval '15 days' where id = '<job id>';` then `select public.run_daily_maintenance();`.

### Left out on purpose

- **"Seeker ignores a shortlist for 7 days → loses priority"** from the spec isn't built. In the app as built, a shortlist needs nothing from the seeker: the referrer shortlists, then refers in the company portal. There's no action for the seeker to ignore, so the rule would punish people for nothing. It makes sense once seekers have to confirm interest after a shortlist; that's worth adding if referrers say shortlisted people go quiet.
- **Resume cleanup 90 days after a post closes**: resumes belong to the profile (one per person, reused across applications), not to a post, so there's nothing per-post to delete. Deleting the account removes the resume.
- **Appeals** happen by email for now; there's no in-app appeal flow.

## Resume parser & best matches

- **Upload resume → form fills itself.** On the profile screen, picking a PDF uploads it and reads its text *on the phone* (pdfbox-android). Plain rules (`resume/ResumeParser.kt`) pull out name, email, Indian mobile, years of experience, city and skills. Empty fields are filled, skills are merged, experience only goes up. Nothing is saved until the user taps Save. Scanned (image-only) PDFs can't be read; the user is told to fill the form in.
- **One skill vocabulary.** `resume/Skills.kt` maps aliases to one spelling ("compose", "jetpack-compose" → "Jetpack Compose"; "k8s" → "Kubernetes"). Profile skills and job-post skills are both normalised with it, so the existing `match_score` lines up. Add skills to the `DICTIONARY` list as you see them in real resumes.
- **Best matches** (feed chip "★ Best matches", or menu): open, fresh jobs ranked by the same score referrers see, with "You have / Missing" skills. Excludes jobs you posted or already applied to.
- **Contact for referrals.** Optional email and mobile on the profile (prefilled from the resume) in a separate `profile_contacts` table only the seeker can read. A referrer sees them through **Contact** on an applicant card, and only once that applicant is shortlisted, referred, interviewing or has an offer. Included in "Download my data"; removed when the account is deleted.

### Setup

1. `supabase db push` (adds `20261015000000_resume_match.sql`).
2. Sync Gradle: two new libraries, `com.tom-roush:pdfbox-android` (app) and `junit` (tests). R8 rules for pdfbox are in `proguard-rules.pro`.
3. Parser tests: `./gradlew :app:testDebugUnitTest` (or run `ResumeParserTest` from Android Studio). These run on the JVM, no device needed.

pdfbox-android adds a few MB to the APK (check with Build → Analyze APK). If that matters later, ML Kit text recognition on a rendered page is the alternative, but it's heavier on CPU and less accurate for text PDFs.


## Job description

- **Post a job** has an optional "Job description" box (up to 4000 characters; paste from the careers page if you like). Known skills mentioned in it show up under the skills field as "+ Kotlin" chips, one tap to add.
- **Job details** shows it under "About the role", collapsed to 8 lines with Show more.
- The database trims it, collapses long runs of blank lines, and stores a blank box as nothing. Setup: `supabase db push` (adds `20261016000000_job_description.sql`).

## App icon (placeholder)

A person next to a check mark that turns into an arrow: a verified employee passing you forward. Teal-to-indigo background, amber mark.

- **Launcher:** adaptive icon in `res/mipmap-anydpi-v26/` built from vector drawables (`ic_launcher_background`, `ic_launcher_foreground`, `ic_launcher_monochrome`). No PNGs are needed because minSdk is 26. Android 13+ themed icons use the monochrome layer.
- **Notifications:** `res/drawable/ic_notification.xml`.
- **Play Store:** `store/play_store_icon_512.png` (512×512, square; Play rounds the corners). `store/icon_source.svg` is the editable source; `store/icon_preview.png` shows it in different masks.
- To rebrand, change the two gradient colours in `ic_launcher_background.xml` and the amber `#FCD34D` in `ic_launcher_foreground.xml`.

## Design system (redesign)

Bold & trustworthy: the icon's teal → indigo with amber accents, Plus Jakarta Sans, rounded cards, light and dark themes.

- **Theme** (`ui/theme/`): `Color.kt` (light/dark schemes plus `MaterialTheme.extra` for the brand gradient and success/warning/info colours), `Type.kt` (Plus Jakarta Sans, bundled in `res/font`, SIL OFL licence in `licenses/`), `Shape.kt` (corner radii, spacing scale `Space`), `Theme.kt`. Brand colours are used instead of Android's wallpaper colours so the app always looks like itself.
- **Components** (`ui/common/Components.kt`, `JobCard.kt`): `HeroHeader`, `AppTopBar`, `AppCard`, `JobCard`, `PrimaryButton` / `SecondaryButton` / `SmallButton`, `Pill`, `MetaPill`, `MatchBadge`, `StatusPill`, `VerifiedBadge`, `CompanyAvatar`, `PersonAvatar`, `Banner` (info/success/warning/error), `EmptyState`, `StatTile`, `ScoreRing`, `LoadingBox`. Screens use these instead of raw Material widgets, so restyling happens in one place.
- **Navigation:** bottom bar with Jobs, For you, Applied, My posts and Profile (sign out and Admin moved into Profile). Each tab keeps its scroll position when you switch.
- `store/redesign_preview.png` is a browser mock-up of four screens using the same colours and font.

## Play Store graphics

`store/play/` has the feature graphic (1024×500) and five captioned 1080×1920 screenshots; `store/listing.md` has the listing text. They're generated by `store/gen/make_store_assets.js` (Playwright). Put real phone screenshots in `store/screens/1.png … 5.png` (Home, For you, Job detail, Applied, My posts) and re-run it; until then it uses drawn mock screens.

## Table grants (required from 30 Oct 2026)

Supabase no longer grants new tables in `public` to the API roles automatically. Migration `20261021000000_explicit_table_grants.sql` grants every table explicitly:

- **anon:** nothing. Every screen needs a signed-in user.
- **authenticated:** only what the app does directly. RLS still decides which rows.
- **service_role:** everything, for Edge Functions and admin SQL.

On the live project it only tightens existing grants. Apply it with `supabase db push`.

**When you add a table,** put its grants in the same migration that creates it:

```sql
revoke all on public.new_table from anon, authenticated;
grant select, insert on public.new_table to authenticated;   -- only what the app needs
grant all on public.new_table to service_role;
```

The local test stub has no default grants either, so a table without explicit grants fails the tests. Suite 4g lists any table that service_role can't use or that anon can reach.

## Sign-in: Google only

Phone (SMS code) sign-in was removed; everyone signs in with Google. In the Supabase dashboard turn **Authentication → Sign In / Providers → Phone** off so the API can't be used to create phone accounts either. Accounts made earlier with a phone number can't sign in any more. Delete them in Authentication → Users if they were only tests.

Work-email verification for employees still uses an emailed code; that's unchanged.

