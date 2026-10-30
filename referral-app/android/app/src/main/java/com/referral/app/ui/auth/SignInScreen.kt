package com.referral.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.BuildConfig
import com.referral.app.R
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.LightStatusBarIcons
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LightStatusBarIcons()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.extra.heroGradient)
            .imePadding(),
    ) {
        // Brand block on the gradient; takes the space left above the form and scrolls if the keyboard squeezes it
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(horizontal = Space.screen, vertical = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.extra.onHero.copy(alpha = 0.12f)),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extra.accent)
            Text(
                "Jobs posted by people who work there",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.extra.onHero,
            )
            listOf(
                "Every post is from a verified employee",
                "Confirmed open in the last 14 days",
                "Free for job seekers, always",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.extra.accent,
                        modifier = Modifier.size(18.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extra.onHeroMuted)
                }
            }
        }

        // Form on a sheet
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text("Sign in", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Use your Google account. Job seekers and employees sign in the same way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PrimaryButton(
                    "Continue with Google",
                    onClick = {
                        scope.launch {
                            val nonce = Nonce.create()
                            runCatching {
                                requestGoogleIdToken(context, BuildConfig.GOOGLE_WEB_CLIENT_ID, nonce.hashed)
                            }.onSuccess { token -> viewModel.onGoogleIdToken(token, nonce.raw) }
                                .onFailure { viewModel.onGoogleError(googleErrorText(it)) }
                        }
                    },
                    loading = ui.loading,
                )

                ui.error?.let { Banner(it, Tone.ERROR) }

                Text(
                    "By continuing you agree that we store your Google account's name and email, and the profile and resume you add, " +
                        "to run referrals. Your resume is shown only to employees whose jobs you apply to. " +
                        "You can download or delete your data anytime from your profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.xs))
            }
        }
    }
}
