package com.referral.app.ui.referrer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.MyPost
import com.referral.app.data.model.PostSummary
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    onBack: () -> Unit,
    onPostJob: () -> Unit,
    onApplicants: (String) -> Unit,
    onVerify: () -> Unit,
    viewModel: MyPostsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }

    // Reload when coming back from "Post a job"
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My posts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPostJob,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Post a job") },
            )
        },
    ) { padding ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${ui.activeCount} of 5 active posts", style = MaterialTheme.typography.labelLarge)
                    ui.stats?.takeIf { it.referred > 0 }?.let { st ->
                        Text(
                            "You've referred ${st.referred} · ${st.interviews} reached interviews · ${st.offers} offers",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            ui.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            if (ui.posts.isEmpty()) {
                item { Text("You haven't posted any jobs yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(ui.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    summary = ui.summaries[post.id],
                    onApplicants = { onApplicants(post.id) },
                    onVerify = onVerify,
                    busy = ui.busyId == post.id,
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

    ConfirmDialog(confirm, onDismiss = { confirm = null })
}

@Composable
private fun PostCard(
    post: MyPost,
    summary: PostSummary?,
    onApplicants: () -> Unit,
    onVerify: () -> Unit,
    busy: Boolean,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium)
            Text(post.location, style = MaterialTheme.typography.bodyMedium)
            val (label, color) = when {
                post.hiddenReason == "admin" ->
                    "Removed by moderators after review" to MaterialTheme.colorScheme.error
                post.hiddenReason == "reports" ->
                    "Under review: hidden after reports from users" to MaterialTheme.colorScheme.error
                post.status == "closed" -> "Closed" to MaterialTheme.colorScheme.outline
                post.hiddenReason == "verification" ->
                    "Hidden: work email verification expired. Verify again, then tap Still hiring" to MaterialTheme.colorScheme.error
                post.status == "hidden" || post.isStale ->
                    "Hidden from feed: confirm it's still open" to MaterialTheme.colorScheme.error
                post.confirmedDaysAgo >= 12 ->
                    "Confirm soon: hides after 14 days (${post.confirmedDaysAgo} days now)" to MaterialTheme.colorScheme.tertiary
                else -> "Live · confirmed ${post.confirmedDaysAgo} days ago" to MaterialTheme.colorScheme.primary
            }
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)

            summary?.let { sm ->
                Text(
                    buildString {
                        append("${sm.applicants} applicant${if (sm.applicants == 1) "" else "s"}")
                        if (sm.newCount > 0) append(" · ${sm.newCount} to review")
                        if (sm.referred > 0) append(" · ${sm.referred} referred")
                        if (sm.waitlisted > 0) append(" · ${sm.waitlisted} waitlisted")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onApplicants) {
                Text(if ((summary?.newCount ?: 0) > 0) "Review applicants (${summary!!.newCount} new)" else "View applicants")
            }

            if (post.status != "closed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        post.isModerated -> Unit   // only moderators can bring it back
                        post.hiddenReason == "verification" -> {
                            OutlinedButton(onClick = onVerify, enabled = !busy) { Text("Verify again") }
                            OutlinedButton(onClick = onConfirm, enabled = !busy) { Text("Still hiring") }
                        }
                        else -> OutlinedButton(onClick = onConfirm, enabled = !busy) { Text("Still hiring") }
                    }
                    TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
                }
            }
        }
    }
}
