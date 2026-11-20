package com.referral.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.referral.app.links.JobLinks
import com.referral.app.links.PendingJobLink
import com.referral.app.ui.navigation.AppRoot
import com.referral.app.ui.theme.ReferralTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) PendingJobLink.set(JobLinks.jobIdFrom(intent?.data))
        setContent {
            ReferralTheme { AppRoot() }
        }
    }

    /** A shared job link tapped while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PendingJobLink.set(JobLinks.jobIdFrom(intent.data))
    }
}
