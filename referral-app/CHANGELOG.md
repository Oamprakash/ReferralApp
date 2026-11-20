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

**Lighter cards (3–4 things at a glance, one main action)**
- Jobs / For you: location, experience and work mode on one line instead of three pills; team moved to the job screen only.
- For you: one summary line ("3 of 5 skills · experience fits"); the score bars and skill pills open with "Why this score".
- Applied: status pill moved into the header; Withdraw moved to the ⋮ menu (still on the job screen).
- My posts: stats as one line; "Still hiring" shows on the card only when the post needs it; Share, Close and Still hiring otherwise in the ⋮ menu.
- Applicants: 3 skills + "+N"; the next step, Resume and Contact stay visible; Decline/Not selected and Report in the ⋮ menu.

**Jobs tab redesign (design review)**
- "For you" merged into Jobs as **All jobs | Best matches**; bottom bar is now Jobs, Applied, (My posts), Profile.
- Each card shows who posted it: "Posted by Priya, verified employee · 12 referred" (needs migration
  `20261121000000_job_posters.sql`; without it the line is just hidden).
- Bookmark on each card; **Saved** filter lists them (stored on the phone, up to 100).
- Search covers role, skill and company. Filter button in the search box opens all filters in a sheet.
- Freshness as a green dot + "Active 7d ago".
- Colours: one teal tint for company avatars, selected chips, the Applied pill and the bottom-bar
  highlight; header avatar is an outlined circle instead of yellow; dark-mode header subtext 70% → 85% white.

**Applied tab (design review)**
- Progress is 4 stages, Applied → Shortlisted → Referred → Offer, each label centred under its dot;
  Interview fills half the line to Offer. Upcoming stages use a darker grey so they stay visible.
- Card footer shows when it last moved ("Applied 3d ago", "Referred yesterday", "Offer Nov 18") and
  a quiet "View details". Withdraw stays in the ⋮ menu.
- Weekly quota bar track is a clearer grey, so it reads as a bar at 0 of 10.

**My posts (design review)**
- "My posts" tab shows only while the work-email verification is valid (was: ever verified). Re-checked
  on every app resume; if it lapses, the tab goes away and Profile → "Hiring at your company?" leads back.
- "Post a job" moved from the floating button to the top bar, so it never covers a card.
- "Hidden" pill is neutral grey; "Confirm soon" stays amber with "Auto-hides in N days. Confirm you're still hiring."
- Two equal buttons per card: Review (filled) + Still hiring / Share (outlined). On a hidden post,
  Still hiring (or Verify again) is the filled one. Close is in the ⋮ menu next to the status pill.
- Divider between the status note and the applicant counts; bottom-tab labels kept to one line.

**Spacing and readability (all cards)**
- Spacing on an 8dp grid with 4dp steps: 4 between a title and its subtitle, 8 between items in a row,
  12 between groups in a card (was 8), 16 card padding, between cards (was 12) and at the screen edges (was 20).
- Line heights on the 4dp grid (titles 24/28, body 20, small 16).
- Secondary lines in cards (company, location, experience, status notes, match summary) 12sp → 14sp;
  12sp is kept for timestamps, badges and tab labels.
- Pills 12×4 padding, chip rows 8dp apart, icons 16/20dp.

**Text spacing guideline**
- "Post a job" back as the floating button on My posts; the list keeps 96dp at the bottom so the last card clears it.
- Applied progress labels are spread with equal gaps (≥12dp on a 360dp phone) and each dot sits over its label.
- Headings: titleLarge 20/24 and slightly tighter tracking; all-caps tour labels get +1sp tracking.
- 16dp from the screen header to the first card on Applied, My posts and Applicants; 6–8dp between an icon and its text.

**Jobs tab vertical rhythm (spacing review)**
- Hero: greeting 4 → headline 8 → subtitle (now 16/24) 16 → search, 20dp under the search box.
- Header → switch 16, switch → chips 16, chips → "Latest openings" 20, title → first card 12, cards 16 apart.
- Filter chips have a light border and white fill again, so they read as buttons.
- Job card: 16dp padding; title → company 4, header → where/exp 12, → posted by 8, → salary/active 16.
- Posted-by line shortened to "Arjun · Verified employee · 2 referrals" so it doesn't cut off.

**Applied tab polish**
- Job titles wrap to 2 lines before "…".
- Stepper dots evenly spaced, each label centred under its dot (falls back to equal label gaps if <12dp).
- Footer names the referrer: "Sent to Arjun · Nov 13", "Referred by Arjun · yesterday", "Interview · 2d ago · via Arjun"
  (needs migration `20261122000000_job_posters_applied.sql`; without it the name is left out).

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
