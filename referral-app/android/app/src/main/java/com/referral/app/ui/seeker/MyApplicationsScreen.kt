package com.referral.app.ui.seeker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.MyApplication
import com.referral.app.data.model.applicationStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationsScreen(
    onBack: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: MyApplicationsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var withdrawing by remember { mutableStateOf<MyApplication?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My applications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("${ui.usedThisWeek} of 10 applications used this week", style = MaterialTheme.typography.labelLarge) }
            ui.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            if (ui.applications.isEmpty()) {
                item { Text("You haven't applied to any jobs yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(ui.applications, key = { it.id }) { app ->
                ApplicationCard(
                    app = app,
                    busy = ui.busyId == app.id,
                    onOpen = { onOpenJob(app.jobId) },
                    onWithdraw = { withdrawing = app },
                )
            }
        }
    }

    withdrawing?.let { app ->
        AlertDialog(
            onDismissRequest = { withdrawing = null },
            title = { Text("Withdraw your application?") },
            text = {
                Text(
                    if (app.reappliedAt != null) "You've already reapplied once, so you won't be able to apply to \"${app.job?.title ?: "this job"}\" again."
                    else "You can reapply once while the job is open, but you'll go to the back of the line.",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.withdraw(app.id); withdrawing = null }) { Text("Withdraw") } },
            dismissButton = { TextButton(onClick = { withdrawing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ApplicationCard(app: MyApplication, busy: Boolean, onOpen: () -> Unit, onWithdraw: () -> Unit) {
    val color: Color = when (app.status) {
        "referred", "interview", "offer", "shortlisted" -> MaterialTheme.colorScheme.primary
        "declined", "not_selected", "withdrawn" -> MaterialTheme.colorScheme.outline
        "waitlisted" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(app.job?.title ?: "Job", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(app.job?.company?.name, if (app.job?.status == "closed") "role closed" else null)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(applicationStatusLabel(app.status), color = color, style = MaterialTheme.typography.labelLarge)
            if (app.canWithdraw) {
                TextButton(onClick = onWithdraw, enabled = !busy) { Text("Withdraw") }
            }
            if (app.canReapply) {
                TextButton(onClick = onOpen) { Text("Apply again") }   // opens the job, which has the reapply button
            }
        }
    }
}
