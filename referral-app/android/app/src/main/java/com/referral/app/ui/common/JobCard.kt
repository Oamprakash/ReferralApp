package com.referral.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

/**
 * The job card used by the feed, Best matches and My applications.
 * [badge] sits top-right (match %, Applied, status); [footer] goes under a divider.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobCard(
    title: String,
    company: String,
    team: String?,
    location: String,
    experience: String,
    workMode: String,
    salary: String?,
    freshness: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            CompanyAvatar(company)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(company, team?.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            badge?.invoke()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaPill(location, icon = Icons.Filled.LocationOn)
            MetaPill(experience)
            MetaPill(workMode)
        }
        if (salary != null || freshness != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                salary?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(Space.md))
                }
                Spacer(Modifier.weight(1f))
                freshness?.let {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.extra.success,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        footer?.let {
            HorizontalDivider(color = MaterialTheme.extra.cardBorder)
            it(this)
        }
    }
}
