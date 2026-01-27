package com.fleetcontrol.data.repositories

import android.util.Log

import com.fleetcontrol.data.dao.TripDao
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.entities.TripStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for Trip data operations
 * Implements Section 4, 5, 8 of BUSINESS_LOGIC_SPEC.md
 * Synced with Cloud Master Data via CloudTripRepository
 */
class TripRepository(
    private val tripDao: TripDao,
    private val cloudRepo: com.fleetcontrol.data.repositories.CloudTripRepository, // Manually injected
    private val driverRepo: com.fleetcontrol.data.repositories.DriverRepository, // Added for Up-Sync Name Resolution
    private val companyRepo: com.fleetcontrol.data.repositories.CompanyRepository, // Added for ID Resolution
    private val pickupRepo: com.fleetcontrol.data.repositories.PickupLocationRepository // Added for ID Resolution
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // ... (Down-Sync Logic remains mostly same, just ensure context is right) ...
        // Start Listening for Cloud Updates (Down-Sync)
        // Start Listening for Cloud Updates (Down-Sync)
        // Start Listening for Cloud Updates (Down-Sync)
        val tripsFlow: Flow<List<com.fleetcontrol.data.entities.FirestoreTrip>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<com.fleetcontrol.data.entities.FirestoreTrip>())
            else cloudRepo.getAllTripsFlow()
        }

        tripsFlow.onEach { cloudTrips ->
            processCloudTrips(cloudTrips)
        }
        .catch { e: Throwable ->
            Log.e("TripRepository", "Error syncing trip", e)
        }
        .launchIn(scope)
        
        // Start Background Sync (Up-Sync) for Pending Trips
        scope.launch {
            try {
                // Initial Sync Attempt
                syncPendingTrips()
            } catch (e: Exception) {
                Log.e("TripRepository", "Error syncing trip", e)
            }
        }
    }
    
    private suspend fun processCloudTrips(cloudTrips: List<com.fleetcontrol.data.entities.FirestoreTrip>) {
        for (fTrip in cloudTrips) {
            // CRITICAL: Use firestoreId lookups instead of toLongOrNull (which fails on driver devices)
            val driver = driverRepo.getDriverByFirestoreId(fTrip.driverId)
            val driverId = driver?.id ?: 0L
            
            // Skip if we can't find the driver locally (not yet synced)
            if (driverId == 0L && fTrip.driverId.isNotEmpty()) {
                // Log.v to avoid alarm - typical during initial sync race conditions
                Log.v("TripRepository", "Deferring trip processing - driver ${fTrip.driverId} not yet synced locally")
                continue
            }
            
            // Back-calculate rates if missing in model but totals exist (Critical for Reports)
            val computedCompanyRate = if (fTrip.bags > 0) fTrip.ownerGross / fTrip.bags else 0.0
            val computedLabourRate = if (fTrip.bags > 0) fTrip.labourCost / fTrip.bags else 0.0

            val existing = tripDao.getTripByDriverAndTimestamp(driverId, fTrip.timestamp)
            
            if (existing == null) {
                val newTrip = TripEntity(
                    driverId = driverId,
                    companyId = 0L, // Company lookup by firestoreId would need DAO method
                    pickupLocationId = 0L, // Pickup lookup by firestoreId would need DAO method
                    clientId = fTrip.clientId,
                    clientName = fTrip.clientName,
                    bagCount = fTrip.bags,
                    snapshotDistanceKm = fTrip.distance,
                    snapshotDriverRate = fTrip.rate,
                    snapshotCompanyRate = computedCompanyRate, // Computed for data integrity
                    snapshotLabourCostPerBag = computedLabourRate, // Computed for data integrity
                    tripDate = fTrip.timestamp,
                    status = fTrip.status.lowercase().ifEmpty { "completed" },
                    isSynced = true
                )
                tripDao.insert(newTrip)
            } else {
                // Update Logic: Check for ANY discrepancy (Admin corrections)
                val cloudStatus = fTrip.status.lowercase().ifEmpty { "completed" }
                
                val needsUpdate = existing.status != cloudStatus ||
                                  existing.bagCount != fTrip.bags ||
                                  existing.snapshotDriverRate != fTrip.rate ||
                                  existing.snapshotCompanyRate != computedCompanyRate ||
                                  (existing.snapshotLabourCostPerBag != computedLabourRate && fTrip.bags > 0)

                if (needsUpdate) {
                     val updatedTrip = existing.copy(
                         status = cloudStatus,
                         bagCount = fTrip.bags,
                         snapshotDriverRate = fTrip.rate,
                         snapshotCompanyRate = computedCompanyRate,
                         snapshotLabourCostPerBag = computedLabourRate,
                         isSynced = true
                     )
                     tripDao.update(updatedTrip)
                } else if (!existing.isSynced) {
                     tripDao.markTripAsSynced(existing.id)
                }
            }
        }
    }
        

    
    suspend fun getPendingTripCount(): Int {
        return tripDao.getUnsyncedTripCount(cloudRepo.currentOwnerId)
    }
    
    suspend fun syncPendingTrips() {
        // Only sync trips belonging to current owner
        val pendingTrips = tripDao.getUnsyncedTrips(cloudRepo.currentOwnerId)
        pendingTrips.forEach { trip ->
            try {
                // CRITICAL: Lookup firestoreIds for cross-device sync
                val driver = driverRepo.getDriverById(trip.driverId)
                val driverFirestoreId = driver?.firestoreId ?: trip.driverId.toString()
                val driverName = driver?.name ?: "Unknown Driver"
                
                // Resolution
                val company = companyRepo.getCompanyById(trip.companyId)
                val companyFirestoreId = company?.firestoreId ?: trip.companyId.toString()
                
                val pickup = pickupRepo.getLocationById(trip.pickupLocationId)
                val pickupFirestoreId = pickup?.firestoreId ?: trip.pickupLocationId.toString()

                val fTrip = com.fleetcontrol.data.entities.FirestoreTrip(
                    driverId = driverFirestoreId,
                    driverName = driverName,
                    vehicleNumber = "",
                    companyId = companyFirestoreId,
                    pickupId = pickupFirestoreId,
                    clientId = trip.clientId,
                    clientName = trip.clientName,
                    timestamp = trip.tripDate,
                    bags = trip.bagCount,
                    rate = trip.snapshotDriverRate,
                    totalAmount = trip.snapshotDriverRate * trip.bagCount,
                    ownerGross = trip.snapshotCompanyRate * trip.bagCount,
                    labourCost = trip.snapshotLabourCostPerBag * trip.bagCount,
                    status = "PENDING", // Driver-created trips require owner approval
                    id = trip.uuid // Use UUID for idempotent sync
                )
                
                // Use retry logic
                val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                    operationName = "addTrip",
                    operation = { cloudRepo.addTrip(fTrip) }
                )
                
                if (cloudId != null) {
                    tripDao.markTripAsSynced(trip.id)
                    Log.d("TripRepository", "Trip synced with ID: $cloudId")
                }
            } catch (e: Exception) {
                // Keep unsynced, retry later
                Log.e("TripRepository", "Error syncing trip", e)
                tripDao.incrementSyncAttempts(trip.id)
            }
        }
    }
    
    suspend fun retrySync(tripId: Long) {
        tripDao.resetSyncAttempts(tripId)
        syncPendingTrips()
    }
    
    // ========================================
    // BASIC QUERIES
    // ========================================
    
    fun getPagedTrips(): Flow<androidx.paging.PagingData<TripEntity>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = 20,
                enablePlaceholders = true
            ),
            pagingSourceFactory = { tripDao.getAllTripsByOwner(cloudRepo.currentOwnerId) }
        ).flow
    }
    
    /**
     * Get paged trips for a specific driver (for driver mode)
     * Per Section 9.1 - Driver can only see their own trips
     */
    fun getPagedTripsByDriver(driverId: Long): Flow<androidx.paging.PagingData<TripEntity>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = 20,
                enablePlaceholders = true
            ),
            pagingSourceFactory = { tripDao.getAllTripsByDriverPaged(cloudRepo.currentOwnerId, driverId) }
        ).flow
    }
    
    /**
     * Get paging source for trips by driver and date range
     */
    fun getPagedTripsByDriverAndDateRange(driverId: Long, startDate: Long, endDate: Long): androidx.paging.PagingSource<Int, TripEntity> {
        return tripDao.getTripsByDriverAndDateRangePaged(cloudRepo.currentOwnerId, driverId, startDate, endDate)
    }
    
    /**
     * Get paging source for trips by driver and date range with search filter
     */
    fun getPagedTripsByDriverAndDateRangeFiltered(driverId: Long, startDate: Long, endDate: Long, searchQuery: String): androidx.paging.PagingSource<Int, TripEntity> {
        return tripDao.getTripsByDriverAndDateRangeFilteredPaged(cloudRepo.currentOwnerId, driverId, startDate, endDate, searchQuery)
    }

    // Retaining legacy flow for other consumers if needed, but Paging is preferred for UI
    // fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips() // Removed as DAO signature changed
    
    fun getTripsByDriver(driverId: Long): Flow<List<TripEntity>> = 
        tripDao.getTripsByDriver(cloudRepo.currentOwnerId, driverId)
    
    fun getTripsByDateRange(startDate: Long, endDate: Long): Flow<List<TripEntity>> = 
        tripDao.getTripsByDateRange(cloudRepo.currentOwnerId, startDate, endDate)
    
    fun getTripsByDriverAndDateRange(driverId: Long, startDate: Long, endDate: Long): Flow<List<TripEntity>> =
        tripDao.getTripsByDriverAndDateRange(cloudRepo.currentOwnerId, driverId, startDate, endDate)
    
    suspend fun getTripById(id: Long): TripEntity? = tripDao.getTripById(id)
    
    // ========================================
    // AGGREGATION QUERIES (Section 8)
    // ========================================
    
    suspend fun getTripCount(startDate: Long, endDate: Long): Int = 
        tripDao.getTripCountByDateRange(cloudRepo.currentOwnerId, startDate, endDate)
    
    suspend fun getTodayTripCount(todayStart: Long): Int = 
        tripDao.getTodayTripCount(cloudRepo.currentOwnerId, todayStart)
    
    suspend fun getTripCountByDriver(driverId: Long, startDate: Long, endDate: Long): Int =
        tripDao.getTripCountByDriver(cloudRepo.currentOwnerId, driverId, startDate, endDate)
    
    suspend fun getTotalBags(startDate: Long, endDate: Long): Int = 
        tripDao.getTotalBagsByDateRange(cloudRepo.currentOwnerId, startDate, endDate)
    
    /**
     * Get driver's gross earnings for a period
     * Formula: sum(bagCount × snapshotDriverRate) per Section 8.1
     */
    suspend fun getDriverGrossEarnings(driverId: Long, startDate: Long, endDate: Long): Double = 
        tripDao.getDriverGrossEarnings(cloudRepo.currentOwnerId, driverId, startDate, endDate)
    
    /**
     * Get owner's gross revenue for a period
     */
    suspend fun getOwnerGrossRevenue(startDate: Long, endDate: Long): Double = 
        tripDao.getOwnerGrossRevenue(cloudRepo.currentOwnerId, startDate, endDate)
    
    /**
     * Get total driver earnings across all drivers
     */
    suspend fun getTotalDriverEarnings(startDate: Long, endDate: Long): Double = 
        tripDao.getTotalDriverEarnings(cloudRepo.currentOwnerId, startDate, endDate)
    
    /**
     * Get total labour cost for a period
     */
    suspend fun getTotalLabourCost(startDate: Long, endDate: Long): Double = 
        tripDao.getTotalLabourCost(cloudRepo.currentOwnerId, startDate, endDate)
    
    /**
     * Get owner's net profit for a period
     * Formula: OwnerGross - DriverEarnings - LabourCost per Section 5.4, 8.2
     */
    suspend fun getOwnerNetProfit(startDate: Long, endDate: Long): Double = 
        tripDao.getOwnerNetProfit(cloudRepo.currentOwnerId, startDate, endDate)
    
    // ========================================
    // SEARCH (Section 10)
    // ========================================
    
    fun searchByClient(query: String): Flow<List<TripEntity>> = tripDao.searchByClient(cloudRepo.currentOwnerId, query)
    
    fun getTripsByCompany(companyId: Long): Flow<List<TripEntity>> = 
        tripDao.getTripsByCompany(cloudRepo.currentOwnerId, companyId)
    
    fun getTripsByPickup(pickupId: Long): Flow<List<TripEntity>> = 
        tripDao.getTripsByPickup(cloudRepo.currentOwnerId, pickupId)
    
    // ========================================
    // MUTATIONS
    // ========================================
    
    suspend fun insert(trip: TripEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val tripWithOwner = trip.copy(ownerId = cloudRepo.currentOwnerId)
        val id = tripDao.insert(tripWithOwner)
        
        // IMMEDIATE CLOUD SYNC - Critical for Real-Time Updates
        scope.launch {
            try {
                val savedTrip = tripDao.getTripById(id) ?: return@launch
                
                // CRITICAL: Lookup firestoreIds for cross-device sync
                val driver = driverRepo.getDriverById(savedTrip.driverId)
                val driverFirestoreId = driver?.firestoreId ?: savedTrip.driverId.toString()
                val driverName = driver?.name ?: "Unknown Driver"
                
                val company = companyRepo.getCompanyById(savedTrip.companyId)
                val companyFirestoreId = company?.firestoreId ?: savedTrip.companyId.toString()
                
                val pickup = pickupRepo.getLocationById(savedTrip.pickupLocationId)
                val pickupFirestoreId = pickup?.firestoreId ?: savedTrip.pickupLocationId.toString()
                
                val fTrip = com.fleetcontrol.data.entities.FirestoreTrip(
                    driverId = driverFirestoreId,
                    driverName = driverName,
                    vehicleNumber = "",
                    companyId = companyFirestoreId,
                    pickupId = pickupFirestoreId,
                    clientId = savedTrip.clientId,
                    clientName = savedTrip.clientName,
                    timestamp = savedTrip.tripDate,
                    bags = savedTrip.bagCount,
                    rate = savedTrip.snapshotDriverRate,
                    totalAmount = savedTrip.snapshotDriverRate * savedTrip.bagCount,
                    ownerGross = savedTrip.snapshotCompanyRate * savedTrip.bagCount,
                    labourCost = savedTrip.snapshotLabourCostPerBag * savedTrip.bagCount,
                    status = "PENDING", // Driver-created trips require owner approval
                    id = savedTrip.uuid // Use UUID for idempotent sync
                )
                
                val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                    operationName = "addTrip",
                    operation = { cloudRepo.addTrip(fTrip) }
                )
                
                if (cloudId != null) {
                    tripDao.markTripAsSynced(id)
                }
            } catch (e: Exception) {
                // Failed - will retry via syncPendingTrips on next app start
                Log.e("TripRepository", "Error syncing trip", e)
                tripDao.incrementSyncAttempts(id)
            }
        }
        
        return id
    }
    
    suspend fun update(trip: TripEntity) {
        tripDao.update(trip)
        
        // IMMEDIATE CLOUD SYNC for updates
        scope.launch {
            try {
                // CRITICAL: Lookup firestoreIds for cross-device sync
                val driver = driverRepo.getDriverById(trip.driverId)
                val driverFirestoreId = driver?.firestoreId ?: trip.driverId.toString()
                val driverName = driver?.name ?: "Unknown Driver"
                
                // Fix firestoreId lookups (Audit Fix)
                val company = companyRepo.getCompanyById(trip.companyId)
                val companyFirestoreId = company?.firestoreId ?: trip.companyId.toString()
                
                val pickup = pickupRepo.getLocationById(trip.pickupLocationId)
                val pickupFirestoreId = pickup?.firestoreId ?: trip.pickupLocationId.toString()
                
                val fTrip = com.fleetcontrol.data.entities.FirestoreTrip(
                    driverId = driverFirestoreId,
                    driverName = driverName,
                    vehicleNumber = "",
                    companyId = companyFirestoreId,  // Fixed: Now uses firestoreId
                    pickupId = pickupFirestoreId,    // Fixed: Now uses firestoreId
                    clientId = trip.clientId,
                    clientName = trip.clientName,
                    timestamp = trip.tripDate,
                    bags = trip.bagCount,
                    rate = trip.snapshotDriverRate,
                    totalAmount = trip.calculateDriverEarning(),
                    ownerGross = trip.calculateOwnerGross(),
                    labourCost = trip.calculateLabourCost(),
                    status = trip.status.uppercase()
                )
                cloudRepo.addTrip(fTrip)
            } catch (e: Exception) {
                Log.e("TripRepository", "Error syncing trip", e)
                tripDao.incrementSyncAttempts(trip.id)
            }
        }
    }
    
    suspend fun markAsVerified(tripId: Long) = tripDao.markAsVerified(tripId)
    
    suspend fun markAsOverridden(tripId: Long) = tripDao.markAsOverridden(tripId)
    
    suspend fun markAsSynced(tripId: Long) = tripDao.markTripAsSynced(tripId)
}
