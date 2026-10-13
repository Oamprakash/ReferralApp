package com.referral.app

import android.app.Application
import com.referral.app.push.PushRegistrar
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ReferralApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushRegistrar.createChannel(this)
    }
}
