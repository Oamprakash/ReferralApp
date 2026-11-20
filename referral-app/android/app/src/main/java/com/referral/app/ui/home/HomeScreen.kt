package com.referral.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.analytics.Analytics
import com.referral.app.data.model.JobPoster
import com.referral.app.ui.common.AppIcons
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.HeroHeader
import com.referral.app.ui.common.JobCard
import com.referral.app.ui.common.LightStatusBarIcons
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.SectionTitle
import com.referral.app.ui.common.SmallButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.matches.MatchesViewModel
import com.referral.app.ui.matches.bestMatchItems
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra
import java.time.LocalTime

/** The Jobs tab: all open roles, or the user's best matches (formerly the separate "For you" tab). */
@OptIn(ExperimentalMaterial3Api::class)   // PullToRefreshBox, segmented buttons, bottom sheet
@Composable
fun HomeScreen(
    onOpenJob: (id: String, from: String) -> Unit,
    onEditProfile: () -> Unit,
    onProfile: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
    matchesViewModel: MatchesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val matches by matchesViewModel.ui.collectAsStateWithLifecycle()
    var bestMatches by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    LightStatusBarIcons()

    LifecycleResumeEffect(bestMatches) {
        if (bestMatches) matchesViewModel.load() else viewModel.onResume()
        onPauseOrDispose { }
    }
    // Keep the screen names analytics had before the two tabs merged
    LaunchedEffect(bestMatches) { if (bestMatches) Analytics.screen("ForYou") }

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

    if (showFilters) FilterSheet(ui, viewModel, onDismiss = { showFilters = false })

    PullToRefreshBox(
        isRefreshing = if (bestMatches) matches.refreshing else ui.refreshing,
        onRefresh = { if (bestMatches) matchesViewModel.load() else viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.cardGap),
        ) {
            item {
                Header(
                    ui, viewModel, onProfile,
                    searchEnabled = !bestMatches,
                    onOpenFilters = { bestMatches = false; showFilters = true },
                )
            }
            item {
                ModeSwitch(bestMatches) { bestMatches = it }
            }

            if (bestMatches) {
                bestMatchItems(matches, onOpenJob = { onOpenJob(it, "ForYou") }, onEditProfile = onEditProfile)
                return@LazyColumn
            }

            item { Filters(ui, viewModel) }
            when {
                ui.loading -> item { LoadingBox(Modifier.padding(top = 60.dp)) }
                ui.error != null -> item {
                    Banner(ui.error!!, Tone.ERROR, Modifier.padding(horizontal = Space.screen))
                }
                ui.jobs.isEmpty() -> item {
                    val savedOnly = ui.filter.savedIds != null
                    EmptyState(
                        icon = if (savedOnly) AppIcons.BookmarkBorder else Icons.Filled.Search,
                        title = when {
                            savedOnly && ui.filter.filterCount == 1 && ui.filter.query.isBlank() -> "No saved jobs"
                            ui.filter.isActive -> "No roles match these filters"
                            else -> "No open roles right now"
                        },
                        body = when {
                            savedOnly && ui.filter.filterCount == 1 && ui.filter.query.isBlank() ->
                                "Tap the bookmark on a job to keep it here. Closed or expired jobs drop off."
                            ui.filter.isActive -> "Try fewer filters."
                            else -> "New posts come in every day. Pull down to refresh."
                        },
                        action = { if (ui.filter.isActive) SmallButton("Clear filters", viewModel::clearFilters, filled = true) },
                    )
                }
                else -> {
                    item {
                        SectionTitle(
                            when {
                                ui.filter.savedIds != null -> "Saved jobs"
                                ui.filter.isActive -> "${ui.jobs.size} matching roles"
                                else -> "Latest openings"
                            },
                            Modifier.padding(horizontal = Space.screen, vertical = Space.xs),
                            action = { if (ui.filter.isActive) SmallButton("Clear", viewModel::clearFilters) },
                        )
                    }
                    items(ui.jobs, key = { it.id }) { job ->
                        val applied = job.id in ui.appliedJobIds
                        JobCard(
                            title = job.title,
                            company = job.companyName,
                            location = job.location,
                            experience = job.experienceLabel,
                            workMode = job.workModeLabel,
                            salary = job.salaryRange,
                            freshness = job.activeLabel,
                            onClick = { onOpenJob(job.id, "Jobs") },
                            modifier = Modifier.padding(horizontal = Space.screen),
                            poster = posterLine(ui.posters[job.id]),
                            badge = {
                                if (applied) AppliedPill()
                                else SaveButton(job.id in ui.savedIds) { viewModel.toggleSaved(job.id) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "Posted by Priya · 12 referred". Null until the poster info has loaded. */
private fun posterLine(p: JobPoster?): String? {
    p ?: return null
    val who = p.firstName?.ifBlank { null }?.let { "Posted by $it, verified employee" } ?: "Posted by a verified employee"
    return if (p.referred > 0) "$who · ${p.referred} referred" else who
}

@Composable
private fun AppliedPill() {
    val c = MaterialTheme.colorScheme
    Pill("Applied", c.primary.copy(alpha = 0.12f), c.primary, icon = Icons.Filled.Check)
}

@Composable
private fun SaveButton(saved: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
        Icon(
            if (saved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
            contentDescription = if (saved) "Remove from saved" else "Save job",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(
    ui: FeedUiState,
    viewModel: FeedViewModel,
    onProfile: () -> Unit,
    searchEnabled: Boolean,
    onOpenFilters: () -> Unit,
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val x = MaterialTheme.extra
    HeroHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.firstName?.let { "$greeting, $it" } ?: greeting,
                    style = MaterialTheme.typography.bodyMedium, color = x.onHeroMuted,
                )
                Text("Find a referral", style = MaterialTheme.typography.headlineMedium, color = x.onHero)
            }
            // Soft outlined avatar in the hero's own colours (was a yellow circle that read like a warning)
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(x.onHero.copy(alpha = 0.16f))
                    .border(1.5.dp, x.onHero.copy(alpha = 0.6f), CircleShape)
                    .clickable(onClickLabel = "Open profile", onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ui.firstName?.firstOrNull()?.uppercase() ?: "🙂",
                    color = x.onHero, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                )
            }
        }
        Text(
            "Every role here is posted by someone who works there.",
            style = MaterialTheme.typography.bodyMedium, color = x.onHeroMuted,
        )
        if (searchEnabled) {
            TextField(
                value = ui.filter.query,
                onValueChange = viewModel::onQuery,
                placeholder = { Text("Search role, skill or company", maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ui.filter.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQuery("") }) { Icon(Icons.Filled.Clear, "Clear search") }
                        }
                        IconButton(onClick = onOpenFilters) {
                            val n = ui.filter.filterCount
                            BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) {
                                Icon(AppIcons.Tune, contentDescription = "Filters")
                            }
                        }
                    }
                },
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
        }
        Spacer(Modifier.size(Space.xs))
    }
}

/** All jobs | Best matches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(bestMatches: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
        listOf(false to "All jobs", true to "Best matches").forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = bestMatches == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.primary.copy(alpha = 0.12f),
                    activeContentColor = c.primary,
                    activeBorderColor = c.primary.copy(alpha = 0.5f),
                    inactiveBorderColor = c.outline,
                ),
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun chipColors(): SelectableChipColors {
    val c = MaterialTheme.colorScheme
    // A soft tint of the brand teal for selected chips, matching the bottom bar
    return FilterChipDefaults.filterChipColors(
        selectedContainerColor = c.primary.copy(alpha = 0.12f),
        selectedLabelColor = c.primary,
        selectedLeadingIconColor = c.primary,
        selectedTrailingIconColor = c.primary,
    )
}

@Composable
private fun chipBorder(selected: Boolean): BorderStroke? = if (selected) {
    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
} else null

/** Quick filters under the search box. The filter button in the search box opens all of them. */
@Composable
private fun Filters(ui: FeedUiState, viewModel: FeedViewModel) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.screen),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterOptions(ui, viewModel)
    }
}

/** All filters in a sheet, wrapped onto rows. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(ui: FeedUiState, viewModel: FeedViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.screen).navigationBarsPadding()
                .padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (ui.filter.filterCount > 0) SmallButton("Clear all", viewModel::clearFilters)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                FilterOptions(ui, viewModel)
            }
            SmallButton(
                if (ui.loading || ui.refreshing) "Show results" else "Show ${ui.jobs.size} roles",
                onDismiss, filled = true,
            )
        }
    }
}

@Composable
private fun FilterOptions(ui: FeedUiState, viewModel: FeedViewModel) {
    var companyMenu by remember { mutableStateOf(false) }
    val colors = chipColors()
    val savedOnly = ui.filter.savedIds != null
    FilterChip(
        selected = savedOnly,
        onClick = viewModel::toggleSavedOnly,
        label = { Text(if (ui.savedIds.isEmpty()) "Saved" else "Saved (${ui.savedIds.size})") },
        leadingIcon = {
            Icon(if (savedOnly) AppIcons.Bookmark else AppIcons.BookmarkBorder, contentDescription = null,
                modifier = Modifier.size(16.dp))
        },
        colors = colors, border = chipBorder(savedOnly),
    )
    FilterChip(
        selected = ui.filter.fitsYears != null,
        onClick = viewModel::toggleFitsMe,
        enabled = ui.myYears != null,
        label = { Text(ui.myYears?.let { "Fits my $it yrs" } ?: "Fits my experience") },
        colors = colors, border = chipBorder(ui.filter.fitsYears != null),
    )
    listOf("onsite" to "Onsite", "hybrid" to "Hybrid", "remote" to "Remote").forEach { (value, label) ->
        val on = value in ui.filter.workModes
        FilterChip(
            selected = on,
            onClick = { viewModel.toggleWorkMode(value) },
            label = { Text(label) },
            colors = colors, border = chipBorder(on),
        )
    }
    Box {
        val on = ui.filter.companyId != null
        FilterChip(
            selected = on,
            onClick = { companyMenu = true },
            label = { Text(ui.selectedCompanyName ?: "Company") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = colors, border = chipBorder(on),
        )
        DropdownMenu(expanded = companyMenu, onDismissRequest = { companyMenu = false }) {
            DropdownMenuItem(text = { Text("Any company") }, onClick = { companyMenu = false; viewModel.setCompany(null) })
            ui.companies.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { companyMenu = false; viewModel.setCompany(c.id) })
            }
        }
    }
}
