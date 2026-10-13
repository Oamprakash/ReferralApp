package com.referral.app.ui.admin

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.DomainRequest
import com.referral.app.data.ReportGroup
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var approving by remember { mutableStateOf<DomainRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = ui.tab.ordinal) {
                AdminTab.entries.forEach { t ->
                    val n = if (t == AdminTab.REPORTS) ui.reports.size else ui.domains.size
                    Tab(selected = ui.tab == t, onClick = { viewModel.selectTab(t) }, text = { Text("${t.label} ($n)") })
                }
            }
            PullToRefreshBox(isRefreshing = false, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize()) {
                if (ui.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@PullToRefreshBox
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.primary) } }
                    ui.error?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }

                    if (ui.tab == AdminTab.REPORTS) {
                        if (ui.reports.isEmpty()) item { Text("No open reports.") }
                        items(ui.reports, key = { it.key }) { g ->
                            ReportCard(
                                g, busy = ui.busyKey == g.key,
                                onDismiss = {
                                    confirm = ConfirmRequest(
                                        "Dismiss these reports?",
                                        if (g.targetType == "job") "The post goes back to the feed if it was held for review."
                                        else "No action is taken against this account.",
                                        "Dismiss", onConfirm = { viewModel.resolve(g, "dismiss") })
                                },
                                onUphold = {
                                    confirm = if (g.targetType == "job") ConfirmRequest(
                                        "Close this post?",
                                        "It's removed for good and the referrer is told it was removed after review.",
                                        "Close post", destructive = true, onConfirm = { viewModel.resolve(g, "uphold") })
                                    else ConfirmRequest(
                                        "Suspend this account?",
                                        "They can't post or apply, their posts close, open applications are withdrawn and push stops.",
                                        "Suspend", destructive = true, onConfirm = { viewModel.resolve(g, "uphold") })
                                },
                            )
                        }
                    } else {
                        if (ui.domains.isEmpty()) item { Text("No company requests waiting.") }
                        items(ui.domains, key = { it.domain }) { d ->
                            DomainCard(
                                d, busy = ui.busyKey == d.domain,
                                onApprove = { approving = d },
                                onReject = {
                                    confirm = ConfirmRequest(
                                        "Reject ${d.domain}?",
                                        "People with this email domain still can't verify. You can approve it later from the SQL Editor.",
                                        "Reject", destructive = true, onConfirm = { viewModel.reject(d.domain) })
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    approving?.let { d -> ApproveDialog(d, onDismiss = { approving = null }) { name -> approving = null; viewModel.approve(d.domain, name) } }
}

@Composable
private fun ReportCard(g: ReportGroup, busy: Boolean, onDismiss: () -> Unit, onUphold: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${if (g.targetType == "job") "Post" else "User"}: ${g.label ?: "(deleted)"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Now: ${g.targetStatus ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${g.reportCount} report${if (g.reportCount == 1) "" else "s"} · " +
                    g.reasons.joinToString(", ") { it.replace('_', ' ') },
                style = MaterialTheme.typography.bodyMedium,
                color = if ("payment_request" in g.reasons) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            g.details.orEmpty().forEach { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Dismiss") }
                Button(onClick = onUphold, enabled = !busy) { Text(if (g.targetType == "job") "Close post" else "Suspend user") }
            }
        }
    }
}

@Composable
private fun DomainCard(d: DomainRequest, busy: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(d.domain, style = MaterialTheme.typography.titleMedium)
            Text("${d.requestCount} people tried to verify with this domain", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !busy) { Text("Approve") }
                TextButton(onClick = onReject, enabled = !busy) { Text("Reject") }
            }
        }
    }
}

/** Approving needs the company's display name; an existing company with that name gets the domain added. */
@Composable
private fun ApproveDialog(d: DomainRequest, onDismiss: () -> Unit, onApprove: (String) -> Unit) {
    var name by remember { mutableStateOf(d.domain.substringBefore('.').replaceFirstChar { it.uppercase() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve ${d.domain}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Check that this is the company's own email domain (not a vendor or a free mail service). " +
                        "Anyone with an address here can then post jobs as an employee.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) },
                    label = { Text("Company name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApprove(name) }, enabled = name.isNotBlank()) { Text("Approve") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
