package com.fleetcontrol.domain.calculators

import com.fleetcontrol.utils.DateUtils

/**
 * Monthly Aggregation Calculator
 * Implements Section 8 of BUSINESS_LOGIC_SPEC.md
 * 
 * Provides date range calculations for aggregation queries:
 * - Daily aggregation
 * - Monthly aggregation
 * - Custom date range aggregation
 */
class MonthlyAggregationCalculator(
    private val ownerProfitCalculator: OwnerProfitCalculator,
    private val driverEarningCalculator: DriverEarningCalculator
) {
    
    /**
     * Calculate owner's profit summary for a specific month
     * Per Section 8.3: OwnerMonthlyProfit = sum(OwnerDailyProfit(all days in month))
     */
    suspend fun calculateOwnerMonthlyProfit(year: Int, month: Int): OwnerProfitSummary {
        val startDate = DateUtils.getStartOfMonth(year, month)
        val endDate = DateUtils.getEndOfMonth(year, month)
        return ownerProfitCalculator.calculateMonthlyProfit(startDate, endDate)
    }
    
    /**
     * Calculate driver's payable summary for a specific month
     */
    suspend fun calculateDriverMonthlyPayable(
        driverId: Long, 
        year: Int, 
        month: Int
    ): DriverPayableSummary {
        val startDate = DateUtils.getStartOfMonth(year, month)
        val endDate = DateUtils.getEndOfMonth(year, month)
        return driverEarningCalculator.calculateNetPayable(driverId, startDate, endDate)
    }
    
    /**
     * Calculate owner's profit summary for today
     * Per Section 8.2: OwnerDailyProfit = sum(OwnerNet(all trips by all drivers))
     */
    suspend fun calculateOwnerTodayProfit(): OwnerProfitSummary {
        val todayStart = DateUtils.getStartOfToday()
        val todayEnd = DateUtils.getEndOfToday()
        return ownerProfitCalculator.calculateDailyProfit(todayStart, todayEnd)
    }
    
    /**
     * Calculate driver's payable summary for today
     */
    suspend fun calculateDriverTodayPayable(driverId: Long): DriverPayableSummary {
        val todayStart = DateUtils.getStartOfToday()
        val todayEnd = DateUtils.getEndOfToday()
        return driverEarningCalculator.calculateDailyPayable(driverId, todayStart, todayEnd)
    }
    
    /**
     * Calculate owner profit from Cloud Driver Stats
     * Aggregates multiple DriverStats documents into one summary
     */
    fun calculateOwnerProfit(stats: List<com.fleetcontrol.data.entities.DriverStats>): OwnerProfitSummary {
        var totalGross = 0.0
        var totalDriverEarnings = 0.0
        var totalLabourCost = 0.0
        var totalTrips = 0
        var totalBags = 0L
        
        stats.forEach { stat ->
            totalGross += stat.totalOwnerGross
            totalDriverEarnings += stat.totalDriverEarnings
            totalLabourCost += stat.totalLabourCost
            totalTrips += stat.totalTrips.toInt()
            totalBags += stat.totalBags
        }
        
        val netProfit = totalGross - totalDriverEarnings - totalLabourCost
        
        return OwnerProfitSummary(
            grossRevenue = totalGross,
            driverEarnings = totalDriverEarnings,
            labourCost = totalLabourCost,
            netProfit = netProfit,
            tripCount = totalTrips,
            totalBags = totalBags.toInt(),
            profitMargin = if (totalGross > 0) (netProfit / totalGross) * 100 else 0.0
        )
    }
}
