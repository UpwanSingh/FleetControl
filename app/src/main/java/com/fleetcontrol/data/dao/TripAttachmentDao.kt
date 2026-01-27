package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.AttachmentType
import com.fleetcontrol.data.entities.TripAttachmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for trip attachments (images/documents)
 */
@Dao
interface TripAttachmentDao {
    
    /**
     * Insert a new attachment
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: TripAttachmentEntity): Long
    
    /**
     * Insert multiple attachments
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<TripAttachmentEntity>)
    
    /**
     * Update an attachment
     */
    @Update
    suspend fun update(attachment: TripAttachmentEntity)
    
    /**
     * Delete an attachment
     */
    @Delete
    suspend fun delete(attachment: TripAttachmentEntity)
    
    /**
     * Delete attachment by ID
     */
    @Query("DELETE FROM trip_attachments WHERE id = :attachmentId")
    suspend fun deleteById(attachmentId: Long)
    
    /**
     * Get all attachments for a trip
     */
    @Query("SELECT * FROM trip_attachments WHERE tripId = :tripId ORDER BY capturedAt DESC")
    fun getAttachmentsByTrip(tripId: Long): Flow<List<TripAttachmentEntity>>
    
    /**
     * Get all attachments for a trip (suspend - one-time fetch)
     */
    @Query("SELECT * FROM trip_attachments WHERE tripId = :tripId ORDER BY capturedAt DESC")
    suspend fun getAttachmentsByTripOnce(tripId: Long): List<TripAttachmentEntity>
    
    /**
     * Get attachments by type for a trip
     */
    @Query("SELECT * FROM trip_attachments WHERE tripId = :tripId AND attachmentType = :type ORDER BY capturedAt DESC")
    fun getAttachmentsByTripAndType(tripId: Long, type: AttachmentType): Flow<List<TripAttachmentEntity>>
    
    /**
     * Get attachment by ID
     */
    @Query("SELECT * FROM trip_attachments WHERE id = :attachmentId")
    suspend fun getById(attachmentId: Long): TripAttachmentEntity?
    
    /**
     * Get count of attachments for a trip
     */
    @Query("SELECT COUNT(*) FROM trip_attachments WHERE tripId = :tripId")
    suspend fun getAttachmentCount(tripId: Long): Int
    
    /**
     * Get count of attachments for a trip (Flow)
     */
    @Query("SELECT COUNT(*) FROM trip_attachments WHERE tripId = :tripId")
    fun getAttachmentCountFlow(tripId: Long): Flow<Int>
    
    /**
     * Mark attachment as viewed by owner
     */
    @Query("UPDATE trip_attachments SET viewedByOwner = 1 WHERE id = :attachmentId")
    suspend fun markAsViewed(attachmentId: Long)
    
    /**
     * Mark all attachments of a trip as viewed
     */
    @Query("UPDATE trip_attachments SET viewedByOwner = 1 WHERE tripId = :tripId")
    suspend fun markAllAsViewedForTrip(tripId: Long)
    
    /**
     * Get unviewed attachments count for owner
     */
    @Query("SELECT COUNT(*) FROM trip_attachments WHERE viewedByOwner = 0")
    fun getUnviewedCount(): Flow<Int>
    
    /**
     * Get all unviewed attachments (for owner notification)
     */
    @Query("""
        SELECT ta.* FROM trip_attachments ta
        INNER JOIN trips t ON ta.tripId = t.id
        WHERE ta.viewedByOwner = 0
        ORDER BY ta.capturedAt DESC
    """)
    fun getUnviewedAttachments(): Flow<List<TripAttachmentEntity>>
    
    /**
     * Get recent attachments across all trips (for owner dashboard)
     */
    @Query("""
        SELECT * FROM trip_attachments 
        ORDER BY capturedAt DESC 
        LIMIT :limit
    """)
    fun getRecentAttachments(limit: Int = 20): Flow<List<TripAttachmentEntity>>
    
    /**
     * Delete all attachments for a trip
     */
    @Query("DELETE FROM trip_attachments WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: Long)
    
    /**
     * Get total storage used by attachments (in bytes)
     */
    @Query("SELECT SUM(fileSize) FROM trip_attachments")
    suspend fun getTotalStorageUsed(): Long?
}
