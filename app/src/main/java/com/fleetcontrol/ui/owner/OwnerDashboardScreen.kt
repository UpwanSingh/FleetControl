package com.fleetcontrol.ui.owner

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.ui.components.*
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.NetworkStatus
import com.fleetcontrol.utils.NetworkUtils
import com.fleetcontrol.viewmodel.owner.OwnerDashboardState
import com.fleetcontrol.viewmodel.owner.OwnerDashboardViewModel

/**
 * Owner Dashboard Screen - Premium Polished Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    viewModel: OwnerDashboardViewModel,
    onNavigateToDrivers: () -> Unit,
    onNavigateToCompanies: () -> Unit,
    onNavigateToPickups: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToProfit: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPendingTrips: () -> Unit = {},  // Security: Approval workflow
    onNavigateToPendingFuel: () -> Unit = {}    // P1: Fuel Requests
) {
    val state by viewModel.dashboardState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Network connectivity observation
    val networkStatus by NetworkUtils.observeNetworkConnectivity(context).collectAsState(initial = NetworkStatus.Available)
    
    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    // Notification Permission
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { hasNotificationPermission = it }
    )

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                onSettingsClick = onNavigateToSettings,
                lastSyncedAt = state.lastSyncedAt ?: 0L,
                isOnline = networkStatus is NetworkStatus.Available
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = FleetColors.Surface
    ) { padding ->
        if (isLoading && !state.isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LoadingState(message = "Loading dashboard...")
            }
        } else {
            RefreshableContainer(
                isRefreshing = isLoading && state.isLoaded,
                onRefresh = { viewModel.refresh() }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(FleetDimens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
                ) {
                    // Network status banner
                    item {
                        NetworkOfflineBanner(isVisible = networkStatus is NetworkStatus.Unavailable)
                    }
                    
                    // Hero Profit Card
                    item {
                        ProfitHeroCard(
                            state = state,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateToProfit()
                            }
                        )
                    }
                    
                    // Today's Stats Row
                    item {
                        TodayStatsRow(state = state)
                    }
                    
                    // Quick Actions Grid
                    item {
                        SectionHeader(title = "QUICK ACTIONS")
                    }
                    
                    item {
                        QuickActionsGrid(
                            onNavigateToDrivers = onNavigateToDrivers,
                            onNavigateToCompanies = onNavigateToCompanies,
                            onNavigateToPickups = onNavigateToPickups,
                            onNavigateToClients = onNavigateToClients,
                            onNavigateToReports = onNavigateToReports,
                            onNavigateToPendingTrips = onNavigateToPendingTrips,
                            pendingTripsCount = viewModel.pendingTripsCount.collectAsState().value,
                            onNavigateToPendingFuel = onNavigateToPendingFuel,
                            pendingFuelCount = viewModel.pendingFuelRequestsCount.collectAsState().value
                        )
                    }
                    
                    // Financial Summary
                    item {
                        SectionHeader(title = "MONTHLY BREAKDOWN")
                    }
                    
                    item {
                        FinancialSummaryCard(state = state)
                    }
                    
                    item { Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    onSettingsClick: () -> Unit,
    lastSyncedAt: Long,
    isOnline: Boolean
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Text(
                    "FleetControl",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
        },
        actions = {
            // Global App Health Indicator
            GlobalSyncIndicator(
                pendingCount = 0, // Owner usually doesn't have pending uploads
                failedCount = 0,
                lastSyncedAt = lastSyncedAt,
                onRetryClick = {}
            )
            
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = FleetColors.TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = FleetColors.Surface
        )
    )
}

@Composable
private fun ProfitHeroCard(
    state: OwnerDashboardState,
    onClick: () -> Unit
) {
    val summary = state.monthlyProfitSummary
    val netProfit = summary?.netProfit ?: 0.0
    val isPositive = netProfit >= 0
    val profitMargin = summary?.profitMargin ?: 0.0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.Primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "This Month's Profit",
                        style = MaterialTheme.typography.titleSmall,
                        color = FleetColors.TextOnDarkSecondary
                    )
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                    Text(
                        CurrencyUtils.format(netProfit),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.TextOnDark
                    )
                }
                
                // Trend Indicator
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPositive) FleetColors.Success.copy(alpha = 0.2f)
                            else FleetColors.Error.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isPositive) FleetColors.Success else FleetColors.Error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingLarge)
            ) {
                HeroStatItem(
                    value = "${summary?.tripCount ?: 0}",
                    label = "Trips"
                )
                HeroStatItem(
                    value = "${summary?.totalBags ?: 0}",
                    label = "Bags"
                )
                HeroStatItem(
                    value = "${String.format("%.1f", profitMargin)}%",
                    label = "Margin"
                )
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            // Tap indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "View Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = FleetColors.TextOnDarkSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = FleetColors.TextOnDarkSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroStatItem(value: String, label: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FleetColors.TextOnDark
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = FleetColors.TextOnDarkSecondary
        )
    }
}

@Composable
private fun TodayStatsRow(state: OwnerDashboardState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
    ) {
        StatsCard(
            modifier = Modifier.weight(1f),
            title = "Today's Trips",
            value = "${state.todayTripCount}",
            icon = Icons.Outlined.LocalShipping,
            iconTint = FleetColors.Info
        )
        StatsCard(
            modifier = Modifier.weight(1f),
            title = "Today's Profit",
            value = CurrencyUtils.formatCompact(state.todayProfit),
            icon = Icons.Outlined.Payments,
            iconTint = if (state.todayProfit >= 0) FleetColors.Success else FleetColors.Error
        )
        StatsCard(
            modifier = Modifier.weight(1f),
            title = "Active Drivers",
            value = "${state.activeDriverCount}",
            icon = Icons.Outlined.People,
            iconTint = FleetColors.Warning
        )
    }
}

@Composable
private fun QuickActionsGrid(
    onNavigateToDrivers: () -> Unit,
    onNavigateToCompanies: () -> Unit,
    onNavigateToPickups: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToPendingTrips: () -> Unit,
    pendingTripsCount: Int,
    onNavigateToPendingFuel: () -> Unit,
    pendingFuelCount: Int
) {
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
    ) {
        // First row - 3 tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.People,
                label = "Drivers",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToDrivers()
                }
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Business,
                label = "Companies",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToCompanies()
                }
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocationOn,
                label = "Pickups",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToPickups()
                }
            )
        }
        
        // Second row - 4 tiles (Clients, Reports, Approvals, Requests)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Store,
                label = "Clients",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToClients()
                }
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Assessment,
                label = "Reports",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToReports()
                }
            )
            // Pending Approvals with count badge
            QuickActionTileWithBadge(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Pending,
                label = "Approvals",
                badgeCount = pendingTripsCount,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToPendingTrips()
                }
            )
            // Pending Fuel Requests
            QuickActionTileWithBadge(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocalGasStation,
                label = "Fuel Req",
                badgeCount = pendingFuelCount,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToPendingFuel()
                }
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FleetColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = FleetColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = FleetColors.TextPrimary
            )
        }
    }
}

@Composable
private fun QuickActionTileWithBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = if (badgeCount > 0) FleetColors.WarningLight else FleetColors.SurfaceElevated
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (badgeCount > 0) FleetColors.Warning.copy(alpha = 0.2f) else FleetColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (badgeCount > 0) FleetColors.Warning else FleetColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Badge
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(FleetColors.Error),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badgeCount > 9) "9+" else "$badgeCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (badgeCount > 0) FleetColors.Warning else FleetColors.TextPrimary
            )
        }
    }
}

@Composable
private fun FinancialSummaryCard(state: OwnerDashboardState) {
    val summary = state.monthlyProfitSummary
    
    SectionCard {
        // Revenue
        FinancialLineItem(
            icon = Icons.Outlined.AccountBalanceWallet,
            label = "Gross Revenue",
            amount = summary?.grossRevenue ?: 0.0,
            isPositive = true
        )
        
        PremiumDivider()
        
        // Expenses
        FinancialLineItem(
            icon = Icons.Outlined.People,
            label = "Driver Payments",
            amount = summary?.driverEarnings ?: 0.0,
            isPositive = false
        )
        
        FinancialLineItem(
            icon = Icons.Outlined.Engineering,
            label = "Labour Costs",
            amount = summary?.labourCost ?: 0.0,
            isPositive = false
        )
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
        
        Divider(
            thickness = 2.dp,
            color = FleetColors.Primary
        )
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
        
        // Net Profit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Net Profit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FleetColors.TextPrimary
            )
            Text(
                CurrencyUtils.format(summary?.netProfit ?: 0.0),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if ((summary?.netProfit ?: 0.0) >= 0) FleetColors.Success else FleetColors.Error
            )
        }
    }
}

@Composable
private fun FinancialLineItem(
    icon: ImageVector,
    label: String,
    amount: Double,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FleetDimens.SpacingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FleetColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = FleetColors.TextSecondary
            )
        }
        Text(
            if (isPositive) CurrencyUtils.format(amount) else "-${CurrencyUtils.format(amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isPositive) FleetColors.Success else FleetColors.Error
        )
    }
}
