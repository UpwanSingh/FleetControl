package com.fleetcontrol.services.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

enum class BackupFrequency {
    OFF, DAILY, WEEKLY, MONTHLY
}

object BackupScheduler {
    private const val WORK_NAME = "auto_backup_work"
    private const val PREFS_NAME = "backup_prefs"
    private const val KEY_FREQUENCY = "frequency"

    fun scheduleBackup(context: Context, frequency: BackupFrequency) {
        val workManager = WorkManager.getInstance(context)
        
        // Save preference
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FREQUENCY, frequency.name).apply()

        if (frequency == BackupFrequency.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val repeatInterval = when (frequency) {
            BackupFrequency.DAILY -> 1L to TimeUnit.DAYS
            BackupFrequency.WEEKLY -> 7L to TimeUnit.DAYS
            BackupFrequency.MONTHLY -> 30L to TimeUnit.DAYS // Approx
            else -> return
        }

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            repeatInterval.first, repeatInterval.second
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Update ensures schedule changes take effect
            backupRequest
        )
    }

    fun getFrequency(context: Context): BackupFrequency {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val freqName = prefs.getString(KEY_FREQUENCY, BackupFrequency.OFF.name)
        return try {
            BackupFrequency.valueOf(freqName ?: BackupFrequency.OFF.name)
        } catch (e: Exception) {
            BackupFrequency.OFF
        }
    }

    private const val KEY_CLOUD_URI = "cloud_backup_uri"

    fun getCloudBackupUri(context: Context): android.net.Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_CLOUD_URI, null)
        return if (uriString != null) android.net.Uri.parse(uriString) else null
    }

    fun setCloudBackupUri(context: Context, uri: android.net.Uri?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CLOUD_URI, uri?.toString()).apply()
    }
}
