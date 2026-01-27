package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for PickupClientDistance operations
 * 
 * Manages the distance mappings between pickup locations and clients.
 * This is critical for:
 * - Calculating driver rates based on distance
 * - Finding the nearest pickup location for a client
 * - Allowing owner to choose preferred routes
 */
@Dao
interface PickupClientDistanceDao {
    
    /**
     * Get all distance mappings
     */
    @Query("SELECT * FROM pickup_client_distances ORDER BY pickupLocationId, clientId")
    fun getAllDistances(): Flow<List<PickupClientDistanceEntity>>
    
    /**
     * Get all distances for a specific pickup location
     */
    @Query("SELECT * FROM pickup_client_distances WHERE pickupLocationId = :pickupId ORDER BY distanceKm ASC")
    fun getDistancesByPickup(pickupId: Long): Flow<List<PickupClientDistanceEntity>>
    
    /**
     * Get all distances for a specific client
     * Returns all pickup locations that can serve this client, sorted by distance
     */
    @Query("SELECT * FROM pickup_client_distances WHERE clientId = :clientId ORDER BY distanceKm ASC")
    fun getDistancesByClient(clientId: Long): Flow<List<PickupClientDistanceEntity>>
    
    /**
     * Get distance between a specific pickup and client
     */
    @Query("SELECT * FROM pickup_client_distances WHERE pickupLocationId = :pickupId AND clientId = :clientId")
    suspend fun getDistance(pickupId: Long, clientId: Long): PickupClientDistanceEntity?
    
    @Query("SELECT * FROM pickup_client_distances WHERE firestoreId = :fid")
    suspend fun getDistanceByFirestoreId(fid: String): PickupClientDistanceEntity?
    
    /**
     * Get distance value between a specific pickup and client
     */
    @Query("SELECT distanceKm FROM pickup_client_distances WHERE pickupLocationId = :pickupId AND clientId = :clientId")
    suspend fun getDistanceKm(pickupId: Long, clientId: Long): Double?
    
    /**
     * Find the nearest pickup location for a client
     * Returns the pickup with minimum distance
     */
    @Query("""
        SELECT * FROM pickup_client_distances 
        WHERE clientId = :clientId 
        ORDER BY distanceKm ASC 
        LIMIT 1
    """)
    suspend fun getNearestPickupForClient(clientId: Long): PickupClientDistanceEntity?
    
    /**
     * Find preferred pickup for a client (if set)
     * Falls back to nearest if no preferred is set
     */
    @Query("""
        SELECT * FROM pickup_client_distances 
        WHERE clientId = :clientId AND isPreferred = 1 
        LIMIT 1
    """)
    suspend fun getPreferredPickupForClient(clientId: Long): PickupClientDistanceEntity?
    
    /**
     * Get all clients that can be served from a pickup location
     */
    @Query("""
        SELECT pcd.* FROM pickup_client_distances pcd
        INNER JOIN clients c ON c.id = pcd.clientId
        WHERE pcd.pickupLocationId = :pickupId AND c.isActive = 1
        ORDER BY pcd.distanceKm ASC
    """)
    fun getActiveClientsByPickup(pickupId: Long): Flow<List<PickupClientDistanceEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(distance: PickupClientDistanceEntity): Long
    
    @Update
    suspend fun update(distance: PickupClientDistanceEntity)
    
    @Delete
    suspend fun delete(distance: PickupClientDistanceEntity)
    
    /**
     * Delete all distances for a pickup location
     */
    @Query("DELETE FROM pickup_client_distances WHERE pickupLocationId = :pickupId")
    suspend fun deleteByPickup(pickupId: Long)
    
    /**
     * Delete all distances for a client
     */
    @Query("DELETE FROM pickup_client_distances WHERE clientId = :clientId")
    suspend fun deleteByClient(clientId: Long)
    
    /**
     * Set a pickup as preferred for a client
     * First clears any existing preferred, then sets the new one
     */
    @Transaction
    suspend fun setPreferredPickup(pickupId: Long, clientId: Long) {
        clearPreferredForClient(clientId)
        setPreferred(pickupId, clientId, true)
    }
    
    @Query("UPDATE pickup_client_distances SET isPreferred = 0 WHERE clientId = :clientId")
    suspend fun clearPreferredForClient(clientId: Long)
    
    @Query("UPDATE pickup_client_distances SET isPreferred = :isPreferred WHERE pickupLocationId = :pickupId AND clientId = :clientId")
    suspend fun setPreferred(pickupId: Long, clientId: Long, isPreferred: Boolean)
    
    /**
     * Batch insert distances - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(distances: List<PickupClientDistanceEntity>): List<Long>
    
    /**
     * Check if a distance mapping exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM pickup_client_distances WHERE pickupLocationId = :pickupId AND clientId = :clientId)")
    suspend fun exists(pickupId: Long, clientId: Long): Boolean
    
    /**
     * Get all distances once (for export)
     */
    @Query("SELECT * FROM pickup_client_distances")
    suspend fun getAllDistancesOnce(): List<PickupClientDistanceEntity>
}
