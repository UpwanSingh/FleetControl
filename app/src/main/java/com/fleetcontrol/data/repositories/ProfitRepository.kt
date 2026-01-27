package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.TripDao
import com.fleetcontrol.domain.calculators.OwnerProfitCalculator
import com.fleetcontrol.domain.calculators.OwnerProfitSummary
import com.fleetcontrol.utils.DateUtils
import java.util.*

/**
 * Repository for profit calculations
 */
class ProfitRepository(
    private val tripDao: TripDao
) {
    
    /**
     * Get profit summary for a specific month
     */
    suspend fun getProfitForMonth(year: Int, month: Int): OwnerProfitSummary {
        val startOfMonth = DateUtils.getStartOfMonth(year, month)
        val endOfMonth = DateUtils.getEndOfMonth(year, month)
        
        val trips = tripDao.getTripsForDateRangeSync(startOfMonth, endOfMonth)
        
        return calculateSummary(trips)
    }
    
    /**
     * Get profit summary for today
     */
    suspend fun getTodayProfit(): OwnerProfitSummary {
        val startOfDay = DateUtils.getStartOfToday()
        val endOfDay = DateUtils.getEndOfToday()
        
        val trips = tripDao.getTripsForDateRangeSync(startOfDay, endOfDay)
        
        return calculateSummary(trips)
    }
    
    /**
     * Get profit summary for a date range
     */
    suspend fun getProfitForDateRange(startDate: Date, endDate: Date): OwnerProfitSummary {
        val trips = tripDao.getTripsForDateRangeSync(startDate.time, endDate.time)
        return calculateSummary(trips)
    }
    
    private fun calculateSummary(trips: List<com.fleetcontrol.data.entities.TripEntity>): OwnerProfitSummary {
        var grossRevenue = 0.0
        var driverEarnings = 0.0
        var labourCost = 0.0
        var totalBags = 0
        
        trips.forEach { trip ->
            grossRevenue += trip.bagCount * trip.snapshotCompanyRate
            driverEarnings += trip.bagCount * trip.snapshotDriverRate
            labourCost += trip.bagCount * trip.snapshotLabourCostPerBag
            totalBags += trip.bagCount
        }
        
        val netProfit = grossRevenue - driverEarnings - labourCost
        val profitMargin = if (grossRevenue > 0) (netProfit / grossRevenue) * 100 else 0.0
        
        return OwnerProfitSummary(
            grossRevenue = grossRevenue,
            driverEarnings = driverEarnings,
            labourCost = labourCost,
            netProfit = netProfit,
            tripCount = trips.size,
            totalBags = totalBags,
            profitMargin = profitMargin
        )
    }
    
    /**
     * Get profit per driver for a month
     */
    suspend fun getProfitByDriver(year: Int, month: Int): Map<Long, Double> {
        val startOfMonth = DateUtils.getStartOfMonth(year, month)
        val endOfMonth = DateUtils.getEndOfMonth(year, month)
        
        val trips = tripDao.getTripsForDateRangeSync(startOfMonth, endOfMonth)
        
        return trips.groupBy { it.driverId }.mapValues { entry ->
            entry.value.sumOf { trip ->
                val revenue = trip.bagCount * trip.snapshotCompanyRate
                val driverCost = trip.bagCount * trip.snapshotDriverRate
                val labourCost = trip.bagCount * trip.snapshotLabourCostPerBag
                revenue - driverCost - labourCost
            }
        }
    }
    
    /**
     * Get profit per company for a month
     */
    suspend fun getProfitByCompany(year: Int, month: Int): Map<Long, Double> {
        val startOfMonth = DateUtils.getStartOfMonth(year, month)
        val endOfMonth = DateUtils.getEndOfMonth(year, month)
        
        val trips = tripDao.getTripsForDateRangeSync(startOfMonth, endOfMonth)
        
        return trips.groupBy { it.companyId }.mapValues { entry ->
            entry.value.sumOf { trip ->
                val revenue = trip.bagCount * trip.snapshotCompanyRate
                val driverCost = trip.bagCount * trip.snapshotDriverRate
                val labourCost = trip.bagCount * trip.snapshotLabourCostPerBag
                revenue - driverCost - labourCost
            }
        }
    }
}
