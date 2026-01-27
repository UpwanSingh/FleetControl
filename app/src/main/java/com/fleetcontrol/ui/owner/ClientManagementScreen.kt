package com.fleetcontrol.ui.owner

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.fleetcontrol.data.entities.ClientEntity
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.ui.components.RefreshableContainer
import com.fleetcontrol.ui.components.SkeletonList
import com.fleetcontrol.viewmodel.owner.ClientManagementViewModel

/**
 * Client Management Screen
 * Owner can:
 * - Add/Edit/Deactivate clients
 * - Set distances from each pickup location to each client
 * - Set preferred pickup for each client
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientManagementScreen(
    viewModel: ClientManagementViewModel,
    onBack: () -> Unit
) {
    val clients by viewModel.clients.collectAsState()
    val pickupLocations by viewModel.pickupLocations.collectAsState()
    val selectedClient by viewModel.selectedClient.collectAsState()
    val clientDistances by viewModel.clientDistances.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val clientSaved by viewModel.clientSaved.collectAsState()
    val distanceSaved by viewModel.distanceSaved.collectAsState()
    
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showAddDistanceDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Filter clients based on search
    val filteredClients = remember(clients, searchQuery) {
        if (searchQuery.isBlank()) clients
        else clients.filter { 
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.address?.contains(searchQuery, ignoreCase = true) == true ||
            it.contactPerson?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    
    // Status bar styling - Light theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    
    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    // Reset dialogs on save
    LaunchedEffect(clientSaved) {
        if (clientSaved) {
            showAddClientDialog = false
            editingClient = null
            viewModel.resetClientSaved()
        }
    }
    
    LaunchedEffect(distanceSaved) {
        if (distanceSaved) {
            showAddDistanceDialog = false
            viewModel.resetDistanceSaved()
        }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Clients", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                    IconButton(onClick = { viewModel.loadClients() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            // Show FAB for adding clients (list view) or adding distances (detail view)
            FloatingActionButton(
                onClick = { 
                    if (selectedClient == null) {
                        showAddClientDialog = true
                    } else {
                        showAddDistanceDialog = true
                    }
                },
                containerColor = FleetColors.Primary,
                shape = CircleShape,
                modifier = Modifier.semantics { 
                    contentDescription = if (selectedClient == null) "Add new client" else "Add distance"
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        if (selectedClient != null) {
            // Show client detail with distances - apply padding
            ClientDetailView(
                modifier = Modifier.padding(padding),
                client = selectedClient!!,
                distances = clientDistances,
                pickupLocations = pickupLocations,
                onBack = { viewModel.clearSelectedClient() },
                onAddDistance = { showAddDistanceDialog = true },
                onSetPreferred = { pickupId -> 
                    selectedClient?.let { client ->
                        viewModel.setPreferredPickup(pickupId, client.id) 
                    }
                },
                onRemoveDistance = { pickupId ->
                    selectedClient?.let { client ->
                        viewModel.removeDistance(pickupId, client.id)
                    }
                }
            )
        } else if (isLoading && clients.isEmpty()) {
            // Skeleton loading state
            Column(modifier = Modifier.padding(padding)) {
                SkeletonList(itemCount = 5)
            }
        } else {
            // Show client list
            RefreshableContainer(
                isRefreshing = isLoading,
                onRefresh = { viewModel.loadClients() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search clients...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FleetColors.Primary,
                            unfocusedBorderColor = FleetColors.Border,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    
                    if (filteredClients.isEmpty()) {
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
                                        if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.People,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = FleetColors.TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                                Text(
                                    if (searchQuery.isNotEmpty()) "No clients found" else "No clients yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FleetColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                                Text(
                                    if (searchQuery.isNotEmpty()) "Try a different search term" else "Tap the + button to add your first client",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FleetColors.TextSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Client count header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${filteredClients.size} client${if (filteredClients.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FleetColors.TextSecondary
                                    )
                                    Text(
                                        "Tap + to add more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FleetColors.Primary
                                    )
                                }
                            }
                            
                            items(filteredClients) { client ->
                                ClientCard(
                                    client = client,
                                    onClick = { viewModel.selectClient(client) },
                                    onEdit = { editingClient = client },
                                    onDeactivate = { viewModel.deactivateClient(client.id) }
                                )
                            }
                            
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
    
    // Add/Edit Client Dialog
    if (showAddClientDialog || editingClient != null) {
        AddEditClientDialog(
            client = editingClient,
            isLoading = isLoading,
            onDismiss = { 
                showAddClientDialog = false
                editingClient = null
            },
            onSave = { name, address, contactPerson, contactPhone, notes ->
                editingClient?.let { client ->
                    viewModel.updateClient(
                        clientId = client.id,
                        name = name,
                        address = address,
                        contactPerson = contactPerson,
                        contactPhone = contactPhone,
                        notes = notes
                    )
                } ?: run {
                    viewModel.addClient(
                        name = name,
                        address = address,
                        contactPerson = contactPerson,
                        contactPhone = contactPhone,
                        notes = notes
                    )
                }
            }
        )
    }
    
    // Add Distance Dialog
    if (showAddDistanceDialog && selectedClient != null) {
        AddDistanceDialog(
            client = selectedClient!!,
            pickupLocations = pickupLocations,
            existingDistances = clientDistances,
            isLoading = isLoading,
            onDismiss = { showAddDistanceDialog = false },
            onSave = { pickupId, distance, isPreferred ->
                selectedClient?.let { client ->
                    viewModel.setDistance(
                        pickupLocationId = pickupId,
                        clientId = client.id,
                        distanceKm = distance,
                        isPreferred = isPreferred
                    )
                }
            }
        )
    }
}

@Composable
private fun ClientCard(
    client: ClientEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Client Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(FleetColors.InfoLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = client.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.Info
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Client Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
                client.address?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                client.contactPerson?.let { contact ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = FleetColors.TextTertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = contact,
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextTertiary
                        )
                    }
                }
            }
            
            // Menu
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
                    DropdownMenuItem(
                        text = { Text("View Distances") },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Route, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Deactivate", color = FleetColors.Error) },
                        onClick = {
                            showMenu = false
                            onDeactivate()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Block,
                                contentDescription = null,
                                tint = FleetColors.Error
                            )
                        }
                    )
                }
            }
            
            // Arrow
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = FleetColors.TextTertiary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientDetailView(
    modifier: Modifier = Modifier,
    client: ClientEntity,
    distances: List<PickupClientDistanceEntity>,
    pickupLocations: List<PickupLocationEntity>,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onAddDistance: () -> Unit, // Handled by FAB now
    onSetPreferred: (Long) -> Unit,
    onRemoveDistance: (Long) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FleetColors.Surface)
    ) {
        // Clean TopAppBar style header
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = FleetColors.TextPrimary
                    )
                }
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Client Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(FleetDimens.CornerLarge),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Client avatar - same style as list view
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(FleetColors.InfoLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = client.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.Info
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    client.address?.let { address ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = FleetColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = FleetColors.TextSecondary
                            )
                        }
                    }
                    client.contactPerson?.let { contact ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = FleetColors.TextTertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = contact,
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextTertiary
                            )
                        }
                    }
                }
            }
        }
        
        // Distances Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Pickup Distances",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FleetColors.TextPrimary
            )
            Surface(
                color = FleetColors.PrimaryLight,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "${distances.size} configured",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.Primary
                )
            }
        }
        
        // Distances List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (distances.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FleetDimens.CornerLarge),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.WarningLight)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = FleetColors.Warning
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "No distances set",
                                    fontWeight = FontWeight.SemiBold,
                                    color = FleetColors.TextPrimary
                                )
                                Text(
                                    "Tap the + button below to add pickup distances for this client.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FleetColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                items(distances) { distance ->
                    val pickup = pickupLocations.find { it.id == distance.pickupLocationId }
                    pickup?.let {
                        DistanceCard(
                            pickupName = pickup.name,
                            distance = distance,
                            onSetPreferred = { onSetPreferred(pickup.id) },
                            onRemove = { onRemoveDistance(pickup.id) }
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DistanceCard(
    pickupName: String,
    distance: PickupClientDistanceEntity,
    onSetPreferred: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(
            containerColor = if (distance.isPreferred) FleetColors.SuccessLight else FleetColors.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (distance.isPreferred) Icons.Filled.LocationOn else Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = if (distance.isPreferred) FleetColors.Success else FleetColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pickupName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (distance.isPreferred) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = FleetColors.Success,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "PREFERRED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = FleetColors.White
                            )
                        }
                    }
                }
                Text(
                    text = "${distance.distanceKm} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FleetColors.TextSecondary
                )
                distance.estimatedTravelMinutes?.let { mins ->
                    Text(
                        text = "~$mins min travel",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextTertiary
                    )
                }
            }
            
            if (!distance.isPreferred) {
                TextButton(onClick = onSetPreferred) {
                    Text("Set Preferred", color = FleetColors.Success)
                }
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = FleetColors.Error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditClientDialog(
    client: ClientEntity?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf(client?.name ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }
    var contactPerson by remember { mutableStateOf(client?.contactPerson ?: "") }
    var contactPhone by remember { mutableStateOf(client?.contactPhone ?: "") }
    var notes by remember { mutableStateOf(client?.notes ?: "") }
    
    val isValid = name.isNotBlank()
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
        title = {
            Text(
                if (client != null) "Edit Client" else "Add Client",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Helper text
                Text(
                    text = "Enter client details. Fields marked with * are required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Client Name *") },
                    placeholder = { Text("e.g., ABC Mart") },
                    supportingText = { Text("Required: Name of the delivery destination") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Store,
                            contentDescription = "Client name icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Client name field. Enter the name of the client or shop." },
                    singleLine = true,
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                )
                
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (optional)") },
                    placeholder = { Text("e.g., 123 Main Street") },
                    supportingText = { Text("Optional: Full address for reference") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = "Address icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Address field. Enter the client's delivery address." },
                    maxLines = 2,
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                )
                
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Contact Person (optional)") },
                    placeholder = { Text("e.g., Mr. Sharma") },
                    supportingText = { Text("Optional: Name of the contact at this location") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = "Contact person icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Contact person field. Enter the name of who to contact at this client." },
                    singleLine = true,
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                )
                
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("Contact Phone (optional)") },
                    placeholder = { Text("e.g., 9876543210") },
                    supportingText = { Text("Optional: Phone number for coordination") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Phone,
                            contentDescription = "Phone icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Contact phone field. Enter the contact number." },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                )
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Any special instructions...") },
                    supportingText = { Text("Optional: Delivery instructions or notes") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Notes,
                            contentDescription = "Notes icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Notes field. Enter any special instructions for this client." },
                    maxLines = 3,
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        address.ifBlank { null },
                        contactPerson.ifBlank { null },
                        contactPhone.ifBlank { null },
                        notes.ifBlank { null }
                    )
                },
                enabled = isValid && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Black)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = FleetColors.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (client != null) "Update" else "Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDistanceDialog(
    client: ClientEntity,
    pickupLocations: List<PickupLocationEntity>,
    existingDistances: List<PickupClientDistanceEntity>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Double, Boolean) -> Unit
) {
    var selectedPickup by remember { mutableStateOf<PickupLocationEntity?>(null) }
    var distance by remember { mutableStateOf("") }
    var isPreferred by remember { mutableStateOf(false) }
    var pickupExpanded by remember { mutableStateOf(false) }
    
    // Filter out pickups that already have distances
    val availablePickups = pickupLocations.filter { pickup ->
        existingDistances.none { it.pickupLocationId == pickup.id }
    }
    
    val isValid = selectedPickup != null && (distance.toDoubleOrNull() ?: 0.0) > 0
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
        title = {
            Column {
                Text("Add Pickup Distance", fontWeight = FontWeight.Bold)
                Text(
                    "for ${client.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Helper text
                Text(
                    text = "Set the distance from a pickup point to this client:",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                
                // Pickup Location dropdown
                ExposedDropdownMenuBox(
                    expanded = pickupExpanded,
                    onExpandedChange = { pickupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPickup?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pickup Location") },
                        placeholder = { Text("Tap to select pickup point") },
                        supportingText = { 
                            Text(
                                "Select the pickup point to set distance from",
                                color = FleetColors.TextTertiary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = "Pickup location icon",
                                tint = FleetColors.TextSecondary
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickupExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .semantics { contentDescription = "Pickup location dropdown. Select where products are collected from." },
                        shape = RoundedCornerShape(FleetDimens.CornerMedium)
                    )
                    ExposedDropdownMenu(
                        expanded = pickupExpanded,
                        onDismissRequest = { pickupExpanded = false }
                    ) {
                        if (availablePickups.isEmpty()) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "All pickup locations already have distances set",
                                        color = FleetColors.TextSecondary
                                    )
                                },
                                onClick = { pickupExpanded = false }
                            )
                        } else {
                            availablePickups.forEach { pickup ->
                                DropdownMenuItem(
                                    text = { Text(pickup.name) },
                                    onClick = {
                                        selectedPickup = pickup
                                        pickupExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.LocationOn,
                                            contentDescription = "Location icon"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Distance input
                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Distance (km)") },
                    placeholder = { Text("e.g., 5.5") },
                    supportingText = {
                        Text(
                            "Enter distance from pickup to ${client.name}",
                            color = FleetColors.TextTertiary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Route,
                            contentDescription = "Distance icon",
                            tint = FleetColors.TextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Distance field. Enter the distance in kilometers from pickup to client." },
                    singleLine = true,
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                )
                
                // Preferred checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPreferred = !isPreferred }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPreferred,
                        onCheckedChange = { isPreferred = it },
                        colors = CheckboxDefaults.colors(checkedColor = FleetColors.Success)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Set as preferred pickup")
                        Text(
                            "Drivers will see this as the recommended option",
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedPickup?.let { pickup ->
                        val dist = distance.toDoubleOrNull() ?: 0.0
                        if (dist > 0) {
                            onSave(pickup.id, dist, isPreferred)
                        }
                    }
                },
                enabled = isValid && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Black)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = FleetColors.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add Distance")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}
