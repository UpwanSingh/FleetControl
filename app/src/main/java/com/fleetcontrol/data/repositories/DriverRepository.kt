package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.DriverDao
import com.fleetcontrol.data.entities.DriverEntity
import kotlinx.coroutines.flow.*
import android.util.Log

/**
 * Repository for Driver data operations
 */
import com.fleetcontrol.data.entities.FirestoreDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Repository for Driver data operations
 * Synchronizes with Firestore (Master Data)
 */
open class DriverRepository(
    private val driverDao: DriverDao,
    private val cloudRepo: CloudMasterDataRepository
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        val driversFlow: Flow<List<FirestoreDriver>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreDriver>())
            else cloudRepo.getDriversFlow()
        }
        
        driversFlow.onEach { cloudDrivers ->
            processCloudDrivers(cloudDrivers)
        }
        .catch { e: Throwable ->
            Log.e("DriverRepository", "Error fetching driver", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudDrivers(cloudDrivers: List<FirestoreDriver>) {
        cloudDrivers.forEach { fDriver ->
            // DEDUP STEP 1: Check by Firestore ID
            val localByFirestoreId = driverDao.getDriverByFirestoreId(fDriver.id)
            
            if (localByFirestoreId != null) {
                // Already synced - update if needed
                val updated = localByFirestoreId.copy(
                    name = fDriver.name,
                    phone = fDriver.phone,
                    pin = fDriver.pin,
                    isActive = fDriver.isActive
                )
                driverDao.update(updated)
            } else {
                // DEDUP STEP 2: Check by logical key (name)
                val localByName = driverDao.getDriverByName(fDriver.name)
                
                if (localByName != null) {
                    // Orphan entry found - link it to cloud ID
                    driverDao.update(localByName.copy(
                        firestoreId = fDriver.id,
                        phone = fDriver.phone,
                        pin = fDriver.pin,
                        isActive = fDriver.isActive
                    ))
                } else {
                    // Truly new entry - insert
                    driverDao.insert(DriverEntity(
                        firestoreId = fDriver.id,
                        name = fDriver.name,
                        phone = fDriver.phone,
                        pin = fDriver.pin,
                        isActive = fDriver.isActive
                    ))
                }
            }
        }
    }
    
    /**
     * Get ALL drivers (Raw access for Migration/Admin)
     */
    fun getAllDriversRaw(): Flow<List<DriverEntity>> = driverDao.getAllDrivers()

    /**
     * Get drivers for Current Owner (Filtered)
     */
    fun getAllDrivers(): Flow<List<DriverEntity>> = driverDao.getAllDrivers()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getActiveDrivers(): Flow<List<DriverEntity>> = driverDao.getAllActiveDrivers()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
        
    /**
     * Get ALL active drivers regardless of owner (for Login Screen detection)
     * SECURITY WARNING: Only use for Login/Auth selection.
     */
    fun getGlobalActiveDrivers(): Flow<List<DriverEntity>> = driverDao.getAllActiveDrivers()
    
    suspend fun getDriverById(id: Long): DriverEntity? = driverDao.getDriverById(id)
    
    /**
     * Find driver by Firestore document ID (for cross-device join flow)
     */
    suspend fun getDriverByFirestoreId(firestoreId: String): DriverEntity? = 
        driverDao.getDriverByFirestoreId(firestoreId)
    
    suspend fun login(phone: String, pin: String): DriverEntity? = driverDao.login(phone, pin)
    
    suspend fun getActiveDriverCount(): Int = 
        driverDao.getAllActiveDrivers().first().count { it.ownerId == cloudRepo.currentOwnerId }
    
    /**
     * Insert driver locally only (no cloud push).
     * Used for fallback when driver joins but reactive sync hasn't completed.
     * The driver already exists in cloud - this just creates local copy.
     */
    suspend fun insertRaw(driver: DriverEntity): Long {
        return driverDao.insert(driver)
    }
    
    suspend fun insert(driver: DriverEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val driverWithOwner = driver.copy(ownerId = cloudRepo.currentOwnerId)
        val id = driverDao.insert(driverWithOwner)
        
        // Push to Cloud with retry (Fire-and-forget, ID is safe)
        scope.launch {
            val fDriver = FirestoreDriver(
                id = driverWithOwner.firestoreId, // Guaranteed non-null UUID
                name = driverWithOwner.name,
                phone = driverWithOwner.phone,
                pin = driverWithOwner.pin,
                isActive = driverWithOwner.isActive
            )
            
            com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "addDriver",
                operation = { cloudRepo.addDriver(fDriver) }
            )
        }
        return id
    }
    
    suspend fun update(driver: DriverEntity) {
        driverDao.update(driver)
        
        // Push to Cloud with retry
        scope.launch {
            val fDriver = FirestoreDriver(
                id = driver.firestoreId, // Guaranteed non-null
                name = driver.name,
                phone = driver.phone,
                pin = driver.pin,
                isActive = driver.isActive
            )
            
            com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "updateDriver",
                operation = { cloudRepo.addDriver(fDriver) }
            )
        }
    }
    
    suspend fun delete(driver: DriverEntity) = driverDao.delete(driver) // Cloud delete not implemented to prevent accidental data loss
    
    suspend fun deactivate(id: Long) {
        driverDao.deactivate(id)
        
        // Push Status to Cloud
        scope.launch {
            val driver = driverDao.getDriverById(id)
            if (driver?.firestoreId != null) {
                // Update only status
                // Since we use 'set' in addDriver, we should fetch-modify-put or just send all fields
                // Simpler: Just reuse the update logic
                update(driver.copy(isActive = false))
            }
        }
    }
    
    suspend fun reactivate(id: Long) {
        driverDao.reactivate(id)
        scope.launch {
            val driver = driverDao.getDriverById(id)
            if (driver?.firestoreId != null) {
                update(driver.copy(isActive = true))
            }
        }
    }
    
    fun getInactiveDrivers(): Flow<List<DriverEntity>> = driverDao.getAllInactiveDrivers()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    /**
     * One-shot query to get active drivers (non-flow)
     */
    suspend fun getActiveDriversOnce(): List<DriverEntity> {
        return driverDao.getAllActiveDrivers().first()
    }
    
    /**
     * One-shot query to get inactive drivers (non-flow)
     */
    suspend fun getInactiveDriversOnce(): List<DriverEntity> {
        return driverDao.getAllInactiveDrivers().first()
    }
    
    suspend fun getDriverName(id: Long): String? {
        return driverDao.getDriverById(id)?.name
    }
    
    suspend fun updateAdvanceBalance(driverId: Long, balance: Double) = 
        driverDao.updateAdvanceBalance(driverId, balance)
}
