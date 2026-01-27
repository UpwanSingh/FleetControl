package com.fleetcontrol.domain.rulesengine

import com.fleetcontrol.data.entities.PickupLocationEntity

/**
 * Pickup Selection Rule
 * Helps select appropriate pickup location based on criteria
 */
class PickupSelectionRule {
    
    /**
     * Find pickup locations within a distance range
     */
    fun findByDistanceRange(
        pickups: List<PickupLocationEntity>,
        minDistance: Double,
        maxDistance: Double
    ): List<PickupLocationEntity> {
        return pickups.filter { pickup ->
            pickup.distanceFromBase >= minDistance && pickup.distanceFromBase <= maxDistance
        }.sortedBy { it.distanceFromBase }
    }
    
    /**
     * Find the nearest pickup location
     */
    fun findNearest(
        pickups: List<PickupLocationEntity>,
        distance: Double
    ): PickupLocationEntity? {
        return pickups.minByOrNull { kotlin.math.abs(it.distanceFromBase - distance) }
    }
    
    /**
     * Group pickups by distance category
     */
    fun groupByCategory(
        pickups: List<PickupLocationEntity>
    ): Map<DistanceCategory, List<PickupLocationEntity>> {
        return pickups.groupBy { pickup ->
            when {
                pickup.distanceFromBase <= 10 -> DistanceCategory.LOCAL
                pickup.distanceFromBase <= 25 -> DistanceCategory.SUBURBAN
                pickup.distanceFromBase <= 50 -> DistanceCategory.CITY
                else -> DistanceCategory.OUTSTATION
            }
        }
    }
    
    /**
     * Validate pickup selection for a trip
     */
    fun validateSelection(
        pickup: PickupLocationEntity,
        companyId: Long
    ): ValidationResult {
        val issues = mutableListOf<String>()
        
        if (!pickup.isActive) {
            issues.add("Pickup location is inactive")
        }
        
        if (pickup.distanceFromBase <= 0) {
            issues.add("Invalid distance")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
}

enum class DistanceCategory {
    LOCAL,      // 0-10 km
    SUBURBAN,   // 10-25 km
    CITY,       // 25-50 km
    OUTSTATION  // 50+ km
}

data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)
