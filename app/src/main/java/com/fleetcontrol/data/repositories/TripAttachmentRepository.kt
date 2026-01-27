package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.TripAttachmentDao
import com.fleetcontrol.data.entities.AttachmentType
import com.fleetcontrol.data.entities.TripAttachmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for trip attachments (images/documents)
 */
class TripAttachmentRepository(private val tripAttachmentDao: TripAttachmentDao) {
    
    /**
     * Add a new attachment
     */
    suspend fun addAttachment(attachment: TripAttachmentEntity): Long {
        return tripAttachmentDao.insert(attachment)
    }
    
    /**
     * Add multiple attachments
     */
    suspend fun addAttachments(attachments: List<TripAttachmentEntity>) {
        tripAttachmentDao.insertAll(attachments)
    }
    
    /**
     * Update attachment (e.g., add caption)
     */
    suspend fun updateAttachment(attachment: TripAttachmentEntity) {
        tripAttachmentDao.update(attachment)
    }
    
    /**
     * Delete an attachment
     */
    suspend fun deleteAttachment(attachment: TripAttachmentEntity) {
        tripAttachmentDao.delete(attachment)
    }
    
    /**
     * Delete by ID
     */
    suspend fun deleteAttachmentById(attachmentId: Long) {
        tripAttachmentDao.deleteById(attachmentId)
    }
    
    /**
     * Get all attachments for a trip (Flow)
     */
    fun getAttachmentsForTrip(tripId: Long): Flow<List<TripAttachmentEntity>> {
        return tripAttachmentDao.getAttachmentsByTrip(tripId)
    }
    
    /**
     * Get attachments for a trip (one-time)
     */
    suspend fun getAttachmentsForTripOnce(tripId: Long): List<TripAttachmentEntity> {
        return tripAttachmentDao.getAttachmentsByTripOnce(tripId)
    }
    
    /**
     * Get attachments by type
     */
    fun getAttachmentsByType(tripId: Long, type: AttachmentType): Flow<List<TripAttachmentEntity>> {
        return tripAttachmentDao.getAttachmentsByTripAndType(tripId, type)
    }
    
    /**
     * Get single attachment by ID
     */
    suspend fun getAttachmentById(attachmentId: Long): TripAttachmentEntity? {
        return tripAttachmentDao.getById(attachmentId)
    }
    
    /**
     * Get attachment count for a trip
     */
    suspend fun getAttachmentCount(tripId: Long): Int {
        return tripAttachmentDao.getAttachmentCount(tripId)
    }
    
    /**
     * Get attachment count as Flow
     */
    fun getAttachmentCountFlow(tripId: Long): Flow<Int> {
        return tripAttachmentDao.getAttachmentCountFlow(tripId)
    }
    
    /**
     * Mark as viewed by owner
     */
    suspend fun markAsViewed(attachmentId: Long) {
        tripAttachmentDao.markAsViewed(attachmentId)
    }
    
    /**
     * Mark all attachments of trip as viewed
     */
    suspend fun markAllAsViewedForTrip(tripId: Long) {
        tripAttachmentDao.markAllAsViewedForTrip(tripId)
    }
    
    /**
     * Get count of unviewed attachments
     */
    fun getUnviewedCount(): Flow<Int> {
        return tripAttachmentDao.getUnviewedCount()
    }
    
    /**
     * Get all unviewed attachments
     */
    fun getUnviewedAttachments(): Flow<List<TripAttachmentEntity>> {
        return tripAttachmentDao.getUnviewedAttachments()
    }
    
    /**
     * Get recent attachments
     */
    fun getRecentAttachments(limit: Int = 20): Flow<List<TripAttachmentEntity>> {
        return tripAttachmentDao.getRecentAttachments(limit)
    }
    
    /**
     * Delete all attachments for a trip
     */
    suspend fun deleteAllForTrip(tripId: Long) {
        tripAttachmentDao.deleteAllForTrip(tripId)
    }
    
    /**
     * Get total storage used
     */
    suspend fun getTotalStorageUsed(): Long {
        return tripAttachmentDao.getTotalStorageUsed() ?: 0L
    }
}
