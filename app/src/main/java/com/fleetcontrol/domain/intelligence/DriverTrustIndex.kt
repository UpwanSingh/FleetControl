package com.fleetcontrol.domain.intelligence

import com.fleetcontrol.data.entities.TripEntity
import java.util.*

/**
 * Driver Trust Index Calculator
 * Calculates a reliability score for drivers based on trip patterns
 */
class DriverTrustIndex {
    
    companion object {
        private const val MAX_SCORE = 100.0
        private const val MIN_SCORE = 0.0
    }
    
    /**
     * Calculate trust index for a driver based on their trips
     */
    fun calculate(trips: List<TripEntity>): TrustIndexResult {
        if (trips.isEmpty()) {
            return TrustIndexResult(
                score = 50.0,
                reliability = "New Driver",
                factors = listOf("Not enough data to calculate trust index")
            )
        }
        
        var score = 50.0 // Start at neutral
        val factors = mutableListOf<String>()
        
        // Factor 1: Consistency - Regular trip frequency
        val avgTripsPerWeek = calculateTripsPerWeek(trips)
        val consistencyScore = when {
            avgTripsPerWeek >= 15 -> 20.0
            avgTripsPerWeek >= 10 -> 15.0
            avgTripsPerWeek >= 5 -> 10.0
            avgTripsPerWeek >= 2 -> 5.0
            else -> 0.0
        }
        score += consistencyScore
        if (consistencyScore > 10) factors.add("High trip frequency")
        
        // Factor 2: Volume - Total bags delivered
        val totalBags = trips.sumOf { it.bagCount }
        val volumeScore = when {
            totalBags >= 5000 -> 15.0
            totalBags >= 2000 -> 10.0
            totalBags >= 500 -> 5.0
            else -> 0.0
        }
        score += volumeScore
        if (volumeScore > 5) factors.add("High delivery volume")
        
        // Factor 3: Tenure - How long they've been active
        val tenureDays = calculateTenure(trips)
        val tenureScore = when {
            tenureDays >= 365 -> 15.0
            tenureDays >= 180 -> 10.0
            tenureDays >= 90 -> 5.0
            tenureDays >= 30 -> 2.0
            else -> 0.0
        }
        score += tenureScore
        if (tenureScore > 5) factors.add("Long-term driver")
        
        // Clamp score
        score = score.coerceIn(MIN_SCORE, MAX_SCORE)
        
        val reliability = when {
            score >= 80 -> "Excellent"
            score >= 60 -> "Good"
            score >= 40 -> "Average"
            score >= 20 -> "Below Average"
            else -> "New"
        }
        
        return TrustIndexResult(
            score = score,
            reliability = reliability,
            factors = if (factors.isEmpty()) listOf("Building trust record") else factors
        )
    }
    
    private fun calculateTripsPerWeek(trips: List<TripEntity>): Double {
        if (trips.size < 2) return trips.size.toDouble()
        
        val sortedTrips = trips.sortedBy { it.tripDate }
        val firstTrip = sortedTrips.first().tripDate
        val lastTrip = sortedTrips.last().tripDate
        
        val weeksActive = ((lastTrip - firstTrip) / (7 * 24 * 60 * 60 * 1000.0)).coerceAtLeast(1.0)
        return trips.size / weeksActive
    }
    
    private fun calculateTenure(trips: List<TripEntity>): Int {
        if (trips.isEmpty()) return 0
        
        val firstTrip = trips.minOf { it.tripDate }
        val now = System.currentTimeMillis()
        return ((now - firstTrip) / (24 * 60 * 60 * 1000)).toInt()
    }
}

data class TrustIndexResult(
    val score: Double,
    val reliability: String,
    val factors: List<String>
)
