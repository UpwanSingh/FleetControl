package com.fleetcontrol.domain.intelligence

import com.fleetcontrol.data.entities.TripEntity
import java.util.*

/**
 * Trend Analyzer
 * Analyzes various trends in fleet operations
 */
class TrendAnalyzer {
    
    /**
     * Analyze trip trends over time
     */
    fun analyzeTripTrends(trips: List<TripEntity>): TripTrendAnalysis {
        if (trips.isEmpty()) {
            return TripTrendAnalysis(
                avgTripsPerDay = 0.0,
                avgBagsPerTrip = 0.0,
                peakDay = null,
                trends = emptyList()
            )
        }
        
        val calendar = Calendar.getInstance()
        
        // Group by date
        val tripsByDate = trips.groupBy { trip ->
            calendar.timeInMillis = trip.tripDate
            calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        }
        
        val avgTripsPerDay = trips.size.toDouble() / tripsByDate.size.coerceAtLeast(1)
        val avgBagsPerTrip = trips.sumOf { it.bagCount }.toDouble() / trips.size
        
        // Find peak day
        val peakDayEntry = tripsByDate.maxByOrNull { it.value.size }
        val peakDay = peakDayEntry?.value?.firstOrNull()?.tripDate?.let { Date(it) }
        
        // Analyze trends
        val trends = mutableListOf<TrendInsight>()
        
        // Trip frequency trend
        if (avgTripsPerDay > 5) {
            trends.add(TrendInsight(
                type = InsightType.POSITIVE,
                title = "High Activity",
                description = "Averaging ${String.format("%.1f", avgTripsPerDay)} trips per day"
            ))
        }
        
        // Volume trend
        if (avgBagsPerTrip > 50) {
            trends.add(TrendInsight(
                type = InsightType.POSITIVE,
                title = "High Volume",
                description = "Average of ${String.format("%.0f", avgBagsPerTrip)} bags per trip"
            ))
        } else if (avgBagsPerTrip < 20) {
            trends.add(TrendInsight(
                type = InsightType.WARNING,
                title = "Low Volume",
                description = "Consider optimizing routes for larger loads"
            ))
        }
        
        // Company diversity
        val uniqueCompanies = trips.map { it.companyId }.distinct().size
        if (uniqueCompanies > 3) {
            trends.add(TrendInsight(
                type = InsightType.POSITIVE,
                title = "Diversified Clients",
                description = "Active with $uniqueCompanies different companies"
            ))
        } else if (uniqueCompanies == 1) {
            trends.add(TrendInsight(
                type = InsightType.WARNING,
                title = "Single Client Risk",
                description = "Consider diversifying your client base"
            ))
        }
        
        return TripTrendAnalysis(
            avgTripsPerDay = avgTripsPerDay,
            avgBagsPerTrip = avgBagsPerTrip,
            peakDay = peakDay,
            trends = trends
        )
    }
    
    /**
     * Analyze driver performance trends
     */
    fun analyzeDriverPerformance(
        driverTrips: Map<Long, List<TripEntity>>
    ): List<DriverPerformance> {
        return driverTrips.map { (driverId, trips) ->
            val totalBags = trips.sumOf { it.bagCount }
            val totalEarnings = trips.sumOf { it.bagCount * it.snapshotDriverRate }
            val avgBagsPerTrip = if (trips.isNotEmpty()) totalBags.toDouble() / trips.size else 0.0
            
            DriverPerformance(
                driverId = driverId,
                tripCount = trips.size,
                totalBags = totalBags,
                totalEarnings = totalEarnings,
                avgBagsPerTrip = avgBagsPerTrip
            )
        }.sortedByDescending { it.totalBags }
    }
}

data class TripTrendAnalysis(
    val avgTripsPerDay: Double,
    val avgBagsPerTrip: Double,
    val peakDay: Date?,
    val trends: List<TrendInsight>
)

data class TrendInsight(
    val type: InsightType,
    val title: String,
    val description: String
)

enum class InsightType {
    POSITIVE,
    WARNING,
    INFO
}

data class DriverPerformance(
    val driverId: Long,
    val tripCount: Int,
    val totalBags: Int,
    val totalEarnings: Double,
    val avgBagsPerTrip: Double
)
