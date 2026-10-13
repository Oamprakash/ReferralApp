package com.referral.app.ui.seeker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.JobDetail
import com.referral.app.data.model.applicationStatusLabel
import com.referral.app.ui.common.ReportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onCompleteProfile: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val job = ui.job
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            job == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(ui.error ?: "This job is no longer available.", style = MaterialTheme.typography.bodyLarge)
            }
            else -> DetailBody(
                job = job,
                ui = ui,
                modifier = Modifier.padding(padding),
                onApply = { onApply(job.id) },
                onCompleteProfile = onCompleteProfile,
                onWithdraw = viewModel::withdraw,
                onReport = viewModel::report,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    job: JobDetail,
    ui: JobDetailUiState,
    modifier: Modifier,
    onApply: () -> Unit,
    onCompleteProfile: () -> Unit,
    onWithdraw: () -> Unit,
    onReport: (com.referral.app.data.ReportReason, String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var confirmWithdraw by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(job.title, style = MaterialTheme.typography.headlineSmall)
        Text(listOfNotNull(job.companyName, job.team).joinToString(" · "), style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Posted by ${job.posterFirstName?.ifBlank { null } ?: "an employee"}, a verified ${job.companyName ?: ""} employee",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (job.posterReferred > 0) {
            Text(
                "Has referred ${job.posterReferred} ${if (job.posterReferred == 1) "person" else "people"} on the app" +
                    if (job.posterInterviews > 0) " · ${job.posterInterviews} reached interviews" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(job.confirmedLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(job.location) })
            AssistChip(onClick = {}, label = { Text(job.experienceLabel) })
            AssistChip(onClick = {}, label = { Text(job.workModeLabel) })
        }
        job.salaryRange?.let { Text("Salary: $it", style = MaterialTheme.typography.bodyLarge) }
        if (job.skills.isNotEmpty()) {
            Text("Skills: ${job.skills.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }
        job.description?.let { DescriptionSection(it) }

        job.officialUrl?.let { url ->
            OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("View on company careers page") }
        }
        job.jobRef?.let { Text("Job ID: $it", style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider()

        Text(
            if (job.spotsLeft > 0) "${job.spotsLeft} of ${job.applicantCap} applicant spots left"
            else "All ${job.applicantCap} spots taken · ${job.waitlistCount} on the waitlist",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (job.questions.isNotEmpty()) {
            Text("You'll answer ${job.questions.size} short question(s) when you apply.", style = MaterialTheme.typography.bodySmall)
        }

        when (ui.action) {
            ApplyAction.APPLY -> Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply for a referral") }
            ApplyAction.JOIN_WAITLIST -> Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Join the waitlist") }
            ApplyAction.REAPPLY -> {
                Text(
                    "You withdrew from this job. You can apply once more; you'll join at the back of the line.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(if (job.spotsLeft == 0) "Rejoin the waitlist" else "Apply again")
                }
            }
            ApplyAction.WITHDRAWN_FINAL -> Text(
                "You withdrew after reapplying, so you can't apply to this job again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ApplyAction.ALREADY_APPLIED -> {
                Text(applicationStatusLabel(job.myApplicationStatus), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                if (job.myApplicationStatus == "applied" || job.myApplicationStatus == "waitlisted") {
                    TextButton(onClick = { confirmWithdraw = true }, enabled = !ui.withdrawing) { Text("Withdraw application") }
                }
            }
            ApplyAction.OWN_POST -> Text("This is your post. Manage it from My posts.", style = MaterialTheme.typography.bodyMedium)
            ApplyAction.CLOSED -> Text("This role is no longer open.", style = MaterialTheme.typography.bodyMedium)
            ApplyAction.COMPLETE_PROFILE -> {
                Text("Add your experience and resume before applying.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onCompleteProfile, modifier = Modifier.fillMaxWidth()) { Text("Complete profile") }
            }
            ApplyAction.WEEKLY_LIMIT -> Text(
                "You've used all 10 applications for this week. This keeps referrers' inboxes manageable.",
                style = MaterialTheme.typography.bodyMedium,
            )
            null -> Unit
        }

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
            "Referrals are always free. Report anyone who asks you for money.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!job.isMine) {
            TextButton(onClick = { reportOpen = true }, enabled = !ui.reporting) {
                Text("Report this post", color = MaterialTheme.colorScheme.error)
            }
        }
        ui.reportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    if (reportOpen) {
        ReportDialog(
            title = "Report this post",
            onDismiss = { reportOpen = false },
            onSubmit = { reason, details -> reportOpen = false; onReport(reason, details) },
        )
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("Withdraw your application?") },
            text = {
                Text(
                    if (job.hasReapplied) "You've already reapplied once, so you won't be able to apply to this job again."
                    else "You can reapply once while the job is open, but you'll go to the back of the line.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmWithdraw = false; onWithdraw() }) { Text("Withdraw") } },
            dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text("Cancel") } },
        )
    }
}

/** "About the role": long descriptions start collapsed at 8 lines. */
@Composable
private fun DescriptionSection(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("About the role", style = MaterialTheme.typography.titleSmall)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
        }
    }
}
