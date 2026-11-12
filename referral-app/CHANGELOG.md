# Changelog

Release notes shown to testers and users on Google Play live in `store/whatsnew/whatsnew-en-US`
(max 500 characters). The release workflow uploads that file with each build. This file is the
detailed, developer-facing history.

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
