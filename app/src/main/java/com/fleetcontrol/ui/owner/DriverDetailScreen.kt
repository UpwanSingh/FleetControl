package com.fleetcontrol.ui.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.owner.DriverManagementViewModel
import com.fleetcontrol.utils.CurrencyUtils
import com.fleetcontrol.utils.ValidationUtils
import com.fleetcontrol.core.InviteCodeManager
import com.fleetcontrol.FleetControlApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Driver Detail Screen - Shows individual driver stats
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDetailScreen(
    viewModel: DriverManagementViewModel,
    driverId: Long,
    onBack: () -> Unit
) {
    LaunchedEffect(driverId) {
        viewModel.loadDriverDetail(driverId)
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
    
    val driverDetail by viewModel.driverDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val advances by viewModel.advances.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAdvanceDialog by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showInviteCodeDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isGeneratingCode by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val app = context.applicationContext as FleetControlApplication
    val inviteCodeManager = remember { InviteCodeManager(app.container.firestore) }
    val coroutineScope = rememberCoroutineScope()
    
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
                title = { Text(driverDetail?.driver?.name ?: "Driver Details", color = FleetColors.TextPrimary, fontWeight = FontWeight.Bold) },
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
                        onClick = { viewModel.loadDriverDetail(driverId) },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showAdvanceDialog = true }) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Add Advance")
                    }
                    // Generate Invite Code button
                    IconButton(onClick = { showInviteCodeDialog = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Generate Invite Code")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading || driverDetail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FleetColors.Primary)
            }
        } else {
            val detail = driverDetail!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Earnings Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.Success)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                "This Month's Earnings",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                CurrencyUtils.format(detail.monthlyPayable.grossEarnings),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                
                // Stats Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Total Trips",
                            value = "${detail.totalTrips}",
                            icon = Icons.Default.LocalShipping
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Advance Due",
                            value = CurrencyUtils.format(detail.monthlyPayable.pendingAdvance),
                            icon = Icons.Default.AccountBalanceWallet,
                            isWarning = detail.monthlyPayable.pendingAdvance > 0
                        )
                    }
                }
                
                // Advance History
                if (advances.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FleetDimens.CornerLarge),
                            colors = CardDefaults.cardColors(containerColor = FleetColors.CardBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Advance History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FleetColors.Black
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                advances.sortedByDescending { it.advanceDate }.take(5).forEach { advance ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                com.fleetcontrol.utils.DateUtils.formatDate(advance.advanceDate),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = FleetColors.Black
                                            )
                                            if (!advance.note.isNullOrEmpty()) {
                                                Text(
                                                    advance.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = FleetColors.TextSecondary
                                                )
                                            }
                                        }
                                        Text(
                                            CurrencyUtils.format(advance.amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = FleetColors.Error
                                        )
                                    }
                                    Divider(color = FleetColors.Border, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                // Driver Info Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FleetDimens.CornerLarge),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.CardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Driver Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FleetColors.Black
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            InfoRow("Name", detail.driver.name)
                            InfoRow("Phone", detail.driver.phone)
                            InfoRow("Status", if (detail.driver.isActive) "Active" else "Inactive")
                        }
                    }
                }
                
                // Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeactivateDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FleetColors.Error),
                            border = BorderStroke(1.dp, FleetColors.Error)
                        ) {
                            Icon(Icons.Default.PersonOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deactivate")
                        }
                        Button(
                            onClick = { showAdvanceDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Black)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Give Advance")
                        }
                    }
                }
            }
        }
    }
    
    if (showAdvanceDialog) {
        AddAdvanceDialog(
            onDismiss = { showAdvanceDialog = false },
            onAdd = { amount, note ->
                viewModel.addAdvance(driverId, amount, note)
                showAdvanceDialog = false
            }
        )
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            containerColor = FleetColors.White,
            title = { Text("Deactivate Driver?", color = FleetColors.Black, fontWeight = FontWeight.Bold) },
            text = { Text("This will prevent the driver from logging in. You can reactivate them later.", color = FleetColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.deactivateDriver(driverId) 
                        showDeactivateDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Error)
                ) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text("Cancel", color = FleetColors.Black)
                }
            }
        )
    }
    
    // Invite Code Dialog
    if (showInviteCodeDialog && driverDetail != null) {
        driverDetail?.let { detail ->
        InviteCodeDialog(
            driverName = detail.driver.name,
            code = generatedCode,
            isGenerating = isGeneratingCode,
            onGenerate = {
                coroutineScope.launch {
                    isGeneratingCode = true
                    try {
                        val ownerId = app.container.authService.currentOwnerId ?: ""
                        val firestoreId = detail.driver.firestoreId
                        
                        if (firestoreId.isNullOrEmpty()) {
                            snackbarHostState.showSnackbar("Driver not synced to cloud yet. Please wait.")
                            isGeneratingCode = false
                            return@launch
                        }
                        
                        val code = inviteCodeManager.generateCode(
                            ownerId = ownerId,
                            firestoreDriverId = firestoreId,
                            driverName = detail.driver.name
                        )
                        generatedCode = code
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to generate code: ${e.message}")
                    }
                    isGeneratingCode = false
                }
            },
            onDismiss = {
                showInviteCodeDialog = false
                generatedCode = null
            },
            onShare = { code ->
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, 
                        "Join FleetControl as a driver!\n\nYour invite code: $code\n\nDownload the app and enter this code to join.")
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Invite Code"))
            }
        )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isWarning: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) FleetColors.Error else FleetColors.Black
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = FleetColors.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = FleetColors.White
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = FleetColors.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = FleetColors.TextSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = FleetColors.Black
        )
    }
}

@Composable
private fun AddAdvanceDialog(
    onDismiss: () -> Unit,
    onAdd: (amount: Double, note: String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    
    val amountValidation = remember(amount) { ValidationUtils.validateAmount(amount) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FleetColors.White,
        title = { Text("Give Advance", color = FleetColors.Black, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    isError = amount.isNotEmpty() && !amountValidation.isValid,
                    supportingText = if (amount.isNotEmpty() && !amountValidation.isValid) {
                        { Text(amountValidation.errorMessage ?: "", color = FleetColors.Error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Black,
                        unfocusedBorderColor = FleetColors.Border,
                        cursorColor = FleetColors.Black,
                        focusedLabelColor = FleetColors.Black
                    )
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
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
                onClick = { onAdd(amount.toDoubleOrNull() ?: 0.0, note) },
                enabled = amountValidation.isValid && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Black)
            ) {
                Text("Add Advance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.Black)
            }
        }
    )
}

/**
 * Invite Code Dialog - Generates and displays invite code for driver
 */
@Composable
private fun InviteCodeDialog(
    driverName: String,
    code: String?,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(30 * 60) } // 30 minutes
    
    // Countdown timer
    LaunchedEffect(code) {
        if (code != null) {
            remainingSeconds = 30 * 60
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = null,
                    tint = FleetColors.Primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Invite Code for $driverName",
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (code == null) {
                    // Generate code prompt
                    Text(
                        "Generate a 6-digit invite code that the driver can use to join your fleet on their own device.",
                        color = FleetColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Show generated code
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Code display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = FleetColors.Primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 8.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Timer
                            val minutes = remainingSeconds / 60
                            val seconds = remainingSeconds % 60
                            Text(
                                text = "Expires in ${minutes}m ${seconds}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Copy and Share buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(code))
                                copied = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = FleetColors.Primary
                            )
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copied) "Copied!" else "Copy")
                        }
                        
                        Button(
                            onClick = { onShare(code) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FleetColors.Primary
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Share this code with $driverName via WhatsApp or phone call",
                        color = FleetColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (code == null) {
                Button(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Generate Code")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (code == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = FleetColors.TextSecondary)
                }
            }
        }
    )
}
