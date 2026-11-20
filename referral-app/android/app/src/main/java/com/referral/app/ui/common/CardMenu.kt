package com.referral.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** One entry in a card's "More" menu. */
data class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The ⋮ button on a card: secondary actions live here so each card shows one main action.
 * Shows nothing when [actions] is empty.
 */
@Composable
fun CardMenu(actions: List<MenuAction>, enabled: Boolean = true, description: String = "More actions") {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = description)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Text(
                            a.label,
                            color = if (a.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}
