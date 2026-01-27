package com.fleetcontrol.domain.calculators

import com.fleetcontrol.data.dao.LabourCostDao
import com.fleetcontrol.data.entities.TripEntity

/**
 * Labour Cost Calculator
 * Implements Section 2.6, 5.3 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Labour is used for loading and unloading
 * - Paid per bag
 * - Cost borne by Owner (affects owner profit, not driver)
 * 
 * Formula: LabourCost = bagCount × LabourCostPerBag (Section 5.3)
 */
class LabourCostCalculator(
    private val labourCostDao: LabourCostDao
) {
    
    /**
     * Get the default labour cost per bag
     */
    suspend fun getDefaultCostPerBag(): Double {
        val defaultRule = labourCostDao.getDefaultRule()
        return defaultRule?.costPerBag ?: 0.0
    }
    
    /**
     * Calculate labour cost for a trip
     * Formula: LabourCost = bagCount × labourCostPerBag (Section 5.3)
     */
    fun calculateTripLabourCost(trip: TripEntity): Double {
        return trip.bagCount * trip.snapshotLabourCostPerBag
    }
    
    /**
     * Calculate labour cost for a given bag count using current rate
     */
    suspend fun calculateLabourCost(bagCount: Int): Double {
        val costPerBag = getDefaultCostPerBag()
        return bagCount * costPerBag
    }
    
    /**
     * Calculate labour cost with a specific rate
     */
    fun calculateLabourCost(bagCount: Int, costPerBag: Double): Double {
        return bagCount * costPerBag
    }
}
