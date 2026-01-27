package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Advance entity for driver advances
 * Implements Section 7 of BUSINESS_LOGIC_SPEC.md
 */
@Entity(
    tableName = "advances",
    foreignKeys = [
        ForeignKey(
            entity = DriverEntity::class,
            parentColumns = ["id"],
            childColumns = ["driverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("driverId"),
        Index("advanceDate"),
        Index("isDeducted"),
        Index("ownerId")
    ]
)
data class AdvanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val driverId: Long,
    val firestoreId: String? = null,
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    val amount: Double,
    val advanceDate: Long = System.currentTimeMillis(),
    val note: String? = null,
    val approvedBy: Long? = null,
    val isDeducted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
