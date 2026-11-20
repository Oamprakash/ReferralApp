# Changelog

Release notes shown to testers and users on Google Play live in `store/whatsnew/whatsnew-en-US`
(max 500 characters). The release workflow uploads that file with each build. This file is the
detailed, developer-facing history.

## Unreleased (next: versionCode 4+)

**From closed-testing feedback**
- "My posts" tab only for people who have verified a work email; job seekers see 4 tabs. Profile has
  "Hiring at your company?" to start posting (#18).
- Sign-in screen: "New here? See how WarmIntro works" replays the welcome tour (#18).

**Growth**
- Share a job: share button on job details and on each active post in My posts. Links look like
  `https://oamprakash.github.io/ReferralApp/job.html?id=<job id>`.
- Job links open straight in the app (Android App Links, verified by
  `oamprakash.github.io/.well-known/assetlinks.json`). Without the app, `docs/job.html` offers
  "Open in the app" and the Play Store. Signed-out users see the job after signing in.
- Play in-app rating prompt at good moments: a seeker's application reaches referred/interview/offer,
  or a referrer has referred someone. At most once every 90 days; Play may show nothing.
- Analytics: `share` (`method` seeker/referrer), `review_prompt` (`reason`), `open_job` with `from = Link`.

**Readability**
- Light mode: softer white text on the teal header raised from 80% to 90% opacity (contrast 4.1:1 → about 4.9:1).
- Small labels (bottom tabs, badges) 11sp → 12sp.
- Numbers in stat tiles, score rings and score bars use same-width digits so they line up.

## 1.0.2 (versionCode 3)

**User-facing**
- Dark mode: onboarding headline and tick-list text were black on the dark background. The theme now
  provides `onBackground` as the default text colour, so text drawn outside a Surface/Scaffold follows
  light and dark mode everywhere (#14).

**Crash reporting (#12)**
- Firebase Crashlytics for release builds; debug builds don't report.
- Release builds upload their R8 mapping file; stack traces keep file names and line numbers.

**Analytics (#13, #15)**
- Firebase Analytics, collection off in debug builds unless `ANALYTICS_IN_DEBUG=true` in `local.properties`.
- Events: `screen_view` (Jobs, ForYou, Applied, MyPosts, Profile, JobDetail, Apply, Applicants, PostJob,
  Verify, HowItWorks, Admin), `open_job` (`from`), `login`, `resume_upload`, `apply_job` (`status`,
  `reapply`), `post_job`, `confirm_still_hiring`. No personal data in events.
- Advertising ID permission removed and its collection disabled; ad personalisation signals off.
- Screen names are matched by route type so they survive R8.

**Policy and listing**
- Privacy policy: crash reports, usage statistics and approximate region (from IP) added.
- Play Data safety: crash logs, diagnostics, app interactions, approximate location (Analytics) added.

## 1.0.0 (versionCode 1)

First closed-testing release: Google sign-in, job feed and filters, best matches from an on-device
resume parse, applications with screening questions, work-email verified referrers, posts that must
be re-confirmed every 14 days, applicant review and referral, reports and moderation, push
notifications, download/delete my data.
