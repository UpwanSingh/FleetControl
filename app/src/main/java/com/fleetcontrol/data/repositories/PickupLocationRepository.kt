package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.PickupLocationDao
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.data.entities.FirestoreLocation
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for Pickup Location data operations
 * Synchronizes with Firestore (Master Data)
 * Implements Section 12 of BUSINESS_LOGIC_SPEC.md
 */
open class PickupLocationRepository(
    private val pickupLocationDao: PickupLocationDao,
    private val cloudRepo: CloudMasterDataRepository
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        val locationsFlow: Flow<List<FirestoreLocation>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreLocation>())
            else cloudRepo.getLocationsFlow()
        }
        
        locationsFlow.onEach { cloudLocs ->
            processCloudLocations(cloudLocs)
        }
        .catch { e: Throwable ->
            Log.e("PickupLocationRepo", "Error syncing pickup location", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudLocations(cloudLocs: List<FirestoreLocation>) {
        cloudLocs.forEach { fLoc ->
            // DEDUP STEP 1: Check by Firestore ID
            val localByFirestoreId = pickupLocationDao.getLocationByFirestoreId(fLoc.id)
            
            if (localByFirestoreId != null) {
                // Already synced - update if needed
                val updated = localByFirestoreId.copy(
                    name = fLoc.name,
                    distanceFromBase = fLoc.distanceFromBase,
                    isActive = fLoc.isActive
                )
                pickupLocationDao.update(updated)
            } else {
                // DEDUP STEP 2: Check by logical key (name)
                val localByName = pickupLocationDao.getLocationByName(fLoc.name)
                
                if (localByName != null) {
                    // Orphan entry found - link it to cloud ID
                    pickupLocationDao.update(localByName.copy(
                        firestoreId = fLoc.id,
                        distanceFromBase = fLoc.distanceFromBase,
                        isActive = fLoc.isActive
                    ))
                } else {
                    // Truly new entry - insert
                    pickupLocationDao.insert(PickupLocationEntity(
                        firestoreId = fLoc.id,
                        name = fLoc.name,
                        distanceFromBase = fLoc.distanceFromBase,
                        isActive = fLoc.isActive
                    ))
                }
            }
        }
    }
    
    /**
     * Get ALL locations (Raw access for Migration/Admin)
     */
    fun getAllLocationsRaw(): Flow<List<PickupLocationEntity>> = 
        pickupLocationDao.getAllLocations()

    fun getAllActiveLocations(): Flow<List<PickupLocationEntity>> = 
        pickupLocationDao.getAllActiveLocations()
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getAllLocations(): Flow<List<PickupLocationEntity>> = 
        pickupLocationDao.getAllLocations()
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getLocationsByDistance(): Flow<List<PickupLocationEntity>> = 
        pickupLocationDao.getLocationsByDistance()
    
    suspend fun getLocationById(id: Long): PickupLocationEntity? = 
        pickupLocationDao.getLocationById(id)
    
    suspend fun getAllPickupLocationsOnce(): List<PickupLocationEntity> =
        pickupLocationDao.getAllPickupLocationsOnce()
    
    suspend fun getLocationByFirestoreId(firestoreId: String): PickupLocationEntity? = 
        pickupLocationDao.getLocationByFirestoreId(firestoreId)
    
    /**
     * Insert location locally only (no cloud push).
     * Used for driver sync when driver joins.
     */
    suspend fun insertRaw(location: PickupLocationEntity): Long {
        return pickupLocationDao.insert(location)
    }
    
    suspend fun insert(location: PickupLocationEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val locationWithOwner = location.copy(ownerId = cloudRepo.currentOwnerId)
        val id = pickupLocationDao.insert(locationWithOwner)
        
        // Push to Cloud with retry
        scope.launch {
            val fLoc = FirestoreLocation(
                id = locationWithOwner.firestoreId ?: "",
                name = locationWithOwner.name,
                distanceFromBase = locationWithOwner.distanceFromBase,
                isActive = locationWithOwner.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "addLocation",
                operation = { cloudRepo.addLocation(fLoc) }
            )
            
            if (cloudId != null && locationWithOwner.firestoreId == null) {
                val inserted = pickupLocationDao.getLocationById(id)
                if (inserted != null) {
                    pickupLocationDao.update(inserted.copy(firestoreId = cloudId))
                }
            }
        }
        return id
    }
    
    suspend fun update(location: PickupLocationEntity) {
        pickupLocationDao.update(location)
        
        // Push to Cloud with retry
        scope.launch {
            val fLoc = FirestoreLocation(
                id = location.firestoreId ?: "",
                name = location.name,
                distanceFromBase = location.distanceFromBase,
                isActive = location.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "updateLocation",
                operation = { cloudRepo.addLocation(fLoc) }
            )
            
            // If it was missing ID, update local DB now
            if (cloudId != null && location.firestoreId == null) {
                val current = pickupLocationDao.getLocationById(location.id)
                if (current != null) {
                    pickupLocationDao.update(current.copy(firestoreId = cloudId))
                }
            }
        }
    }
    
    suspend fun delete(location: PickupLocationEntity) = pickupLocationDao.delete(location)
    
    suspend fun deactivate(id: Long) {
        pickupLocationDao.deactivate(id)
        
        scope.launch {
            val loc = pickupLocationDao.getLocationById(id)
            if (loc?.firestoreId != null) {
                update(loc.copy(isActive = false))
            }
        }
    }
    
    suspend fun insertAll(locations: List<PickupLocationEntity>): List<Long> = 
        pickupLocationDao.insertAll(locations)
    
    /**
     * Insert or update pickup location (for import)
     */
    suspend fun upsert(location: PickupLocationEntity) {
        val existing = pickupLocationDao.getLocationById(location.id)
        if (existing != null) {
            update(location)
        } else {
            insert(location)
        }
    }
    
    suspend fun getPickupLocationName(id: Long): String? {
        return pickupLocationDao.getLocationById(id)?.name
    }
}
