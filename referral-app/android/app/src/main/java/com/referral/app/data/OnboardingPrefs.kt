package com.referral.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers on this phone whether the welcome tour has been shown. Cleared with app data / reinstall. */
@Singleton
class OnboardingPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    val seen: Boolean get() = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() = prefs.edit().putBoolean(KEY_SEEN, true).apply()

    /** "Not now" on the Jobs-tab card that invites employees to post openings. */
    val postPromptDismissed: Boolean get() = prefs.getBoolean(KEY_POST_PROMPT, false)

    fun dismissPostPrompt() = prefs.edit().putBoolean(KEY_POST_PROMPT, true).apply()

    private companion object {
        const val KEY_SEEN = "tour_seen_v1"
        const val KEY_POST_PROMPT = "post_prompt_dismissed_v1"
    }
}
