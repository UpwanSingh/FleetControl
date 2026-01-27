package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.FuelEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Fuel operations
 * Implements Section 6 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key: Fuel is deducted ONLY from Driver earnings (Section 6.2)
 * 
 * Transaction Safety (Section 13):
 * - Fuel entries are ACID-compliant
 * - Bulk operations are atomic
 */
@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel_entries ORDER BY entryDate DESC")
    fun getAllFuelEntries(): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM fuel_entries WHERE driverId = :driverId ORDER BY entryDate DESC")
    fun getFuelEntriesByDriver(driverId: Long): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM fuel_entries WHERE driverId = :driverId AND entryDate BETWEEN :startDate AND :endDate ORDER BY entryDate DESC")
    fun getFuelEntriesByDriverAndDateRange(driverId: Long, startDate: Long, endDate: Long): Flow<List<FuelEntryEntity>>
    
    @Query("SELECT * FROM fuel_entries WHERE driverId = :driverId AND entryDate BETWEEN :startDate AND :endDate ORDER BY entryDate DESC")
    fun getFuelEntriesByDriverAndDateRangePaged(driverId: Long, startDate: Long, endDate: Long): androidx.paging.PagingSource<Int, FuelEntryEntity>
    
    @Query("SELECT * FROM fuel_entries WHERE driverId = :driverId AND entryDate BETWEEN :startDate AND :endDate ORDER BY entryDate DESC")
    suspend fun getFuelEntriesByDriverAndDateRangeSync(driverId: Long, startDate: Long, endDate: Long): List<FuelEntryEntity>

    /**
     * Get total fuel cost for a driver in a date range
     * Used in: DriverNetPayable = GrossEarnings - FuelCost - Advance (Section 7.3)
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) 
        FROM fuel_entries 
        WHERE driverId = :driverId AND entryDate BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalFuelCostByDriver(driverId: Long, startDate: Long, endDate: Long): Double

    /**
     * Get driver's fuel cost for today
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) 
        FROM fuel_entries 
        WHERE driverId = :driverId AND entryDate >= :todayStart
    """)
    suspend fun getTodayFuelCostByDriver(driverId: Long, todayStart: Long): Double

    @Query("SELECT * FROM fuel_entries WHERE id = :id")
    suspend fun getFuelEntryById(id: Long): FuelEntryEntity?

    @Query("SELECT * FROM fuel_entries WHERE firestoreId = :fid")
    suspend fun getFuelEntryByFirestoreId(fid: String): FuelEntryEntity?
    
    /**
     * Find entry by logical key for deduplication
     * Used to detect orphan entries that haven't synced their firestoreId yet
     */
    @Query("SELECT * FROM fuel_entries WHERE driverId = :driverId AND entryDate = :date AND amount = :amount LIMIT 1")
    suspend fun getFuelEntryByLogicalKey(driverId: Long, date: Long, amount: Double): FuelEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fuelEntry: FuelEntryEntity): Long

    @Update
    suspend fun update(fuelEntry: FuelEntryEntity)

    @Delete
    suspend fun delete(fuelEntry: FuelEntryEntity)
    
    /**
     * Get unsynced fuel entries for WorkManager
     */
    @Query("SELECT * FROM fuel_entries WHERE ownerId = :ownerId AND (firestoreId IS NULL OR firestoreId = '') ORDER BY entryDate ASC")
    suspend fun getUnsyncedFuel(ownerId: String): List<FuelEntryEntity>
    
    /**
     * Count unsynced fuel entries for WorkManager
     */
    @Query("SELECT COUNT(*) FROM fuel_entries WHERE ownerId = :ownerId AND (firestoreId IS NULL OR firestoreId = '')")
    suspend fun getUnsyncedFuelCount(ownerId: String): Int
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch insert fuel entries - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fuelEntries: List<FuelEntryEntity>): List<Long>
}
