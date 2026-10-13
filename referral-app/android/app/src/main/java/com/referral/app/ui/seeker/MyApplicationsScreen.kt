package com.referral.app.ui.seeker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.MyApplication
import com.referral.app.data.model.applicationStatusLabel
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.CompanyAvatar
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.SmallButton
import com.referral.app.ui.common.StatusPill
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

@Composable
fun MyApplicationsScreen(
    onOpenJob: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: MyApplicationsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { AppTopBar("Applied", subtitle = "Track every referral request") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { WeeklyQuota(ui.usedThisWeek) }
            ui.error?.let { msg -> item { Banner(msg, Tone.ERROR) } }
            if (ui.applications.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "No applications yet",
                        body = "Apply to a role and you'll see every step here: shortlisted, referred, interview, offer.",
                        action = { SmallButton("Browse jobs", onBrowse, filled = true) },
                    )
                }
            }
            items(ui.applications, key = { it.id }) { app ->
                ApplicationCard(
                    app = app,
                    busy = ui.busyId == app.id,
                    onOpen = { onOpenJob(app.jobId) },
                    onWithdraw = {
                        confirm = ConfirmRequest(
                            "Withdraw your application?",
                            if (app.reappliedAt != null)
                                "You've already reapplied once, so you won't be able to apply to \"${app.job?.title ?: "this job"}\" again."
                            else "You can reapply once while the job is open, but you'll go to the back of the line.",
                            "Withdraw", destructive = true, onConfirm = { viewModel.withdraw(app.id) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun WeeklyQuota(used: Int) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$used of 10 used", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { (used / 10f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            "The weekly limit keeps referrers' inboxes manageable, so they actually read each one.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val STEPS = listOf("applied" to "Applied", "shortlisted" to "Shortlisted", "referred" to "Referred",
    "interview" to "Interview", "offer" to "Offer")

@Composable
private fun ApplicationCard(app: MyApplication, busy: Boolean, onOpen: () -> Unit, onWithdraw: () -> Unit) {
    val company = app.job?.company?.name ?: "Company"
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            CompanyAvatar(company, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(app.job?.title ?: "Job", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(company, if (app.job?.status == "closed") "role closed" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusPill(app.status, applicationStatusLabel(app.status))
        Progress(app.status)
        if (app.canWithdraw || app.canReapply) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (app.canReapply) SmallButton("Apply again", onOpen, filled = true)   // the job screen has the reapply button
                if (app.canWithdraw) SmallButton("Withdraw", onWithdraw, enabled = !busy, destructive = true)
            }
        }
    }
}

/** Five dots joined by lines: how far along the referral is. Hidden for ended applications. */
@Composable
private fun Progress(status: String) {
    val reached = when (status) {
        "applied", "waitlisted" -> 0
        "shortlisted" -> 1
        "referred" -> 2
        "interview" -> 3
        "offer" -> 4
        else -> return      // declined, not selected, withdrawn: no progress bar
    }
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            STEPS.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == reached) 12.dp else 10.dp).clip(CircleShape)
                        .background(if (i <= reached) (if (i == 4) MaterialTheme.extra.success else on) else off),
                )
                if (i < STEPS.lastIndex) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i < reached) on else off))
                }
            }
        }
        Row {
            STEPS.forEachIndexed { i, (_, label) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i <= reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}
