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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.referral.app.ui.theme.extra

@Composable
fun MyPostsScreen(
    onPostJob: () -> Unit,
    onApplicants: (String) -> Unit,
    onVerify: () -> Unit,
    viewModel: MyPostsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { AppTopBar("My posts", subtitle = "Openings you've shared as an employee") },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPostJob,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Post a job", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.extra.accent,
                contentColor = MaterialTheme.extra.onAccent,
                shape = MaterialTheme.shapes.medium,
            )
        },
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = Space.sm, bottom = 96.dp),
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
) {
    val x = MaterialTheme.extra
    val c = MaterialTheme.colorScheme
    val look = when {
        post.hiddenReason == "admin" -> StatusLook("Removed", c.errorContainer, c.onErrorContainer, "Removed by moderators after review.")
        post.hiddenReason == "reports" -> StatusLook("Under review", c.errorContainer, c.onErrorContainer, "Hidden after reports from users. We'll notify you.")
        post.status == "closed" -> StatusLook("Closed", c.surfaceVariant, c.onSurfaceVariant, null)
        post.hiddenReason == "verification" -> StatusLook("Hidden", x.warningContainer, x.onWarningContainer, "Your work email verification expired. Verify again, then tap Still hiring.")
        post.status == "hidden" || post.isStale -> StatusLook("Hidden", x.warningContainer, x.onWarningContainer, "Not confirmed for 14 days. Tap Still hiring to bring it back.")
        post.confirmedDaysAgo >= 12 -> StatusLook("Confirm soon", x.warningContainer, x.onWarningContainer, "Hides after 14 days without a confirmation (${post.confirmedDaysAgo} days now).")
        else -> StatusLook("Live", x.successContainer, x.onSuccessContainer, null)
    }

    AppCard(onClick = onApplicants) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(post.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (look.label == "Live") "${post.location} · confirmed ${post.confirmedDaysAgo}d ago" else post.location,
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
            Pill(look.label, look.bg, look.fg)
        }
        look.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = look.fg) }

        Row(Modifier.fillMaxWidth()) {
            StatTile("${summary?.applicants ?: 0}", "Applicants", Modifier.weight(1f))
            StatTile("${summary?.newCount ?: 0}", "To review", Modifier.weight(1f))
            StatTile("${summary?.referred ?: 0}", "Referred", Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(
                if ((summary?.newCount ?: 0) > 0) "Review ${summary!!.newCount} new" else "Applicants",
                onApplicants, filled = true,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (post.status != "closed") {
                when {
                    post.isModerated -> Unit                                    // only moderators can bring it back
                    post.hiddenReason == "verification" -> {
                        SmallButton("Verify", onVerify, enabled = !busy)
                        SmallButton("Still hiring", onConfirm, enabled = !busy)
                    }
                    else -> SmallButton("Still hiring", onConfirm, enabled = !busy)
                }
                SmallButton("Close", onClose, enabled = !busy, destructive = true)
            }
        }
    }
}
