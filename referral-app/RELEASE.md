# Release checklist

Work through this top to bottom for the first Play Store release. Commands are for Windows PowerShell, run from `referral-app\android` unless noted.

---

## 1. Decide the name

`ReferralApp` is still a placeholder. It's used in `android/app/src/main/res/values/strings.xml` (`app_name`), `store/listing.md`, the store graphics and the pages in `docs/`. Pick the real name before creating the Play listing, because the listing name is what people search for.

## 2. Create your upload key (once, keep it safe)

```powershell
mkdir "$env:USERPROFILE\keys" -Force
keytool -genkeypair -v -keystore "$env:USERPROFILE\keys\referral-upload.jks" -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Pick a strong password and store the `.jks` file and password in a password manager or backup drive. **Never commit it.** `*.jks` is git-ignored.

Then copy `keystore.properties.example` to `keystore.properties` (same folder, git-ignored) and fill it in:

```
storeFile=C:/Users/<you>/keys/referral-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

With Play App Signing (the default), Google holds the key that signs what users install. Yours only proves uploads come from you. If you lose it, Play support can reset it.

## 3. Build and test the release version on your phone

Release builds are shrunk by R8, which can break things that work in debug. Always test the release build before uploading.

```powershell
.\gradlew clean assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

Run through the main flows:

- [ ] Sign in with phone, and with Google. Google needs your **upload key SHA-1** added as an Android OAuth client first; get it with `keytool -list -v -keystore "$env:USERPROFILE\keys\referral-upload.jks" -alias upload`.
- [ ] Feed loads, filters work, a job opens.
- [ ] Upload a resume: the profile fills in, and Best matches load.
- [ ] Apply to a job.
- [ ] Post a job (work-email verify), shortlist an applicant, and see Contact.
- [ ] A push notification arrives.
- [ ] Download my data saves a file.

If anything crashes only in release, it's almost always a missing R8 keep rule. Send the Logcat stack trace.

Then build what you upload:

```powershell
.\gradlew bundleRelease
# -> app\build\outputs\bundle\release\app-release.aab
```

For every later upload, bump `versionCode` in `app/build.gradle.kts` (2, 3, …). Play rejects a repeated code.

## 4. Supabase: production settings

- [ ] **Migrations:** `supabase db push` from `referral-app` (includes the explicit table grants).
- [ ] **Work-email codes:** set `RESEND_API_KEY` and `EMAIL_FROM`, then `supabase secrets unset OTP_DEV_MODE` and redeploy both OTP functions. **Never ship with dev mode on.** Anyone could verify any work email. (README → "Going live with real email".)
- [ ] **Phone sign-in SMS:** Authentication → Sign In / Providers → Phone. Configure a real SMS provider. Twilio Verify is the simplest for Indian numbers; ordinary SMS to India requires DLT-registered templates.
- [ ] **Reviewer test number:** in the same Phone settings, add a test number with a fixed code (e.g. `919000000000=123456`). Play reviewers use it to sign in without a real SMS (step 6).
- [ ] **Remove the fictional data:** run `supabase/seed/remove_seed_data.sql` in the SQL Editor. Keep the company allowlist.
- [ ] **Daily maintenance cron** is scheduled (`select * from cron.job;`), and you're in `admins`.
- [ ] **Push:** the `send-push` function and the Vault secrets are set (they already work in your project).
- [ ] **Plan:** free projects pause after a week with no activity, and then the app stops working. Use the Pro plan once real users depend on it, or at least watch for pause emails.

## 5. Google sign-in for the Play build

- [ ] In Play Console → Test and release → Setup → **App signing**, copy the **App signing key SHA-1**. In Google Cloud, add it as another **Android** OAuth client (package `com.referral.app`). Without it, Google sign-in fails for everyone who installs from Play.
- [ ] Google Auth Platform → **Audience** → **Publish app**, so any Google account can sign in, not just test users.

## 6. Host the privacy policy and account-deletion pages

The pages are in `docs/` at the repo root. Replace `support@example.com` with your support address (and the name, if it changed), then enable GitHub Pages:

GitHub repo → **Settings → Pages** → Source: *Deploy from a branch* → Branch: `main`, folder `/docs` → Save.

After a minute they're live at:

- `https://oamprakash.github.io/ReferralApp/privacy.html`
- `https://oamprakash.github.io/ReferralApp/delete-account.html`

## 7. Play Console

Create the app (free, *App*, not a game), then fill in **Dashboard → Set up your app**:

| Section | Answer |
|---|---|
| Privacy policy | the `privacy.html` URL |
| App access | *Some functionality is restricted* → add instructions: "Sign in with mobile number 9000000000, code 123456" (the test number from step 4) |
| Ads | No |
| Content rating | Questionnaire: category *Utility / productivity / other*; users can share content (job posts): **yes**; no violence, sexual content, gambling or drugs |
| Target audience | **18 and over** only |
| News app | No |
| Data safety | see below |
| Government app / Financial features / Health | No |
| Store listing | text from `store/listing.md`; icon `store/play_store_icon_512.png`; feature graphic and screenshots from `store/play/` (replace the drawn screenshots with real ones first, see the README) |

**Data safety form:**

- Data is **encrypted in transit**: yes. Users can **request deletion**: yes, with the `delete-account.html` URL.
- Collected (all *not shared* with third parties for their own use; processing by Supabase, Firebase etc. on your behalf doesn't count as sharing):

| Type | Purpose | Optional? |
|---|---|---|
| Personal info → Name | App functionality | Required |
| Personal info → Email address | App functionality, Account management | Optional (Google sign-in, referral contact) |
| Personal info → Phone number | Account management, App functionality | Optional (phone sign-in, referral contact) |
| Personal info → Other info (city, skills, experience, work email) | App functionality | Required for seekers / referrers |
| Files and docs → Resume | App functionality | Required to apply |
| App activity → Other user-generated content (posts, applications, answers, reports) | App functionality | Required |
| App info and performance | not collected (no analytics or crash SDK) | n/a |
| Device or other IDs → push token | App functionality | Required |

## 8. Closed testing, then production

New personal developer accounts must run a **closed test with at least 12 testers for 14 days in a row** before they can apply for production.

1. Test and release → Testing → **Closed testing** → create a track, testers = a Google Group, upload the `.aab`.
2. Share the group link and the opt-in link (the Reddit post drafts are earlier in our chat).
3. After 14 days with 12+ opted-in testers, Dashboard → **Apply for production**, then roll out.

## 9. Automated releases from GitHub

After the **first bundle is uploaded by hand** (step 8: Google requires that before any API upload), every later release can come from GitHub:

- **Push a tag** such as `v1.0.1` → builds a signed bundle and rolls it out to the **closed testing** track.
- **Actions → Play release → Run workflow** → choose the track (`internal`, `alpha`, `beta`, `production`) and `completed` (roll out) or `draft` (you press the button in Play Console).

The workflow sets `versionCode` to the run number + 10, so it always goes up, and `versionName` from the tag. The R8 mapping file is uploaded too, so crash reports in Play Console are readable. A separate **Android build** workflow compiles the app and runs the unit tests on every pull request.

### One-time setup: GitHub secrets

GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**. Base64 values come from PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\keys\referral-upload.jks")) | Set-Clipboard
```

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | your `.jks` as base64 (command above) |
| `UPLOAD_KEYSTORE_PASSWORD` | keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | key password |
| `SUPABASE_URL` | same as in `local.properties` |
| `SUPABASE_ANON_KEY` | same as in `local.properties` |
| `GOOGLE_WEB_CLIENT_ID` | same as in `local.properties` |
| `GOOGLE_SERVICES_JSON_BASE64` | `android\app\google-services.json` as base64 (same command, other path) |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole JSON key file from the next section, pasted as is |

Secrets are encrypted by GitHub, aren't shown in logs, and aren't available to pull requests from forks.

### One-time setup: Play service account

This lets the workflow upload, and nothing else.

1. **Google Cloud** (the same project as Firebase) → **APIs & Services → Library** → enable **Google Play Android Developer API**.
2. **IAM & Admin → Service accounts → Create service account**, e.g. `play-release`. Skip the optional role steps.
3. Open it → **Keys → Add key → JSON**. A file downloads; that's `PLAY_SERVICE_ACCOUNT_JSON`. Keep it private and delete the download once it's in GitHub.
4. **Play Console → Users and permissions → Invite new users** → paste the service account's email (`…@….iam.gserviceaccount.com`) → **App permissions** → add the app with **Release to testing tracks** (and **Release to production** when you're ready) → **Invite user**.

It can take up to a day before a new service account is allowed to upload. If the first run fails with "The caller does not have permission", wait and run it again.

### Releasing

```powershell
git tag v1.0.1
git push origin v1.0.1
```

Then watch **Actions → Play release**. When it's green, the build is on the closed testing track. For production, run the workflow by hand with `production` and `draft`, check the release in Play Console, and press **Start rollout**.

## Already handled in the code

- Targets **API 36** (required for new apps since 31 Aug 2026); edge-to-edge and predictive back are compatible.
- Release builds are **minified and resource-shrunk**, with keep rules for serialization, Supabase, Credential Manager and pdfbox.
- `allowBackup=false`, so sign-in tokens aren't restored to another device.
- Secrets (`local.properties`, `keystore.properties`, `google-services.json`, keys) are git-ignored.
- In-app **Download my data** and **Delete my account** (Play's account-deletion requirement) plus the web deletion page.
