package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Driver Rate Slab entity
 * Implements Section 3.2 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Driver rate is per bag
 * - Rate depends on pickup distance slab
 * - Rates are set ONLY by Owner
 * - Driver CANNOT modify rates
 */
@Entity(tableName = "driver_rate_slabs")
data class DriverRateSlabEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val firestoreId: String? = null,
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    /**
     * Minimum distance (inclusive) in km for this slab
     */
    val minDistance: Double = 0.0,
    
    /**
     * Maximum distance (exclusive) in km for this slab
     * Use Double.MAX_VALUE for the highest slab
     */
    val maxDistance: Double = 0.0,
    
    /**
     * Rate per bag for deliveries from pickups in this distance range
     * Used in: DriverEarning = bagCount × ratePerBag (Section 5.1)
     */
    val ratePerBag: Double = 0.0,
    
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
