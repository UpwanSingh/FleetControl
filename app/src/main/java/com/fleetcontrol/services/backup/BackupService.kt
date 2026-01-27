package com.fleetcontrol.services.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.util.Log

/**
 * Full Database Backup and Restore Service
 * Creates zip backups of SQLite database files
 */
class BackupService(private val context: Context) {
    
    companion object {
        private const val DATABASE_NAME = com.fleetcontrol.core.AppConfig.DATABASE_NAME
        private const val BACKUP_PREFIX = "fleetcontrol_backup_"
        private const val DB_ENTRY_NAME = "fleetcontrol_database"
    }
    
    /**
     * Create backup of database
     * Returns BackupResult with file path on success
     */
    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDirectory()
            val databasePath = context.getDatabasePath(DATABASE_NAME)
            
            if (!databasePath.exists()) {
                return@withContext BackupResult.Error("Database not found")
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFileName = "${BACKUP_PREFIX}$timestamp.zip"
            val backupFile = File(backupDir, backupFileName)
            
            // SECURITY: Force WAL Checkpoint to flush transactions to .db file
            // prevent "dirty" backups where .wal contains critical data not yet in .db
            try {
                val db = com.fleetcontrol.data.database.AppDatabase.getDatabase(context)
                
                // SECURITY CHECK: PREVENT DATA LEAKAGE ON SHARED DEVICES
                // If multiple owners exist, raw DB backup is strictly forbidden as it contains everyone's data.
                val ownerCount = db.ownerDao().getOwnerCount()
                if (ownerCount > 1) {
                    return@withContext BackupResult.Error("Security Restricted: Local Backup is disabled on Shared Devices. Please use Cloud Sync.")
                }
                
                val cursor = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                if (cursor.moveToFirst()) {
                    // Checkpoint successful
                }
                cursor.close()
            } catch (e: Exception) {
                // Return error if security check failed
                if (e.message?.contains("Security Restricted") == true) {
                    return@withContext BackupResult.Error(e.message ?: "Security Restricted")
                }
                // Log but continue for other errors - backup is better than no backup
                Log.e("BackupService", "Error during backup", e)
            }
            
            // Create backup
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // Add main database file
                addFileToZip(zipOut, databasePath, DB_ENTRY_NAME)
                
                // Add WAL file if exists
                val walFile = File("${databasePath.absolutePath}-wal")
                if (walFile.exists()) {
                    addFileToZip(zipOut, walFile, "$DB_ENTRY_NAME-wal")
                }
                
                // Add SHM file if exists
                val shmFile = File("${databasePath.absolutePath}-shm")
                if (shmFile.exists()) {
                    addFileToZip(zipOut, shmFile, "$DB_ENTRY_NAME-shm")
                }
            }
            
            // Verify the backup was created
            if (!backupFile.exists() || backupFile.length() == 0L) {
                return@withContext BackupResult.Error("Backup file creation failed")
            }
            
            // Calculate size
            val size = backupFile.length()
            
            BackupResult.Success(
                filePath = backupFile.absolutePath,
                fileName = backupFileName,
                fileSize = size
            )
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Failed to create backup")
        }
    }
    
    /**
     * Restore from a backup file (internal path)
     */
    suspend fun restoreFromBackup(filePath: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val backupFile = File(filePath)
            if (!backupFile.exists()) {
                return@withContext RestoreResult.Error("Backup file not found")
            }
            
            // Validate zip file before restore
            if (!isValidBackupZip(backupFile)) {
                return@withContext RestoreResult.Error("Invalid or corrupted backup file")
            }
            
            val databasePath = context.getDatabasePath(DATABASE_NAME)
            val databaseDir = databasePath.parentFile ?: return@withContext RestoreResult.Error("Database path error")
            
            // Close database connections securely to release file locks
            com.fleetcontrol.data.database.AppDatabase.closeDatabase()
            
            // Small delay to ensure file handles are released
            kotlinx.coroutines.delay(100)
            
            // SECURITY: Delete existing database files to prevent corruption
            databasePath.delete()
            File("${databasePath.absolutePath}-wal").delete()
            File("${databasePath.absolutePath}-shm").delete()
            
            // Extract backup - map entry names to actual database filenames
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    // Map backup entry names to actual database filenames
                    val outputFileName = when (entry.name) {
                        DB_ENTRY_NAME -> DATABASE_NAME
                        "$DB_ENTRY_NAME-wal" -> "$DATABASE_NAME-wal"
                        "$DB_ENTRY_NAME-shm" -> "$DATABASE_NAME-shm"
                        else -> entry.name // Fallback for legacy backups
                    }
                    val outputFile = File(databaseDir, outputFileName)
                    FileOutputStream(outputFile).use { fos ->
                        zipIn.copyTo(fos)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            // CRITICAL: Reinitialize the database after restore
            // This ensures the app uses the restored data immediately
            com.fleetcontrol.data.database.AppDatabase.getDatabase(context)
            
            RestoreResult.Success(backupDate = Date(backupFile.lastModified()))
        } catch (e: Exception) {
            RestoreResult.Error(e.message ?: "Failed to restore backup")
        }
    }
    
    /**
     * Restore from external URI (from file picker)
     */
    suspend fun restoreFromUri(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext RestoreResult.Error("Could not open file")
            
            // Copy to temp file first to validate
            val tempFile = File(context.cacheDir, "temp_restore.zip")
            FileOutputStream(tempFile).use { fos ->
                inputStream.copyTo(fos)
            }
            
            // Validate zip file
            if (!isValidBackupZip(tempFile)) {
                tempFile.delete()
                return@withContext RestoreResult.Error("Invalid or corrupted backup file")
            }
            
            val databasePath = context.getDatabasePath(DATABASE_NAME)
            val databaseDir = databasePath.parentFile ?: return@withContext RestoreResult.Error("Database path error")
            
            // Close database connections securely
            com.fleetcontrol.data.database.AppDatabase.closeDatabase()
            
            // Small delay to ensure file handles are released
            kotlinx.coroutines.delay(100)
            
            // SECURITY: Delete existing database files
            databasePath.delete()
            File("${databasePath.absolutePath}-wal").delete()
            File("${databasePath.absolutePath}-shm").delete()
            
            // Extract backup from temp file - map entry names to actual database filenames
            ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    // Map backup entry names to actual database filenames
                    val outputFileName = when (entry.name) {
                        DB_ENTRY_NAME -> DATABASE_NAME
                        "$DB_ENTRY_NAME-wal" -> "$DATABASE_NAME-wal"
                        "$DB_ENTRY_NAME-shm" -> "$DATABASE_NAME-shm"
                        else -> entry.name // Fallback for legacy backups
                    }
                    val outputFile = File(databaseDir, outputFileName)
                    FileOutputStream(outputFile).use { fos ->
                        zipIn.copyTo(fos)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            // Cleanup temp file
            tempFile.delete()
            
            // CRITICAL: Reinitialize the database after restore
            // This ensures the app uses the restored data immediately
            com.fleetcontrol.data.database.AppDatabase.getDatabase(context)
            
            RestoreResult.Success(backupDate = Date())
        } catch (e: Exception) {
            RestoreResult.Error(e.message ?: "Failed to restore backup")
        }
    }
    
    /**
     * Validate if the zip file is a valid FleetControl backup
     */
    private fun isValidBackupZip(file: File): Boolean {
        return try {
            ZipInputStream(FileInputStream(file)).use { zipIn ->
                var hasDatabase = false
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == DB_ENTRY_NAME) {
                        hasDatabase = true
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                hasDatabase
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get list of available backups
     */
    fun getAvailableBackups(): List<BackupInfo> {
        val backupDir = getBackupDirectory()
        return backupDir.listFiles { file ->
            file.extension == "zip" && file.name.startsWith("fleetcontrol_backup_")
        }?.map { file ->
            BackupInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                createdAt = Date(file.lastModified())
            )
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }
    
    /**
     * Delete a backup file
     */
    fun deleteBackup(filePath: String): Boolean {
        val file = File(filePath)
        return file.exists() && file.delete()
    }
    
    /**
     * Share a backup (returns file URI)
     */
    fun getBackupUri(filePath: String): Uri? {
        val file = File(filePath)
        return if (file.exists()) {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } else null
    }
    
    private fun getBackupDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zipOut.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
}

/**
 * Backup info for display
 */
data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val createdAt: Date
) {
    val formattedSize: String
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
        }
    
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(createdAt)
}

/**
 * Backup result
 */
sealed class BackupResult {
    data class Success(val filePath: String, val fileName: String, val fileSize: Long) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

/**
 * Restore result
 */
sealed class RestoreResult {
    data class Success(val backupDate: Date) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}
