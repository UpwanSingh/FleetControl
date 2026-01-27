package com.fleetcontrol.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.driver.DriverFuelViewModel
import com.fleetcontrol.viewmodel.driver.DriverTripViewModel

/**
 * Driver History Screen
 * Consolidates Trip and Fuel records in a tabbed view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHistoryScreen(
    tripViewModel: DriverTripViewModel = viewModel(factory = AppViewModelProvider.Factory),
    fuelViewModel: DriverFuelViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val titles = listOf("Trips", "Fuel")
    val icons = listOf(Icons.Default.LocalShipping, Icons.Default.LocalGasStation)
    val pagedTrips = tripViewModel.pagedTrips.collectAsLazyPagingItems()

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.White.toArgb()
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("My Records", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = FleetColors.TextPrimary,
                    actionIconContentColor = FleetColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) {
                                pagedTrips.refresh()
                            } else {
                                fuelViewModel.refreshEntries()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = FleetColors.Primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = FleetColors.Primary,
                        height = 3.dp
                    )
                }
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = FleetColors.TextPrimary
                            )
                        },
                        icon = {
                            Icon(
                                icons[index],
                                contentDescription = when (index) {
                                    0 -> "Trips tab"
                                    1 -> "Fuel entries tab"
                                    else -> "Tab ${index + 1}"
                                },
                                tint = if (selectedTab == index) FleetColors.TextPrimary else FleetColors.TextSecondary
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> DriverTripsList(pagedTrips)
                1 -> DriverFuelList(fuelViewModel)
            }
        }
    }
}

@Composable
private fun DriverTripsList(pagedTrips: androidx.paging.compose.LazyPagingItems<TripEntity>) {
    if (pagedTrips.itemCount == 0 && pagedTrips.loadState.refresh !is androidx.paging.LoadState.Loading) {
        EmptyState("No trips recorded")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pagedTrips.itemCount) { index ->
                pagedTrips[index]?.let { trip ->
                    CompactTripCard(trip)
                }
            }
        }
    }
}

@Composable
private fun DriverFuelList(viewModel: DriverFuelViewModel) {
    val fuelEntries by viewModel.recentFuelEntries.collectAsState()

    if (fuelEntries.isEmpty()) {
        EmptyState("No fuel entries recorded")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(fuelEntries) { entry ->
                FuelEntryCard(entry)
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Information: No history available",
                tint = FleetColors.TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = FleetColors.TextSecondary
            )
        }
    }
}

@Composable
private fun CompactTripCard(trip: TripEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.clientName.ifEmpty { "Client Trip" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DateUtils.formatDateTime(trip.tripDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
                  Spacer(modifier = Modifier.height(4.dp))
                // Tag for trip route if available or just bag count
                Surface(
                    color = FleetColors.Primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                     Text(
                        text = "${trip.bagCount} Bags",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = FleetColors.Primary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                 Text(
                    "Earning",
                     style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.TextSecondary
                )
                Text(
                    text = CurrencyUtils.format(trip.calculateDriverEarning()), 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.Success
                )
            }
        }
    }
}

@Composable
private fun FuelEntryCard(entry: FuelEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(FleetColors.Warning.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalGasStation,
                    contentDescription = "Warning: No fuel entries available",
                    tint = FleetColors.Warning
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fuelStation?.takeIf { it.isNotEmpty() } ?: "Fuel Station",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DateUtils.formatDateTime(entry.entryDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
            Text(
                text = "-${CurrencyUtils.format(entry.amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = FleetColors.Error
            )
        }
    }
}
