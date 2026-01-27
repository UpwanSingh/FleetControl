package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fuel Entry entity
 * Implements Section 6 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules (Section 6):
 * - Entered by Driver
 * - Daily entry
 * - Monetary value only
 * - Fuel deducted ONLY from Driver earnings (Section 6.2)
 * - Never reimbursed by Owner
 * - Does NOT affect Owner profit
 */
@Entity(
    tableName = "fuel_entries",
    foreignKeys = [
        ForeignKey(
            entity = DriverEntity::class,
            parentColumns = ["id"],
            childColumns = ["driverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("driverId"), Index("entryDate"), Index("ownerId")]
)
data class FuelEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val driverId: Long,
    
    val firestoreId: String? = null,
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    /**
     * Total monetary amount spent on fuel
     * This is what gets deducted from driver earnings
     */
    val amount: Double,
    
    /**
     * Quantity in liters (optional)
     */
    val liters: Double = 0.0,
    
    /**
     * Price per liter (optional, for record keeping)
     */
    val pricePerLiter: Double = 0.0,
    
    /**
     * Fuel station name (optional)
     */
    val fuelStation: String? = null,
    
    /**
     * Receipt image path (optional)
     */
    val receiptImage: String? = null,
    
    /**
     * Date of fuel entry
     */
    val entryDate: Long = System.currentTimeMillis(),
    
    val createdAt: Long = System.currentTimeMillis()
)
