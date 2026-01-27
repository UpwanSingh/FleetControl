package com.fleetcontrol.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fleetcontrol.data.entities.DriverRateSlabEntity
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.viewmodel.settings.RateSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateSettingsScreen(
    viewModel: RateSettingsViewModel,
    onBack: () -> Unit
) {
    val slabs by viewModel.slabs.collectAsState()
    val labourCost by viewModel.labourCost.collectAsState()
    var editingSlab by remember { mutableStateOf<DriverRateSlabEntity?>(null) }
    var editingLabour by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Rate Settings", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    navigationIconContentColor = FleetColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = FleetColors.Primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Slab", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = FleetDimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
        ) {
            item {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingXSmall))
                // Labour Cost Section
                Text(
                    "Labour Cost",
                    style = MaterialTheme.typography.titleMedium,
                    color = FleetColors.Primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Cost collected from owner for loading/unloading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                LabourCostCard(
                    cost = labourCost,
                    onEdit = { editingLabour = true }
                )
                
                Divider(modifier = Modifier.padding(vertical = FleetDimens.SpacingMedium), color = FleetColors.Divider)
            }

            if (slabs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FleetDimens.SpacingXLarge), // 32dp
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No rate slabs defined.\nTap + to add one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = FleetColors.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Driver Rate Slabs",
                        style = MaterialTheme.typography.titleMedium,
                        color = FleetColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Define how much drivers are paid per bag based on distance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                }

                items(slabs) { slab ->
                    RateSlabCard(
                        slab = slab,
                        onEdit = { editingSlab = slab },
                        onDelete = { viewModel.deleteSlab(slab) }
                    )
                }
            }
            // Bottom spacer for FAB
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
    
    if (editingLabour) {
        EditLabourDialog(
            currentCost = labourCost,
            onDismiss = { editingLabour = false },
            onSave = { newCost ->
                viewModel.setLabourCost(newCost)
                editingLabour = false
            }
        )
    }
    
    if (showAddDialog) {
        AddSlabDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { min, max, rate ->
                viewModel.addSlab(min, max, rate)
                showAddDialog = false
            }
        )
    }

    if (editingSlab != null) {
        EditRateDialog(
            slab = editingSlab!!,
            onDismiss = { editingSlab = null },
            onSave = { newRate ->
                viewModel.updateSlab(editingSlab!!, newRate)
                editingSlab = null
            }
        )
    }
}

@Composable
fun RateSlabCard(
    slab: DriverRateSlabEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${slab.minDistance.toInt()} - ${if (slab.maxDistance == Double.MAX_VALUE) "∞" else slab.maxDistance.toInt().toString()} km",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Text(
                    "Distance Range",
                    style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        CurrencyUtils.formatRate(slab.ratePerBag),
                        style = MaterialTheme.typography.titleLarge,
                        color = FleetColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "per bag",
                        style = MaterialTheme.typography.labelSmall,
                        color = FleetColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Edit",
                        tint = FleetColors.Primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = FleetColors.Error
                    )
                }
            }
        }
    }
}

@Composable
fun EditRateDialog(
    slab: DriverRateSlabEntity,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var rate by remember { mutableStateOf(slab.ratePerBag.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Rate") },
        text = {
            Column {
                Text(
                    "Range: ${slab.minDistance.toInt()} - ${if (slab.maxDistance == Double.MAX_VALUE) "∞" else slab.maxDistance.toInt().toString()} km",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                OutlinedTextField(
                    value = rate,
                    onValueChange = { 
                        rate = it
                        error = null
                    },
                    label = { Text("Rate per Bag (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRate = rate.toDoubleOrNull()
                    if (newRate != null && newRate > 0) {
                        onSave(newRate)
                    } else {
                        error = "Invalid rate"
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddSlabDialog(
    onDismiss: () -> Unit,
    onAdd: (Double, Double, Double) -> Unit
) {
    var minDistance by remember { mutableStateOf("") }
    var maxDistance by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isInfinite by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Rate Slab") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)) {
                OutlinedTextField(
                    value = minDistance,
                    onValueChange = { minDistance = it },
                    label = { Text("Min Distance (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (!isInfinite) {
                    OutlinedTextField(
                        value = maxDistance,
                        onValueChange = { maxDistance = it },
                        label = { Text("Max Distance (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isInfinite,
                        onCheckedChange = { isInfinite = it }
                    )
                    Text("Infinite Max Distance (e.g. 500+)")
                }

                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Rate per Bag (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (error != null) {
                    Text(
                        text = error!!,
                        color = FleetColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val min = minDistance.toDoubleOrNull()
                    val max = if (isInfinite) Double.MAX_VALUE else maxDistance.toDoubleOrNull()
                    val r = rate.toDoubleOrNull()
                    
                    if (min != null && max != null && r != null) {
                        if (min >= max) {
                            error = "Min distance must be less than Max"
                        } else {
                            onAdd(min, max, r)
                        }
                    } else {
                        error = "Please enter valid numbers"
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LabourCostCard(
    cost: Double,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Standard Labour",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Text(
                    "Loading & Unloading",
                    style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        CurrencyUtils.formatRate(cost),
                        style = MaterialTheme.typography.titleLarge,
                        color = FleetColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "per bag",
                        style = MaterialTheme.typography.labelSmall,
                        color = FleetColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Edit",
                        tint = FleetColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
fun EditLabourDialog(
    currentCost: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var rate by remember { mutableStateOf(currentCost.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Labour Cost") },
        text = {
            Column {
                Text(
                    "Set the cost for loading/unloading per bag.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                OutlinedTextField(
                    value = rate,
                    onValueChange = { 
                        rate = it
                        error = null
                    },
                    label = { Text("Cost per Bag (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRate = rate.toDoubleOrNull()
                    if (newRate != null && newRate >= 0) {
                        onSave(newRate)
                    } else {
                        error = "Invalid cost"
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
