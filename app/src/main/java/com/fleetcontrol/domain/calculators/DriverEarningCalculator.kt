package com.fleetcontrol.domain.calculators

import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.repositories.FuelRepository
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.data.repositories.AdvanceRepository

/**
 * Driver Earning Calculator
 * Implements Section 5.1, 7.3, 8.1 of BUSINESS_LOGIC_SPEC.md
 * 
 * Formulas:
 * - DriverEarning(trip) = bagCount × DriverRate(pickupLocation) [Section 5.1]
 * - DriverDailyPayable = sum(DriverEarning) - FuelCost - AdvanceDeduction [Section 8.1]
 * - DriverNetPayable = GrossEarnings - FuelCost - AdvanceDeduction [Section 7.3]
 */
class DriverEarningCalculator(
    private val tripRepository: TripRepository,
    private val fuelRepository: FuelRepository,
    private val advanceRepository: AdvanceRepository
) {
    
    /**
     * Calculate driver earning for a single trip
     * Formula: DriverEarning = bagCount × snapshotDriverRate (Section 5.1)
     */
    fun calculateTripEarning(trip: TripEntity): Double {
        return trip.bagCount * trip.snapshotDriverRate
    }
    
    /**
     * Calculate driver's gross earnings for a date range
     * Formula: sum(DriverEarning(all trips)) (Section 8.1)
     */
    suspend fun calculateGrossEarnings(driverId: Long, startDate: Long, endDate: Long): Double {
        return tripRepository.getDriverGrossEarnings(driverId, startDate, endDate)
    }
    
    /**
     * Calculate driver's fuel cost for a date range
     * Per Section 6.2: Fuel is deducted ONLY from Driver earnings
     */
    suspend fun calculateFuelCost(driverId: Long, startDate: Long, endDate: Long): Double {
        return fuelRepository.getTotalFuelCost(driverId, startDate, endDate)
    }
    
    /**
     * Get outstanding advance balance for a driver
     * Per Section 7.3: Balance can be zero, never negative
     */
    suspend fun getOutstandingAdvanceBalance(driverId: Long): Double {
        return advanceRepository.getOutstandingBalance(driverId)
    }
    
    /**
     * Calculate driver's net payable
     * Formula: DriverNetPayable = GrossEarnings - FuelCost - AdvanceDeduction (Section 7.3)
     * 
     * Rules:
     * - Net payable can never be negative
     * - Remaining advance balance carries forward
     */
    suspend fun calculateNetPayable(
        driverId: Long, 
        startDate: Long, 
        endDate: Long
    ): DriverPayableSummary {
        val grossEarnings = calculateGrossEarnings(driverId, startDate, endDate)
        val fuelCost = calculateFuelCost(driverId, startDate, endDate)
        val outstandingAdvance = getOutstandingAdvanceBalance(driverId)
        
        // Calculate how much can be deducted (earnings after fuel)
        val availableForDeduction = grossEarnings - fuelCost
        
        // Per Section 7.3: Net payable can never be negative
        val advanceDeduction = if (availableForDeduction > 0) {
            minOf(outstandingAdvance, availableForDeduction)
        } else {
            0.0
        }
        
        val netPayable = maxOf(0.0, availableForDeduction - advanceDeduction)
        val remainingAdvanceBalance = outstandingAdvance - advanceDeduction
        
        return DriverPayableSummary(
            grossEarnings = grossEarnings,
            fuelCost = fuelCost,
            advanceDeduction = advanceDeduction,
            netPayable = netPayable,
            remainingAdvanceBalance = remainingAdvanceBalance
        )
    }
    
    /**
     * Calculate daily payable summary
     * Formula: DriverDailyPayable = sum(DriverEarning) - FuelCost - AdvanceDeduction (Section 8.1)
     */
    suspend fun calculateDailyPayable(driverId: Long, dayStart: Long, dayEnd: Long): DriverPayableSummary {
        return calculateNetPayable(driverId, dayStart, dayEnd)
    }
}

/**
 * Summary of driver's payable calculation
 */
data class DriverPayableSummary(
    val grossEarnings: Double,
    val fuelCost: Double,
    val advanceDeduction: Double,
    val netPayable: Double,
    val remainingAdvanceBalance: Double
) {
    val pendingAdvance: Double get() = remainingAdvanceBalance
}
