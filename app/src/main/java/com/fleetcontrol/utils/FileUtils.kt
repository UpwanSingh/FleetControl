package com.fleetcontrol.utils

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * File Utilities for backup/export operations
 */
object FileUtils {
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    /**
     * Get the export directory, creating it if needed
     */
    fun getExportDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Get the backup directory, creating it if needed
     */
    fun getBackupDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Generate a unique filename with timestamp
     */
    fun generateFileName(prefix: String, extension: String): String {
        val timestamp = dateFormat.format(Date())
        return "${prefix}_$timestamp.$extension"
    }
    
    /**
     * Copy a file to another location
     */
    fun copyFile(source: File, destination: File): Boolean {
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: IOException) {
            Logger.e("Failed to copy file: ${source.name} -> ${destination.name}", e)
            false
        }
    }
    
    /**
     * Delete a file safely
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Logger.e("Failed to delete file: ${file.name}", e)
            false
        }
    }
    
    /**
     * Get file size in human-readable format
     */
    fun getReadableFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${sizeBytes / (1024 * 1024)} MB"
            else -> "${sizeBytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * List files in directory with extension filter
     */
    fun listFiles(directory: File, extension: String? = null): List<File> {
        return directory.listFiles()?.filter { file ->
            extension == null || file.extension.equals(extension, ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Get available storage space
     */
    fun getAvailableSpace(context: Context): Long {
        val dir = context.getExternalFilesDir(null) ?: return 0
        return dir.freeSpace
    }
    
    /**
     * Check if there's enough space for a file of given size
     */
    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        return getAvailableSpace(context) > requiredBytes
    }
}
