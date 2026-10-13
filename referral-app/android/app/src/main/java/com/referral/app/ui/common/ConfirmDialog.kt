package com.referral.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * A pending confirmation. Hold it in state (`var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }`),
 * set it from a button, and render it with [ConfirmDialog].
 */
data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    /** Red confirm button for actions that end something (decline, close, withdraw, sign out). */
    val destructive: Boolean = false,
    val onConfirm: () -> Unit,
)

/** Shared confirmation for actions that notify someone, use up a limit, or can't be undone. */
@Composable
fun ConfirmDialog(request: ConfirmRequest?, onDismiss: () -> Unit) {
    if (request == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(request.title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(request.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            Button(
                onClick = { onDismiss(); request.onConfirm() },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (request.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(request.confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
