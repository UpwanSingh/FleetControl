package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.RateSlabDao
import com.fleetcontrol.data.entities.DriverRateSlabEntity
import com.fleetcontrol.data.entities.FirestoreDriverRateSlab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Repository for Driver Rate Slab operations
 * Synchronizes with Firestore (Master Data)
 */
class RateSlabRepository(
    private val rateSlabDao: RateSlabDao,
    private val cloudRepo: CloudMasterDataRepository
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        // Start Listening for Cloud Updates
        val rateSlabsFlow: Flow<List<FirestoreDriverRateSlab>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreDriverRateSlab>())
            else cloudRepo.getRateSlabsFlow()
        }
        
        rateSlabsFlow.onEach { cloudSlabs ->
            processCloudSlabs(cloudSlabs)
        }
        .catch { e: Throwable ->
            Log.e("RateSlabRepository", "Error syncing rate slab", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudSlabs(cloudSlabs: List<FirestoreDriverRateSlab>) {
        cloudSlabs.forEach { fSlab ->
            // DEDUP STEP 1: Check by Firestore ID
            val localByFirestoreId = rateSlabDao.getRateSlabByFirestoreId(fSlab.id)
            
            if (localByFirestoreId != null) {
                // Already synced - update if needed
                val updated = localByFirestoreId.copy(
                    minDistance = fSlab.minDistance,
                    maxDistance = fSlab.maxDistance,
                    ratePerBag = fSlab.ratePerBag,
                    isActive = fSlab.isActive
                )
                rateSlabDao.update(updated)
            } else {
                // DEDUP STEP 2: Check by logical key (distance range)
                val localByRange = rateSlabDao.getRateSlabByRange(fSlab.minDistance, fSlab.maxDistance)
                
                if (localByRange != null) {
                    // Orphan entry found - link it to cloud ID
                    rateSlabDao.update(localByRange.copy(
                        firestoreId = fSlab.id,
                        ratePerBag = fSlab.ratePerBag,
                        isActive = fSlab.isActive
                    ))
                } else {
                    // Truly new entry - insert
                    rateSlabDao.insert(DriverRateSlabEntity(
                        firestoreId = fSlab.id,
                        minDistance = fSlab.minDistance,
                        maxDistance = fSlab.maxDistance,
                        ratePerBag = fSlab.ratePerBag,
                        isActive = fSlab.isActive
                    ))
                }
            }
        }
    }
    
    /**
     * Get ALL rate slabs (Raw access for Migration/Admin)
     */
    fun getAllRateSlabsRaw(): Flow<List<DriverRateSlabEntity>> = rateSlabDao.getAllRateSlabs()
    
    fun getAllActiveRateSlabs(): Flow<List<DriverRateSlabEntity>> = rateSlabDao.getAllActiveRateSlabs()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getAllRateSlabs(): Flow<List<DriverRateSlabEntity>> = rateSlabDao.getAllRateSlabs()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    suspend fun getRateSlabById(id: Long): DriverRateSlabEntity? = rateSlabDao.getRateSlabById(id)
    
    suspend fun getRateSlabForDistance(distance: Double): DriverRateSlabEntity? = 
        rateSlabDao.getRateSlabForDistance(distance)
    
    suspend fun getRateSlabByFirestoreId(firestoreId: String): DriverRateSlabEntity? = 
        rateSlabDao.getRateSlabByFirestoreId(firestoreId)
    
    /**
     * Insert rate slab locally only (no cloud push).
     * Used for driver sync when driver joins.
     */
    suspend fun insertRaw(rateSlab: DriverRateSlabEntity): Long {
        return rateSlabDao.insert(rateSlab)
    }
        
    suspend fun insert(rateSlab: DriverRateSlabEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val slabWithOwner = rateSlab.copy(ownerId = cloudRepo.currentOwnerId)
        val id = rateSlabDao.insert(slabWithOwner)
        
        // Push to Cloud with retry
        scope.launch {
            val fSlab = FirestoreDriverRateSlab(
                id = slabWithOwner.firestoreId ?: "",
                minDistance = slabWithOwner.minDistance,
                maxDistance = slabWithOwner.maxDistance,
                ratePerBag = slabWithOwner.ratePerBag,
                isActive = slabWithOwner.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "addRateSlab",
                operation = { cloudRepo.addRateSlab(fSlab) }
            )
            
            if (cloudId != null && slabWithOwner.firestoreId == null) {
                val inserted = rateSlabDao.getRateSlabById(id)
                if (inserted != null) {
                    rateSlabDao.update(inserted.copy(firestoreId = cloudId))
                }
            }
        }
        return id
    }
    
    suspend fun update(rateSlab: DriverRateSlabEntity) {
        rateSlabDao.update(rateSlab)
        
        // Push to Cloud with retry
        scope.launch {
            val fSlab = FirestoreDriverRateSlab(
                id = rateSlab.firestoreId ?: "",
                minDistance = rateSlab.minDistance,
                maxDistance = rateSlab.maxDistance,
                ratePerBag = rateSlab.ratePerBag,
                isActive = rateSlab.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "updateRateSlab",
                operation = { cloudRepo.addRateSlab(fSlab) }
            )
            
            // If it was missing ID, update local
            if (cloudId != null && rateSlab.firestoreId == null) {
                val current = rateSlabDao.getRateSlabById(rateSlab.id)
                if (current != null) {
                    rateSlabDao.update(current.copy(firestoreId = cloudId))
                }
            }
        }
    }
    
    suspend fun delete(rateSlab: DriverRateSlabEntity) {
        // Soft delete locally first
        rateSlabDao.update(rateSlab.copy(isActive = false))
        
        scope.launch {
            try {
                if (rateSlab.firestoreId != null) {
                    val fSlab = FirestoreDriverRateSlab(
                        id = rateSlab.firestoreId,
                        minDistance = rateSlab.minDistance,
                        maxDistance = rateSlab.maxDistance,
                        ratePerBag = rateSlab.ratePerBag,
                        isActive = false
                    )
                    cloudRepo.addRateSlab(fSlab)
                }
            } catch (e: Exception) {
                Log.e("RateSlabRepository", "Error syncing rate slab", e)
            }
        }
    }
    
    suspend fun deactivate(id: Long) {
        rateSlabDao.deactivate(id)
        
        scope.launch {
            val slab = rateSlabDao.getRateSlabById(id)
            if (slab?.firestoreId != null) {
                update(slab.copy(isActive = false))
            }
        }
    }
    
    suspend fun deactivateAll() {
        rateSlabDao.deactivateAll()
        // Cloud sync for bulk is tricky. We'd need to iterate.
        // For now, assuming UI loop calls deactivate individually or we implement loop here if needed.
        // But deactivateAll is usually for full reset.
    }
    
    suspend fun getAllActiveRateSlabsSync(): List<DriverRateSlabEntity> = 
        rateSlabDao.getAllActiveRateSlabsSync()
    
    suspend fun getAllRateSlabsOnce(): List<DriverRateSlabEntity> = 
        rateSlabDao.getAllRateSlabsOnce()
        
    suspend fun insertAll(rateSlabs: List<DriverRateSlabEntity>): List<Long> = 
        rateSlabDao.insertAll(rateSlabs)
        
    suspend fun deleteAll() = rateSlabDao.deleteAll()
    
    suspend fun replaceAllRateSlabs(newSlabs: List<DriverRateSlabEntity>): List<Long> = 
        rateSlabDao.replaceAllRateSlabs(newSlabs)
        
    suspend fun upsert(rateSlab: DriverRateSlabEntity) {
        val existing = rateSlabDao.getRateSlabById(rateSlab.id)
        if (existing != null) {
            update(rateSlab)
        } else {
            insert(rateSlab)
        }
    }
}
