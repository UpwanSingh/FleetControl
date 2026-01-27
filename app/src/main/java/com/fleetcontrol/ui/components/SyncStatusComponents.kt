package com.fleetcontrol.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.utils.DateUtils

/**
 * Sync Status UI Components
 * Part of Sync Audit Fix #1: Users MUST see sync status
 */

/**
 * Sync state for a record
 */
enum class SyncState {
    SYNCED,         // Successfully uploaded
    PENDING,        // Waiting to sync
    SYNCING,        // Currently syncing
    FAILED          // Sync failed after retries
}

/**
 * Determine sync state from TripEntity
 */
fun TripEntity.getSyncState(): SyncState {
    return when {
        isSynced -> SyncState.SYNCED
        syncAttempts >= 3 -> SyncState.FAILED
        syncAttempts > 0 -> SyncState.PENDING // Retry in progress
        else -> SyncState.PENDING
    }
}

/**
 * Compact sync status badge for trip cards
 */
@Composable
fun SyncStatusBadge(
    trip: TripEntity,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val syncState = trip.getSyncState()
    
    val (icon, color, text) = when (syncState) {
        SyncState.SYNCED -> {
            val timeText = trip.lastSyncedAt?.let { DateUtils.formatRelativeTime(it) } ?: "Synced"
            Triple(Icons.Filled.CloudDone, FleetColors.Success, timeText)
        }
        SyncState.PENDING -> Triple(Icons.Filled.CloudUpload, FleetColors.Warning, "Pending")
        SyncState.SYNCING -> Triple(Icons.Filled.Sync, FleetColors.Info, "Syncing...")
        SyncState.FAILED -> Triple(Icons.Filled.CloudOff, FleetColors.Error, "Retry")
    }
    
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300)
    )
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(animatedColor.copy(alpha = 0.15f))
            .clickable(enabled = syncState == SyncState.FAILED) { onRetry() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(14.dp),
            tint = animatedColor
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = animatedColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Global sync status indicator for app bar
 */
@Composable
fun GlobalSyncIndicator(
    pendingCount: Int,
    failedCount: Int,
    lastSyncedAt: Long?,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color, text) = when {
        failedCount > 0 -> Triple(Icons.Filled.CloudOff, FleetColors.Error, "$failedCount failed")
        pendingCount > 0 -> Triple(Icons.Filled.CloudUpload, FleetColors.Warning, "$pendingCount pending")
        else -> Triple(Icons.Filled.CloudDone, FleetColors.Success, "All synced")
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(enabled = failedCount > 0) { onRetryClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color
        )
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
            lastSyncedAt?.let {
                Text(
                    text = "Last: ${DateUtils.formatRelativeTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Last synced timestamp display
 */
@Composable
fun LastSyncedInfo(
    lastSyncedAt: Long?,
    modifier: Modifier = Modifier
) {
    val text = if (lastSyncedAt != null) {
        "Synced ${DateUtils.formatRelativeTime(lastSyncedAt)}"
    } else {
        "Not yet synced"
    }
    
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = FleetColors.TextSecondary,
        modifier = modifier
    )
}


