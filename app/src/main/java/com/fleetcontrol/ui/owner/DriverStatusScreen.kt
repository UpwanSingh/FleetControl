package com.fleetcontrol.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.ui.components.RefreshableContainer
import com.fleetcontrol.ui.components.SkeletonList
import com.fleetcontrol.viewmodel.owner.DriverManagementViewModel
import com.fleetcontrol.viewmodel.owner.DriverWithStats
import com.fleetcontrol.viewmodel.owner.DriverFilter
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.ValidationUtils

/**
 * Driver Status/List Screen - Premium Polished Design
 * Supports Active/Inactive driver filtering and reactivation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverStatusScreen(
    viewModel: DriverManagementViewModel,
    onDriverClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val activeDrivers by viewModel.drivers.collectAsState()
    val inactiveDrivers by viewModel.inactiveDrivers.collectAsState()
    val currentFilter by viewModel.driverFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    
    // Get drivers based on current filter
    val displayedDrivers = remember(activeDrivers, inactiveDrivers, currentFilter) {
        when (currentFilter) {
            DriverFilter.ACTIVE -> activeDrivers
            DriverFilter.INACTIVE -> inactiveDrivers
            DriverFilter.ALL -> activeDrivers + inactiveDrivers
        }
    }
    
    // Filter drivers based on search
    val filteredDrivers = remember(displayedDrivers, searchQuery) {
        if (searchQuery.isBlank()) displayedDrivers
        else displayedDrivers.filter { 
            it.driver.name.contains(searchQuery, ignoreCase = true) ||
            it.driver.phone.contains(searchQuery)
        }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Drivers", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
                containerColor = FleetColors.Primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Driver", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs
            // BUG FIX: Map DriverFilter enum to UI Tab Index
            // UI Order: Active(0), Inactive(1), All(2)
            // Enum Order: ALL(0), ACTIVE(1), INACTIVE(2)
            val selectedTabIndex = when(currentFilter) {
                DriverFilter.ACTIVE -> 0
                DriverFilter.INACTIVE -> 1
                DriverFilter.ALL -> 2
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = FleetColors.Primary
            ) {
                Tab(
                    selected = currentFilter == DriverFilter.ACTIVE,
                    onClick = { viewModel.setDriverFilter(DriverFilter.ACTIVE) },
                    text = { 
                        Text(
                            "Active (${activeDrivers.size})",
                            fontWeight = if (currentFilter == DriverFilter.ACTIVE) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = currentFilter == DriverFilter.INACTIVE,
                    onClick = { viewModel.setDriverFilter(DriverFilter.INACTIVE) },
                    text = { 
                        Text(
                            "Inactive (${inactiveDrivers.size})",
                            fontWeight = if (currentFilter == DriverFilter.INACTIVE) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = currentFilter == DriverFilter.ALL,
                    onClick = { viewModel.setDriverFilter(DriverFilter.ALL) },
                    text = { 
                        Text(
                            "All (${activeDrivers.size + inactiveDrivers.size})",
                            fontWeight = if (currentFilter == DriverFilter.ALL) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
            
            if (isLoading && displayedDrivers.isEmpty()) {
                // Skeleton loading state
                SkeletonList(itemCount = 4)
            } else if (displayedDrivers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(FleetColors.SurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (currentFilter == DriverFilter.INACTIVE) Icons.Default.PersonOff else Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = FleetColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                        Text(
                            if (currentFilter == DriverFilter.INACTIVE) "No inactive drivers" else "No drivers yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                        Text(
                            if (currentFilter == DriverFilter.INACTIVE) "All your drivers are currently active" 
                            else "Add your first driver to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
            } else {
                RefreshableContainer(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.loadDrivers() }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search drivers...", color = FleetColors.TextTertiary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = FleetColors.TextSecondary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FleetColors.TextSecondary)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = FleetDimens.SpacingMedium)
                                .padding(top = FleetDimens.SpacingMedium),
                            singleLine = true,
                            shape = RoundedCornerShape(FleetDimens.CornerLarge),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FleetColors.Primary,
                                unfocusedBorderColor = FleetColors.Border,
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White
                            )
                        )
                        
                        if (filteredDrivers.isEmpty() && searchQuery.isNotEmpty()) {
                            // Search empty state
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(FleetColors.SurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = FleetColors.TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                                    Text(
                                        "No drivers found",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FleetColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                                    Text(
                                        "Try a different search term",
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
                                // Stats Card
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (currentFilter == DriverFilter.INACTIVE) FleetColors.TextSecondary else FleetColors.Primary
                                        ),
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
                                                    "${filteredDrivers.size}",
                                                    style = MaterialTheme.typography.displaySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FleetColors.TextOnDark
                                                )
                                                Text(
                                                    when (currentFilter) {
                                                        DriverFilter.ACTIVE -> "Active Drivers"
                                                        DriverFilter.INACTIVE -> "Inactive Drivers"
                                                        DriverFilter.ALL -> "Total Drivers"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = FleetColors.TextOnDarkSecondary
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (currentFilter == DriverFilter.INACTIVE) Icons.Default.PersonOff else Icons.Default.People,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                items(filteredDrivers) { driverWithStats ->
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    DriverCard(
                                        driver = driverWithStats,
                                        onClick = { onDriverClick(driverWithStats.driver.id) },
                                        onDeactivate = { viewModel.deactivateDriver(driverWithStats.driver.id) },
                                        onReactivate = { viewModel.reactivateDriver(driverWithStats.driver.id) }
                                    )
                                }
                                
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddDriverDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone, pin ->
                viewModel.addDriver(name, phone, pin)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverCard(
    driver: DriverWithStats,
    onClick: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }
    
    val isActive = driver.driver.isActive
    
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color.White else FleetColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 0.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with status indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isActive) FleetColors.Primary else FleetColors.TextSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = driver.driver.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                // Status indicator dot
                if (!isActive) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(FleetColors.Error)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = driver.driver.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isActive) FleetColors.TextPrimary else FleetColors.TextSecondary
                    )
                    if (!isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = FleetColors.Error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Inactive",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = FleetColors.Error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = CurrencyUtils.format(driver.monthlyEarnings),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = FleetColors.Success
                    )
                    if (driver.outstandingAdvance > 0) {
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FleetColors.ErrorLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "-${CurrencyUtils.format(driver.outstandingAdvance)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FleetColors.Error
                            )
                        }
                    }
                }
            }
            
            // More options menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = FleetColors.TextSecondary
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (isActive) {
                        DropdownMenuItem(
                            text = { Text("Deactivate Driver") },
                            onClick = {
                                showMenu = false
                                confirmTitle = "Deactivate Driver"
                                confirmMessage = "Are you sure you want to deactivate ${driver.driver.name}? They won't be able to log in until reactivated."
                                confirmAction = onDeactivate
                                showConfirmDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PersonOff, contentDescription = null, tint = FleetColors.Warning)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Reactivate Driver") },
                            onClick = {
                                showMenu = false
                                confirmTitle = "Reactivate Driver"
                                confirmMessage = "Are you sure you want to reactivate ${driver.driver.name}? They will be able to log in again."
                                confirmAction = onReactivate
                                showConfirmDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = FleetColors.Success)
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = Color.White,
            title = { Text(confirmTitle, color = FleetColors.TextPrimary) },
            text = { Text(confirmMessage, color = FleetColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAction?.invoke()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmTitle.contains("Deactivate")) FleetColors.Warning else FleetColors.Success
                    )
                ) {
                    Text(if (confirmTitle.contains("Deactivate")) "Deactivate" else "Reactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = FleetColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AddDriverDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, pin: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    
    val nameValidation = remember(name) { ValidationUtils.validateDriverName(name) }
    val phoneValidation = remember(phone) { ValidationUtils.validatePhone(phone) }
    val pinValidation = remember(pin) { ValidationUtils.validatePin(pin) }
    val isValid = nameValidation.isValid && phoneValidation.isValid && pinValidation.isValid
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = FleetColors.Primary) },
        title = { Text("Add New Driver", color = FleetColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Driver Name") },
                    isError = name.isNotEmpty() && !nameValidation.isValid,
                    supportingText = if (name.isNotEmpty() && !nameValidation.isValid) {
                        { Text(nameValidation.errorMessage ?: "", color = FleetColors.Error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() } },
                    label = { Text("Phone Number") },
                    isError = phone.isNotEmpty() && !phoneValidation.isValid,
                    supportingText = if (phone.isNotEmpty() && !phoneValidation.isValid) {
                        { Text(phoneValidation.errorMessage ?: "", color = FleetColors.Error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4-Digit PIN") },
                    isError = pin.isNotEmpty() && !pinValidation.isValid,
                    supportingText = if (pin.isNotEmpty() && !pinValidation.isValid) {
                        { Text(pinValidation.errorMessage ?: "", color = FleetColors.Error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, phone, pin) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
            ) {
                Text("Add Driver")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.TextSecondary)
            }
        }
    )
}
