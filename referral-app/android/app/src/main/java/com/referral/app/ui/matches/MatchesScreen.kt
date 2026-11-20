package com.referral.app.ui.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.referral.app.data.model.JobMatch
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.JobCard
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.MatchTier
import com.referral.app.ui.common.ScoreRing
import com.referral.app.ui.common.colors
import com.referral.app.ui.theme.tabular
import com.referral.app.ui.common.SmallButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

/**
 * The "Best matches" view inside the Jobs tab (it used to be its own "For you" tab): open roles ranked
 * by the user's skills and experience. Items go straight into the Jobs list.
 */
fun LazyListScope.bestMatchItems(
    ui: MatchesUiState,
    onOpenJob: (String) -> Unit,
    onEditProfile: () -> Unit,
) {
    val pad = Modifier.padding(horizontal = Space.screen)
    if (ui.loading) {
        item { LoadingBox(Modifier.padding(top = 60.dp)) }
        return
    }
    ui.error?.let { m -> item { Banner(m, Tone.ERROR, pad) } }
    if (ui.profileThin) {
        item {
            Banner(
                "Add skills to your profile, or upload your resume, for sharper matches.",
                Tone.WARNING,
                pad,
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
    items(ui.matches, key = { "m-" + it.id }) { m ->
        MatchCard(m, ui.myYears, onClick = { onOpenJob(m.id) }, modifier = pad.padding(bottom = Space.xs))
    }
    if (ui.matches.isNotEmpty()) {
        item {
            Text(
                "Same score referrers see when you apply: up to 50 for experience fit, up to 50 for skills.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.sm),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(m: JobMatch, myYears: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    JobCard(
        title = m.title,
        company = m.companyName,
        location = m.location,
        experience = m.experienceLabel,
        workMode = m.workModeLabel,
        salary = m.salaryRange,
        freshness = m.activeLabel,
        onClick = onClick,
        modifier = modifier,
        badge = { ScoreRing(m.score) },
        footer = {
            // One line at a glance; the full breakdown opens on tap.
            var open by rememberSaveable(m.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    matchSummary(m, myYears),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SmallButton(if (open) "Hide" else "Why this score", { open = !open })
            }
            if (open) {
                ScoreBar("Skills", m.skillPoints, skillsCaption(m))
                ScoreBar("Experience", m.experiencePoints(myYears), experienceCaption(m, myYears))
                if (m.have.isNotEmpty() || m.missing.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        m.have.forEach { Pill("✓ $it", MaterialTheme.extra.successContainer, MaterialTheme.extra.onSuccessContainer) }
                        m.missing.forEach { Pill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
    )
}

/** One half of the score: label, points out of 50, a bar, and why. */
@Composable
private fun ScoreBar(label: String, points: Int, caption: String) {
    // Tier by share of the 50 points: ≥70% green, ≥40% amber, below slate (see MatchTier)
    val t = MatchTier.ofPoints(points).colors()
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("$points/50", style = MaterialTheme.typography.labelMedium.tabular(), color = t.text)
        }
        LinearProgressIndicator(
            progress = { points / 50f },
            modifier = Modifier.fillMaxWidth(),
            color = t.bar,
            trackColor = t.track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun matchSummary(m: JobMatch, years: Int?): String {
    val total = m.have.size + m.missing.size
    val skills = if (total == 0) null else "${m.have.size} of $total skills"
    val exp = when {
        years == null -> null
        m.experiencePoints(years) == 50 -> "experience fits"
        years > m.expMax -> "above the experience range"
        else -> "below the experience range"
    }
    return listOfNotNull(skills, exp).joinToString(" · ").ifEmpty { "Tap for the score breakdown" }
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
