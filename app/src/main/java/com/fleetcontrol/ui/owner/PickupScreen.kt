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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.ui.components.RefreshableContainer
import com.fleetcontrol.ui.components.SkeletonList
import com.fleetcontrol.utils.ValidationUtils
import com.fleetcontrol.viewmodel.owner.PickupViewModel

/**
 * Pickup Location Management Screen - Properly wired with ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupScreen(
    viewModel: PickupViewModel,
    onBack: () -> Unit
) {
    val pickups by viewModel.pickupLocations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Status bar styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = FleetColors.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    
    // Filter pickups based on search
    val filteredPickups = remember(pickups, searchQuery) {
        if (searchQuery.isBlank()) pickups
        else pickups.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Pickup Locations", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    navigationIconContentColor = FleetColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPickups() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                Icon(Icons.Default.Add, contentDescription = "Add Location", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error display
            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.Error.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = FleetColors.Error
                    )
                }
            }
            
            if (isLoading && pickups.isEmpty()) {
                // Skeleton loading state
                SkeletonList(itemCount = 4)
            } else if (pickups.isEmpty()) {
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
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = FleetColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                        Text(
                            "No pickup locations yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                        Text(
                            "Add locations to set distance-based rates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
            } else {
                RefreshableContainer(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.loadPickups() }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search locations...", color = FleetColors.TextSecondary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = FleetColors.TextPrimary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FleetColors.TextPrimary)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(FleetDimens.CornerLarge),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FleetColors.Primary,
                                unfocusedBorderColor = FleetColors.Border,
                                cursorColor = FleetColors.Primary,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        
                        if (filteredPickups.isEmpty() && searchQuery.isNotEmpty()) {
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
                                        "No locations found",
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
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredPickups) { pickup ->
                                    PickupCard(
                                        pickup = pickup,
                                        onDelete = { viewModel.deletePickup(pickup) }
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
        AddPickupDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name ->
                viewModel.addPickup(name)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickupCard(
    pickup: PickupLocationEntity,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(FleetColors.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = FleetColors.White
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pickup.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = FleetColors.Black
                )
                Text(
                    "Set distances in Client Management",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextTertiary
                )
            }
            
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = FleetColors.Error
                )
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = FleetColors.White,
            title = { Text("Delete Location?", color = FleetColors.Black) },
            text = { Text("Are you sure you want to delete ${pickup.name}?", color = FleetColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = FleetColors.Black)
                }
            }
        )
    }
}

@Composable
private fun AddPickupDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FleetColors.White,
        title = { Text("Add Pickup Location", color = FleetColors.Black, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Create a pickup point. You can set distances to clients in Client Management.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = null
                    },
                    label = { Text("Location Name") },
                    placeholder = { Text("e.g., Warehouse A, Factory B") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = FleetColors.Error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Black,
                        unfocusedBorderColor = FleetColors.Border,
                        cursorColor = FleetColors.Black,
                        focusedLabelColor = FleetColors.Black
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameValidation = ValidationUtils.validateCompanyName(name)
                    
                    if (!nameValidation.isValid) {
                        nameError = nameValidation.errorMessage
                    } else {
                        onAdd(name)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Black)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.Black)
            }
        }
    )
}
