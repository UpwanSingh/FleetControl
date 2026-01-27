package com.fleetcontrol.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.utils.RoomPaginatedDao

/**
 * Paginated DAO for trip data
 * Extends RoomPaginatedDao for automatic pagination support
 */
@Dao
abstract class TripPaginatedDao : RoomPaginatedDao<TripEntity>() {
    
    /**
     * Get trips with pagination and filtering
     */
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId ORDER BY tripDate DESC LIMIT :limit OFFSET :offset")
    abstract override suspend fun getItemsPaged(offset: Int, limit: Int): List<TripEntity>
    
    /**
     * Get total count of trips for owner
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId")
    abstract override suspend fun getTotalCount(): Int
    
    /**
     * Get trips for specific driver with pagination
     */
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND driverId = :driverId ORDER BY tripDate DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getDriverTripsPaged(
        ownerId: String,
        driverId: Long,
        offset: Int,
        limit: Int
    ): List<TripEntity>
    
    /**
     * Get total count of trips for specific driver
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId AND driverId = :driverId")
    abstract suspend fun getDriverTripCount(ownerId: String, driverId: Long): Int
    
    /**
     * Get trips for date range with pagination
     */
    @Query("""
        SELECT * FROM trips 
        WHERE ownerId = :ownerId 
        AND tripDate >= :startDate 
        AND tripDate <= :endDate 
        ORDER BY tripDate DESC 
        LIMIT :limit OFFSET :offset
    """)
    abstract suspend fun getTripsByDateRangePaged(
        ownerId: String,
        startDate: Long,
        endDate: Long,
        offset: Int,
        limit: Int
    ): List<TripEntity>
    
    /**
     * Get total count of trips in date range
     */
    @Query("""
        SELECT COUNT(*) FROM trips 
        WHERE ownerId = :ownerId 
        AND tripDate >= :startDate 
        AND tripDate <= :endDate
    """)
    abstract suspend fun getTripCountByDateRange(
        ownerId: String,
        startDate: Long,
        endDate: Long
    ): Int
    
    /**
     * Get pending trips with pagination
     */
    @Query("SELECT * FROM trips WHERE ownerId = :ownerId AND status = 'PENDING' ORDER BY tripDate DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getPendingTripsPaged(ownerId: String, offset: Int, limit: Int): List<TripEntity>
    
    /**
     * Get total count of pending trips
     */
    @Query("SELECT COUNT(*) FROM trips WHERE ownerId = :ownerId AND status = 'PENDING'")
    abstract suspend fun getPendingTripCount(ownerId: String): Int
    
    /**
     * Search trips with pagination
     */
    @Query("""
        SELECT * FROM trips 
        WHERE ownerId = :ownerId 
        AND (clientName LIKE '%' || :query || '%' OR driverId IN (
            SELECT id FROM drivers WHERE name LIKE '%' || :query || '%'
        ))
        ORDER BY tripDate DESC 
        LIMIT :limit OFFSET :offset
    """)
    abstract suspend fun searchTripsPaged(
        ownerId: String,
        query: String,
        offset: Int,
        limit: Int
    ): List<TripEntity>
    
    /**
     * Get total count of search results
     */
    @Query("""
        SELECT COUNT(*) FROM trips 
        WHERE ownerId = :ownerId 
        AND (clientName LIKE '%' || :query || '%' OR driverId IN (
            SELECT id FROM drivers WHERE name LIKE '%' || :query || '%'
        ))
    """)
    abstract suspend fun getSearchTripCount(ownerId: String, query: String): Int
}
