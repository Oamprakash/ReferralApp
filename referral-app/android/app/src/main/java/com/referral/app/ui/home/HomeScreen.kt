package com.referral.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.Job
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenJob: (String) -> Unit,
    onEditProfile: () -> Unit,
    onPostJob: () -> Unit,
    onMyPosts: () -> Unit,
    onMyApplications: () -> Unit,
    onAdmin: () -> Unit,
    onMatches: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    // Android 13+: ask once for notification permission (status updates, new applicants).
    // If the user declines, Android stops showing the prompt after the second time.
    val context = LocalContext.current
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }   // ask at most once per app session
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Best matches for me") }, onClick = { menuOpen = false; onMatches() })
                        DropdownMenuItem(text = { Text("My applications") }, onClick = { menuOpen = false; onMyApplications() })
                        DropdownMenuItem(text = { Text("Post a job") }, onClick = { menuOpen = false; onPostJob() })
                        DropdownMenuItem(text = { Text("My posts") }, onClick = { menuOpen = false; onMyPosts() })
                        DropdownMenuItem(text = { Text("Profile & privacy") }, onClick = { menuOpen = false; onEditProfile() })
                        if (ui.isAdmin) {
                            DropdownMenuItem(text = { Text("Admin") }, onClick = { menuOpen = false; onAdmin() })
                        }
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = {
                            menuOpen = false
                            confirm = ConfirmRequest(
                                "Sign out?", "You'll stop getting notifications on this phone until you sign in again.",
                                "Sign out", destructive = true, onConfirm = viewModel::signOut)
                        })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterBar(ui = ui, viewModel = viewModel, onMatches = onMatches)
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (ui.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    // LazyColumn even for empty/error states so pull-to-refresh still works
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ui.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
                        if (ui.error == null && ui.jobs.isEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        if (ui.filter.isActive) "No open roles match these filters."
                                        else "No open roles right now. Pull down to refresh.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (ui.filter.isActive) {
                                        TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") }
                                    }
                                }
                            }
                        }
                        items(ui.jobs, key = { it.id }) { job ->
                            JobCard(job, applied = job.id in ui.appliedJobIds, onClick = { onOpenJob(job.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(ui: FeedUiState, viewModel: FeedViewModel, onMatches: () -> Unit) {
    var companyMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = ui.filter.query,
            onValueChange = viewModel::onQuery,
            placeholder = { Text("Search job titles") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (ui.filter.query.isNotEmpty()) {
                { IconButton(onClick = { viewModel.onQuery("") }) { Icon(Icons.Default.Clear, "Clear search") } }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = onMatches, label = { Text("★ Best matches") })
            FilterChip(
                selected = ui.filter.fitsYears != null,
                onClick = viewModel::toggleFitsMe,
                enabled = ui.myYears != null,
                label = { Text(ui.myYears?.let { "Fits my $it yrs" } ?: "Fits my experience") },
            )
            listOf("onsite" to "Onsite", "hybrid" to "Hybrid", "remote" to "Remote").forEach { (value, label) ->
                FilterChip(
                    selected = value in ui.filter.workModes,
                    onClick = { viewModel.toggleWorkMode(value) },
                    label = { Text(label) },
                )
            }
            Box {
                FilterChip(
                    selected = ui.filter.companyId != null,
                    onClick = { companyMenu = true },
                    label = { Text(ui.selectedCompanyName ?: "Company") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = companyMenu, onDismissRequest = { companyMenu = false }) {
                    DropdownMenuItem(text = { Text("Any company") }, onClick = { companyMenu = false; viewModel.setCompany(null) })
                    ui.companies.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { companyMenu = false; viewModel.setCompany(c.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(job: Job, applied: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(job.companyName, job.team).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClick, label = { Text(job.location) })
                AssistChip(onClick = onClick, label = { Text(job.experienceLabel) })
                AssistChip(onClick = onClick, label = { Text(job.workModeLabel) })
                if (applied) SuggestionChip(onClick = onClick, label = { Text("Applied") })
            }
            job.salaryRange?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                job.confirmedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
