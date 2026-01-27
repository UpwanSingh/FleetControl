package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.DriverRateSlabEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Driver Rate Slab operations
 * Implements queries for Section 3.2 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Driver rate is per bag
 * - Rate depends on pickup distance slab
 * - Rates are set ONLY by Owner
 * - Driver CANNOT modify rates
 * 
 * Transaction Safety (Section 13):
 * - Rate updates are ACID-compliant
 * - Bulk operations are atomic
 */
@Dao
interface RateSlabDao {
    @Query("SELECT * FROM driver_rate_slabs WHERE isActive = 1 ORDER BY minDistance ASC")
    fun getAllActiveRateSlabs(): Flow<List<DriverRateSlabEntity>>

    @Query("SELECT * FROM driver_rate_slabs ORDER BY minDistance ASC")
    fun getAllRateSlabs(): Flow<List<DriverRateSlabEntity>>

    @Query("SELECT * FROM driver_rate_slabs WHERE id = :id")
    suspend fun getRateSlabById(id: Long): DriverRateSlabEntity?
    
    @Query("SELECT * FROM driver_rate_slabs WHERE firestoreId = :fid")
    suspend fun getRateSlabByFirestoreId(fid: String): DriverRateSlabEntity?
    
    /**
     * Find rate slab by distance range for deduplication (catches orphan entries)
     */
    @Query("SELECT * FROM driver_rate_slabs WHERE minDistance = :minDist AND maxDistance = :maxDist LIMIT 1")
    suspend fun getRateSlabByRange(minDist: Double, maxDist: Double): DriverRateSlabEntity?

    /**
     * Get the rate slab for a specific distance
     * Used to determine driver rate based on pickup location distance
     * 
     * Boundary Logic: [minDistance, maxDistance] - INCLUSIVE upper bound
     * Example: Slab 0-40 includes exactly 40km (₹23), Slab 40-60 means >40 to 60 (₹25)
     * So 40km exactly → first slab, 40.01km → second slab
     */
    @Query("""
        SELECT * FROM driver_rate_slabs 
        WHERE isActive = 1 
        AND minDistance <= :distance 
        AND maxDistance >= :distance 
        ORDER BY minDistance ASC
        LIMIT 1
    """)
    suspend fun getRateSlabForDistance(distance: Double): DriverRateSlabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rateSlab: DriverRateSlabEntity): Long

    @Update
    suspend fun update(rateSlab: DriverRateSlabEntity)

    @Delete
    suspend fun delete(rateSlab: DriverRateSlabEntity)

    @Query("UPDATE driver_rate_slabs SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
    
    /**
     * Deactivate all rate slabs
     */
    @Query("UPDATE driver_rate_slabs SET isActive = 0")
    suspend fun deactivateAll()
    
    /**
     * Get all active rate slabs synchronously
     * Used by TransactionManager for rate replacement
     */
    @Query("SELECT * FROM driver_rate_slabs WHERE isActive = 1 ORDER BY minDistance ASC")
    suspend fun getAllActiveRateSlabsSync(): List<DriverRateSlabEntity>
    
    /**
     * Get all rate slabs as one-shot list (not Flow)
     */
    @Query("SELECT * FROM driver_rate_slabs ORDER BY minDistance ASC")
    suspend fun getAllRateSlabsOnce(): List<DriverRateSlabEntity>
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch insert rate slabs - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rateSlabs: List<DriverRateSlabEntity>): List<Long>
    
    /**
     * Delete all rate slabs (for replacement)
     */
    @Query("DELETE FROM driver_rate_slabs")
    suspend fun deleteAll()
    
    /**
     * Replace all rate slabs atomically - TRANSACTION
     * Deletes all existing slabs and inserts new ones
     */
    @Transaction
    suspend fun replaceAllRateSlabs(newSlabs: List<DriverRateSlabEntity>): List<Long> {
        deleteAll()
        return insertAll(newSlabs)
    }
}
