package com.fleetcontrol.ui.driver

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fleetcontrol.data.entities.ClientEntity
import com.fleetcontrol.data.entities.CompanyEntity
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.ui.components.*
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.driver.DriverTripViewModel
import androidx.paging.compose.collectAsLazyPagingItems

/**
 * Driver Trip Screen - Premium Polished Design
 * Implements Section 4, 9.1 of BUSINESS_LOGIC_SPEC.md
 * 
 * Trip Flow:
 * 1. Select Company (product supplier)
 * 2. Select Client (delivery destination)  
 * 3. Select Pickup Location (suggested nearest shown)
 * 4. Enter Bag Count
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripScreen(
    viewModel: DriverTripViewModel,
    onBack: () -> Unit
) {
    val companies by viewModel.companies.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val pickupLocations by viewModel.pickupLocations.collectAsState()
    val pickupOptionsForClient by viewModel.pickupOptionsForClient.collectAsState()
    val suggestedPickup by viewModel.suggestedPickup.collectAsState()
    val pagedTrips = viewModel.pagedTrips.collectAsLazyPagingItems()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val tripSaved by viewModel.tripSaved.collectAsState()
    val selectedTrip by viewModel.selectedTripForAttachment.collectAsState()
    val tripAttachments by viewModel.tripAttachments.collectAsState()
    val attachmentAdded by viewModel.attachmentAdded.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Track attachment counts for trips
    val attachmentCounts = remember { mutableStateMapOf<Long, Int>() }
    
    // Reset state on trip saved
    LaunchedEffect(tripSaved) {
        if (tripSaved) {
            showAddDialog = false
            viewModel.resetTripSaved()
            viewModel.clearClientSelection()
            pagedTrips.refresh()
        }
    }
    
    // Refresh attachment count when added
    LaunchedEffect(attachmentAdded) {
        if (attachmentAdded) {
            selectedTrip?.let { trip ->
                attachmentCounts[trip.id] = viewModel.getAttachmentCount(trip.id)
            }
            viewModel.resetAttachmentAdded()
        }
    }
    
    Scaffold(
        topBar = {
            PremiumTopAppBar(
                title = "My Trips",
                subtitle = "Record your deliveries",
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = { pagedTrips.refresh() },
                        enabled = pagedTrips.loadState.refresh !is androidx.paging.LoadState.Loading
                    ) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = "Refresh",
                            tint = FleetColors.TextPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                },
                containerColor = FleetColors.Primary,
                contentColor = FleetColors.OnPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Trip", fontWeight = FontWeight.SemiBold) }
            )
        },
        containerColor = FleetColors.Surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error Banner
            AnimatedVisibility(
                visible = error != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                error?.let {
                    ErrorBanner(
                        message = it,
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }
            
            // Content
            when {
                pagedTrips.loadState.refresh is androidx.paging.LoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingState(message = "Loading trips...")
                    }
                }
                pagedTrips.itemCount == 0 -> {
                    EmptyState(
                        icon = Icons.Outlined.LocalShipping,
                        title = "No trips recorded",
                        subtitle = "Start adding your deliveries by tapping the button below",
                        action = {
                            PrimaryButton(
                                text = "Add First Trip",
                                icon = Icons.Default.Add,
                                onClick = { showAddDialog = true },
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(FleetDimens.SpacingMedium),
                        verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
                    ) {
                        // Stats Header
                        item {
                            TripStatsHeader(tripCount = pagedTrips.itemCount)
                        }
                        
                        items(
                            count = pagedTrips.itemCount,
                            key = { index -> pagedTrips[index]?.id ?: index }
                        ) { index ->
                            pagedTrips[index]?.let { trip ->
                                TripCard(
                                    trip = trip,
                                    attachmentCount = attachmentCounts[trip.id] ?: 0,
                                    onClick = remember(trip.id) { { viewModel.selectTripForAttachments(trip) } },
                                    onRetry = remember(trip.id) { { viewModel.retrySync(trip) } }
                                )
                            }
                        }
                        
                        // Loading more indicator
                        if (pagedTrips.loadState.append is androidx.paging.LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(FleetDimens.SpacingMedium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(FleetDimens.IconMedium),
                                        color = FleetColors.Primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        
                        // Bottom spacing for FAB
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
    
    // Add Trip Dialog
    if (showAddDialog) {
        AddTripDialog(
            companies = companies,
            clients = clients,
            pickupLocations = pickupLocations,
            pickupOptionsForClient = pickupOptionsForClient,
            suggestedPickup = suggestedPickup,
            isLoading = isLoading,
            onClientSelected = { clientId -> viewModel.onClientSelected(clientId) },
            onDismiss = { 
                showAddDialog = false
                viewModel.clearClientSelection()
            },
            onAdd = { companyId, clientId, pickupId, bagCount ->
                viewModel.addTrip(companyId, clientId, pickupId, bagCount)
            }
        )
    }
    
    // Trip Detail Bottom Sheet with Attachments
    selectedTrip?.let { trip ->
        TripDetailSheet(
            trip = trip,
            attachments = tripAttachments,
            onDismiss = { viewModel.clearSelectedTrip() },
            onAddAttachment = { uri, type ->
                viewModel.addAttachmentFromGallery(uri, trip.id, type)
            },
            onAddCameraAttachment = { file, _, type ->
                // Note: URI from FileProvider not needed as we process the file directly
                viewModel.addAttachmentFromCamera(file, trip.id, type)
            },
            onDeleteAttachment = { attachment ->
                viewModel.deleteAttachment(attachment)
            }
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(FleetDimens.SpacingMedium),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(containerColor = FleetColors.ErrorLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = FleetColors.Error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = FleetColors.Error
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(FleetDimens.IconMedium)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = FleetColors.Error,
                    modifier = Modifier.size(FleetDimens.IconSmall)
                )
            }
        }
    }
}

@Composable
private fun TripStatsHeader(tripCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = FleetDimens.SpacingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent Trips",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FleetColors.TextPrimary
        )
        StatusBadge(
            text = "$tripCount trips",
            type = StatusType.Info
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripCard(
    trip: TripEntity,
    attachmentCount: Int = 0,
    onClick: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(containerColor = FleetColors.SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(FleetDimens.IconXLarge) // 48dp
                    .clip(CircleShape)
                    .background(FleetColors.SuccessLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = FleetColors.Success,
                    modifier = Modifier.size(FleetDimens.IconMedium)
                )
            }
            
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            
            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
                ) {
                    Text(
                        text = trip.clientName.ifEmpty { "Delivery" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = FleetColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Attachment indicator
                    if (attachmentCount > 0) {
                        Row(
                            modifier = Modifier
                                .background(FleetColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(FleetDimens.CornerSmall))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.Attachment,
                                contentDescription = null,
                                tint = FleetColors.Primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$attachmentCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = FleetColors.Primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
                ) {
                    Icon(
                        Icons.Outlined.Inventory,
                        contentDescription = null,
                        tint = FleetColors.TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${trip.bagCount} bags",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextTertiary
                    )
                    Text(
                        text = DateUtils.formatDateTime(trip.tripDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                }
            }
            
            // Earnings
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.format(trip.calculateDriverEarning()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.Success
                )
                Text(
                    text = "Earned",
                    style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.TextTertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Visual Sync Status (Security Hardening)
                SyncStatusBadge(
                    trip = trip,
                    onRetry = onRetry
                )
            }
            
            // Chevron to indicate clickable
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = FleetColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTripDialog(
    companies: List<CompanyEntity>,
    clients: List<ClientEntity>,
    pickupLocations: List<PickupLocationEntity>,
    pickupOptionsForClient: List<PickupClientDistanceEntity>,
    suggestedPickup: PickupClientDistanceEntity?,
    isLoading: Boolean,
    onClientSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (Long, Long, Long, Int) -> Unit
) {
    var selectedCompany by remember { mutableStateOf<CompanyEntity?>(null) }
    var selectedClient by remember { mutableStateOf<ClientEntity?>(null) }
    var selectedPickup by remember { mutableStateOf<PickupLocationEntity?>(null) }
    var selectedDistance by remember { mutableStateOf<Double?>(null) }
    var bagCount by remember { mutableStateOf("") }
    var companyExpanded by remember { mutableStateOf(false) }
    var clientExpanded by remember { mutableStateOf(false) }
    var pickupExpanded by remember { mutableStateOf(false) }
    
    // When client is selected, set suggested pickup if available
    LaunchedEffect(suggestedPickup, selectedClient) {
        if (selectedClient != null && suggestedPickup != null) {
            val pickup = pickupLocations.find { it.id == suggestedPickup.pickupLocationId }
            if (pickup != null) {
                selectedPickup = pickup
                selectedDistance = suggestedPickup.distanceKm
            }
        }
    }
    
    val isValid = selectedCompany != null && 
                  selectedClient != null &&
                  selectedPickup != null && 
                  selectedDistance != null &&
                  (bagCount.toIntOrNull() ?: 0) > 0
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
        containerColor = FleetColors.SurfaceElevated,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(FleetColors.SuccessLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.LocalShipping,
                        contentDescription = null,
                        tint = FleetColors.Success,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "New Trip",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)) {
                // Step indicator for guidance
                Text(
                    text = "Fill in the trip details below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                
                // Company dropdown
                ExposedDropdownMenuBox(
                    expanded = companyExpanded,
                    onExpandedChange = { companyExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCompany?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Company (Product From)") },
                        placeholder = { Text("Tap to select company") },
                        supportingText = { 
                            Text(
                                "Step 1: Select the company supplying the product",
                                color = FleetColors.TextTertiary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Business,
                                contentDescription = "Company icon",
                                tint = FleetColors.TextSecondary
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(companyExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .semantics { contentDescription = "Company selection dropdown. Tap to choose the company supplying the product." },
                        shape = RoundedCornerShape(FleetDimens.CornerMedium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FleetColors.Primary,
                            unfocusedBorderColor = FleetColors.Border
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = companyExpanded,
                        onDismissRequest = { companyExpanded = false }
                    ) {
                        companies.forEach { company ->
                            DropdownMenuItem(
                                text = { Text(company.name) },
                                onClick = {
                                    selectedCompany = company
                                    companyExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Business,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Client dropdown (NEW - replaces free text)
                ExposedDropdownMenuBox(
                    expanded = clientExpanded,
                    onExpandedChange = { clientExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Client (Deliver To)") },
                        placeholder = { Text("Tap to select client") },
                        supportingText = { 
                            Text(
                                "Step 2: Select where you're delivering the product",
                                color = FleetColors.TextTertiary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = "Client icon",
                                tint = FleetColors.TextSecondary
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .semantics { contentDescription = "Client selection dropdown. Tap to choose the delivery destination." },
                        shape = RoundedCornerShape(FleetDimens.CornerMedium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FleetColors.Primary,
                            unfocusedBorderColor = FleetColors.Border
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = clientExpanded,
                        onDismissRequest = { clientExpanded = false }
                    ) {
                        if (clients.isEmpty()) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "No clients available. Ask owner to add clients.",
                                        color = FleetColors.TextSecondary
                                    )
                                },
                                onClick = { clientExpanded = false }
                            )
                        } else {
                            clients.forEach { client ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(client.name)
                                            client.address?.let { address ->
                                                Text(
                                                    address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = FleetColors.TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedClient = client
                                        clientExpanded = false
                                        // Clear previous pickup selection
                                        selectedPickup = null
                                        selectedDistance = null
                                        // Load pickup options for this client
                                        onClientSelected(client.id)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(FleetDimens.IconSmall) // 18dp vs 20dp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Pickup dropdown - Shows available pickups for selected client with distances
                ExposedDropdownMenuBox(
                    expanded = pickupExpanded,
                    onExpandedChange = { if (selectedClient != null) pickupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedPickup != null && selectedDistance != null) {
                            "${selectedPickup?.name} (${selectedDistance} km)"
                        } else {
                            selectedPickup?.name ?: ""
                        },
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedClient != null,
                        label = { Text("Pickup Location") },
                        placeholder = { 
                            Text(
                                if (selectedClient == null) "Select client first" 
                                else "Tap to select pickup point"
                            ) 
                        },
                        supportingText = { 
                            Text(
                                if (selectedClient == null) 
                                    "Step 3: First select a client above"
                                else 
                                    "Step 3: Choose where you picked up from",
                                color = if (selectedClient == null) FleetColors.Warning else FleetColors.TextTertiary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = "Pickup location icon",
                                tint = if (selectedClient != null) FleetColors.TextSecondary else FleetColors.TextTertiary
                            )
                        },
                        trailingIcon = { 
                            if (selectedClient != null) {
                                ExposedDropdownMenuDefaults.TrailingIcon(pickupExpanded) 
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .semantics { contentDescription = "Pickup location dropdown. Select where you collected the product from." },
                        shape = RoundedCornerShape(FleetDimens.CornerMedium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FleetColors.Primary,
                            unfocusedBorderColor = FleetColors.Border,
                            disabledBorderColor = FleetColors.BorderLight,
                            disabledTextColor = FleetColors.TextTertiary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = pickupExpanded,
                        onDismissRequest = { pickupExpanded = false }
                    ) {
                        if (pickupOptionsForClient.isEmpty()) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "No pickup locations set for this client.\nAsk owner to configure distances.",
                                        color = FleetColors.TextSecondary
                                    )
                                },
                                onClick = { pickupExpanded = false }
                            )
                        } else {
                            pickupOptionsForClient.forEach { distanceMapping ->
                                val pickup = pickupLocations.find { it.id == distanceMapping.pickupLocationId }
                                pickup?.let {
                                    val isRecommended = distanceMapping.isPreferred || 
                                        distanceMapping.pickupLocationId == suggestedPickup?.pickupLocationId
                                    
                                    DropdownMenuItem(
                                        text = { 
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(pickup.name)
                                                        if (isRecommended) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                if (distanceMapping.isPreferred) "★ Preferred" else "⚡ Nearest",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = FleetColors.Success
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "${distanceMapping.distanceKm} km to client",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = FleetColors.TextSecondary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedPickup = pickup
                                            selectedDistance = distanceMapping.distanceKm
                                            pickupExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (isRecommended) Icons.Filled.LocationOn else Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isRecommended) FleetColors.Success else FleetColors.TextSecondary
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Distance info (shown when pickup is selected)
                if (selectedDistance != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.InfoLight),
                        shape = RoundedCornerShape(FleetDimens.CornerSmall)
                    ) {
                        Row(
                            modifier = Modifier.padding(FleetDimens.SpacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Route,
                                contentDescription = null,
                                tint = FleetColors.TextSecondary,
                                modifier = Modifier.size(12.dp) // Small icon matching text size
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Distance: ${selectedDistance} km",
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.Info
                            )
                        }
                    }
                }
                
                // Bag Count
                OutlinedTextField(
                    value = bagCount,
                    onValueChange = { bagCount = it.filter { c -> c.isDigit() } },
                    label = { Text("Number of Bags") },
                    placeholder = { Text("e.g., 10") },
                    supportingText = { 
                        Text(
                            "Step 4: Enter how many bags you delivered",
                            color = FleetColors.TextTertiary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Inventory,
                            contentDescription = "Bag count icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Number of bags field. Enter the total bags delivered." },
                    singleLine = true,
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        unfocusedBorderColor = FleetColors.Border
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedCompany?.let { company ->
                        selectedClient?.let { client ->
                            selectedPickup?.let { pickup ->
                                val bags = bagCount.toIntOrNull() ?: 0
                                if (bags > 0) {
                                    onAdd(company.id, client.id, pickup.id, bags)
                                }
                            }
                        }
                    }
                },
                enabled = isValid && !isLoading,
                shape = RoundedCornerShape(FleetDimens.CornerMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FleetColors.Primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = FleetColors.OnPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                    Text("Add Trip", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel", color = FleetColors.TextSecondary)
            }
        }
    )
}

/**
 * Trip Detail Bottom Sheet with Attachments
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailSheet(
    trip: TripEntity,
    attachments: List<com.fleetcontrol.data.entities.TripAttachmentEntity>,
    onDismiss: () -> Unit,
    onAddAttachment: (android.net.Uri, com.fleetcontrol.data.entities.AttachmentType) -> Unit,
    onAddCameraAttachment: (java.io.File, android.net.Uri, com.fleetcontrol.data.entities.AttachmentType) -> Unit,
    onDeleteAttachment: (com.fleetcontrol.data.entities.TripAttachmentEntity) -> Unit
) {
    var viewingAttachment by remember { mutableStateOf<com.fleetcontrol.data.entities.TripAttachmentEntity?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FleetColors.White,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Trip Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Trip info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FleetColors.CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                trip.clientName.ifEmpty { "Delivery" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FleetColors.Black
                            )
                            Text(
                                DateUtils.formatDateTime(trip.tripDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                CurrencyUtils.format(trip.calculateDriverEarning()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FleetColors.Success
                            )
                            Text(
                                "${trip.bagCount} bags",
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextSecondary
                            )
                        }
                    }
                    
                    // Only show distance if it's a valid positive value
                    if (trip.snapshotDistanceKm > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Route,
                                contentDescription = null,
                                tint = FleetColors.TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "${trip.snapshotDistanceKm} km",
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextSecondary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Attachments Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Photos & Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.Black
                )
                Text(
                    "${attachments.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextTertiary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add attachment button
            com.fleetcontrol.ui.components.AddAttachmentButton(
                tripId = trip.id,
                onImageCaptured = { uri, type -> onAddAttachment(uri, type) },
                onCameraFilePrepared = { file, uri, type -> onAddCameraAttachment(file, uri, type) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Attachments list
            if (attachments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(FleetColors.CardBackground, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = FleetColors.TextTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No photos yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextTertiary
                        )
                        Text(
                            "Add delivery proofs, receipts, etc.",
                            style = MaterialTheme.typography.labelSmall,
                            color = FleetColors.TextTertiary
                        )
                    }
                }
            } else {
                com.fleetcontrol.ui.components.TripAttachmentsList(
                    attachments = attachments,
                    onViewAttachment = { viewingAttachment = it },
                    onDeleteAttachment = onDeleteAttachment
                )
            }
        }
    }
    
    // Image viewer dialog
    viewingAttachment?.let { attachment ->
        com.fleetcontrol.ui.components.ImageViewerDialog(
            attachment = attachment,
            onDismiss = { viewingAttachment = null }
        )
    }
}
