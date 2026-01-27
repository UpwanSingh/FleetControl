package com.fleetcontrol.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.domain.calculators.DriverPayableSummary
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.viewmodel.driver.DriverEarningViewModel

/**
 * Driver Summary/Earnings Screen
 * Implements Section 5.1, 7.3, 9.1 of BUSINESS_LOGIC_SPEC.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSummaryScreen(
    viewModel: DriverEarningViewModel,
    onNavigateToTrips: () -> Unit,
    onNavigateToFuel: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onLogout: () -> Unit
) {
    val todayPayable by viewModel.todayPayable.collectAsState()
    val monthlyPayable by viewModel.monthlyPayable.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Earnings", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = FleetColors.TextPrimary,
                    actionIconContentColor = FleetColors.TextPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.forceSync() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Force Sync")
                    }
                    IconButton(onClick = onNavigateToBackup) {
                         Icon(Icons.Default.CloudUpload, contentDescription = "Backup")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading && todayPayable == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FleetColors.Primary)
            }
        } else {
            val scrollState = rememberScrollState()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Refresh indicator
                if (isLoading && todayPayable != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = FleetColors.Primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Refreshing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
                
                // Today's Summary Card
                todayPayable?.let { summary ->
                    TodayEarningsCard(summary)
                }
                
                // Monthly Summary Card
                monthlyPayable?.let { summary ->
                    MonthlyEarningsCard(summary)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Quick Actions
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Add Trip",
                        icon = Icons.Default.LocalShipping,
                        onClick = onNavigateToTrips
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Add Fuel",
                        icon = Icons.Default.LocalGasStation,
                        onClick = onNavigateToFuel
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Records",
                        icon = Icons.Default.History,
                        onClick = onNavigateToHistory
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Reports",
                        icon = Icons.Default.Assessment,
                        onClick = onNavigateToReports
                    )
                }
                
                // Bottom spacing for scroll
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TodayEarningsCard(summary: DriverPayableSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(FleetDimens.SpacingLarge)) {
            Text(
                text = "Today's Earnings",
                style = MaterialTheme.typography.titleMedium,
                color = FleetColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = CurrencyUtils.format(summary.grossEarnings),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = FleetColors.Success
            )
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingExtraLarge))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FleetColors.Surface, RoundedCornerShape(FleetDimens.CornerMedium))
                    .padding(FleetDimens.SpacingSmall),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Fuel Cost",
                        style = MaterialTheme.typography.labelMedium,
                        color = FleetColors.TextSecondary
                    )
                    Text(
                        "-${CurrencyUtils.format(summary.fuelCost)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FleetColors.Error
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(FleetColors.Border))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Net Profit",
                        style = MaterialTheme.typography.labelMedium,
                        color = FleetColors.TextSecondary
                    )
                    Text(
                        CurrencyUtils.format(summary.netPayable),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.Success
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyEarningsCard(summary: DriverPayableSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(FleetDimens.SpacingLarge)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "This Month",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
                Surface(
                    color = FleetColors.Success.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = FleetColors.Success
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingExtraLarge))
            
            EarningsRow("Gross Earnings", summary.grossEarnings, FleetColors.TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            EarningsRow("Fuel Cost", -summary.fuelCost, FleetColors.TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            EarningsRow("Advance Deduction", -summary.advanceDeduction, FleetColors.TextPrimary)
            
            Divider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = FleetColors.Border
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Net Payable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Text(
                    CurrencyUtils.format(summary.netPayable),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.Success
                )
            }
            
                if (summary.remainingAdvanceBalance > 0) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                Surface(
                    color = FleetColors.Warning.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(FleetDimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "Warning: No trips today", 
                            tint = FleetColors.Warning,
                            modifier = Modifier.size(FleetDimens.IconSmall)
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(
                            "Outstanding Advance: ${CurrencyUtils.format(summary.remainingAdvanceBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningsRow(label: String, amount: Double, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.8f) // Slightly dimmer for label
        )
        Text(
            if (amount >= 0) CurrencyUtils.format(amount) else "-${CurrencyUtils.format(-amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(FleetDimens.ButtonHeight) // Approx 56.dp
                    .background(FleetColors.Primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(FleetDimens.IconMedium),
                    tint = FleetColors.Primary
                )
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FleetColors.TextPrimary
            )
        }
    }
}
