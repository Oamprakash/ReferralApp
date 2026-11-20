package com.referral.app.ui.referrer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.MyPost
import com.referral.app.data.model.PostSummary
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.CardMenu
import com.referral.app.ui.common.MenuAction
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.SmallButton
import com.referral.app.ui.common.StatTile
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.tabular
import com.referral.app.ui.theme.extra
import androidx.compose.ui.platform.LocalContext
import com.referral.app.links.JobLinks
import com.referral.app.analytics.Analytics

@Composable
fun MyPostsScreen(
    onPostJob: () -> Unit,
    onApplicants: (String) -> Unit,
    onVerify: () -> Unit,
    viewModel: MyPostsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            AppTopBar("My posts", subtitle = "Openings you've shared as an employee") {
                // In the top bar rather than a floating button, so it never covers a card's actions
                SmallButton("Post a job", onPostJob, filled = true, icon = Icons.Filled.Add)
                Spacer(Modifier.width(Space.sm))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.sm, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { ImpactCard(ui) }
            ui.error?.let { msg -> item { Banner(msg, Tone.ERROR) } }
            if (ui.posts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Create,
                        title = "Share your first opening",
                        body = "Verify your work email once, then post roles open at your company. You review every applicant.",
                        action = { SmallButton("Post a job", onPostJob, filled = true) },
                    )
                }
            }
            items(ui.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    summary = ui.summaries[post.id],
                    busy = ui.busyId == post.id,
                    onApplicants = { onApplicants(post.id) },
                    onVerify = onVerify,
                    onConfirm = {
                        confirm = ConfirmRequest(
                            "Still hiring for \"${post.title}\"?",
                            "Confirm only if the role is still open at your company. The post stays in the feed for another 14 days.",
                            "Yes, still hiring", onConfirm = { viewModel.confirm(post.id) })
                    },
                    onShare = {
                        Analytics.jobShared(asReferrer = true)
                        JobLinks.share(context, JobLinks.referrerText(post.title, ui.companyName, post.location, post.id))
                    },
                    onClose = {
                        confirm = ConfirmRequest(
                            "Close \"${post.title}\"?",
                            "It disappears from the feed and no one else can apply. This can't be undone.",
                            "Close post", destructive = true, onConfirm = { viewModel.close(post.id) })
                    },
                )
            }
        }
    }
}

private data class StatusLook(
    val label: String,
    val bg: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val note: String?,
)

/** Gradient card: your track record as a referrer and how many post slots you're using. */
@Composable
private fun ImpactCard(ui: MyPostsUiState) {
    val st = ui.stats
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.extra.heroGradient).padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("Your impact", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extra.onHeroMuted)
        Row(Modifier.fillMaxWidth()) {
            StatTile("${st?.referred ?: 0}", "Referred", Modifier.weight(1f), onHero = true)
            StatTile("${st?.interviews ?: 0}", "Interviews", Modifier.weight(1f), onHero = true)
            StatTile("${st?.offers ?: 0}", "Offers", Modifier.weight(1f), onHero = true)
        }
        Pill("${ui.activeCount} of 5 active posts", MaterialTheme.extra.accent, MaterialTheme.extra.onAccent)
    }
}

@Composable
private fun PostCard(
    post: MyPost,
    summary: PostSummary?,
    busy: Boolean,
    onApplicants: () -> Unit,
    onVerify: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val x = MaterialTheme.extra
    val c = MaterialTheme.colorScheme
    val daysLeft = (14 - post.confirmedDaysAgo).coerceAtLeast(1)
    val look = when {
        post.hiddenReason == "admin" -> StatusLook("Removed", c.errorContainer, c.onErrorContainer, "Removed by moderators after review.")
        post.hiddenReason == "reports" -> StatusLook("Under review", c.errorContainer, c.onErrorContainer, "Hidden after reports from users. We'll notify you.")
        post.status == "closed" -> StatusLook("Closed", c.surfaceVariant, c.onSurfaceVariant, null)
        // Hidden = paused, so neutral grey; amber is kept for "act soon"
        post.hiddenReason == "verification" -> StatusLook("Hidden", c.surfaceVariant, c.onSurfaceVariant,
            "Your work email verification expired. Verify again to bring it back.")
        post.status == "hidden" || post.isStale -> StatusLook("Hidden", c.surfaceVariant, c.onSurfaceVariant,
            "Hidden from job seekers. Tap Still hiring to bring it back.")
        post.confirmedDaysAgo >= 12 -> StatusLook("Confirm soon", x.warningContainer, x.onWarningContainer,
            "Auto-hides in $daysLeft day${if (daysLeft == 1) "" else "s"}. Confirm you're still hiring.")
        else -> StatusLook("Live", x.successContainer, x.onSuccessContainer, null)
    }
    val open = post.status != "closed" && !post.isModerated     // moderated: only moderators can bring it back
    val needsVerify = open && post.hiddenReason == "verification"
    val hidden = open && look.label == "Hidden"
    val needsConfirm = open && look.label != "Live"

    AppCard(onClick = onApplicants) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(post.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (look.label == "Live") "${post.location} · confirmed ${post.confirmedDaysAgo}d ago" else post.location,
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
            Pill(look.label, look.bg, look.fg)
            // Close (and the less common actions) live up here, away from the main buttons
            if (post.status != "closed") {
                CardMenu(
                    buildList {
                        if (open && !needsConfirm) add(MenuAction("Still hiring", onClick = onConfirm))
                        if (needsVerify) add(MenuAction("Still hiring", onClick = onConfirm))
                        if (post.isActive && !post.isModerated && needsConfirm) add(MenuAction("Share", onClick = onShare))
                        add(MenuAction("Close post", destructive = true, onClick = onClose))
                    },
                    enabled = !busy,
                )
            }
        }
        look.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (look.label == "Confirm soon") x.warning else c.onSurfaceVariant)
            HorizontalDivider(color = x.cardBorder)
        }

        val applicants = summary?.applicants ?: 0
        val newCount = summary?.newCount ?: 0
        val referred = summary?.referred ?: 0
        Text(
            "$applicants applicant${if (applicants == 1) "" else "s"} · $newCount to review · $referred referred",
            style = MaterialTheme.typography.bodyMedium.tabular(), color = c.onSurfaceVariant,
        )

        // Two equal buttons. When the post is hidden, bringing it back is the main action.
        val reviewLabel = if (newCount > 0) "Review $newCount new" else "Applicants"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val half = Modifier.weight(1f)
            when {
                needsVerify -> {
                    SmallButton("Verify again", onVerify, enabled = !busy, filled = true, modifier = half)
                    SmallButton(reviewLabel, onApplicants, outlined = true, modifier = half)
                }
                hidden -> {
                    SmallButton("Still hiring", onConfirm, enabled = !busy, filled = true, modifier = half)
                    SmallButton(reviewLabel, onApplicants, outlined = true, modifier = half)
                }
                needsConfirm -> {
                    SmallButton(reviewLabel, onApplicants, filled = true, modifier = half)
                    SmallButton("Still hiring", onConfirm, enabled = !busy, outlined = true, modifier = half)
                }
                post.isActive && !post.isModerated -> {
                    SmallButton(reviewLabel, onApplicants, filled = true, modifier = half)
                    SmallButton("Share", onShare, enabled = !busy, outlined = true, modifier = half)
                }
                else -> SmallButton(reviewLabel, onApplicants, filled = true, modifier = half)
            }
        }
    }
}
