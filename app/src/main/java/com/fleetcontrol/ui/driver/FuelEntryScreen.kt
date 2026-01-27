package com.fleetcontrol.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.utils.ValidationUtils
import com.fleetcontrol.viewmodel.driver.DriverFuelViewModel

/**
 * Fuel Entry Screen - Premium Polished Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEntryScreen(
    viewModel: DriverFuelViewModel,
    onBack: () -> Unit
) {
    val recentEntries by viewModel.recentFuelEntries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val entrySaved by viewModel.entrySaved.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(entrySaved) {
        if (entrySaved) {
            showAddDialog = false
            viewModel.resetEntrySaved()
        }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Fuel Log", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    navigationIconContentColor = FleetColors.TextPrimary,
                    actionIconContentColor = FleetColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshEntries() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
                containerColor = FleetColors.Primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Fuel", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FleetDimens.SpacingMedium),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.ErrorLight)
                ) {
                    Row(
                        modifier = Modifier.padding(FleetDimens.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = FleetColors.Error)
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(it, color = FleetColors.Error)
                    }
                }
            }
            
            if (recentEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp) // Keep large size or add IconXLarge * 2? 80dp is custom.
                                .clip(CircleShape)
                                .background(FleetColors.SurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = FleetColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                        Text(
                            "No fuel entries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                        Text(
                            "Tap + to add your first fuel entry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(FleetDimens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
                ) {
                    // Total Fuel Stats Card
                    item {
                        val totalAmount = recentEntries.sumOf { it.amount }
                        val totalLiters = recentEntries.sumOf { it.liters }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                            colors = CardDefaults.cardColors(containerColor = FleetColors.Primary),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FleetDimens.SpacingLarge),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        CurrencyUtils.format(totalAmount),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = FleetColors.TextOnDark
                                    )
                                    Text(
                                        if (totalLiters > 0) "Total Fuel • ${String.format("%.1f", totalLiters)}L"
                                        else "Total Fuel Expense",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FleetColors.TextOnDarkSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(FleetDimens.ButtonHeight)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(FleetDimens.IconLarge) // 32dp approx or 28dp
                                    )
                                }
                            }
                        }
                    }
                    
                    items(recentEntries) { entry ->
                        FuelEntryCard(entry)
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddFuelDialog(
            isLoading = isLoading,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, liters, station ->
                viewModel.addFuelEntry(amount, liters, 0.0, station)
            }
        )
    }
}

@Composable
private fun FuelEntryCard(entry: FuelEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(FleetDimens.CornerMedium))
                    .background(FleetColors.WarningLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = FleetColors.Warning,
                    modifier = Modifier.size(FleetDimens.IconMedium)
                )
            }
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fuelStation ?: "Fuel",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = FleetColors.TextPrimary
                )
                Text(
                    text = if (entry.liters > 0) "${String.format("%.1f", entry.liters)}L • ${DateUtils.formatDate(entry.entryDate)}" 
                           else DateUtils.formatDate(entry.entryDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
            Text(
                text = "-${CurrencyUtils.format(entry.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FleetColors.Error
            )
        }
    }
}

@Composable
private fun AddFuelDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAdd: (Double, Double, String?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    
    val amountValidation = remember(amount) { ValidationUtils.validateAmount(amount) }
    val isValid = amountValidation.isValid && (amount.toDoubleOrNull() ?: 0.0) > 0
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Default.LocalGasStation, contentDescription = "Fuel icon", tint = FleetColors.Primary) },
        title = { Text("Add Fuel Entry", color = FleetColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)) {
                // Helper text
                Text(
                    text = "Record your fuel expense below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("e.g., 500") },
                    isError = amount.isNotEmpty() && !amountValidation.isValid,
                    supportingText = {
                        Text(
                            if (amount.isNotEmpty() && !amountValidation.isValid) 
                                amountValidation.errorMessage ?: ""
                            else 
                                "Required: Enter the total fuel cost",
                            color = if (amount.isNotEmpty() && !amountValidation.isValid) FleetColors.Error else FleetColors.TextTertiary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CurrencyRupee,
                            contentDescription = "Amount icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Fuel amount field. Enter the total cost of fuel in rupees." },
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    )
                )
                
                OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Liters (optional)") },
                    placeholder = { Text("e.g., 10.5") },
                    supportingText = { 
                        Text(
                            "Optional: Enter fuel quantity for tracking",
                            color = FleetColors.TextTertiary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.LocalGasStation,
                            contentDescription = "Liters icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Fuel liters field. Optional - enter the quantity of fuel in liters." },
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    )
                )
                
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text("Station Name (optional)") },
                    placeholder = { Text("e.g., HP Petrol Pump") },
                    supportingText = { 
                        Text(
                            "Optional: Enter station name for reference",
                            color = FleetColors.TextTertiary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = "Station icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Fuel station name field. Optional - enter the name of the petrol pump." },
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: 0.0
                    val litersVal = liters.toDoubleOrNull() ?: 0.0
                    if (amountVal > 0) {
                        onAdd(amountVal, litersVal, station.takeIf { it.isNotBlank() })
                    }
                },
                enabled = isValid && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(FleetDimens.IconSmall),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add Entry")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.TextSecondary)
            }
        }
    )
}
