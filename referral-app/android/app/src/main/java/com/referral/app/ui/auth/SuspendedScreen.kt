package com.referral.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown instead of the app when an admin has suspended the account. */
@Composable
fun SuspendedScreen(reason: String?, onSignOut: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Account suspended", style = MaterialTheme.typography.headlineSmall)
            Text(
                "After reviewing reports, our moderators suspended this account. " +
                    "You can't post jobs or apply while it's suspended.",
                style = MaterialTheme.typography.bodyMedium,
            )
            reason?.takeIf { it.isNotBlank() }?.let {
                Text("Reason: $it", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "If you think this is a mistake, write to us from the email on the Play Store listing.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
