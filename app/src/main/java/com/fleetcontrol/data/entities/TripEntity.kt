package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Trip entity
 * Implements Section 4, 5 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules (Section 4.1):
 * - Trip MUST include: Driver, Company, Client, Pickup Location, Bag Count, Timestamp
 * - Drivers may perform unlimited trips per day (Section 4.2)
 * - Trips cannot be deleted; Owner may override with audit (Section 4.3)
 * 
 * Calculations (Section 5):
 * - DriverEarning = bagCount × snapshotDriverRate (5.1)
 * - OwnerGross = bagCount × snapshotCompanyRate (5.2)
 * - LabourCost = bagCount × labourCostPerBag (5.3)
 * - OwnerNet = OwnerGross − DriverEarning − LabourCost (5.4)
 * 
 * IMPORTANT: Per Section 13, derived values should be computed on demand.
 * We store rate snapshots to preserve historical accuracy.
 */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = DriverEntity::class,
            parentColumns = ["id"],
            childColumns = ["driverId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PickupLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["pickupLocationId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("driverId"),
        Index("companyId"),
        Index("pickupLocationId"),
        Index("clientId"),
        Index("tripDate"),
        Index("ownerId"),
        Index("uuid")
    ]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * UUID for idempotent cloud sync - used as Firestore document ID
     * Generated locally when trip is created
     */
    val uuid: String = "",
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    // Required relationships
    val driverId: Long,
    val companyId: Long,
    val pickupLocationId: Long,
    
    /**
     * Client ID - Reference to the client being delivered to
     * This replaces the old clientName string field
     */
    val clientId: Long,
    
    /**
     * Legacy client name field - kept for backward compatibility
     * New trips should use clientId instead
     * @deprecated Use clientId instead
     */
    val clientName: String = "",
    
    /**
     * Number of bags delivered
     * Must be > 0 per Section 4.1
     */
    val bagCount: Int,
    
    // ========================================
    // RATE SNAPSHOTS (frozen at trip time)
    // Per Section 13: Preserves historical accuracy
    // ========================================
    
    /**
     * Distance in km from pickup to client at the time of trip
     * Frozen for historical accuracy
     */
    val snapshotDistanceKm: Double = 0.0,
    
    /**
     * Driver rate per bag at the time of trip
     * Determined by pickup location distance slab
     */
    val snapshotDriverRate: Double,
    
    /**
     * Company rate per bag at the time of trip
     * What the company pays per bag
     */
    val snapshotCompanyRate: Double,
    
    /**
     * Labour cost per bag at the time of trip
     */
    val snapshotLabourCostPerBag: Double = 0.0,
    
    // ========================================
    // TRIP DETAILS
    // ========================================
    
    /**
     * Trip date/time
     */
    val tripDate: Long = System.currentTimeMillis(),
    
    /**
     * Trip status
     */
    val status: String = TripStatus.COMPLETED,
    
    /**
     * Has owner verified this trip?
     */
    val isVerified: Boolean = false,
    
    /**
     * Has this trip been overridden?
     * If true, check AuditLog for details
     */
    val isOverridden: Boolean = false,
    
    /**
     * isSynced: Track if this trip has been uploaded to Firestore
     * Used for Historic Data Migration & Offline Sync
     */
    val isSynced: Boolean = false,
    
    /**
     * Sync Status Tracking (Audit Fix #1)
     * - lastSyncedAt: When this trip was last successfully synced
     * - syncAttempts: How many times sync was attempted (for retry UI)
     */
    val lastSyncedAt: Long? = null,
    
    @androidx.room.ColumnInfo(defaultValue = "0")
    val syncAttempts: Int = 0,
    
    /**
     * Version for Conflict Detection (Audit Fix #3)
     * Incremented on every update, used to detect concurrent edits
     */
    @androidx.room.ColumnInfo(defaultValue = "1")
    val version: Long = 1L,
    
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Calculate driver earning for this trip
     * Formula: DriverEarning = bagCount × snapshotDriverRate (Section 5.1)
     */
    fun calculateDriverEarning(): Double = bagCount * snapshotDriverRate
    
    /**
     * Calculate owner gross revenue for this trip
     * Formula: OwnerGross = bagCount × snapshotCompanyRate (Section 5.2)
     */
    fun calculateOwnerGross(): Double = bagCount * snapshotCompanyRate
    
    /**
     * Calculate labour cost for this trip
     * Formula: LabourCost = bagCount × labourCostPerBag (Section 5.3)
     */
    fun calculateLabourCost(): Double = bagCount * snapshotLabourCostPerBag
    
    /**
     * Calculate owner net profit for this trip
     * Formula: OwnerNet = OwnerGross − DriverEarning − LabourCost (Section 5.4)
     */
    fun calculateOwnerNetProfit(): Double = 
        calculateOwnerGross() - calculateDriverEarning() - calculateLabourCost()
}

object TripStatus {
    const val PENDING = "pending"
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
}
