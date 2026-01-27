package com.fleetcontrol.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fleetcontrol.FleetControlApplication
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.data.repositories.FuelRepository
import com.fleetcontrol.utils.Logger

/**
 * WorkManager worker for reliable background sync of pending uploads.
 * 
 * This worker ensures that pending trips and fuel entries are uploaded to Firestore
 * even if the app is killed or network becomes available while app is in background.
 * 
 * Features:
 * - Network constraint required
 * - Exponential backoff retry
 * - Syncs both trips and fuel entries
 * - Logs sync results for debugging
 */
class SyncPendingUploadsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    private val app = context.applicationContext as FleetControlApplication
    private val tripRepository: TripRepository = app.container.tripRepository
    private val fuelRepository: FuelRepository = app.container.fuelRepository

    override suspend fun doWork(): Result {
        return try {
            Logger.d("Starting background sync")
            
            var syncedTrips = 0
            var syncedFuel = 0
            
            // Sync pending trips
            try {
                tripRepository.syncPendingTrips()
                syncedTrips = tripRepository.getPendingTripCount()
                Logger.d("Synced $syncedTrips trips")
            } catch (e: Exception) {
                Logger.e("Failed to sync trips", e)
                // Continue with fuel sync even if trips fail
            }
            
            // Sync pending fuel entries
            try {
                fuelRepository.syncPendingFuel()
                syncedFuel = fuelRepository.getPendingFuelCount()
                Logger.d("Synced $syncedFuel fuel entries")
            } catch (e: Exception) {
                Logger.e("Failed to sync fuel entries", e)
            }
            
            Logger.d("Background sync completed: $syncedTrips trips, $syncedFuel fuel entries")
            Result.success()
        } catch (e: Exception) {
            Logger.e("Background sync failed", e)
            Result.retry()
        }
    }
}
