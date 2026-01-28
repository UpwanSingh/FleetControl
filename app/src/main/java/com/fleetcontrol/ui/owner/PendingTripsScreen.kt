package com.fleetcontrol.ui.owner

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.data.entities.FirestoreTrip
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.DateUtils
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending Trips Approval Screen
 * Security Hardening: Owner must approve driver-submitted trips
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTripsScreen(
    pendingTrips: StateFlow<List<FirestoreTrip>>,
    isLoading: StateFlow<Boolean>,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val trips by pendingTrips.collectAsState()
    val loading by isLoading.collectAsState()
    
    PendingTripsScreenContent(
        trips = trips,
        loading = loading,
        onApprove = onApprove,
        onReject = onReject,
        onBack = onBack
    )
}

/**
 * Simple version with plain parameters for NavGraph integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTripsScreenSimple(
    pendingTrips: List<FirestoreTrip>,
    isLoading: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onBack: () -> Unit
) {
    PendingTripsScreenContent(
        trips = pendingTrips,
        loading = isLoading,
        onApprove = onApprove,
        onReject = onReject,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingTripsScreenContent(
    trips: List<FirestoreTrip>,
    loading: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onBack: () -> Unit
) {
    
    Scaffold(
        // Clean TopAppBar style header (Consistent with Client Screen)
        topBar = {
            Surface(
                color = androidx.compose.ui.graphics.Color.White, // Pure White for contrast
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, FleetColors.SurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FleetColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pending Approvals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = FleetColors.Surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = FleetColors.Success
                )
            } else if (trips.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = FleetColors.Success
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "All Caught Up!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = FleetColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No pending trips to approve",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FleetColors.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(FleetDimens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trips, key = { it.id }) { trip ->
                        PendingTripCard(
                            trip = trip,
                            onApprove = remember(trip.id) { { onApprove(trip.id) } },
                            onReject = remember(trip.id) { { reason -> onReject(trip.id, reason) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingTripCard(
    trip: FirestoreTrip,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(FleetDimens.SpacingMedium)
        ) {
            // Header: Driver & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = trip.driverName.ifEmpty { "Unknown Driver" },
                        style = MaterialTheme.typography.titleMedium,
                        color = FleetColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = DateUtils.formatDate(trip.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                }
                
                // Status badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FleetColors.WarningLight
                ) {
                    Text(
                        text = "PENDING",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = FleetColors.Warning,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = FleetColors.Divider)
            Spacer(modifier = Modifier.height(12.dp))
            
            // Trip details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TripDetailItem(
                    icon = Icons.Default.ShoppingBag,
                    value = "${trip.bags}",
                    label = "Bags"
                )
                TripDetailItem(
                    icon = Icons.Default.AttachMoney,
                    value = CurrencyUtils.format(trip.rate),
                    label = "Rate"
                )
                TripDetailItem(
                    icon = Icons.Default.Payments,
                    value = CurrencyUtils.format(trip.totalAmount),
                    label = "Total"
                )
            }
            
            if (trip.clientName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = FleetColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Client: ${trip.clientName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FleetColors.Error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
                
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FleetColors.Success
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
    
    // Reject dialog
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Trip") },
            text = {
                Column {
                    Text("Are you sure you want to reject this trip?")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReject(rejectReason)
                        showRejectDialog = false
                        rejectReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Error)
                ) {
                    Text("Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TripDetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FleetColors.Info,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = FleetColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FleetColors.TextSecondary
        )
    }
}
