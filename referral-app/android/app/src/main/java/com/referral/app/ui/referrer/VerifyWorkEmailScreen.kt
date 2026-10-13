package com.referral.app.ui.referrer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.common.VerifiedBadge
import com.referral.app.ui.theme.Space

@Composable
fun VerifyWorkEmailScreen(
    onBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: VerifyWorkEmailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.verified) { if (ui.verified) onVerified() }

    Scaffold(
        topBar = { AppTopBar("Verify work email", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Email, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
            Text(
                if (!ui.codeSent) "Prove you work there" else "Check your work inbox",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (!ui.codeSent) "Only current employees can post jobs. We'll send a 6-digit code to your company email."
                else "We sent a 6-digit code to ${ui.email}.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AppCard {
                if (!ui.codeSent) {
                    OutlinedTextField(
                        value = ui.email,
                        onValueChange = viewModel::onEmail,
                        label = { Text("Work email") },
                        placeholder = { Text("you@company.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("Send code", viewModel::sendCode, enabled = ui.email.contains('@'), loading = ui.loading)
                } else {
                    ui.companyName?.let { VerifiedBadge(it) }
                    OutlinedTextField(
                        value = ui.code,
                        onValueChange = viewModel::onCode,
                        label = { Text("6-digit code") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("Verify", viewModel::verify, enabled = ui.code.length == 6, loading = ui.loading)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = viewModel::changeEmail) { Text("Different email") }
                        TextButton(onClick = viewModel::sendCode, enabled = !ui.loading) { Text("Resend code") }
                    }
                    ui.devCode?.let { Banner("Dev mode code: $it", Tone.WARNING) }
                }
            }

            ui.message?.let { Banner(it, Tone.INFO) }
            ui.error?.let { Banner(it, Tone.ERROR) }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    "Your work email is never shown to job seekers. Verification lasts 90 days.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
