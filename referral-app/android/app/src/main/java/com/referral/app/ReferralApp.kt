package com.referral.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.referral.app.push.PushRegistrar
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ReferralApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushRegistrar.createChannel(this)
        // Crash reports only from release builds, so the dashboard shows what users hit, not our debug sessions.
        // Without google-services.json Firebase isn't set up and this is skipped.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }
}
