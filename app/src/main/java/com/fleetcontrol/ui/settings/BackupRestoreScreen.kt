package com.fleetcontrol.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleetcontrol.services.backup.BackupInfo
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.ui.components.RefreshableContainer
import com.fleetcontrol.viewmodel.settings.BackupStatus
import com.fleetcontrol.viewmodel.settings.BackupViewModel
import com.fleetcontrol.viewmodel.settings.RestoreStatus

/**
 * Backup & Restore Screen - Fully functional
 * Creates zip backups of database, allows restore and sharing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val backups by viewModel.backups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedBackup by remember { mutableStateOf<BackupInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showAllBackups by remember { mutableStateOf(false) }
    
    // New state for pre-upload dialog
    var showCloudInfoDialog by remember { mutableStateOf(false) }
    var backupToSave by remember { mutableStateOf<BackupInfo?>(null) }
    
    // File picker for external restore
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreFromUri(it) }
    }
    
    // File saver for cloud backup
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { 
            backupToSave?.let { backup ->
                viewModel.saveBackupToUri(context, backup, it)
            }
        }
    }
    
    // Cloud Backup
    val cloudUri by viewModel.cloudBackupUri.collectAsState()
    val backupFrequency by viewModel.backupFrequency.collectAsState()
    
    val cloudLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setCloudBackupLocation(context, uri)
        }
    }
    
    // Status Bar Enforcement
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadBackupFrequency(context)
        viewModel.loadCloudBackupLocation(context)
    }

    LaunchedEffect(backupStatus) {
        if (backupStatus is BackupStatus.Success) {
            // Trigger share dialog
            showShareDialog = true
        }
    }

    if (showShareDialog && backupStatus is BackupStatus.Success) {
        val file = (backupStatus as BackupStatus.Success).fileName
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Backup Created") },
            text = { Text("Backup '$file' created successfully.\n\nWould you like to share it to Drive/Email now?") },
            confirmButton = {
                Button(onClick = {
                    val info = backups.find { it.fileName == file }
                    if (info != null) {
                        viewModel.shareBackup(context, info)
                    }
                    showShareDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)) {
                    Text("Share Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text("Later", color = FleetColors.TextPrimary)
                }
            },
             containerColor = Color.White,
            titleContentColor = FleetColors.TextPrimary,
            textContentColor = FleetColors.TextSecondary
        )
    }

    // Cloud Save Info Dialog (Pre-upload feedback)
    if (showCloudInfoDialog && backupToSave != null) {
        AlertDialog(
            onDismissRequest = { showCloudInfoDialog = false },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = FleetColors.Primary) },
            title = { Text("Save to Cloud") },
            text = { Text("You are about to save this backup externally.\n\nPlease select 'Drive' or your preferred cloud storage to keep your data safe.") },
            confirmButton = {
                Button(onClick = {
                    backupToSave?.let { backup ->
                        saveFileLauncher.launch("fleet_control_backup_${backup.createdAt.time}.zip")
                        showCloudInfoDialog = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)) {
                    Text("Select Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudInfoDialog = false }) {
                    Text("Cancel", color = FleetColors.TextPrimary)
                }
            },
            containerColor = Color.White,
            titleContentColor = FleetColors.TextPrimary,
            textContentColor = FleetColors.TextSecondary
        )
    }
    
    // Clear status after showing
    LaunchedEffect(backupStatus, restoreStatus) {
        if (backupStatus is BackupStatus.Success) {
            val status = backupStatus as BackupStatus.Success
            // Find the matching backup info object to enable sharing
            // We'll delay slightly to ensure the list is refreshed
            kotlinx.coroutines.delay(500)
            val newBackup = backups.find { it.fileName == status.fileName }
            if (newBackup != null) {
                selectedBackup = newBackup
            }
            
            kotlinx.coroutines.delay(4500) // Remaining delay
            viewModel.clearStatus()
        } else if (backupStatus is BackupStatus.CloudSuccess) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearStatus()
        } 
        // NOTE: We do NOT auto-clear RestoreStatus.Success anymore.
        // We act on it via the Restart Dialog below.
    }
    
    // Restore Success -> Restart Dialog
    if (restoreStatus is RestoreStatus.Success) {
        AlertDialog(
            onDismissRequest = { }, // Force restart
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FleetColors.Success) },
            title = { Text("Restore Complete") },
            text = { Text("Data restored successfully.\n\nThe app needs to restart to load your restored data.") },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
                ) {
                    Text("Restart App", color = Color.White)
                }
            },
            containerColor = Color.White,
            titleContentColor = FleetColors.TextPrimary,
            textContentColor = FleetColors.TextSecondary
        )
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                    IconButton(onClick = { viewModel.loadBackups() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        RefreshableContainer(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadBackups() }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = FleetColors.Primary.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = FleetColors.Primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Backups are stored locally on your device. Share backups to save them to Google Drive, WhatsApp, or other apps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextPrimary
                        )
                    }
                }
            }
            
            // Status Messages (Exclude Restore Success as it's a Dialog now)
            item {
                when (val status = backupStatus) {
                    is BackupStatus.Success -> StatusCard(
                        icon = Icons.Default.CheckCircle,
                        message = "Backup created: ${status.fileName} (${status.fileSize})",
                        isError = false
                    )
                    is BackupStatus.CloudSuccess -> StatusCard(
                        icon = Icons.Default.CloudDone,
                        message = status.message,
                        isError = false
                    )
                    is BackupStatus.Error -> StatusCard(
                        icon = Icons.Default.Error,
                        message = status.message,
                        isError = true
                    )
                    else -> {}
                }
                
                when (val status = restoreStatus) {
                    is RestoreStatus.Error -> StatusCard(
                        icon = Icons.Default.Error,
                        message = status.message,
                        isError = true
                    )
                    else -> {}
                }
            }
            
            // Auto-Backup Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = FleetColors.Primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Automatic Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Frequency", style = MaterialTheme.typography.labelMedium, color = FleetColors.TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        com.fleetcontrol.services.backup.BackupFrequency.values().forEach { freq ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setBackupFrequency(context, freq) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (backupFrequency == freq),
                                    onClick = { viewModel.setBackupFrequency(context, freq) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = FleetColors.Primary,
                                        unselectedColor = FleetColors.TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when(freq) {
                                        com.fleetcontrol.services.backup.BackupFrequency.OFF -> "Off (Manual only)"
                                        com.fleetcontrol.services.backup.BackupFrequency.DAILY -> "Daily (Recommended)"
                                        com.fleetcontrol.services.backup.BackupFrequency.WEEKLY -> "Weekly"
                                        com.fleetcontrol.services.backup.BackupFrequency.MONTHLY -> "Monthly"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FleetColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Cloud Location Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = FleetColors.Primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cloud Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Automatically save backups to a specific folder (e.g., Google Drive) for safety.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))

                        if (cloudUri != null) {
                            Text(
                                "Connected Folder:",
                                style = MaterialTheme.typography.labelSmall,
                                color = FleetColors.TextSecondary
                            )
                            Text(
                                "✅ ...${cloudUri?.lastPathSegment?.takeLast(20) ?: "Cloud Folder"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = FleetColors.Success
                            )
                            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                            OutlinedButton(
                                onClick = { viewModel.clearCloudBackupLocation(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FleetColors.Error),
                                border = BorderStroke(1.dp, FleetColors.Error.copy(alpha = 0.5f))
                            ) {
                                Text("Disconnect Folder")
                            }
                        } else {
                            Button(
                                onClick = { cloudLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Cloud Folder")
                            }
                        }
                    }
                }
            }
            
            // Create Backup Section
            item {
                Text(
                    "Create Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Export all your data including trips, drivers, companies, advances, and settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.createBackup() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && backupStatus !is BackupStatus.InProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary, contentColor = Color.White)
                        ) {
                            if (backupStatus is BackupStatus.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (backupStatus is BackupStatus.InProgress) "Creating Backup..." else "Create Backup Now")
                        }
                    }
                }
            }
            
            // Restore Section
            item {
                Text(
                    "Restore Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Restore from a backup file. This will replace all current data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("application/zip")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && restoreStatus !is RestoreStatus.InProgress,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FleetColors.Primary),
                            border = BorderStroke(1.dp, FleetColors.Primary),
                        ) {
                            if (restoreStatus is RestoreStatus.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = FleetColors.Primary
                                )
                            } else {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Backup File")
                        }
                    }
                }
            }
            
            // Previous Backups
            if (backups.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FleetDimens.SpacingMedium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Previous Backups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FleetColors.TextPrimary
                        )
                        Text(
                            "${backups.size} backup${if (backups.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FleetColors.TextSecondary
                        )
                    }
                }
                
                // Show only last 5 backups by default, or all if expanded
                val displayedBackups = if (showAllBackups) backups else backups.take(5)
                
                items(displayedBackups) { backup ->
                    BackupCard(
                        backup = backup,
                        onRestore = {
                            selectedBackup = backup
                            showRestoreDialog = true
                        },
                        onShare = { 
                            backupToSave = backup
                            showCloudInfoDialog = true
                        },
                        onShareIntent = { viewModel.shareBackup(context, backup) },
                        onDelete = {
                            selectedBackup = backup
                            showDeleteDialog = true
                        }
                    )
                }
                
                // Show "View all" / "Show less" button if more than 5 backups
                if (backups.size > 5) {
                    item {
                        TextButton(
                            onClick = { showAllBackups = !showAllBackups },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (showAllBackups) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = FleetColors.Primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (showAllBackups) "Show less" else "View all ${backups.size} backups",
                                color = FleetColors.Primary
                            )
                        }
                    }
                }
            }
        }
        }
    }
    
    // Restore Confirmation Dialog
    if (showRestoreDialog && selectedBackup != null) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = FleetColors.Warning) },
            title = { Text("Restore Data?") },
            text = { 
                Text("This will replace ALL your current data with the backup from ${selectedBackup?.formattedDate}. You will need to restart the app after restore. This action cannot be undone.") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedBackup?.let { viewModel.restoreFromBackup(it) }
                        showRestoreDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FleetColors.Error
                    )
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = FleetColors.TextPrimary)
                }
            },
            containerColor = Color.White,
            titleContentColor = FleetColors.TextPrimary,
            textContentColor = FleetColors.TextSecondary
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && selectedBackup != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Backup?") },
            text = { Text("Are you sure you want to delete this backup? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedBackup?.let { viewModel.deleteBackup(it) }
                        showDeleteDialog = false
                        selectedBackup = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FleetColors.Error
                    )
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = FleetColors.TextPrimary)
                }
            },
            containerColor = Color.White,
            titleContentColor = FleetColors.TextPrimary,
            textContentColor = FleetColors.TextSecondary
        )
    }
    
    // Share Confirmation Dialog (Auto-prompt)
    if (showShareDialog && selectedBackup != null) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            icon = { Icon(Icons.Default.Share, contentDescription = null, tint = FleetColors.Primary) },
            title = { Text("Backup Created!") },
            text = { 
                Text("Your backup is saved locally. Would you like to share it to Google Drive or WhatsApp for safety?") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedBackup?.let { viewModel.shareBackup(context, it) }
                        showShareDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
                ) {
                    Text("Share Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text("Later", color = FleetColors.TextPrimary)
                }
            },
            containerColor = Color.White,
            titleContentColor = FleetColors.TextPrimary,
            textContentColor = FleetColors.TextSecondary
        )
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    isError: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) 
                FleetColors.Error.copy(alpha = 0.1f)
            else 
                FleetColors.Success.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isError) FleetColors.Error else FleetColors.Success,
                modifier = Modifier.size(FleetDimens.IconMedium)
            )
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) FleetColors.Error else FleetColors.TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupCard(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onShare: () -> Unit, // This is now Cloud Save
    onShareIntent: () -> Unit, // This is generic share
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backup info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    backup.formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                Text(
                    backup.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
            
            // Action buttons - icons only
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restore
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Restore, 
                        contentDescription = "Restore",
                        tint = FleetColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Cloud Save
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.CloudUpload, 
                        contentDescription = "Save to Cloud",
                        tint = FleetColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share
                IconButton(
                    onClick = onShareIntent,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Share, 
                        contentDescription = "Share",
                        tint = FleetColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = FleetColors.Error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
