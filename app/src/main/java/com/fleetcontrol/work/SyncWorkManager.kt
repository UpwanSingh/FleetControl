package com.fleetcontrol.work

import android.content.Context
import androidx.work.*
import com.fleetcontrol.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduler for reliable background sync.
 * 
 * This class provides methods to schedule and manage background sync workers
 * that ensure pending trips and fuel entries are uploaded to Firestore.
 */
class SyncWorkManager(private val context: Context) {
    
    companion object {
        const val SYNC_WORK_NAME = "SyncPendingUploadsWorker"
        const val UNIQUE_WORK_NAME = "SyncPendingUploadsWorker_Unique"
    }
    
    /**
     * Schedule immediate sync (runs as soon as network is available)
     */
    fun scheduleImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = OneTimeWorkRequestBuilder<SyncPendingUploadsWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10_000L,
                TimeUnit.MILLISECONDS
            )
            .addTag(SYNC_WORK_NAME)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        
        Logger.d("Scheduling immediate sync")
    }
    
    /**
     * Schedule periodic sync (every 6 hours when network is available)
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true) // Avoid draining battery
            .build()
            
        val periodicRequest = PeriodicWorkRequestBuilder<SyncPendingUploadsWorker>(
            6, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10_000L,
                TimeUnit.MILLISECONDS
            )
            .addTag(SYNC_WORK_NAME)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "${SYNC_WORK_NAME}_Periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
        
        Logger.d("Scheduled periodic sync (every 6 hours)")
    }
    
    /**
     * Cancel all scheduled sync work
     */
    fun cancelAllSyncWork() {
        WorkManager.getInstance(context).cancelAllWorkByTag(SYNC_WORK_NAME)
        Logger.d("Cancelled all sync work")
    }
    
    /**
     * Check if there are any pending sync workers
     */
    fun hasPendingSyncWork(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag(SYNC_WORK_NAME)
            .get()
            
        return workInfos.any { !it.state.isFinished }
    }
}
