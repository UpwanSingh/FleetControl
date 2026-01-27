package com.fleetcontrol.domain.calculators

import com.fleetcontrol.data.repositories.FuelRepository

/**
 * Fuel Impact Calculator
 * Implements Section 6 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules (Section 6.2):
 * - Fuel deducted ONLY from Driver earnings
 * - Never reimbursed by Owner
 * - Does NOT affect Owner profit
 */
class FuelImpactCalculator(
    private val fuelRepository: FuelRepository
) {
    
    /**
     * Calculate total fuel cost for a driver in a date range
     * This is deducted from driver earnings per Section 6.2
     */
    suspend fun calculateDriverFuelCost(
        driverId: Long, 
        startDate: Long, 
        endDate: Long
    ): Double {
        return fuelRepository.getTotalFuelCost(driverId, startDate, endDate)
    }
    
    /**
     * Calculate today's fuel cost for a driver
     */
    suspend fun calculateTodayFuelCost(driverId: Long, todayStart: Long): Double {
        return fuelRepository.getTodayFuelCost(driverId, todayStart)
    }
    
    /**
     * Fuel does NOT affect owner profit (Section 6.2)
     * This method is intentionally a no-op for documentation purposes
     */
    fun getOwnerFuelImpact(): Double {
        // Per Section 6.2: Fuel does NOT affect Owner profit
        return 0.0
    }
}
