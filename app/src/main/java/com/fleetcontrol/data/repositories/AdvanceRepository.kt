package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.AdvanceDao
import com.fleetcontrol.data.dao.DriverDao
import com.fleetcontrol.data.entities.AdvanceEntity
import com.fleetcontrol.data.entities.FirestoreAdvance
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for Advance data operations
 * Implements Section 7 - Driver Advance Rules
 */
class AdvanceRepository(
    private val advanceDao: AdvanceDao,
    private val cloudRepo: CloudMasterDataRepository,
    private val driverDao: DriverDao
) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        val advancesFlow: Flow<List<FirestoreAdvance>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreAdvance>())
            else cloudRepo.getAdvancesFlow(null)
        }
        
        advancesFlow.onEach { cloudList ->
            processCloudAdvances(cloudList)
        }
        .catch { e: Throwable ->
            Log.e("AdvanceRepository", "Error syncing advance", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudAdvances(cloudList: List<FirestoreAdvance>) {
        cloudList.forEach { fAdv ->
            val driver = driverDao.getDriverByFirestoreId(fAdv.driverId)
            if (driver != null) {
                // DEDUP STEP 1: Check by Firestore ID
                val localByFirestoreId = advanceDao.getAdvanceByFirestoreId(fAdv.id)
                
                if (localByFirestoreId != null) {
                    // Already synced - update if needed
                    advanceDao.update(localByFirestoreId.copy(
                        amount = fAdv.amount,
                        advanceDate = fAdv.date,
                        note = fAdv.reason,
                        isDeducted = fAdv.isDeducted
                    ))
                } else {
                    // DEDUP STEP 2: Check by logical key (catches orphan entries)
                    val localByKey = advanceDao.getAdvanceByLogicalKey(
                        driverId = driver.id,
                        date = fAdv.date,
                        amount = fAdv.amount
                    )
                    
                    if (localByKey != null) {
                        // Orphan entry found - link it to cloud ID
                        advanceDao.update(localByKey.copy(firestoreId = fAdv.id))
                    } else {
                        // Truly new entry - insert
                        advanceDao.insert(AdvanceEntity(
                            firestoreId = fAdv.id,
                            driverId = driver.id,
                            amount = fAdv.amount,
                            advanceDate = fAdv.date,
                            note = fAdv.reason,
                            isDeducted = fAdv.isDeducted
                        ))
                    }
                }
            }
        }
    }
    
    /**
     * Get ALL advances (Raw access for Migration/Admin)
     */
    fun getAllAdvancesRaw(): Flow<List<AdvanceEntity>> = advanceDao.getAllAdvances()

    fun getAllAdvances(): Flow<List<AdvanceEntity>> = advanceDao.getAllAdvances()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getAdvancesByDriver(driverId: Long): Flow<List<AdvanceEntity>> = 
        advanceDao.getAdvancesByDriver(driverId)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getUndeductedAdvances(driverId: Long): Flow<List<AdvanceEntity>> = 
        advanceDao.getUndeductedAdvancesByDriver(driverId)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    suspend fun getOutstandingBalance(driverId: Long): Double = 
        advanceDao.getOutstandingBalanceByDriver(driverId)
        
    suspend fun addAdvance(driverId: Long, amount: Double, notes: String) {
        val advance = AdvanceEntity(
            driverId = driverId,
            amount = amount,
            note = notes,
            advanceDate = System.currentTimeMillis(),
            ownerId = cloudRepo.currentOwnerId // Multi-Tenancy
        )
        val id = advanceDao.insert(advance)
        syncToCloud(id)
    }
    
    fun getAdvancesByDateRange(startDate: Long, endDate: Long): Flow<List<AdvanceEntity>> =
        advanceDao.getAdvancesByDateRange(startDate, endDate)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    suspend fun getAdvanceById(id: Long): AdvanceEntity? = advanceDao.getAdvanceById(id)
    
    suspend fun createAdvance(advance: AdvanceEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val advanceWithOwner = advance.copy(ownerId = cloudRepo.currentOwnerId)
        val id = advanceDao.insert(advanceWithOwner)
        syncToCloud(id)
        return id
    }
    
    suspend fun update(advance: AdvanceEntity) {
        advanceDao.update(advance)
        syncToCloud(advance.id)
    }
    
    suspend fun markAsDeducted(id: Long) {
        advanceDao.markAsDeducted(id)
        syncToCloud(id)
    }
    
    private fun syncToCloud(id: Long) {
        scope.launch {
            try {
                val entity = advanceDao.getAdvanceById(id) ?: return@launch
                val driver = driverDao.getDriverById(entity.driverId)
                
                if (driver?.firestoreId != null) {
                    val fAdv = FirestoreAdvance(
                        id = entity.firestoreId ?: "",
                        driverId = driver.firestoreId,
                        amount = entity.amount,
                        date = entity.advanceDate,
                        reason = entity.note ?: "",
                        isDeducted = entity.isDeducted
                    )
                    val cloudId = cloudRepo.addAdvance(fAdv)
                    
                    if (entity.firestoreId == null) {
                        advanceDao.update(entity.copy(firestoreId = cloudId))
                    }
                }
            } catch (e: Exception) {
                Log.e("AdvanceRepository", "Error syncing advance", e)
            }
        }
    }
}
