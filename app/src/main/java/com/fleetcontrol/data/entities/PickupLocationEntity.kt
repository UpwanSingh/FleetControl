package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pickup Location entity
 * Implements Section 2.5, 3.1, 3.2 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Origin point of goods
 * - Has a distance metric (determines driver rate slab)
 * - Default pickup is NEAREST to client (Section 3.1)
 */
@Entity(
    tableName = "pickup_locations",
    indices = [
        Index("ownerId"),
        Index("name")
    ]
)
data class PickupLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val firestoreId: String = java.util.UUID.randomUUID().toString(),
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    val name: String,
    
    /**
     * Distance from base in kilometers
     * Used for rate slab lookup (Section 3.2)
     * 
     * Example slabs:
     * 0-50 km: ₹5/bag
     * 50-150 km: ₹7/bag
     * 150-500 km: ₹10/bag
     * 500+ km: ₹12/bag
     */
    val distanceFromBase: Double = 0.0,
    
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
