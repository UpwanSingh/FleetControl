package com.fleetcontrol.ui.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.owner.ReportsViewModel
import com.fleetcontrol.viewmodel.owner.ExportResult
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.*

/**
 * Reports Screen - Trip history and export
 * Implements Section 9.2, 12 of BUSINESS_LOGIC_SPEC.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit
) {
    val trips by viewModel.trips.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val canExportCsv by viewModel.canExportCsv.collectAsState()
    val canExportPdf by viewModel.canExportPdf.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val exportResult by viewModel.exportResult.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // CSV file picker launcher
    val csvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        viewModel.getCsvExportService().writeCsvToStream(
                            outputStream,
                            viewModel.getTripsForExport()
                        )
                    }
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("CSV exported successfully!")
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Export failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    // PDF file picker launcher
    val pdfFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        viewModel.getPdfExportService().writePdfToStream(
                            outputStream,
                            viewModel.getTripsForExport(),
                            viewModel.getSelectedYear(),
                            viewModel.getSelectedMonth()
                        )
                    }
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("PDF exported successfully!")
                    }
                } catch (e: Exception) {
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Export failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    // Status bar styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = FleetColors.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    
    // Listen for export results
    LaunchedEffect(exportResult) {
        exportResult?.let { result ->
            when (result) {
                is ExportResult.Success -> {
                    snackbarHostState.showSnackbar("Export saved to: ${result.filePath}")
                    viewModel.clearExportResult()
                }
                is ExportResult.Error -> {
                    snackbarHostState.showSnackbar("Export Failed: ${result.message}", withDismissAction = true)
                    viewModel.clearExportResult()
                }
                is ExportResult.RequiresUpgrade -> {
                    snackbarHostState.showSnackbar("Upgrade Required: ${result.message}", withDismissAction = true)
                    viewModel.clearExportResult()
                }
            }
        }
    }

    // Export Confirmation Dialog State
    var showExportConfirmDialog by remember { mutableStateOf<String?>(null) } // "CSV" or "PDF"

    Scaffold(
        containerColor = FleetColors.Surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reports", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                        onClick = { 
                            if (viewModel.canExportCsvNow()) {
                                showExportConfirmDialog = "CSV"
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("CSV export requires Basic subscription")
                                }
                            }
                        },
                        enabled = canExportCsv
                    ) {
                        Icon(
                            Icons.Default.FileDownload, 
                            contentDescription = "Export CSV",
                            tint = if (canExportCsv) FleetColors.Success else FleetColors.TextPrimary.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { 
                            if (viewModel.canExportPdfNow()) {
                                showExportConfirmDialog = "PDF"
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("PDF export requires Premium subscription")
                                }
                            }
                        },
                        enabled = canExportPdf
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf, 
                            contentDescription = "Export PDF",
                            tint = if (canExportPdf) FleetColors.Error else FleetColors.TextPrimary.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        
        if (showExportConfirmDialog != null) {
            AlertDialog(
                onDismissRequest = { showExportConfirmDialog = null },
                title = { Text("Export ${showExportConfirmDialog}?") },
                text = { Text("Do you want to export the current report as a ${showExportConfirmDialog} file?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (showExportConfirmDialog == "CSV") {
                                csvFileLauncher.launch(viewModel.getCsvFileName())
                            } else {
                                pdfFileLauncher.launch(viewModel.getPdfFileName())
                            }
                            showExportConfirmDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showExportConfirmDialog == "CSV") FleetColors.Success else FleetColors.Error
                        )
                    ) {
                        Text("Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportConfirmDialog = null }) {
                        Text("Cancel", color = FleetColors.TextSecondary)
                    }
                },
                containerColor = Color.White
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = FleetColors.TextPrimary)
                    }
                    
                    // Clickable month text with calendar icon
                    Row(
                        modifier = Modifier.clickable { showDatePicker = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Pick date",
                            tint = FleetColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = monthFormat.format(currentMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                    }
                    
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = FleetColors.TextPrimary)
                    }
                }
            }
            
            // Search Bar
            val searchQuery by viewModel.searchQuery.collectAsState()
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search client, driver...", color = FleetColors.TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = FleetColors.TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FleetColors.Primary,
                    unfocusedBorderColor = FleetColors.Border,
                    cursorColor = FleetColors.Primary,
                    focusedTextColor = FleetColors.TextPrimary,
                    unfocusedTextColor = FleetColors.TextPrimary
                )
            )
            
            // Export Status
            if (!canExportCsv || !canExportPdf) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.Warning.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = FleetColors.Warning
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Upgrade to export",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FleetColors.Black
                            )
                            Text(
                                if (!canExportCsv) "CSV: Basic+ | PDF: Premium"
                                else "PDF export requires Premium",
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Trip List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FleetColors.Primary)
                }
            } else if (trips.isEmpty()) {
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
                                Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = FleetColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                        Text(
                            "No trips this month",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                        Text(
                            "Tap refresh to reload",
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
                    items(trips) { trip ->
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
                                // Trip icon
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(FleetColors.Primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = FleetColors.Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        trip.clientName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = FleetColors.Black
                                    )
                                    Text(
                                        "${dateFormat.format(trip.tripDate)} • ${trip.bagCount} bags",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FleetColors.TextSecondary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        CurrencyUtils.format(trip.bagCount * trip.snapshotCompanyRate),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FleetColors.Success
                                    )
                                    Text(
                                        "Revenue",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FleetColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Month/Year Picker Dialog
    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { time = currentMonth }
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonthValue = calendar.get(Calendar.MONTH)
        
        var selectedYear by remember { mutableIntStateOf(currentYear) }
        var selectedMonth by remember { mutableIntStateOf(currentMonthValue) }
        
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val years = (2020..Calendar.getInstance().get(Calendar.YEAR)).toList()
        
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select Month & Year") },
            text = {
                Column {
                    // Year selector
                    Text("Year", style = MaterialTheme.typography.labelMedium, color = FleetColors.TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        years.takeLast(5).forEach { year ->
                            FilterChip(
                                selected = selectedYear == year,
                                onClick = { selectedYear = year },
                                label = { Text(year.toString()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FleetColors.Primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Month selector
                    Text("Month", style = MaterialTheme.typography.labelMedium, color = FleetColors.TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 3 rows of 4 months
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0..2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..3) {
                                    val monthIndex = row * 4 + col
                                    FilterChip(
                                        selected = selectedMonth == monthIndex,
                                        onClick = { selectedMonth = monthIndex },
                                        label = { Text(months[monthIndex].take(3)) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FleetColors.Primary,
                                            selectedLabelColor = Color.White
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
                    onClick = {
                        viewModel.setMonth(selectedYear, selectedMonth)
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = FleetColors.TextPrimary)
                }
            }
        )
    }
}
