package com.referral.app.ui.seeker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.CompanyAvatar
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

@Composable
fun ApplyScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ApplyViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    Scaffold(
        topBar = { if (ui.resultStatus == null) AppTopBar("Ask for a referral", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val job = ui.job
        when {
            ui.loading -> LoadingBox(Modifier.padding(padding))
            ui.resultStatus != null -> Done(ui.resultStatus!!, Modifier.padding(padding), onDone)
            job == null -> EmptyState(
                icon = Icons.Filled.Search,
                title = "This job isn't available",
                body = ui.error ?: "It may have been closed or filled.",
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        CompanyAvatar(job.companyName)
                        Column(Modifier.weight(1f)) {
                            Text(job.title, style = MaterialTheme.typography.titleMedium)
                            Text(job.companyName.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (job.myApplicationStatus == "withdrawn") Banner("This is your one reapply for this job.", Tone.WARNING)
                if (job.spotsLeft == 0) {
                    Banner("All spots are taken, so you'll join the waitlist and move up if someone withdraws.", Tone.WARNING)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Text(
                        "Your profile and resume go only to ${job.posterFirstName?.ifBlank { null } ?: "the employee"}, " +
                            "who posted this role. No one else on the app can see them.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (job.questions.isNotEmpty()) {
                    Text("A few quick questions", style = MaterialTheme.typography.titleMedium)
                    job.questions.forEachIndexed { i, q ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${i + 1}. $q", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = ui.answers.getOrElse(i) { "" },
                                onValueChange = { viewModel.onAnswer(i, it) },
                                placeholder = { Text("Your answer") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                ui.error?.let { Banner(it, Tone.ERROR) }
                Text(
                    "${job.applicationsThisWeek} of 10 applications used this week",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    text = if (job.spotsLeft == 0) "Join the waitlist" else "Send application",
                    loading = ui.submitting,
                    enabled = ui.canSubmit,
                    onClick = {
                        val reapply = job.myApplicationStatus == "withdrawn"
                        confirm = ConfirmRequest(
                            if (job.spotsLeft == 0) "Join the waitlist?" else "Send your application?",
                            buildString {
                                append("Your profile, resume and answers go to ${job.posterFirstName?.ifBlank { null } ?: "the referrer"}. ")
                                append("This uses 1 of your 10 applications this week")
                                append(if (reapply) ", and it's your only reapply for this job." else ".")
                            },
                            if (job.spotsLeft == 0) "Join waitlist" else "Send",
                            onConfirm = viewModel::submit,
                        )
                    },
                )
                Spacer(Modifier.height(Space.lg))
            }
        }
    }
}

/** Success screen: big check, what happens next, Done. */
@Composable
private fun Done(status: String, modifier: Modifier, onDone: () -> Unit) {
    val waitlisted = status == "waitlisted"
    Column(
        modifier.fillMaxSize().padding(horizontal = Space.screen, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(if (waitlisted) MaterialTheme.extra.warningContainer else MaterialTheme.extra.successContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = if (waitlisted) MaterialTheme.extra.warning else MaterialTheme.extra.success)
        }
        Text(if (waitlisted) "You're on the waitlist" else "Application sent",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            if (waitlisted) "If a spot opens up you'll move up automatically. We'll notify you."
            else "The referrer will review it. You'll get a notification at every step, and can track it under Applied.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.sm))
        PrimaryButton("Done", onDone)
    }
}
