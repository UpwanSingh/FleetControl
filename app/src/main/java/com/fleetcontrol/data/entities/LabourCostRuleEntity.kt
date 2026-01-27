package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Labour Cost Rule entity
 * Implements Section 2.6, 5.3 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Labour is used for loading and unloading
 * - Paid per bag
 * - Cost borne by Owner
 * 
 * Formula: LabourCost = bagCount × labourCostPerBag (Section 5.3)
 */
@Entity(tableName = "labour_cost_rules")
data class LabourCostRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Name/description of this labour cost rule
     */
    val name: String,
    
    /**
     * Cost per bag for labour
     * Used in: LabourCost = bagCount × costPerBag (Section 5.3)
     */
    val costPerBag: Double = 0.0,
    
    /**
     * Is this the default rule to use?
     */
    val isDefault: Boolean = false,
    
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
