package com.fleetcontrol.domain.rulesengine

import com.fleetcontrol.data.entities.TripEntity

/**
 * Money Leak Detector
 * Identifies potential revenue losses and inefficiencies
 */
class MoneyLeakDetector {
    
    /**
     * Detect potential money leaks in operations
     */
    fun detect(
        trips: List<TripEntity>,
        averageLabourCostPerBag: Double = 2.0
    ): MoneyLeakReport {
        val leaks = mutableListOf<MoneyLeak>()
        var totalLeakageEstimate = 0.0
        
        if (trips.isEmpty()) {
            return MoneyLeakReport(
                leaks = emptyList(),
                totalLeakageEstimate = 0.0,
                riskLevel = RiskLevel.LOW
            )
        }
        
        // Check for unusual rate discrepancies
        val avgCompanyRate = trips.map { it.snapshotCompanyRate }.average()
        val avgDriverRate = trips.map { it.snapshotDriverRate }.average()
        
        val lowMarginTrips = trips.filter { trip ->
            val margin = trip.snapshotCompanyRate - trip.snapshotDriverRate - trip.snapshotLabourCostPerBag
            margin < avgCompanyRate * 0.1 // Less than 10% margin
        }
        
        if (lowMarginTrips.isNotEmpty()) {
            val leakage = lowMarginTrips.sumOf { trip ->
                val expectedMargin = avgCompanyRate * 0.15
                val actualMargin = trip.snapshotCompanyRate - trip.snapshotDriverRate - trip.snapshotLabourCostPerBag
                (expectedMargin - actualMargin) * trip.bagCount
            }
            leaks.add(MoneyLeak(
                type = LeakType.LOW_MARGIN,
                description = "${lowMarginTrips.size} trips with unusually low profit margins",
                estimatedLoss = leakage,
                recommendation = "Review pricing for these routes"
            ))
            totalLeakageEstimate += leakage
        }
        
        // Check for inconsistent company rates
        val companiesWithVariableRates = trips.groupBy { it.companyId }
            .filter { (_, companyTrips) -> 
                val rates = companyTrips.map { it.snapshotCompanyRate }.distinct()
                rates.size > 1
            }
        
        if (companiesWithVariableRates.isNotEmpty()) {
            leaks.add(MoneyLeak(
                type = LeakType.RATE_INCONSISTENCY,
                description = "${companiesWithVariableRates.size} companies with varying rates",
                estimatedLoss = 0.0, // Hard to estimate
                recommendation = "Standardize rates per company"
            ))
        }
        
        // Check for high fuel/trip ratio (if fuel data available)
        // This would require fuel data passed in
        
        // Check for small bag trips
        val smallTrips = trips.filter { it.bagCount < 10 }
        if (smallTrips.size > trips.size * 0.3) {
            leaks.add(MoneyLeak(
                type = LeakType.INEFFICIENT_LOADS,
                description = "${(smallTrips.size * 100 / trips.size)}% of trips have less than 10 bags",
                estimatedLoss = smallTrips.size * 50.0, // Estimated opportunity cost per trip
                recommendation = "Combine small loads when possible"
            ))
            totalLeakageEstimate += smallTrips.size * 50.0
        }
        
        val riskLevel = when {
            totalLeakageEstimate > 50000 -> RiskLevel.HIGH
            totalLeakageEstimate > 10000 -> RiskLevel.MEDIUM
            leaks.isNotEmpty() -> RiskLevel.LOW
            else -> RiskLevel.NONE
        }
        
        return MoneyLeakReport(
            leaks = leaks,
            totalLeakageEstimate = totalLeakageEstimate,
            riskLevel = riskLevel
        )
    }
}

data class MoneyLeakReport(
    val leaks: List<MoneyLeak>,
    val totalLeakageEstimate: Double,
    val riskLevel: RiskLevel
)

data class MoneyLeak(
    val type: LeakType,
    val description: String,
    val estimatedLoss: Double,
    val recommendation: String
)

enum class LeakType {
    LOW_MARGIN,
    RATE_INCONSISTENCY,
    INEFFICIENT_LOADS,
    EXCESSIVE_FUEL,
    UNTRACKED_EXPENSE
}

enum class RiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}
