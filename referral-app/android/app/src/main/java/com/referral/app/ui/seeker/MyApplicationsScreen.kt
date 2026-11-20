package com.referral.app.ui.seeker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.MyApplication
import com.referral.app.data.model.applicationStatusLabel
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.CardMenu
import com.referral.app.ui.common.MenuAction
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.referral.app.links.ReviewPrompt

@Composable
fun MyApplicationsScreen(
    onOpenJob: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: MyApplicationsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A good moment to ask for a rating: one of their applications got referred (or further)
    val progressed = ui.applications.any { it.status in setOf("referred", "interview", "offer") }
    LaunchedEffect(progressed) { if (progressed) ReviewPrompt.maybeAsk(context, "seeker_referred") }
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
            trackColor = MaterialTheme.colorScheme.outlineVariant,   // #E2E8F0: reads as a bar even at 0
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            "The weekly limit keeps referrers' inboxes manageable, so they actually read each one.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Four stages, so each label has room under its dot on a 360dp phone. Interview sits between Referred and Offer. */
private val STAGES = listOf("Applied", "Shortlisted", "Referred", "Offer")

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
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Withdraw is rarely needed, so it sits in the menu (also on the job screen), never as a red button.
            CardMenu(
                if (app.canWithdraw) listOf(MenuAction("Withdraw application", destructive = true, onClick = onWithdraw)) else emptyList(),
                enabled = !busy,
            )
        }
        StatusPill(app.status, applicationStatusLabel(app.status))
        Progress(app.status)
        // Footer: when it last moved, and the way in
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                app.activityLabel,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (app.canReapply) SmallButton("Apply again", onOpen, filled = true)   // the job screen has the reapply button
            else SmallButton("View details", onOpen)
        }
    }
}

/**
 * Applied → Shortlisted → Referred → Offer. Each label is centred under its dot (the first and last
 * are kept inside the card). Interview fills half the line from Referred to Offer. Hidden for ended
 * applications (declined, not selected, withdrawn).
 */
@Composable
private fun Progress(status: String) {
    val reached = when (status) {
        "applied", "waitlisted" -> 0f
        "shortlisted" -> 1f
        "referred" -> 2f
        "interview" -> 2.5f
        "offer" -> 3f
        else -> return
    }
    val on = MaterialTheme.colorScheme.primary
    val done = MaterialTheme.extra.success
    val off = MaterialTheme.colorScheme.outline      // #CBD5E1 in light: upcoming stages stay visible
    val current = kotlin.math.floor(reached).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val n = STAGES.size
            val cell = size.width / n
            val cy = size.height / 2
            fun x(i: Float) = cell * (i + 0.5f)
            // track, then the filled part
            drawLine(off, Offset(x(0f), cy), Offset(x((n - 1).toFloat()), cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            if (reached > 0f) {
                drawLine(on, Offset(x(0f), cy), Offset(x(reached), cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            for (i in 0 until n) {
                val reachedDot = i <= current
                val color = when {
                    !reachedDot -> off
                    i == n - 1 -> done
                    else -> on
                }
                val r = if (i == current) 6.dp.toPx() else 5.dp.toPx()
                drawCircle(color, radius = r, center = Offset(x(i.toFloat()), cy))
                if (i == current && i != n - 1) drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x(i.toFloat()), cy))
            }
        }
        CenteredLabels(STAGES) { i ->
            Text(
                STAGES[i],
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Medium,
                color = if (i <= current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Places each label centred on its cell (same maths as the dots), nudged in at the edges. */
@Composable
private fun CenteredLabels(items: List<String>, label: @Composable (Int) -> Unit) {
    Layout(
        content = { items.indices.forEach { label(it) } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cell = width / items.size.toFloat()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val centre = cell * (i + 0.5f)
                val x = (centre - p.width / 2f).toInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.placeRelative(x, 0)
            }
        }
    }
}
