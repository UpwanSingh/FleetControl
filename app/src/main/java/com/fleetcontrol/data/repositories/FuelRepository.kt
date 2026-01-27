package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.DriverDao
import com.fleetcontrol.data.dao.FuelDao
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.FirestoreFuel
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for Fuel data operations
 * Implements Section 6 of BUSINESS_LOGIC_SPEC.md with Sync
 */
open class FuelRepository(
    private val fuelDao: com.fleetcontrol.data.dao.FuelDao,
    private val cloudRepo: com.fleetcontrol.data.repositories.CloudMasterDataRepository,
    private val driverDao: com.fleetcontrol.data.dao.DriverDao
) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        val fuelFlow: Flow<List<FirestoreFuel>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreFuel>())
            else cloudRepo.getFuelFlow(null)
        }
        
        fuelFlow.onEach { cloudList ->
            processCloudFuel(cloudList)
        }
        .catch { e: Throwable ->
            Log.e("FuelRepository", "Error syncing fuel", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudFuel(cloudList: List<FirestoreFuel>) {
        cloudList.forEach { fFuel ->
            val driver = driverDao.getDriverByFirestoreId(fFuel.driverId)
            if (driver != null) {
                // DEDUP STEP 1: Check by Firestore ID
                val localByFirestoreId = fuelDao.getFuelEntryByFirestoreId(fFuel.id)
                
                if (localByFirestoreId != null) {
                    // Already synced - update if needed
                    val updated = localByFirestoreId.copy(
                        amount = fFuel.amount,
                        entryDate = fFuel.date,
                        fuelStation = fFuel.notes
                    )
                    fuelDao.update(updated)
                } else {
                    // DEDUP STEP 2: Check by logical key (catches orphan entries)
                    val localByKey = fuelDao.getFuelEntryByLogicalKey(
                        driverId = driver.id,
                        date = fFuel.date,
                        amount = fFuel.amount
                    )
                    
                    if (localByKey != null) {
                        // Orphan entry found - link it to cloud ID
                        fuelDao.update(localByKey.copy(firestoreId = fFuel.id))
                    } else {
                        // Truly new entry - insert
                        fuelDao.insert(FuelEntryEntity(
                            firestoreId = fFuel.id,
                            driverId = driver.id,
                            amount = fFuel.amount,
                            entryDate = fFuel.date,
                            fuelStation = fFuel.notes
                        ))
                    }
                }
            }
        }
    }
    
    /**
     * Get ALL fuel entries (Raw access for Migration/Admin)
     */
    fun getAllFuelEntriesRaw(): Flow<List<FuelEntryEntity>> = fuelDao.getAllFuelEntries()

    fun getAllFuelEntries(): Flow<List<FuelEntryEntity>> = fuelDao.getAllFuelEntries()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getFuelEntriesByDriver(driverId: Long): Flow<List<FuelEntryEntity>> = 
        fuelDao.getFuelEntriesByDriver(driverId)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getFuelEntriesByDriverAndDateRange(
        driverId: Long, 
        startDate: Long, 
        endDate: Long
    ): Flow<List<FuelEntryEntity>> = 
        fuelDao.getFuelEntriesByDriverAndDateRange(driverId, startDate, endDate)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getPagedFuelByDriverAndDateRange(driverId: Long, startDate: Long, endDate: Long): androidx.paging.PagingSource<Int, FuelEntryEntity> {
        return fuelDao.getFuelEntriesByDriverAndDateRangePaged(driverId, startDate, endDate)
    }
    
    fun getEntriesByDriverAndDateRange(driverId: Long, startDate: Long, endDate: Long): Flow<List<FuelEntryEntity>> {
        return fuelDao.getFuelEntriesByDriverAndDateRange(driverId, startDate, endDate)
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    }
    
    suspend fun getTotalFuelCost(driverId: Long, startDate: Long, endDate: Long): Double = 
        fuelDao.getTotalFuelCostByDriver(driverId, startDate, endDate)
    
    suspend fun getTodayFuelCost(driverId: Long, todayStart: Long): Double = 
        fuelDao.getTodayFuelCostByDriver(driverId, todayStart)
    
    suspend fun getFuelEntryById(id: Long): FuelEntryEntity? = fuelDao.getFuelEntryById(id)
    
    suspend fun insert(fuelEntry: FuelEntryEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val entryWithOwner = fuelEntry.copy(ownerId = cloudRepo.currentOwnerId)
        val id = fuelDao.insert(entryWithOwner)
        syncToCloud(id)
        return id
    }
    
    suspend fun update(fuelEntry: FuelEntryEntity) {
        fuelDao.update(fuelEntry)
        syncToCloud(fuelEntry.id)
    }
    
    suspend fun delete(fuelEntry: FuelEntryEntity) = fuelDao.delete(fuelEntry)
    
    private fun syncToCloud(id: Long) {
        scope.launch {
            try {
                val entity = fuelDao.getFuelEntryById(id) ?: return@launch
                val driver = driverDao.getDriverById(entity.driverId)
                
                if (driver?.firestoreId != null) {
                    val fFuel = FirestoreFuel(
                        id = entity.firestoreId ?: "",
                        driverId = driver.firestoreId,
                        amount = entity.amount,
                        date = entity.entryDate,
                        notes = entity.fuelStation ?: ""
                    )
                    val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                        operationName = "addFuel",
                        operation = { cloudRepo.addFuel(fFuel) }
                    )
                    
                    if (cloudId != null && entity.firestoreId == null) {
                        fuelDao.update(entity.copy(firestoreId = cloudId))
                    }
                }
            } catch (e: Exception) {
                Log.e("FuelRepository", "Error syncing fuel", e)
            }
        }
    }
    
    /**
     * Get count of pending fuel entries for WorkManager
     */
    suspend fun getPendingFuelCount(): Int {
        return fuelDao.getUnsyncedFuelCount(cloudRepo.currentOwnerId)
    }
    
    /**
     * Sync pending fuel entries to Firestore
     * Used by WorkManager for background sync
     */
    suspend fun syncPendingFuel() {
        val pendingFuel = fuelDao.getUnsyncedFuel(cloudRepo.currentOwnerId)
        pendingFuel.forEach { entity ->
            try {
                val driver = driverDao.getDriverById(entity.driverId)
                if (driver != null) {
                    val fFuel = FirestoreFuel(
                        id = entity.firestoreId ?: "",
                        driverId = driver.firestoreId ?: "",
                        amount = entity.amount,
                        date = entity.entryDate,
                        notes = entity.fuelStation ?: ""
                    )
                    val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                        operationName = "addFuel",
                        operation = { cloudRepo.addFuel(fFuel) }
                    )
                    
                    if (cloudId != null && entity.firestoreId == null) {
                        fuelDao.update(entity.copy(firestoreId = cloudId))
                    }
                }
            } catch (e: Exception) {
                Log.e("FuelRepository", "Error syncing fuel", e)
            }
        }
    }
}
