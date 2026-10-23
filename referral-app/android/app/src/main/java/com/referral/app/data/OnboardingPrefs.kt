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

    private companion object { const val KEY_SEEN = "tour_seen_v1" }
}
