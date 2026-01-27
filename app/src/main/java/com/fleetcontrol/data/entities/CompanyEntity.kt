package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Company entity (Client that pays Owner)
 * Implements Section 2.2 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Pays Owner per delivered bag/item
 * - Has fixed per-bag rate (owner-defined)
 */
@Entity(
    tableName = "companies",
    indices = [
        Index("isActive"),
        Index("name"),
        Index("ownerId")
    ]
)
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val firestoreId: String? = null,
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    val name: String,
    val contactPerson: String? = null,
    val contactPhone: String? = null,
    
    /**
     * Rate per bag that this company pays to the Owner
     * Used in: OwnerGross = bagCount × perBagRate (Section 5.2)
     */
    val perBagRate: Double,
    
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
