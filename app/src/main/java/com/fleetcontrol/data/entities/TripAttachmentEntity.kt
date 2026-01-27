package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Trip Attachment Entity
 * Stores images/documents captured by drivers for trips
 * 
 * Use Cases:
 * - Delivery proof photos
 * - Loading confirmation photos
 * - Signed receipts/challans
 * - Damage reports
 * - Vehicle issues during trip
 */
@Entity(
    tableName = "trip_attachments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["attachmentType"])
    ]
)
data class TripAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Reference to the trip
     */
    val tripId: Long,
    
    /**
     * Type of attachment
     */
    val attachmentType: AttachmentType,
    
    /**
     * File path in app's internal/external storage
     */
    val filePath: String,
    
    /**
     * Original filename (for display)
     */
    val fileName: String,
    
    /**
     * MIME type (image/jpeg, image/png, application/pdf)
     */
    val mimeType: String,
    
    /**
     * File size in bytes
     */
    val fileSize: Long = 0,
    
    /**
     * Optional caption/note from driver
     */
    val caption: String? = null,
    
    /**
     * Timestamp when captured
     */
    val capturedAt: Long = System.currentTimeMillis(),
    
    /**
     * Location where captured (optional - lat,lng string)
     */
    val captureLocation: String? = null,
    
    /**
     * Whether this has been viewed by owner
     */
    val viewedByOwner: Boolean = false,
    
    /**
     * Sync status for future cloud backup
     */
    val isSynced: Boolean = false
)

/**
 * Types of attachments drivers can add
 */
enum class AttachmentType {
    /** Photo of goods being loaded at pickup */
    LOADING_PHOTO,
    
    /** Photo proving delivery at destination */
    DELIVERY_PROOF,
    
    /** Signed receipt or challan from client */
    RECEIPT_CHALLAN,
    
    /** Photo of damaged goods */
    DAMAGE_REPORT,
    
    /** Vehicle issue during trip */
    VEHICLE_ISSUE,
    
    /** Any other document */
    OTHER
}
