package com.referral.app.ui.matches

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.JobMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onBack: () -> Unit,
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
        topBar = {
            TopAppBar(
                title = { Text("Best matches") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@PullToRefreshBox
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Ranked by your experience and skills, the same score referrers see when you apply.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ui.error?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }
                if (ui.profileThin) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Add skills to your profile (or upload your resume) for better matches.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = onEditProfile) { Text("Update profile") }
                        }
                    }
                }
                if (ui.error == null && ui.matches.isEmpty()) {
                    item { Text("No open roles match your profile right now. New posts come in daily.") }
                }
                items(ui.matches, key = { it.id }) { m -> MatchCard(m, ui.myYears, onClick = { onOpenJob(m.id) }) }
            }
        }
    }
}

@Composable
private fun MatchCard(m: JobMatch, myYears: Int?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${m.score}% match", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { m.score / 100f }, modifier = Modifier.fillMaxWidth())
            Text(
                listOfNotNull(m.companyName, m.team, m.location).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("${m.experienceLabel} · ${m.workModeLabel}", style = MaterialTheme.typography.bodySmall)
            ScoreBreakdown(m, myYears)
            if (m.have.isNotEmpty()) {
                Text("You have: ${m.have.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (m.missing.isNotEmpty()) {
                Text("Missing: ${m.missing.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (m.have.isEmpty() && m.missing.isEmpty()) {
                Text("No skills listed on this post, so it's matched on experience only.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(m.confirmedLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Why the score is what it is: two halves of 50, the same rules referrers' scores use. */
@Composable
private fun ScoreBreakdown(m: JobMatch, myYears: Int?) {
    val total = m.have.size + m.missing.size
    val skills = if (total == 0) "Skills: none listed on the post" else "Skills: ${m.have.size} of $total"
    val expPts = m.experiencePoints(myYears)
    val exp = when {
        myYears == null -> "Experience: add your years to your profile"
        expPts == 50 -> "Experience: $myYears yrs, in range"
        myYears > m.expMax -> "Experience: $myYears yrs, above the ${m.expMin}–${m.expMax} yr range"
        else -> "Experience: $myYears yrs, below the ${m.expMin}–${m.expMax} yr range"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$skills → ${m.skillPoints}/50", style = MaterialTheme.typography.bodySmall)
        Text(
            "$exp → $expPts/50",
            style = MaterialTheme.typography.bodySmall,
            color = if (expPts < 50) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
