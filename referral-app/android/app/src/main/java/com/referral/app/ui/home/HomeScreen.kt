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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.HeroHeader
import com.referral.app.ui.common.JobCard
import com.referral.app.ui.common.LightStatusBarIcons
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.PersonAvatar
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.SectionTitle
import com.referral.app.ui.common.SmallButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenJob: (String) -> Unit,
    onMatches: () -> Unit,
    onProfile: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LightStatusBarIcons()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    // Android 13+: ask once per app session for notification permission (status updates, new applicants).
    val context = LocalContext.current
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { Header(ui, viewModel, onProfile) }
            item { Filters(ui, viewModel, onMatches) }

            when {
                ui.loading -> item { LoadingBox(Modifier.padding(top = 60.dp)) }
                ui.error != null -> item {
                    Banner(ui.error!!, Tone.ERROR, Modifier.padding(horizontal = Space.screen))
                }
                ui.jobs.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = if (ui.filter.isActive) "No roles match these filters" else "No open roles right now",
                        body = if (ui.filter.isActive) "Try fewer filters." else "New posts come in every day. Pull down to refresh.",
                        action = { if (ui.filter.isActive) SmallButton("Clear filters", viewModel::clearFilters, filled = true) },
                    )
                }
                else -> {
                    item {
                        SectionTitle(
                            if (ui.filter.isActive) "${ui.jobs.size} matching roles" else "Latest openings",
                            Modifier.padding(horizontal = Space.screen, vertical = Space.xs),
                            action = { if (ui.filter.isActive) SmallButton("Clear", viewModel::clearFilters) },
                        )
                    }
                    items(ui.jobs, key = { it.id }) { job ->
                        JobCard(
                            title = job.title,
                            company = job.companyName,
                            team = job.team,
                            location = job.location,
                            experience = job.experienceLabel,
                            workMode = job.workModeLabel,
                            salary = job.salaryRange,
                            freshness = job.confirmedLabel,
                            onClick = { onOpenJob(job.id) },
                            modifier = Modifier.padding(horizontal = Space.screen),
                            badge = {
                                if (job.id in ui.appliedJobIds) {
                                    Pill("Applied", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(ui: FeedUiState, viewModel: FeedViewModel, onProfile: () -> Unit) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    HeroHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.firstName?.let { "$greeting, $it" } ?: greeting,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extra.onHeroMuted,
                )
                Text("Find a referral", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.extra.onHero)
            }
            Box(Modifier.clickable(onClick = onProfile)) { PersonAvatar(ui.firstName, size = 44.dp) }
        }
        Text(
            "Every role here is posted by someone who works there.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extra.onHeroMuted,
        )
        TextField(
            value = ui.filter.query,
            onValueChange = viewModel::onQuery,
            placeholder = { Text("Search job titles") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (ui.filter.query.isNotEmpty()) {
                { IconButton(onClick = { viewModel.onQuery("") }) { Icon(Icons.Filled.Clear, "Clear search") } }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(2.dp))
    }
}

@Composable
private fun Filters(ui: FeedUiState, viewModel: FeedViewModel, onMatches: () -> Unit) {
    var companyMenu by remember { mutableStateOf(false) }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.screen),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = true,
            onClick = onMatches,
            label = { Text("Best matches") },
            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.extra.accent,
                selectedLabelColor = MaterialTheme.extra.onAccent,
                selectedLeadingIconColor = MaterialTheme.extra.onAccent,
            ),
            border = null,
        )
        FilterChip(
            selected = ui.filter.fitsYears != null,
            onClick = viewModel::toggleFitsMe,
            enabled = ui.myYears != null,
            label = { Text(ui.myYears?.let { "Fits my $it yrs" } ?: "Fits my experience") },
            colors = chipColors,
        )
        listOf("onsite" to "Onsite", "hybrid" to "Hybrid", "remote" to "Remote").forEach { (value, label) ->
            FilterChip(
                selected = value in ui.filter.workModes,
                onClick = { viewModel.toggleWorkMode(value) },
                label = { Text(label) },
                colors = chipColors,
            )
        }
        Box {
            FilterChip(
                selected = ui.filter.companyId != null,
                onClick = { companyMenu = true },
                label = { Text(ui.selectedCompanyName ?: "Company") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                colors = chipColors,
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
