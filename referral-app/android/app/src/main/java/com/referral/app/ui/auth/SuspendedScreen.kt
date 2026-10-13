package com.referral.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.SecondaryButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space

/** Shown instead of the app when an admin has suspended the account. */
@Composable
fun SuspendedScreen(reason: String?, onSignOut: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                icon = Icons.Filled.Lock,
                title = "Account suspended",
                body = "After reviewing reports, our moderators suspended this account. " +
                    "You can't post jobs or apply while it's suspended.",
            )
            reason?.takeIf { it.isNotBlank() }?.let { Banner("Reason: $it", Tone.WARNING) }
            Text(
                "If you think this is a mistake, write to us from the email on the Play Store listing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SecondaryButton("Sign out", onSignOut)
        }
    }
}
