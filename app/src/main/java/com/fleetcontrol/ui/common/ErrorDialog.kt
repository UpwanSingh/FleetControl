package com.fleetcontrol.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Generic Error Dialog for Global Error Handling.
 * Roadmap Item: Global Error Handling Strategy.
 */
@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    if (message.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                if (onRetry != null) {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                } else {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            },
            dismissButton = if (onRetry != null) {
                {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            } else null
        )
    }
}
