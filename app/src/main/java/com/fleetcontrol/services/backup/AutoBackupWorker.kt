package com.fleetcontrol.services.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val MAX_LOCAL_BACKUPS = 5 // Keep only last 5 auto-backups
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val backupService = BackupService(applicationContext)
            val result = backupService.createBackup()
            
            when (result) {
                is BackupResult.Success -> {
                    Log.d(TAG, "Auto-backup successful: ${result.fileName}")
                    
                    // Cleanup old backups to save storage
                    cleanupOldBackups(backupService)
                    
                    // Check for Cloud Backup
                    val cloudUri = BackupScheduler.getCloudBackupUri(applicationContext)
                    if (cloudUri != null) {
                        uploadToCloud(result, cloudUri)
                    }
                    Result.success()
                }
                is BackupResult.Error -> {
                    Log.e(TAG, "Auto-backup failed: ${result.message}")
                    if (result.message.contains("Security Restricted")) {
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup exception", e)
            Result.failure()
        }
    }
    
    private suspend fun uploadToCloud(result: BackupResult.Success, cloudUri: android.net.Uri) {
        try {
            val sourceFile = java.io.File(result.filePath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file not found for cloud upload: ${result.filePath}")
                return
            }
            
            val targetDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, cloudUri)
            
            if (targetDir != null && targetDir.canWrite()) {
                // Delete existing file with same name if exists
                targetDir.findFile(result.fileName)?.delete()
                
                val cloudFile = targetDir.createFile("application/zip", result.fileName)
                if (cloudFile != null) {
                    applicationContext.contentResolver.openOutputStream(cloudFile.uri)?.use { out ->
                        sourceFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    Log.d(TAG, "Cloud backup successful for ${result.fileName}")
                } else {
                    Log.e(TAG, "Failed to create cloud file for ${result.fileName}")
                }
            } else {
                Log.e(TAG, "Cloud target directory not writable or null for URI: $cloudUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud backup failed for ${result.fileName}", e)
            // Fail silently for cloud part, local backup is still safe
        }
    }
    
    private fun cleanupOldBackups(backupService: BackupService) {
        try {
            val backups = backupService.getAvailableBackups()
            if (backups.size > MAX_LOCAL_BACKUPS) {
                // Delete oldest backups (list is sorted by date descending)
                backups.drop(MAX_LOCAL_BACKUPS).forEach { backup ->
                    backupService.deleteBackup(backup.filePath)
                    Log.d(TAG, "Deleted old backup: ${backup.fileName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old backups", e)
        }
    }
}
