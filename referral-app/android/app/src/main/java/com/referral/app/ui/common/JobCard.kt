package com.referral.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

/**
 * The job card used by Jobs (All and Best matches). Kept to a few things at a glance: title, company,
 * one line of where/experience/mode, who posted it, and salary + how fresh the post is. Team, skills
 * and the rest are on the job screen. [badge] sits top-right (match score, Applied, bookmark);
 * [footer] goes under a divider.
 */
@Composable
fun JobCard(
    title: String,
    company: String,
    location: String,
    experience: String,
    workMode: String,
    salary: String?,
    freshness: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Priya · 12 referred": the referrer behind the post, what sets this apart from a job board. */
    poster: String? = null,
    badge: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            CompanyAvatar(company)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    company,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            badge?.invoke()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                listOf(location, experience, workMode).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        poster?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (salary != null || freshness != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                salary?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(Space.md))
                }
                Spacer(Modifier.weight(1f))
                freshness?.let {
                    // Green dot + short text: scannable, and leaves room for the salary on small phones
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.extra.success))
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
        footer?.let {
            HorizontalDivider(color = MaterialTheme.extra.cardBorder)
            it(this)
        }
    }
}
