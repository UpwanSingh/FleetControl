package com.fleetcontrol.ui.screens.driver

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.models.DriverExportResult
import com.fleetcontrol.data.models.DriverReportSummary
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.driver.DriverReportsViewModel
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverReportsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DriverReportsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val summary by viewModel.summary.collectAsState()
    
    val tripsPaging: LazyPagingItems<TripEntity> = viewModel.filteredTrips.collectAsLazyPagingItems()
    val fuelPaging: LazyPagingItems<FuelEntryEntity> = viewModel.filteredFuelEntries.collectAsLazyPagingItems()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportOptions by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    
    // CSV export launcher
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch {
                val result = viewModel.exportToExternalCsv(context.contentResolver, uri)
                isExporting = false
                when (result) {
                    is DriverExportResult.Success -> 
                        Toast.makeText(context, "CSV exported successfully!", Toast.LENGTH_SHORT).show()
                    is DriverExportResult.Error -> 
                        Toast.makeText(context, "Export failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // PDF export launcher
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch {
                val result = viewModel.exportToExternalPdf(context.contentResolver, uri)
                isExporting = false
                when (result) {
                    is DriverExportResult.Success -> 
                        Toast.makeText(context, "PDF exported successfully!", Toast.LENGTH_SHORT).show()
                    is DriverExportResult.Error -> 
                        Toast.makeText(context, "Export failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    val tabs = listOf("Trips", "Fuel")
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("My Earnings Report", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Go back" }
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export button
                    IconButton(
                        onClick = { showExportOptions = true },
                        enabled = !isExporting,
                        modifier = Modifier.semantics { contentDescription = "Export report options" }
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = FleetColors.Primary
                            )
                        } else {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Export")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = FleetColors.TextPrimary,
                    navigationIconContentColor = FleetColors.TextPrimary,
                    actionIconContentColor = FleetColors.TextPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Month Navigator
            MonthNavigatorRow(
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() }
            )
            
            // Summary Card
            EarningsSummaryCard(summary = summary)
            
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newValue: String -> viewModel.setSearchQuery(newValue) },
                label = { Text("Search") },
                placeholder = { Text("Search by client name...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FleetDimens.SpacingMedium, vertical = FleetDimens.SpacingSmall)
                    .semantics { contentDescription = "Search records by client name" },
                singleLine = true
            )
            
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = FleetColors.Primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                when (index) {
                                    0 -> "$title (${summary.totalTrips})"
                                    1 -> "$title (${fuelPaging.itemCount})"
                                    else -> title
                                }
                            )
                        },
                        modifier = Modifier.semantics { 
                            contentDescription = "Tab: $title" 
                        }
                    )
                }
            }
            
            // Tab Content
            when (selectedTab) {
                0 -> TripsList(tripsPaging = tripsPaging)
                1 -> FuelList(fuelPaging = fuelPaging)
            }
        }
        
        // Export Options Dialog
        if (showExportOptions) {
            AlertDialog(
                onDismissRequest = { showExportOptions = false },
                title = { Text("Export Earnings Report") },
                text = {
                    Column {
                        Text(
                            "Choose export format:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = FleetDimens.SpacingMedium)
                        )
                        
                        // CSV option
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportOptions = false
                                    csvLauncher.launch(viewModel.getCsvFileName())
                                }
                                .padding(vertical = FleetDimens.SpacingXSmall)
                                .semantics { contentDescription = "Export as CSV spreadsheet" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FleetDimens.SpacingMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.TableChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
                                Column {
                                    Text(
                                        "CSV Spreadsheet",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "For Excel, Google Sheets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                        
                        // PDF option
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportOptions = false
                                    pdfLauncher.launch(viewModel.getPdfFileName())
                                }
                                .padding(vertical = FleetDimens.SpacingXSmall)
                                .semantics { contentDescription = "Export as PDF document" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FleetDimens.SpacingMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
                                Column {
                                    Text(
                                        "PDF Document",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "For printing, sharing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExportOptions = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun MonthNavigatorRow(
    selectedYear: Int,
    selectedMonth: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val currentCalendar = Calendar.getInstance()
    val isCurrentMonth = selectedYear == currentCalendar.get(Calendar.YEAR) &&
            selectedMonth == currentCalendar.get(Calendar.MONTH)
    
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(FleetDimens.SpacingMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.semantics { contentDescription = "Previous month" }
            ) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            
            Text(
                text = "${monthNames[selectedMonth]} $selectedYear",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = onNextMonth,
                enabled = !isCurrentMonth,
                modifier = Modifier.semantics { contentDescription = "Next month" }
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isCurrentMonth) 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else 
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EarningsSummaryCard(summary: DriverReportSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FleetDimens.SpacingMedium)
            .semantics { contentDescription = "Earnings summary for the selected month" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(FleetDimens.SpacingMedium)) {
            Text(
                "Monthly Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(FleetDimens.CornerMedium))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Trips",
                    value = "${summary.totalTrips}",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Bags",
                    value = "${summary.totalBags}",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Distance",
                    value = "${String.format("%.1f", summary.totalDistanceKm)} km",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = FleetDimens.CornerMedium))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Gross Earnings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "₹${String.format("%,.0f", summary.grossEarnings)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2E7D32)
                    )
                }
                Column {
                    Text(
                        "Fuel Cost",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "-₹${String.format("%,.0f", summary.fuelCost)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFC62828)
                    )
                }
                Column {
                    Text(
                        "Net Earnings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "₹${String.format("%,.0f", summary.netEarnings)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripsList(tripsPaging: LazyPagingItems<TripEntity>) {
    if (tripsPaging.itemCount == 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                Text(
                    "No trips found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Trips you complete will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(FleetDimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
        ) {
            items(
                count = tripsPaging.itemCount,
                key = tripsPaging.itemKey { it.id }
            ) { index ->
                tripsPaging[index]?.let { trip ->
                    TripItemCard(trip = trip)
                }
            }
        }
    }
}

@Composable
private fun TripItemCard(trip: TripEntity) {
    val earnings = trip.bagCount * trip.snapshotDriverRate
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { 
                contentDescription = "Trip on ${DateUtils.formatDate(trip.tripDate)}, ${trip.bagCount} bags to ${trip.clientName}, earned ${earnings.toInt()} rupees"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.CornerMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trip.clientName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${trip.bagCount} bags @ ₹${trip.snapshotDriverRate.toInt()}/bag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(FleetDimens.IconMicro),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingExtraSmall))
                    Text(
                        DateUtils.formatDate(trip.tripDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    trip.snapshotDistanceKm?.let { dist ->
                        Spacer(modifier = Modifier.width(FleetDimens.CornerMedium))
                        Icon(
                            Icons.Filled.Route,
                            contentDescription = null,
                            modifier = Modifier.size(FleetDimens.IconMicro),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingExtraSmall))
                        Text(
                            "${String.format("%.1f", dist)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${earnings.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun FuelList(fuelPaging: LazyPagingItems<FuelEntryEntity>) {
    if (fuelPaging.itemCount == 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                Text(
                    "No fuel entries found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Fuel purchases will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(FleetDimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
        ) {
            items(
                count = fuelPaging.itemCount,
                key = fuelPaging.itemKey { it.id }
            ) { index ->
                fuelPaging[index]?.let { fuel ->
                    FuelItemCard(fuel = fuel)
                }
            }
        }
    }
}

@Composable
private fun FuelItemCard(fuel: FuelEntryEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Fuel entry on ${DateUtils.formatDate(fuel.entryDate)}, ${fuel.amount.toInt()} rupees for ${String.format("%.1f", fuel.liters)} liters"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.CornerMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fuel.fuelStation ?: "Unknown Station",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (fuel.liters > 0) {
                    Text(
                        "${String.format("%.1f", fuel.liters)} liters @ ₹${String.format("%.2f", fuel.amount / fuel.liters)}/L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(FleetDimens.IconMicro),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingExtraSmall))
                    Text(
                        DateUtils.formatDate(fuel.entryDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "-₹${fuel.amount.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            }
        }
    }
}
