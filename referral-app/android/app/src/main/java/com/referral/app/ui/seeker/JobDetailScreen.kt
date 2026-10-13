package com.referral.app.ui.seeker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.ReportReason
import com.referral.app.data.model.JobDetail
import com.referral.app.data.model.applicationStatusLabel
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.CompanyAvatar
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.MetaPill
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.PersonAvatar
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.ReportDialog
import com.referral.app.ui.common.SecondaryButton
import com.referral.app.ui.common.StatusPill
import com.referral.app.ui.common.Tone
import com.referral.app.ui.common.VerifiedBadge
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onCompleteProfile: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var reportOpen by remember { mutableStateOf(false) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val job = ui.job
    Scaffold(
        topBar = { AppTopBar("", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (job != null) {
                ActionBar(
                    job, ui,
                    onApply = { onApply(job.id) },
                    onCompleteProfile = onCompleteProfile,
                    onWithdraw = {
                        confirm = ConfirmRequest(
                            "Withdraw your application?",
                            if (job.hasReapplied) "You've already reapplied once, so you won't be able to apply to this job again."
                            else "You can reapply once while the job is open, but you'll go to the back of the line.",
                            "Withdraw", destructive = true, onConfirm = viewModel::withdraw,
                        )
                    },
                )
            }
        },
    ) { padding ->
        when {
            ui.loading -> LoadingBox(Modifier.padding(padding))
            job == null -> EmptyState(
                icon = Icons.Filled.Search,
                title = "This job isn't available",
                body = ui.error ?: "It may have been closed or filled.",
                modifier = Modifier.padding(padding),
            )
            else -> DetailBody(job, ui, Modifier.padding(padding), onReport = { reportOpen = true })
        }
    }

    if (reportOpen) {
        ReportDialog(
            title = "Report this post",
            onDismiss = { reportOpen = false },
            onSubmit = { reason: ReportReason, details: String -> reportOpen = false; viewModel.report(reason, details) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(job: JobDetail, ui: JobDetailUiState, modifier: Modifier, onReport: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen).padding(bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        // Title block
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            CompanyAvatar(job.companyName, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(job.companyName, job.team?.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaPill(job.location, icon = Icons.Filled.LocationOn)
            MetaPill(job.experienceLabel)
            MetaPill(job.workModeLabel)
            job.salaryRange?.let { Pill(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
        }

        // Who posted it: the reason to trust this app
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PersonAvatar(job.posterFirstName, size = 40.dp,
                    container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text("Posted by ${job.posterFirstName?.ifBlank { null } ?: "an employee"}", style = MaterialTheme.typography.titleSmall)
                    Text("Works at ${job.companyName ?: "this company"}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VerifiedBadge("Verified")
            }
            if (job.posterReferred > 0) {
                Text(
                    "Has referred ${job.posterReferred} ${if (job.posterReferred == 1) "person" else "people"}" +
                        if (job.posterInterviews > 0) " · ${job.posterInterviews} reached interviews" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.success,
                )
            }
            Text(job.confirmedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Spots
        AppCard {
            val used = job.applicantCap - job.spotsLeft
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Applicant spots", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (job.spotsLeft > 0) "${job.spotsLeft} of ${job.applicantCap} left" else "Full · ${job.waitlistCount} waiting",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (job.spotsLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.extra.warning,
                )
            }
            LinearProgressIndicator(
                progress = { if (job.applicantCap == 0) 1f else used / job.applicantCap.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
                color = if (job.spotsLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.extra.warning,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                "Each post takes a limited number of applicants so the referrer can review every one." +
                    if (job.questions.isNotEmpty()) " You'll answer ${job.questions.size} short question(s)." else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (job.skills.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Skills", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    job.skills.forEach { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        }

        job.description?.let { DescriptionSection(it) }

        AppCard {
            Text("Official listing", style = MaterialTheme.typography.titleSmall)
            job.jobRef?.let {
                Text("Job ID: $it", style = MaterialTheme.typography.bodyMedium)
            }
            job.officialUrl?.let { url ->
                SecondaryButton("View on careers page", onClick = { runCatching { uriHandler.openUri(url) } },
                    icon = Icons.Filled.Share)
            }
            Text("Check the role on the company's own site. The referral goes through their official portal.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Banner("Referrals are always free. Report anyone who asks you for money.", Tone.WARNING)
        ui.reportMessage?.let { Banner(it, Tone.SUCCESS) }
        if (!job.isMine) {
            TextButton(onClick = onReport, enabled = !ui.reporting, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Report this post", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Pinned to the bottom: the one thing to do next on this job. */
@Composable
private fun ActionBar(
    job: JobDetail,
    ui: JobDetailUiState,
    onApply: () -> Unit,
    onCompleteProfile: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.screen, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            ui.error?.let { Banner(it, Tone.ERROR) }
            when (ui.action) {
                ApplyAction.APPLY -> PrimaryButton("Ask for a referral", onApply, icon = Icons.AutoMirrored.Filled.ArrowForward)
                ApplyAction.JOIN_WAITLIST -> PrimaryButton("Join the waitlist", onApply)
                ApplyAction.REAPPLY -> {
                    Text("You withdrew. You can apply once more; you'll join at the back of the line.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton(if (job.spotsLeft == 0) "Rejoin the waitlist" else "Apply again", onApply)
                }
                ApplyAction.ALREADY_APPLIED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your application", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusPill(job.myApplicationStatus, applicationStatusLabel(job.myApplicationStatus))
                        }
                        if (job.myApplicationStatus == "applied" || job.myApplicationStatus == "waitlisted") {
                            TextButton(onClick = onWithdraw, enabled = !ui.withdrawing) {
                                Text("Withdraw", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                ApplyAction.WITHDRAWN_FINAL -> Note("You withdrew after reapplying, so you can't apply to this job again.")
                ApplyAction.OWN_POST -> Note("This is your post. Manage it from My posts.")
                ApplyAction.CLOSED -> Note("This role is no longer open.")
                ApplyAction.COMPLETE_PROFILE -> {
                    Text("Add your experience and resume before applying.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton("Complete profile", onCompleteProfile)
                }
                ApplyAction.WEEKLY_LIMIT -> Note("You've used all 10 applications this week. New ones open up as the week rolls over.")
                null -> Unit
            }
        }
    }
}

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Space.sm))

/** "About the role": long descriptions start collapsed at 8 lines. */
@Composable
private fun DescriptionSection(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("About the role", style = MaterialTheme.typography.titleMedium)
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
