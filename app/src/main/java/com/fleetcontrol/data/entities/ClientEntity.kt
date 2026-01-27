package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Client entity - Represents delivery destinations/customers
 * 
 * Key Concepts:
 * - Owner delivers products TO clients
 * - Each client can receive deliveries from multiple pickup locations
 * - Distance from pickup to client determines driver rates
 */
@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["name"]),
        Index(value = ["isActive"]),
        Index("ownerId")
    ]
)
data class ClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val firestoreId: String? = null,
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    /**
     * Client/Customer name
     */
    val name: String,
    
    /**
     * Client address/location description
     */
    val address: String? = null,
    
    /**
     * Contact person name
     */
    val contactPerson: String? = null,
    
    /**
     * Contact phone number
     */
    val contactPhone: String? = null,
    
    /**
     * Whether this client is active
     */
    val isActive: Boolean = true,
    
    /**
     * Notes or additional info
     */
    val notes: String? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)
