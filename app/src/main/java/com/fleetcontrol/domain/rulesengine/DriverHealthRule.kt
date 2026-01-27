package com.fleetcontrol.domain.rulesengine

import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.entities.FuelEntryEntity

/**
 * Driver Health Rule
 * Evaluates driver activity and performance health
 */
class DriverHealthRule {
    
    /**
     * Evaluate driver health based on recent activity
     */
    fun evaluate(
        driverId: Long,
        recentTrips: List<TripEntity>,
        recentFuel: List<FuelEntryEntity>,
        outstandingAdvance: Double
    ): DriverHealthReport {
        val issues = mutableListOf<HealthIssue>()
        var healthScore = 100.0
        
        // Check trip activity
        val tripCount = recentTrips.size
        when {
            tripCount == 0 -> {
                issues.add(HealthIssue(
                    severity = Severity.WARNING,
                    category = "Activity",
                    message = "No trips in the last 30 days"
                ))
                healthScore -= 20
            }
            tripCount < 5 -> {
                issues.add(HealthIssue(
                    severity = Severity.INFO,
                    category = "Activity",
                    message = "Low trip activity ($tripCount trips)"
                ))
                healthScore -= 10
            }
        }
        
        // Check fuel logging
        if (recentTrips.isNotEmpty() && recentFuel.isEmpty()) {
            issues.add(HealthIssue(
                severity = Severity.WARNING,
                category = "Fuel",
                message = "No fuel entries logged despite active trips"
            ))
            healthScore -= 15
        }
        
        // Check outstanding advance
        if (outstandingAdvance > 10000) {
            issues.add(HealthIssue(
                severity = Severity.CRITICAL,
                category = "Finance",
                message = "High outstanding advance: ₹$outstandingAdvance"
            ))
            healthScore -= 25
        } else if (outstandingAdvance > 5000) {
            issues.add(HealthIssue(
                severity = Severity.WARNING,
                category = "Finance",
                message = "Moderate outstanding advance: ₹$outstandingAdvance"
            ))
            healthScore -= 10
        }
        
        // Check earnings consistency
        if (recentTrips.size >= 5) {
            val avgBags = recentTrips.map { it.bagCount }.average()
            val lowBagTrips = recentTrips.count { it.bagCount < avgBags * 0.5 }
            if (lowBagTrips > recentTrips.size / 3) {
                issues.add(HealthIssue(
                    severity = Severity.INFO,
                    category = "Performance",
                    message = "Inconsistent load sizes"
                ))
                healthScore -= 5
            }
        }
        
        val status = when {
            healthScore >= 80 -> HealthStatus.HEALTHY
            healthScore >= 60 -> HealthStatus.NEEDS_ATTENTION
            healthScore >= 40 -> HealthStatus.AT_RISK
            else -> HealthStatus.CRITICAL
        }
        
        return DriverHealthReport(
            driverId = driverId,
            healthScore = healthScore.coerceAtLeast(0.0),
            status = status,
            issues = issues
        )
    }
}

data class DriverHealthReport(
    val driverId: Long,
    val healthScore: Double,
    val status: HealthStatus,
    val issues: List<HealthIssue>
)

enum class HealthStatus {
    HEALTHY,
    NEEDS_ATTENTION,
    AT_RISK,
    CRITICAL
}

data class HealthIssue(
    val severity: Severity,
    val category: String,
    val message: String
)

enum class Severity {
    INFO,
    WARNING,
    CRITICAL
}
