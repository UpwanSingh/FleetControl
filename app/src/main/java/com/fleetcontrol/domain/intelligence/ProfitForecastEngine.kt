package com.fleetcontrol.domain.intelligence

import com.fleetcontrol.data.entities.TripEntity
import java.util.*

/**
 * Profit Forecast Engine
 * Predicts future profit based on historical trends
 */
class ProfitForecastEngine {
    
    /**
     * Forecast profit for the next period based on historical data
     */
    fun forecast(
        historicalTrips: List<TripEntity>,
        periodsToForecast: Int = 1
    ): ForecastResult {
        if (historicalTrips.size < 7) {
            return ForecastResult(
                predictedProfit = 0.0,
                confidence = 0.0,
                trend = Trend.INSUFFICIENT_DATA,
                message = "Need at least 7 days of data for accurate forecasting"
            )
        }
        
        // Group trips by week
        val weeklyProfits = groupByWeek(historicalTrips)
        
        if (weeklyProfits.size < 2) {
            return ForecastResult(
                predictedProfit = weeklyProfits.firstOrNull()?.second ?: 0.0,
                confidence = 0.3,
                trend = Trend.STABLE,
                message = "Limited data - forecast based on current week only"
            )
        }
        
        // Calculate trend using simple linear regression
        val (slope, intercept) = linearRegression(weeklyProfits.mapIndexed { i, (_, profit) -> i.toDouble() to profit })
        
        // Predict next period
        val nextPeriodIndex = weeklyProfits.size.toDouble()
        val predictedProfit = (slope * nextPeriodIndex + intercept) * periodsToForecast
        
        // Calculate confidence based on data consistency
        val volatility = calculateVolatility(weeklyProfits.map { it.second })
        val confidence = (1.0 - volatility).coerceIn(0.2, 0.95)
        
        // Determine trend
        val trend = when {
            slope > 50 -> Trend.STRONG_UP
            slope > 10 -> Trend.UP
            slope < -50 -> Trend.STRONG_DOWN
            slope < -10 -> Trend.DOWN
            else -> Trend.STABLE
        }
        
        val message = when (trend) {
            Trend.STRONG_UP -> "Strong growth trend detected"
            Trend.UP -> "Positive growth trend"
            Trend.STABLE -> "Profit is stable"
            Trend.DOWN -> "Slight decline in profit"
            Trend.STRONG_DOWN -> "Significant decline detected"
            Trend.INSUFFICIENT_DATA -> "More data needed"
        }
        
        return ForecastResult(
            predictedProfit = predictedProfit.coerceAtLeast(0.0),
            confidence = confidence,
            trend = trend,
            message = message
        )
    }
    
    private fun groupByWeek(trips: List<TripEntity>): List<Pair<Int, Double>> {
        val calendar = Calendar.getInstance()
        
        return trips.groupBy { trip ->
            calendar.timeInMillis = trip.tripDate
            calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.WEEK_OF_YEAR)
        }.map { (weekId, weekTrips) ->
            val weekProfit = weekTrips.sumOf { trip ->
                val revenue = trip.bagCount * trip.snapshotCompanyRate
                val driverCost = trip.bagCount * trip.snapshotDriverRate
                val labourCost = trip.bagCount * trip.snapshotLabourCostPerBag
                revenue - driverCost - labourCost
            }
            weekId to weekProfit
        }.sortedBy { it.first }
    }
    
    private fun linearRegression(points: List<Pair<Double, Double>>): Pair<Double, Double> {
        val n = points.size
        val sumX = points.sumOf { it.first }
        val sumY = points.sumOf { it.second }
        val sumXY = points.sumOf { it.first * it.second }
        val sumXX = points.sumOf { it.first * it.first }
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n
        
        return slope to intercept
    }
    
    private fun calculateVolatility(values: List<Double>): Double {
        if (values.size < 2) return 0.5
        
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        return (stdDev / mean).coerceIn(0.0, 1.0)
    }
}

data class ForecastResult(
    val predictedProfit: Double,
    val confidence: Double,
    val trend: Trend,
    val message: String
)

enum class Trend {
    STRONG_UP,
    UP,
    STABLE,
    DOWN,
    STRONG_DOWN,
    INSUFFICIENT_DATA
}
