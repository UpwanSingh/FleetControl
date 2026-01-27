package com.fleetcontrol.domain.calculators

import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.repositories.TripRepository

/**
 * Owner Profit Calculator
 * Implements Section 5.2, 5.4, 8.2, 8.3 of BUSINESS_LOGIC_SPEC.md
 * 
 * Formulas:
 * - OwnerGross(trip) = bagCount × CompanyRate (Section 5.2)
 * - OwnerNet(trip) = OwnerGross - DriverEarning - LabourCost (Section 5.4)
 * - OwnerDailyProfit = sum(OwnerNet(all trips by all drivers)) (Section 8.2)
 * - OwnerMonthlyProfit = sum(OwnerDailyProfit(all days in month)) (Section 8.3)
 * 
 * Important: Per Section 6.2 & 7.2:
 * - Fuel does NOT affect Owner profit (deducted from driver only)
 * - Advances do NOT affect Owner profit (recoverable, not a business cost)
 */
class OwnerProfitCalculator(
    private val tripRepository: TripRepository
) {
    
    /**
     * Calculate owner gross revenue for a single trip
     * Formula: OwnerGross = bagCount × snapshotCompanyRate (Section 5.2)
     */
    fun calculateTripGrossRevenue(trip: TripEntity): Double {
        return trip.bagCount * trip.snapshotCompanyRate
    }
    
    /**
     * Calculate owner net profit for a single trip
     * Formula: OwnerNet = OwnerGross - DriverEarning - LabourCost (Section 5.4)
     */
    fun calculateTripNetProfit(trip: TripEntity): Double {
        val ownerGross = trip.bagCount * trip.snapshotCompanyRate
        val driverEarning = trip.bagCount * trip.snapshotDriverRate
        val labourCost = trip.bagCount * trip.snapshotLabourCostPerBag
        return ownerGross - driverEarning - labourCost
    }
    
    /**
     * Calculate owner gross revenue for a date range
     */
    suspend fun calculateGrossRevenue(startDate: Long, endDate: Long): Double {
        return tripRepository.getOwnerGrossRevenue(startDate, endDate)
    }
    
    /**
     * Calculate total driver earnings for a date range
     */
    suspend fun calculateTotalDriverEarnings(startDate: Long, endDate: Long): Double {
        return tripRepository.getTotalDriverEarnings(startDate, endDate)
    }
    
    /**
     * Calculate total labour cost for a date range
     */
    suspend fun calculateTotalLabourCost(startDate: Long, endDate: Long): Double {
        return tripRepository.getTotalLabourCost(startDate, endDate)
    }
    
    /**
     * Calculate owner net profit for a date range
     * Formula: sum(OwnerGross - DriverEarning - LabourCost) (Section 5.4)
     */
    suspend fun calculateNetProfit(startDate: Long, endDate: Long): Double {
        return tripRepository.getOwnerNetProfit(startDate, endDate)
    }
    
    /**
     * Get full profit summary for a date range
     */
    suspend fun calculateProfitSummary(startDate: Long, endDate: Long): OwnerProfitSummary {
        val grossRevenue = calculateGrossRevenue(startDate, endDate)
        val driverEarnings = calculateTotalDriverEarnings(startDate, endDate)
        val labourCost = calculateTotalLabourCost(startDate, endDate)
        val netProfit = calculateNetProfit(startDate, endDate)
        val tripCount = tripRepository.getTripCount(startDate, endDate)
        val totalBags = tripRepository.getTotalBags(startDate, endDate)
        
        return OwnerProfitSummary(
            grossRevenue = grossRevenue,
            driverEarnings = driverEarnings,
            labourCost = labourCost,
            netProfit = netProfit,
            tripCount = tripCount,
            totalBags = totalBags,
            profitMargin = if (grossRevenue > 0) (netProfit / grossRevenue) * 100 else 0.0
        )
    }
    
    /**
     * Calculate daily profit (Section 8.2)
     * Formula: OwnerDailyProfit = sum(OwnerNet(all trips by all drivers))
     */
    suspend fun calculateDailyProfit(dayStart: Long, dayEnd: Long): OwnerProfitSummary {
        return calculateProfitSummary(dayStart, dayEnd)
    }
    
    /**
     * Calculate monthly profit (Section 8.3)
     * Formula: OwnerMonthlyProfit = sum(OwnerDailyProfit(all days in month))
     */
    suspend fun calculateMonthlyProfit(monthStart: Long, monthEnd: Long): OwnerProfitSummary {
        return calculateProfitSummary(monthStart, monthEnd)
    }
}

/**
 * Summary of owner's profit calculation
 */
data class OwnerProfitSummary(
    val grossRevenue: Double,
    val driverEarnings: Double,
    val labourCost: Double,
    val netProfit: Double,
    val tripCount: Int,
    val totalBags: Int,
    val profitMargin: Double  // Percentage
)
