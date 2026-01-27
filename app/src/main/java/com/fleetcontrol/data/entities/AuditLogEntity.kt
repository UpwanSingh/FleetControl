package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit Log entity
 * Implements Section 11 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules (Section 11):
 * - Override is Owner-only
 * - Reason mandatory
 * - Timestamp mandatory
 * - Written in SAME transaction as the override
 * - Stores: Original value, New value, Reason, Timestamp
 * - No override without audit
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Type of entity being audited (e.g., "trip", "advance", "driver")
     */
    val entityType: String,
    
    /**
     * ID of the entity being audited
     */
    val entityId: Long,
    
    /**
     * Action performed (e.g., "override", "delete", "update")
     */
    val action: String,
    
    /**
     * Original value (JSON or string representation)
     */
    val originalValue: String,
    
    /**
     * New value after change (JSON or string representation)
     */
    val newValue: String,
    
    /**
     * MANDATORY: Reason for the override
     * Per Section 11.1: Reason mandatory
     */
    val reason: String,
    
    /**
     * Who performed the action
     */
    val performedBy: Long? = null,
    
    /**
     * When the action was performed
     * Per Section 11.1: Timestamp mandatory
     */
    val createdAt: Long = System.currentTimeMillis()
)

object AuditAction {
    const val OVERRIDE = "override"
    const val UPDATE = "update"
    const val DELETE = "delete"
    const val CREATE = "create"
}
