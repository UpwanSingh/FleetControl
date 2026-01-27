package com.fleetcontrol.viewmodel.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.fleetcontrol.services.backup.BackupInfo
import com.fleetcontrol.services.backup.BackupResult
import com.fleetcontrol.services.backup.BackupService
import com.fleetcontrol.services.backup.RestoreResult
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for Backup & Restore functionality
 */
class BackupViewModel(
    private val backupService: BackupService
) : BaseViewModel() {
    
    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backups: StateFlow<List<BackupInfo>> = _backups.asStateFlow()
    
    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()
    
    private val _restoreStatus = MutableStateFlow<RestoreStatus>(RestoreStatus.Idle)
    val restoreStatus: StateFlow<RestoreStatus> = _restoreStatus.asStateFlow()
    
    init {
        loadBackups()
    }
    
    /**
     * Load available backups
     */
    fun loadBackups() {
        _backups.value = backupService.getAvailableBackups()
    }
    
    /**
     * Create a new backup
     */
    fun createBackup() {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress
            _isLoading.value = true
            
            when (val result = backupService.createBackup()) {
                is BackupResult.Success -> {
                    _backupStatus.value = BackupStatus.Success(
                        fileName = result.fileName,
                        fileSize = formatSize(result.fileSize)
                    )
                    loadBackups()
                }
                is BackupResult.Error -> {
                    _backupStatus.value = BackupStatus.Error(result.message)
                    _error.value = result.message
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Restore from a backup file
     */
    fun restoreFromBackup(backupInfo: BackupInfo) {
        viewModelScope.launch {
            _restoreStatus.value = RestoreStatus.InProgress
            _isLoading.value = true
            
            when (val result = backupService.restoreFromBackup(backupInfo.filePath)) {
                is RestoreResult.Success -> {
                    _restoreStatus.value = RestoreStatus.Success(
                        message = "Data restored successfully. Please restart the app."
                    )
                }
                is RestoreResult.Error -> {
                    _restoreStatus.value = RestoreStatus.Error(result.message)
                    _error.value = result.message
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Restore from external file URI
     */
    fun restoreFromUri(uri: Uri) {
        viewModelScope.launch {
            _restoreStatus.value = RestoreStatus.InProgress
            _isLoading.value = true
            
            when (val result = backupService.restoreFromUri(uri)) {
                is RestoreResult.Success -> {
                    _restoreStatus.value = RestoreStatus.Success(
                        message = "Data restored successfully. Please restart the app."
                    )
                }
                is RestoreResult.Error -> {
                    _restoreStatus.value = RestoreStatus.Error(result.message)
                    _error.value = result.message
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Save existing backup to Cloud/External URI
     */
    fun saveBackupToUri(context: Context, backupInfo: BackupInfo?, targetUri: Uri) {
        if (backupInfo == null) {
            _error.value = "No backup selected"
            return
        }
        
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress
            _isLoading.value = true
            
            try {
                val sourceFile = File(backupInfo.filePath)
                if (!sourceFile.exists()) {
                    _backupStatus.value = BackupStatus.Error("Backup file not found")
                    _isLoading.value = false
                    return@launch
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("Could not open output stream")
                }
                _backupStatus.value = BackupStatus.CloudSuccess(
                    message = "Successfully saved to Cloud Storage"
                )
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error("Failed to save to cloud: ${e.message}")
                _error.value = "Failed to save: ${e.message}"
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Delete a backup
     */
    fun deleteBackup(backupInfo: BackupInfo) {
        if (backupService.deleteBackup(backupInfo.filePath)) {
            loadBackups()
        } else {
            _error.value = "Failed to delete backup"
        }
    }
    
    /**
     * Share a backup file
     */
    fun shareBackup(context: Context, backupInfo: BackupInfo) {
        val file = File(backupInfo.filePath)
        if (!file.exists()) {
            _error.value = "Backup file not found"
            return
        }
        
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FleetControl Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share Backup"))
        } catch (e: Exception) {
            _error.value = "Failed to share backup: ${e.message}"
        }
    }
    
    /**
     * Clear status messages
     */
    fun clearStatus() {
        _backupStatus.value = BackupStatus.Idle
        _restoreStatus.value = RestoreStatus.Idle
        clearError()
    }
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    // === Auto-Backup ===
    
    private val _backupFrequency = MutableStateFlow(com.fleetcontrol.services.backup.BackupFrequency.OFF)
    val backupFrequency: StateFlow<com.fleetcontrol.services.backup.BackupFrequency> = _backupFrequency.asStateFlow()
    
    fun loadBackupFrequency(context: Context) {
        _backupFrequency.value = com.fleetcontrol.services.backup.BackupScheduler.getFrequency(context)
    }
    
    fun setBackupFrequency(context: Context, frequency: com.fleetcontrol.services.backup.BackupFrequency) {
        com.fleetcontrol.services.backup.BackupScheduler.scheduleBackup(context, frequency)
        _backupFrequency.value = frequency
    }

    // === Cloud Backup ===

    private val _cloudBackupUri = MutableStateFlow<Uri?>(null)
    val cloudBackupUri: StateFlow<Uri?> = _cloudBackupUri.asStateFlow()

    fun loadCloudBackupLocation(context: Context) {
        _cloudBackupUri.value = com.fleetcontrol.services.backup.BackupScheduler.getCloudBackupUri(context)
    }

    fun setCloudBackupLocation(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Persist permission so we can write to this folder later in background
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                // Save to prefs
                com.fleetcontrol.services.backup.BackupScheduler.setCloudBackupUri(context, uri)
                _cloudBackupUri.value = uri
            } catch (e: Exception) {
                _error.value = "Failed to set cloud location: ${e.message}"
            }
        }
    }

    fun clearCloudBackupLocation(context: Context) {
        viewModelScope.launch {
            try {
                val currentUri = _cloudBackupUri.value
                if (currentUri != null) {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(currentUri, flags)
                }
            } catch (e: Exception) {
                // Ignore release errors
            }
            com.fleetcontrol.services.backup.BackupScheduler.setCloudBackupUri(context, null)
            _cloudBackupUri.value = null
        }
    }
}

/**
 * Backup operation status
 */
sealed class BackupStatus {
    object Idle : BackupStatus()
    object InProgress : BackupStatus()
    data class Success(val fileName: String, val fileSize: String) : BackupStatus()
    data class CloudSuccess(val message: String) : BackupStatus()
    data class Error(val message: String) : BackupStatus()
}

/**
 * Restore operation status
 */
sealed class RestoreStatus {
    object Idle : RestoreStatus()
    object InProgress : RestoreStatus()
    data class Success(val message: String) : RestoreStatus()
    data class Error(val message: String) : RestoreStatus()
}
