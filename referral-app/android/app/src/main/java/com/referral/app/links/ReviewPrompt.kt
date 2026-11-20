package com.referral.app.links

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory
import com.referral.app.analytics.Analytics

/**
 * Asks for a Play rating at a good moment (an application reached "referred" or later, or a referrer
 * just referred someone). Never on launch or after an error.
 *
 * At most once every 90 days from our side; Play adds its own limits and may show nothing at all,
 * which is expected. Failures are ignored: the rating prompt must never get in the way.
 */
object ReviewPrompt {
    private const val PREFS = "review_prompt"
    private const val KEY_LAST = "last_asked_ms"
    private const val GAP_MS = 90L * 24 * 60 * 60 * 1000

    fun maybeAsk(context: Context, reason: String) {
        val activity = context.findActivity() ?: return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST, 0L) < GAP_MS) return
        prefs.edit().putLong(KEY_LAST, now).apply()
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing) {
                    manager.launchReviewFlow(activity, request.result)
                    Analytics.reviewPrompted(reason)
                }
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
