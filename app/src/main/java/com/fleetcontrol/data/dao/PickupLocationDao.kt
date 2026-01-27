package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.PickupLocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Pickup Location operations
 * 
 * Transaction Safety (Section 13):
 * - Location operations are ACID-compliant
 * - Bulk operations are atomic
 */
@Dao
interface PickupLocationDao {
    @Query("SELECT * FROM pickup_locations WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveLocations(): Flow<List<PickupLocationEntity>>

    @Query("SELECT * FROM pickup_locations ORDER BY name ASC")
    fun getAllLocations(): Flow<List<PickupLocationEntity>>

    @Query("SELECT * FROM pickup_locations WHERE id = :id")
    suspend fun getLocationById(id: Long): PickupLocationEntity?

    @Query("SELECT * FROM pickup_locations WHERE firestoreId = :fid LIMIT 1")
    suspend fun getLocationByFirestoreId(fid: String): PickupLocationEntity?
    
    /**
     * Find location by name for deduplication (catches orphan entries)
     */
    @Query("SELECT * FROM pickup_locations WHERE name = :name LIMIT 1")
    suspend fun getLocationByName(name: String): PickupLocationEntity?

    /**
     * Get pickup locations ordered by distance (for finding nearest)
     */
    @Query("SELECT * FROM pickup_locations WHERE isActive = 1 ORDER BY distanceFromBase ASC")
    fun getLocationsByDistance(): Flow<List<PickupLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: PickupLocationEntity): Long

    @Update
    suspend fun update(location: PickupLocationEntity)

    @Delete
    suspend fun delete(location: PickupLocationEntity)
    
    @Query("UPDATE pickup_locations SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
    
    // ========================================
    // TRANSACTION OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch insert locations - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(locations: List<PickupLocationEntity>): List<Long>
    
    /**
     * Get all pickup locations as one-shot list (not Flow)
     */
    @Query("SELECT * FROM pickup_locations ORDER BY name ASC")
    suspend fun getAllPickupLocationsOnce(): List<PickupLocationEntity>
}
