package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.TripEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Trip operations
 * Implements queries for Section 4, 5, 8 of BUSINESS_LOGIC_SPEC.md
 * 
 * Aggregation Rules (Section 8):
 * - DriverDailyPayable = sum(DriverEarning(all trips)) - FuelCost - AdvanceDeduction
 * - OwnerDailyProfit = sum(OwnerNet(all trips by all drivers))
 * - OwnerMonthlyProfit = sum(OwnerDailyProfit(all days in month))
 * 
 * Transaction Safety (Section 13):
 * - All writes are ACID-compliant
 * - No partial saves
 * - No silent failures
 */
@Dao
interface TripDao {
    // ========================================
    // BASIC QUERIES
    // ========================================
    
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId ORDER BY tripDate DESC")
    fun getAllTripsByOwner(ownerId: String): androidx.paging.PagingSource<Int, TripEntity>
    
    // Kept for Raw Migration (if needed) but discouraged for UI
    @Query("SELECT * FROM trips ORDER BY tripDate DESC")
    fun getAllTripsRaw(): androidx.paging.PagingSource<Int, TripEntity>
    
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND driverId = :driverId ORDER BY tripDate DESC")
    fun getAllTripsByDriverPaged(ownerId: String, driverId: Long): androidx.paging.PagingSource<Int, TripEntity>
    
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND driverId = :driverId AND tripDate BETWEEN :startDate AND :endDate ORDER BY tripDate DESC")
    fun getTripsByDriverAndDateRangePaged(ownerId: String, driverId: Long, startDate: Long, endDate: Long): androidx.paging.PagingSource<Int, TripEntity>
    
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND driverId = :driverId AND tripDate BETWEEN :startDate AND :endDate AND clientName LIKE '%' || :searchQuery || '%' ORDER BY tripDate DESC")
    fun getTripsByDriverAndDateRangeFilteredPaged(ownerId: String, driverId: Long, startDate: Long, endDate: Long, searchQuery: String): androidx.paging.PagingSource<Int, TripEntity>

    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND driverId = :driverId ORDER BY tripDate DESC")
    fun getTripsByDriver(ownerId: String, driverId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate ORDER BY tripDate DESC")
    fun getTripsByDateRange(ownerId: String, startDate: Long, endDate: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND driverId = :driverId AND tripDate BETWEEN :startDate AND :endDate ORDER BY tripDate DESC")
    fun getTripsByDriverAndDateRange(ownerId: String, driverId: Long, startDate: Long, endDate: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Long): TripEntity?
    
    @Query("SELECT * FROM trips WHERE driverId = :driverId AND tripDate = :timestamp LIMIT 1")
    suspend fun getTripByDriverAndTimestamp(driverId: Long, timestamp: Long): TripEntity?
    
    @Query("SELECT * FROM trips WHERE tripDate BETWEEN :startDate AND :endDate ORDER BY tripDate DESC")
    suspend fun getTripsForDateRangeSync(startDate: Long, endDate: Long): List<TripEntity>

    // ========================================
    // AGGREGATION QUERIES (Section 8)
    // ========================================
    
    /**
     * Count trips for a date range
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate")
    suspend fun getTripCountByDateRange(ownerId: String, startDate: Long, endDate: Long): Int

    /**
     * Count trips today
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId AND tripDate >= :todayStart")
    suspend fun getTodayTripCount(ownerId: String, todayStart: Long): Int

    /**
     * Count trips for a specific driver in date range
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId AND driverId = :driverId AND tripDate BETWEEN :startDate AND :endDate")
    suspend fun getTripCountByDriver(ownerId: String, driverId: Long, startDate: Long, endDate: Long): Int

    /**
     * Total bags delivered in date range
     */
    @Query("SELECT COALESCE(SUM(bagCount), 0) FROM trips WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate")
    suspend fun getTotalBagsByDateRange(ownerId: String, startDate: Long, endDate: Long): Int

    /**
     * Driver's gross earnings for a date range
     * Formula: sum(bagCount × snapshotDriverRate) per Section 8.1
     */
    @Query("""
        SELECT COALESCE(SUM(bagCount * snapshotDriverRate), 0.0) 
        FROM trips 
        WHERE ownerId = :ownerId AND driverId = :driverId AND tripDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getDriverGrossEarnings(ownerId: String, driverId: Long, startDate: Long, endDate: Long): Double

    /**
     * Owner's gross revenue for a date range
     * Formula: sum(bagCount × snapshotCompanyRate) per Section 5.2
     */
    @Query("""
        SELECT COALESCE(SUM(bagCount * snapshotCompanyRate), 0.0) 
        FROM trips 
        WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getOwnerGrossRevenue(ownerId: String, startDate: Long, endDate: Long): Double

    /**
     * Total driver earnings for a date range (all drivers)
     */
    @Query("""
        SELECT COALESCE(SUM(bagCount * snapshotDriverRate), 0.0) 
        FROM trips 
        WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalDriverEarnings(ownerId: String, startDate: Long, endDate: Long): Double

    /**
     * Total labour cost for a date range
     * Formula: sum(bagCount × snapshotLabourCostPerBag) per Section 5.3
     */
    @Query("""
        SELECT COALESCE(SUM(bagCount * snapshotLabourCostPerBag), 0.0) 
        FROM trips 
        WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalLabourCost(ownerId: String, startDate: Long, endDate: Long): Double

    /**
     * Owner's net profit for a date range
     * Formula: OwnerGross - DriverEarnings - LabourCost per Section 5.4, 8.2
     */
    @Query("""
        SELECT COALESCE(
            SUM((bagCount * snapshotCompanyRate) - (bagCount * snapshotDriverRate) - (bagCount * snapshotLabourCostPerBag)), 
            0.0
        ) 
        FROM trips 
        WHERE ownerId = :ownerId AND tripDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getOwnerNetProfit(ownerId: String, startDate: Long, endDate: Long): Double

    // ========================================
    // SEARCH QUERIES (Section 10)
    // Owner must be able to search by driver, client, company, pickup
    // ========================================
    
    // Search by Client Name OR Driver Name (Subquery)
    @Query("""
        SELECT * FROM trips 
        WHERE ownerId = :ownerId AND (
            clientName LIKE '%' || :query || '%' 
            OR driverId IN (SELECT id FROM drivers WHERE ownerId = :ownerId AND name LIKE '%' || :query || '%')
        )
        ORDER BY tripDate DESC
    """)
    fun searchByClient(ownerId: String, query: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND companyId = :companyId ORDER BY tripDate DESC")
    fun getTripsByCompany(ownerId: String, companyId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND pickupLocationId = :pickupId ORDER BY tripDate DESC")
    fun getTripsByPickup(ownerId: String, pickupId: Long): Flow<List<TripEntity>>

    // ========================================
    // MUTATIONS
    // ========================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    /**
     * Per Section 4.3: Trips cannot be deleted, only overridden
     * This is for emergency use only with audit log
     */
    @Delete
    suspend fun delete(trip: TripEntity)

    @Query("UPDATE trips SET isVerified = 1 WHERE id = :tripId")
    suspend fun markAsVerified(tripId: Long)

    @Query("UPDATE trips SET isOverridden = 1 WHERE id = :tripId")
    suspend fun markAsOverridden(tripId: Long)

    /**
     * Get Unsynced Trips (Filtered by Owner to avoid cross-tenant sync)
     */
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND isSynced = 0 ORDER BY tripDate ASC")
    suspend fun getUnsyncedTrips(ownerId: String): List<TripEntity>
    
    /**
     * Count unsynced trips for WorkManager
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId AND isSynced = 0")
    suspend fun getUnsyncedTripCount(ownerId: String): Int
    
    // Kept for DataIntegrity Check (All unsynced on device)
    @Query("SELECT * FROM trips WHERE isSynced = 0 ORDER BY tripDate ASC")
    suspend fun getAllUnsyncedTripsRaw(): List<TripEntity>

    @Query("UPDATE trips SET isSynced = 1, syncAttempts = 0, lastSyncedAt = :timestamp WHERE id = :tripId")
    suspend fun markTripAsSynced(tripId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE trips SET syncAttempts = syncAttempts + 1 WHERE id = :tripId")
    suspend fun incrementSyncAttempts(tripId: Long)

    @Query("UPDATE trips SET syncAttempts = 0 WHERE id = :tripId")
    suspend fun resetSyncAttempts(tripId: Long)
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // Note: Complex multi-DAO transactions (like override with audit) 
    // should use TransactionManager
    // ========================================
    
    /**
     * Batch insert trips - ATOMIC TRANSACTION
     * Either all trips are inserted or none
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>): List<Long>
}
