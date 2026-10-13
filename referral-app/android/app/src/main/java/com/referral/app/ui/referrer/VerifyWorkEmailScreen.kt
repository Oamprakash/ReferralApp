package com.referral.app.ui.referrer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyWorkEmailScreen(
    onBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: VerifyWorkEmailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.verified) { if (ui.verified) onVerified() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify work email") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Only current employees can post jobs. We'll send a code to your work email. " +
                    "Your work email is never shown to job seekers.",
                style = MaterialTheme.typography.bodyMedium,
            )

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
                Button(
                    onClick = viewModel::sendCode,
                    enabled = ui.email.contains('@') && !ui.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ui.loading) "Sending…" else "Send code") }
            } else {
                ui.companyName?.let { Text("Company: $it", style = MaterialTheme.typography.titleSmall) }
                OutlinedTextField(
                    value = ui.code,
                    onValueChange = viewModel::onCode,
                    label = { Text("6-digit code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::verify,
                    enabled = ui.code.length == 6 && !ui.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ui.loading) "Checking…" else "Verify") }
                TextButton(onClick = viewModel::sendCode, enabled = !ui.loading) { Text("Resend code") }
                TextButton(onClick = viewModel::changeEmail) { Text("Use a different email") }
                ui.devCode?.let {
                    Text("Dev mode code: $it", color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge)
                }
            }

            ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
