package com.fleetcontrol.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.owner.ProfitViewModel
import com.fleetcontrol.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * Monthly Profit Screen - Shows profit breakdown by month
 * Implements Section 5.2, 8.2, 8.3 of BUSINESS_LOGIC_SPEC.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyProfitScreen(
    viewModel: ProfitViewModel,
    onBack: () -> Unit
) {
    val profitSummary by viewModel.profitSummary.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    // Status bar styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profit Analysis", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                        onClick = { viewModel.loadProfitData() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.previousMonth() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = FleetColors.TextPrimary)
                    }
                    TextButton(onClick = { showDatePicker = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Select Month",
                                modifier = Modifier.size(20.dp),
                                tint = FleetColors.Primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = monthFormat.format(currentMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FleetColors.TextPrimary
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = FleetColors.TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = FleetColors.TextPrimary)
                    }
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FleetColors.Primary)
                }
            } else {
                // Net Profit Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                    colors = CardDefaults.cardColors(
                        containerColor = if ((profitSummary?.netProfit ?: 0.0) >= 0) FleetColors.Success else FleetColors.Error
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            "Net Profit",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            CurrencyUtils.format(profitSummary?.netProfit ?: 0.0),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerLarge),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Profit Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        BreakdownRow(
                            label = "Gross Revenue",
                            amount = profitSummary?.grossRevenue ?: 0.0,
                            isPositive = true
                        )
                        BreakdownRow(
                            label = "Driver Earnings",
                            amount = -(profitSummary?.driverEarnings ?: 0.0),
                            isPositive = false
                        )
                        BreakdownRow(
                            label = "Labour Costs",
                            amount = -(profitSummary?.labourCost ?: 0.0),
                            isPositive = false
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = FleetColors.Border)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Net Profit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FleetColors.Black
                            )
                            Text(
                                CurrencyUtils.format(profitSummary?.netProfit ?: 0.0),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if ((profitSummary?.netProfit ?: 0.0) >= 0) FleetColors.Success else FleetColors.Error
                            )
                        }
                    }
                }
                
                // Stats Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(FleetDimens.CornerLarge),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.Primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${profitSummary?.tripCount ?: 0}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Trips",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(FleetDimens.CornerLarge),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.Success)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${profitSummary?.totalBags ?: 0}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Bags",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        MonthYearPickerDialog(
            currentYear = selectedYear,
            currentMonth = selectedMonth,
            onDismiss = { showDatePicker = false },
            onConfirm = { year, month ->
                viewModel.setMonth(year, month)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun BreakdownRow(label: String, amount: Double, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = FleetColors.TextSecondary
        )
        Text(
            if (amount >= 0) CurrencyUtils.format(amount) else "-${CurrencyUtils.format(-amount)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPositive) FleetColors.Success else FleetColors.Error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthYearPickerDialog(
    currentYear: Int,
    currentMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }
    
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val years = (2020..Calendar.getInstance().get(Calendar.YEAR) + 1).toList()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FleetColors.White,
        title = { Text("Select Month", color = FleetColors.Black, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Year selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (selectedYear > years.first()) selectedYear-- }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = FleetColors.Black)
                    }
                    Text(
                        "$selectedYear",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.Black
                    )
                    IconButton(onClick = { if (selectedYear < years.last()) selectedYear++ }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = FleetColors.Black)
                    }
                }
                
                // Month grid
                Column {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0..2) {
                                val monthIndex = row * 3 + col
                                val isSelected = monthIndex == selectedMonth
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedMonth = monthIndex },
                                    label = { Text(months[monthIndex].take(3)) },
                                    modifier = Modifier.padding(2.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FleetColors.Black,
                                        selectedLabelColor = FleetColors.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedYear, selectedMonth) },
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Black)
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.Black)
            }
        }
    )
}

