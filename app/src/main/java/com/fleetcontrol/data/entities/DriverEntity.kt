package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Driver entity
 * Implements Section 2.4 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - No fixed salary
 * - Paid per bag delivered
 * - Driver rate determined by pickup location distance (Section 3.2)
 * - Pays fuel from own earnings (Section 6.2)
 * - May receive advances (Section 7)
 */
@Entity(
    tableName = "drivers",
    indices = [
        Index("isActive"),
        Index("name"),
        Index("ownerId")
    ]
)
data class DriverEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val firestoreId: String? = null,
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    val name: String,
    val phone: String = "",
    
    /**
     * PIN for driver login (4 digits)
     */
    val pin: String = "",
    
    /**
     * Current outstanding advance balance (computed, but cached for quick access)
     * Actual calculation should still be done from AdvanceEntity records
     * 
     * Per Section 7.3: Balance can be zero, never negative
     */
    val currentAdvanceBalance: Double = 0.0,
    
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
