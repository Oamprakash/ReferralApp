package com.referral.app.ui.seeker

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.referral.app.ui.common.OfflineBanner
import com.referral.app.ui.common.rememberIsOnline
import com.referral.app.ui.common.skeletonItems
import com.referral.app.ui.common.ErrorState
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)   // PullToRefreshBox
@Composable
fun MyApplicationsScreen(
    onOpenJob: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: MyApplicationsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val online = rememberIsOnline()   // offline strip only when needed (an empty item would still add a gap)
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
      PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().padding(padding),
      ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.lg, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.cardGap),
        ) {
            if (!online && !(ui.error != null && ui.applications.isEmpty())) item { OfflineBanner() }   // the error state says it itself
            if (ui.loading) {
                skeletonItems(3, Modifier)
                return@LazyColumn
            }
            // Couldn't load at all: full error with Try again. Later errors (e.g. withdraw) stay a banner.
            if (ui.error != null && ui.applications.isEmpty()) {
                item { ErrorState(ui.error!!, onRetry = viewModel::retry) }
                return@LazyColumn
            }
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
                    referrer = ui.referrers[app.jobId],
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
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Four stages, so each label has room under its dot on a 360dp phone. Interview sits between Referred and Offer. */
private val STAGES = listOf("Applied", "Shortlisted", "Referred", "Offer")

@Composable
private fun ApplicationCard(app: MyApplication, referrer: String?, busy: Boolean, onOpen: () -> Unit, onWithdraw: () -> Unit) {
    val company = app.job?.company?.name ?: "Company"
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            CompanyAvatar(company, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                // Two lines before "…", so senior titles ("Engineering Manager, Mobile") show in full
                Text(app.job?.title ?: "Job", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(company, if (app.job?.status == "closed") "role closed" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                app.activityLabel(referrer),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (app.canReapply) SmallButton("Apply again", onOpen, filled = true)   // the job screen has the reapply button
            else SmallButton("View details", onOpen)
        }
    }
}

/**
 * Applied → Shortlisted → Referred → Offer. Dots are evenly spaced, with the first and last
 * pulled in just far enough that "Applied" and "Offer" end at the card edges; every label is
 * centred under its own dot. If that would leave less than 12dp between two labels (very large
 * font sizes), the labels are spread with equal gaps instead and the dots follow them. Interview fills half the line from Referred to Offer. Hidden for ended
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
    val ring = MaterialTheme.colorScheme.surface
    val current = kotlin.math.floor(reached).toInt()
    val n = STAGES.size
    // Filled in while measuring, read while drawing (drawing always follows layout)
    val centres = remember { FloatArray(n) }

    Layout(
        content = {
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val cy = size.height / 2
                fun x(i: Float): Float {
                    val lo = kotlin.math.floor(i).toInt().coerceIn(0, n - 1)
                    val hi = (lo + 1).coerceAtMost(n - 1)
                    return centres[lo] + (centres[hi] - centres[lo]) * (i - lo)
                }
                val stroke = 3.dp.toPx()
                drawLine(off, Offset(centres[0], cy), Offset(centres[n - 1], cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (reached > 0f) drawLine(on, Offset(centres[0], cy), Offset(x(reached), cy), strokeWidth = stroke, cap = StrokeCap.Round)
                for (i in 0 until n) {
                    val color = when {
                        i > current -> off
                        i == n - 1 -> done
                        else -> on
                    }
                    drawCircle(color, radius = if (i == current) 6.dp.toPx() else 5.dp.toPx(), center = Offset(centres[i], cy))
                    if (i == current && i != n - 1) drawCircle(ring, radius = 2.dp.toPx(), center = Offset(centres[i], cy))
                }
            }
            STAGES.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Medium,
                    color = if (i <= current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val labels = measurables.drop(1).map { it.measure(Constraints(maxWidth = width)) }
        // Evenly spaced dots between the half-widths of the end labels
        val first = labels.first().width / 2f
        val last = width - labels.last().width / 2f
        val even = FloatArray(n) { i -> first + (last - first) * i / (n - 1) }
        val minGap = 12.dp.toPx()
        val fits = (0 until n - 1).all { i ->
            (even[i + 1] - labels[i + 1].width / 2f) - (even[i] + labels[i].width / 2f) >= minGap
        }
        val xs: List<Float> = if (fits) {
            labels.mapIndexed { i, p -> even[i] - p.width / 2f }
        } else {
            val gap = ((width - labels.sumOf { it.width }) / (n - 1).toFloat()).coerceAtLeast(0f)
            var x = 0f
            labels.map { p -> x.also { x += p.width + gap } }
        }
        labels.forEachIndexed { i, p -> centres[i] = xs[i] + p.width / 2f }
        val track = measurables[0].measure(Constraints.fixed(width, 16.dp.roundToPx()))
        val spacing = Space.sm.roundToPx()
        val labelHeight = labels.maxOf { it.height }
        layout(width, track.height + spacing + labelHeight) {
            track.place(0, 0)
            labels.forEachIndexed { i, p -> p.place(xs[i].toInt(), track.height + spacing) }
        }
    }
}
