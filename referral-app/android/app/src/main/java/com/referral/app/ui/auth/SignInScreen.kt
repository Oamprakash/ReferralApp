package com.referral.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Jobs posted by people who work there", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Every role is posted by a verified employee and confirmed open in the last 14 days. Free for job seekers, always.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!ui.otpSent) {
            OutlinedTextField(
                value = ui.phoneDigits,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Mobile number") },
                prefix = { Text("+91 ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::sendOtp,
                enabled = ui.phoneValid && !ui.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send code") }
        } else {
            Text("Enter the 6-digit code sent to +91 ${ui.phoneDigits}")
            OutlinedTextField(
                value = ui.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::verifyOtp,
                enabled = ui.code.length == 6 && !ui.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verify") }
            TextButton(onClick = viewModel::editNumber) { Text("Change number") }
            TextButton(onClick = viewModel::sendOtp, enabled = !ui.loading) { Text("Resend code") }
        }

        HorizontalDivider()

        OutlinedButton(
            onClick = {
                scope.launch {
                    val nonce = Nonce.create()
                    runCatching {
                        requestGoogleIdToken(context, BuildConfig.GOOGLE_WEB_CLIENT_ID, nonce.hashed)
                    }.onSuccess { token -> viewModel.onGoogleIdToken(token, nonce.raw) }
                        .onFailure { viewModel.onGoogleError(it.message) }
                }
            },
            enabled = !ui.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue with Google") }

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
            "By continuing you agree that we store your phone number or email, and the profile and resume you add, " +
                "to run referrals. Your resume is shown only to employees whose jobs you apply to. " +
                "You can download or delete your data anytime from Profile & privacy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
