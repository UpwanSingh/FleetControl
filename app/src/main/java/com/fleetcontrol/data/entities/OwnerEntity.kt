package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Owner entity - represents the fleet owner/business
 * 
 * Multi-Tenancy: Each owner has their own isolated dataset.
 * The firebaseUid is the primary key and links to Firebase Auth.
 */
@Entity(tableName = "owners")
data class OwnerEntity(
    @PrimaryKey
    val firebaseUid: String,           // Firebase Auth UID (primary key)
    
    val email: String,
    val businessName: String = "",
    val phone: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
