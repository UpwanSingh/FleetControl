package com.fleetcontrol.services.backup

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Database restore service
 */
class RestoreService(private val context: Context) {
    
    companion object {
        private const val DATABASE_NAME = "fleetcontrol_database"
    }
    
    /**
     * Restore database from backup file
     * Returns true if successful
     */
    suspend fun restoreFromBackup(backupFilePath: String): Boolean {
        val backupFile = File(backupFilePath)
        if (!backupFile.exists()) {
            return false
        }
        
        val databasePath = context.getDatabasePath(DATABASE_NAME)
        val dbDir = databasePath.parentFile
        
        // Close any open database connections first
        // In a real app, you'd need to coordinate this with the database instance
        
        ZipInputStream(backupFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val destFile = File(dbDir, entry.name)
                
                FileOutputStream(destFile).use { fos ->
                    zipIn.copyTo(fos)
                }
                
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        
        return true
    }
    
    /**
     * Validate backup file
     */
    fun validateBackup(backupFilePath: String): BackupValidationResult {
        val backupFile = File(backupFilePath)
        
        if (!backupFile.exists()) {
            return BackupValidationResult.NotFound
        }
        
        if (backupFile.length() == 0L) {
            return BackupValidationResult.Empty
        }
        
        // Check if it's a valid zip file
        try {
            ZipInputStream(backupFile.inputStream()).use { zipIn ->
                var hasDatabase = false
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "fleetcontrol_database") {
                        hasDatabase = true
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                
                return if (hasDatabase) {
                    BackupValidationResult.Valid
                } else {
                    BackupValidationResult.Invalid("No database found in backup")
                }
            }
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(e.message ?: "Unknown error")
        }
    }
}

sealed class BackupValidationResult {
    object Valid : BackupValidationResult()
    object NotFound : BackupValidationResult()
    object Empty : BackupValidationResult()
    data class Invalid(val reason: String) : BackupValidationResult()
}
