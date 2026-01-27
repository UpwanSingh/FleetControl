package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.ClientDao
import com.fleetcontrol.data.dao.PickupClientDistanceDao
import com.fleetcontrol.data.dao.PickupLocationDao
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import com.fleetcontrol.data.entities.FirestorePickupClientDistance
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for PickupClientDistance operations
 * Synchronizes with Firestore (Master Data) handling FK resolution
 */
class PickupClientDistanceRepository(
    private val pickupClientDistanceDao: PickupClientDistanceDao,
    private val cloudRepo: CloudMasterDataRepository,
    private val pickupDao: PickupLocationDao,
    private val clientDao: ClientDao
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        val distancesFlow: Flow<List<FirestorePickupClientDistance>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestorePickupClientDistance>())
            else cloudRepo.getDistancesFlow()
        }
        
        distancesFlow.onEach { cloudList ->
            processCloudDistances(cloudList)
        }
        .catch { e: Throwable ->
            Log.e("PickupClientDistRepo", "Error syncing distance", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudDistances(cloudList: List<FirestorePickupClientDistance>) {
        cloudList.forEach { fDist ->
            // Resolve FKs
            val pickup = pickupDao.getLocationByFirestoreId(fDist.pickupId)
            val client = clientDao.getClientByFirestoreId(fDist.clientId)
            
            if (pickup != null && client != null) {
                val localDist = pickupClientDistanceDao.getDistanceByFirestoreId(fDist.id)
                
                if (localDist == null) {
                    // Check if exists by unique unique (pickupId, clientId) to avoid duplicates if firestoreId missing locally
                    val existing = pickupClientDistanceDao.getDistance(pickup.id, client.id)
                    if (existing != null) {
                        // Update existing with firestoreId
                        pickupClientDistanceDao.update(existing.copy(firestoreId = fDist.id))
                    } else {
                        // Insert New
                        pickupClientDistanceDao.insert(PickupClientDistanceEntity(
                            firestoreId = fDist.id,
                            pickupLocationId = pickup.id,
                            clientId = client.id,
                            distanceKm = fDist.distanceKm,
                            estimatedTravelMinutes = fDist.estimatedTravelMinutes,
                            isPreferred = fDist.isPreferred,
                            notes = fDist.notes
                        ))
                    }
                } else {
                    // Update
                    val updated = localDist.copy(
                        pickupLocationId = pickup.id,
                        clientId = client.id,
                        distanceKm = fDist.distanceKm,
                        estimatedTravelMinutes = fDist.estimatedTravelMinutes,
                        isPreferred = fDist.isPreferred,
                        notes = fDist.notes
                    )
                    pickupClientDistanceDao.update(updated)
                }
            }
        }
    }

    /**
     * Get all distance mappings
     */
    /**
     * Get ALL distance mappings (Raw access for Migration/Admin)
     */
    fun getAllDistancesRaw(): Flow<List<PickupClientDistanceEntity>> = 
        pickupClientDistanceDao.getAllDistances()

    /**
     * Get all distance mappings (Filtered)
     */
    fun getAllDistances(): Flow<List<PickupClientDistanceEntity>> = 
        pickupClientDistanceDao.getAllDistances()
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    /**
     * Get distances for a specific pickup location
     */
    fun getDistancesByPickup(pickupId: Long): Flow<List<PickupClientDistanceEntity>> = 
        pickupClientDistanceDao.getDistancesByPickup(pickupId)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    /**
     * Get all pickup options for a client (sorted by distance)
     */
    fun getDistancesByClient(clientId: Long): Flow<List<PickupClientDistanceEntity>> = 
        pickupClientDistanceDao.getDistancesByClient(clientId)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    /**
     * Get distance between a specific pickup and client
     */
    suspend fun getDistance(pickupId: Long, clientId: Long): PickupClientDistanceEntity? = 
        pickupClientDistanceDao.getDistance(pickupId, clientId)
    
    /**
     * Get just the distance value in km
     */
    suspend fun getDistanceKm(pickupId: Long, clientId: Long): Double? = 
        pickupClientDistanceDao.getDistanceKm(pickupId, clientId)
    
    /**
     * Find the nearest pickup location for a client
     */
    suspend fun getNearestPickupForClient(clientId: Long): PickupClientDistanceEntity? = 
        pickupClientDistanceDao.getNearestPickupForClient(clientId)
    
    /**
     * Find preferred pickup for a client (if set)
     */
    suspend fun getPreferredPickupForClient(clientId: Long): PickupClientDistanceEntity? = 
        pickupClientDistanceDao.getPreferredPickupForClient(clientId)
    
    /**
     * Get best pickup for a client
     * Returns preferred if set, otherwise nearest
     */
    suspend fun getBestPickupForClient(clientId: Long): PickupClientDistanceEntity? {
        return pickupClientDistanceDao.getPreferredPickupForClient(clientId)
            ?: pickupClientDistanceDao.getNearestPickupForClient(clientId)
    }
    
    /**
     * Get active clients that can be served from a pickup
     */
    fun getActiveClientsByPickup(pickupId: Long): Flow<List<PickupClientDistanceEntity>> = 
        pickupClientDistanceDao.getActiveClientsByPickup(pickupId)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    /**
     * Insert or update a distance mapping
     */
    suspend fun insert(distance: PickupClientDistanceEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val distanceWithOwner = distance.copy(ownerId = cloudRepo.currentOwnerId)
        val id = pickupClientDistanceDao.insert(distanceWithOwner)
        // Re-construct entity with ID for sync
        val saved = distanceWithOwner.copy(id = id)
        scope.launch { syncEntityToCloud(saved) }
        return id
    }
    
    /**
     * Update a distance mapping
     */
    suspend fun update(distance: PickupClientDistanceEntity) {
        pickupClientDistanceDao.update(distance)
        scope.launch { syncEntityToCloud(distance) }
    }
    
    // Improved Sync Method taking the Entity directly
    private suspend fun syncEntityToCloud(entity: PickupClientDistanceEntity) {
        try {
            val pickup = pickupDao.getLocationById(entity.pickupLocationId)
            val client = clientDao.getClientById(entity.clientId)
            
            if (pickup?.firestoreId != null && client?.firestoreId != null) {
                val fDist = FirestorePickupClientDistance(
                    id = entity.firestoreId ?: "",
                    pickupId = pickup.firestoreId,
                    clientId = client.firestoreId,
                    distanceKm = entity.distanceKm,
                    estimatedTravelMinutes = entity.estimatedTravelMinutes ?: 0,
                    isPreferred = entity.isPreferred,
                    notes = entity.notes ?: ""
                )
                val cloudId = cloudRepo.addDistance(fDist)
                
                if (entity.firestoreId == null) {
                     // Update local with cloud ID
                     // Need to execute update
                     pickupClientDistanceDao.update(entity.copy(firestoreId = cloudId))
                }
            }
        } catch (e: Exception) {
            Log.e("PickupClientDistanceRepo", "Error syncing from cloud", e)
        }
    }

    /**
     * Insert (Overridden to use sync)
     */
    suspend fun insertWithSync(distance: PickupClientDistanceEntity): Long {
        val id = pickupClientDistanceDao.insert(distance)
        // Re-construct entity with ID for sync
        val saved = distance.copy(id = id)
        scope.launch { syncEntityToCloud(saved) }
        return id
    }

    /**
     * Delete a distance mapping
     */
    suspend fun delete(distance: PickupClientDistanceEntity) {
        pickupClientDistanceDao.delete(distance)
        // Cloud Delete? Or just let it be?
        // Master Data usually doesn't delete, but connections might change.
        // For now, no delete sync implemented to be safe options.
    }
    
    /**
     * Delete all distances for a pickup location
     */
    suspend fun deleteByPickup(pickupId: Long) = 
        pickupClientDistanceDao.deleteByPickup(pickupId)
    
    /**
     * Delete all distances for a client
     */
    suspend fun deleteByClient(clientId: Long) = 
        pickupClientDistanceDao.deleteByClient(clientId)
    
    /**
     * Set a pickup as the preferred route for a client
     */
    suspend fun setPreferredPickup(pickupId: Long, clientId: Long) {
        pickupClientDistanceDao.setPreferredPickup(pickupId, clientId)
        // Sync the updated record
        val dist = pickupClientDistanceDao.getDistance(pickupId, clientId)
        if (dist != null) {
            scope.launch { syncEntityToCloud(dist) }
        }
    }
    
    /**
     * Batch insert distances
     */
    suspend fun insertAll(distances: List<PickupClientDistanceEntity>): List<Long> = 
        pickupClientDistanceDao.insertAll(distances)
    
    /**
     * Check if a distance mapping exists
     */
    suspend fun exists(pickupId: Long, clientId: Long): Boolean = 
        pickupClientDistanceDao.exists(pickupId, clientId)
    
    /**
     * Get all distances as one-shot list (not Flow)
     */
    suspend fun getAllDistancesOnce(): List<PickupClientDistanceEntity> = 
        pickupClientDistanceDao.getAllDistancesOnce()
    
    /**
     * Insert or update distance (for import)
     */
    suspend fun upsert(distance: PickupClientDistanceEntity) {
        val existing = pickupClientDistanceDao.getDistance(distance.pickupLocationId, distance.clientId)
        if (existing != null) {
            val updated = distance.copy(id = existing.id, firestoreId = existing.firestoreId)
            pickupClientDistanceDao.update(updated)
            scope.launch { syncEntityToCloud(updated) }
        } else {
            val id = pickupClientDistanceDao.insert(distance)
            scope.launch { syncEntityToCloud(distance.copy(id = id)) }
        }
    }
}
