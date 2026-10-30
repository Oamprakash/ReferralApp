package com.referral.app.ui.matches

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.JobMatch
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.JobCard
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.ScoreRing
import com.referral.app.ui.common.SmallButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

@OptIn(ExperimentalMaterial3Api::class)   // PullToRefreshBox / scrollable tab row
@Composable
fun MatchesScreen(
    onOpenJob: (String) -> Unit,
    onEditProfile: () -> Unit,
    viewModel: MatchesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { AppTopBar("For you", subtitle = "Open roles ranked by your skills and experience") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (ui.loading) {
                LoadingBox()
                return@PullToRefreshBox
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                ui.error?.let { m -> item { Banner(m, Tone.ERROR) } }
                if (ui.profileThin) {
                    item {
                        Banner(
                            "Add skills to your profile, or upload your resume, for sharper matches.",
                            Tone.WARNING,
                            action = { SmallButton("Update profile", onEditProfile) },
                        )
                    }
                }
                if (ui.error == null && ui.matches.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Star,
                            title = "No matches yet",
                            body = "Nothing open fits your profile right now. New posts come in every day.",
                            action = { SmallButton("Update profile", onEditProfile, filled = true) },
                        )
                    }
                }
                items(ui.matches, key = { it.id }) { m -> MatchCard(m, ui.myYears, onClick = { onOpenJob(m.id) }) }
                if (ui.matches.isNotEmpty()) {
                    item {
                        Text(
                            "Same score referrers see when you apply: up to 50 for experience fit, up to 50 for skills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Space.sm),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(m: JobMatch, myYears: Int?, onClick: () -> Unit) {
    JobCard(
        title = m.title,
        company = m.companyName,
        team = m.team,
        location = m.location,
        experience = m.experienceLabel,
        workMode = m.workModeLabel,
        salary = m.salaryRange,
        freshness = null,
        onClick = onClick,
        badge = { ScoreRing(m.score) },
        footer = {
            ScoreBar("Skills", m.skillPoints, skillsCaption(m))
            ScoreBar("Experience", m.experiencePoints(myYears), experienceCaption(m, myYears))
            if (m.have.isNotEmpty() || m.missing.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.have.forEach { Pill("✓ $it", MaterialTheme.extra.successContainer, MaterialTheme.extra.onSuccessContainer) }
                    m.missing.forEach { Pill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        },
    )
}

/** One half of the score: label, points out of 50, a bar, and why. */
@Composable
private fun ScoreBar(label: String, points: Int, caption: String) {
    val color = when {
        points >= 40 -> MaterialTheme.extra.success
        points >= 20 -> MaterialTheme.extra.warning
        else -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("$points/50", style = MaterialTheme.typography.labelMedium, color = color)
        }
        LinearProgressIndicator(
            progress = { points / 50f },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun skillsCaption(m: JobMatch): String {
    val total = m.have.size + m.missing.size
    return if (total == 0) "No skills listed on this post" else "You have ${m.have.size} of $total listed skills"
}

private fun experienceCaption(m: JobMatch, years: Int?): String = when {
    years == null -> "Add your years of experience to your profile"
    m.experiencePoints(years) == 50 -> "$years yrs fits the ${m.expMin}–${m.expMax} yr range"
    years > m.expMax -> "$years yrs is above the ${m.expMin}–${m.expMax} yr range"
    else -> "$years yrs is below the ${m.expMin}–${m.expMax} yr range"
}
