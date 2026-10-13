package com.referral.app.ui.seeker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Apply") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val job = ui.job
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.resultStatus != null -> Done(ui.resultStatus!!, Modifier.padding(padding), onDone)
            job == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(ui.error ?: "This job is no longer available.")
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(job.title, style = MaterialTheme.typography.titleLarge)
                if (job.myApplicationStatus == "withdrawn") {
                    Text(
                        "This is your one reapply for this job.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(job.companyName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(
                    "Your profile and resume go only to ${job.posterFirstName?.ifBlank { null } ?: "the employee"}, " +
                        "who posted this role. No one else on the app can see them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (job.spotsLeft == 0) {
                    Text(
                        "All spots are taken, so you'll join the waitlist and move up if someone withdraws.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                job.questions.forEachIndexed { i, q ->
                    OutlinedTextField(
                        value = ui.answers.getOrElse(i) { "" },
                        onValueChange = { viewModel.onAnswer(i, it) },
                        label = { Text(q) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    "${job.applicationsThisWeek} of 10 applications used this week.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
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
                    enabled = ui.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            ui.submitting -> "Sending…"
                            job.spotsLeft == 0 -> "Join the waitlist"
                            else -> "Send application"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Done(status: String, modifier: Modifier, onDone: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (status == "waitlisted") "You're on the waitlist" else "Application sent",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (status == "waitlisted") "If a spot opens up you'll move up automatically. Track it in My applications."
            else "The referrer will review it. You'll see every update in My applications.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
