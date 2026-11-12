package com.referral.app.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.referral.app.BuildConfig

/**
 * Thin wrapper over Firebase Analytics.
 *
 * - No-op when Firebase isn't set up (no google-services.json), so the app still runs without it.
 * - Collection is off in debug builds, so the dashboard shows real users only. To test events from a
 *   debug build, add ANALYTICS_IN_DEBUG=true to android/local.properties and watch Firebase → DebugView.
 * - Never log personal data here: no names, emails, job titles typed by users, or resume content.
 *   Events carry only coarse, non-identifying values.
 */
object Analytics {
    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        fa = FirebaseAnalytics.getInstance(context).apply {
            setAnalyticsCollectionEnabled(!BuildConfig.DEBUG || BuildConfig.ANALYTICS_IN_DEBUG)
        }
    }

    /** Compose navigation has one Activity, so screen views are logged per destination. */
    fun screen(name: String) = log(FirebaseAnalytics.Event.SCREEN_VIEW) {
        putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
        putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
    }

    fun login() = log(FirebaseAnalytics.Event.LOGIN) { putString(FirebaseAnalytics.Param.METHOD, "google") }

    /** status: "applied" or "waitlisted" */
    fun applied(status: String, reapply: Boolean = false) = log("apply_job") {
        putString("status", status)
        putString("reapply", reapply.toString())
    }

    /** A job opened from a list. from: "Jobs", "ForYou" or "Applied". */
    fun jobOpened(from: String) = log("open_job") { putString("from", from) }

    fun resumeUploaded() = log("resume_upload")
    fun jobPosted() = log("post_job")
    fun stillHiringConfirmed() = log("confirm_still_hiring")

    private fun log(event: String, params: Bundle.() -> Unit = {}) {
        fa?.logEvent(event, Bundle().apply(params))
    }
}
