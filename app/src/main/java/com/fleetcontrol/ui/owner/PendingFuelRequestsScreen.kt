package com.fleetcontrol.ui.owner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.data.entities.FirestoreFuelRequest
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.owner.OwnerDashboardViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Screen for Owners to review pending fuel requests
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingFuelRequestsScreen(
    viewModel: OwnerDashboardViewModel,
    onBack: () -> Unit
) {
    val pendingRequests by viewModel.pendingFuelRequests.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    Scaffold(
        containerColor = com.fleetcontrol.ui.components.FleetColors.Surface,
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, com.fleetcontrol.ui.components.FleetColors.SurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            "Back",
                            tint = com.fleetcontrol.ui.components.FleetColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pending Fuel Requests",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = com.fleetcontrol.ui.components.FleetColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (pendingRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = com.fleetcontrol.ui.components.FleetColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No pending requests", 
                            style = MaterialTheme.typography.titleMedium,
                            color = com.fleetcontrol.ui.components.FleetColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingRequests) { request ->
                        FuelRequestCard(
                            request = request,
                            onApprove = { viewModel.approveFuelRequest(request) },
                            onReject = { reason -> viewModel.rejectFuelRequest(request.id, reason) }
                        )
                    }
                }
            }
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                     color = com.fleetcontrol.ui.components.FleetColors.Primary
                )
            }
        }
    }
}

@Composable
fun FuelRequestCard(
    request: FirestoreFuelRequest,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = com.fleetcontrol.ui.components.FleetColors.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.fleetcontrol.ui.components.FleetDimens.CornerLarge)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = com.fleetcontrol.ui.components.FleetColors.Primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "₹${request.amount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                         color = com.fleetcontrol.ui.components.FleetColors.TextPrimary
                    )
                    Text(
                        text = DateUtils.formatDate(request.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = com.fleetcontrol.ui.components.FleetColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                
                 Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = com.fleetcontrol.ui.components.FleetColors.WarningLight
                ) {
                    Text(
                        text = "PENDING",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = com.fleetcontrol.ui.components.FleetColors.Warning,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (request.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Notes: ${request.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.fleetcontrol.ui.components.FleetColors.TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = com.fleetcontrol.ui.components.FleetColors.Error)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = com.fleetcontrol.ui.components.FleetColors.Success)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
    
    if (showRejectDialog) {
        RejectFuelDialog(
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason -> 
                showRejectDialog = false
                onReject(reason)
            }
        )
    }
}

@Composable
fun RejectFuelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Request") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text("Reject", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
