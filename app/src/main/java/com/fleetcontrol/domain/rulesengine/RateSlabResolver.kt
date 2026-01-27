package com.fleetcontrol.domain.rulesengine

import com.fleetcontrol.data.entities.DriverRateSlabEntity
import com.fleetcontrol.data.repositories.RateSlabRepository

/**
 * Rate Slab Resolver
 * Implements Section 3.2 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Driver rate is per bag
 * - Rate depends on pickup distance slab
 * - Rates are set ONLY by Owner
 * - Driver CANNOT modify rates
 * 
 * Default slabs (from PROJECT_REFERENCE.md):
 * 0-50 km: ₹5/bag
 * 50-150 km: ₹7/bag
 * 150-500 km: ₹10/bag
 * 500+ km: ₹12/bag
 */
class RateSlabResolver(
    private val rateSlabRepository: RateSlabRepository
) {
    
    /**
     * Get the driver rate for a given pickup distance
     * Returns 0.0 if no matching slab found
     */
    suspend fun resolveDriverRate(pickupDistance: Double): Double {
        return rateSlabRepository.getRateSlabForDistance(pickupDistance)?.ratePerBag ?: 0.0
    }
    
    /**
     * Get the full rate slab for a given pickup distance
     */
    suspend fun resolveRateSlab(pickupDistance: Double): DriverRateSlabEntity? {
        return rateSlabRepository.getRateSlabForDistance(pickupDistance)
    }
    
    /**
     * Validate that a rate slab exists for the given distance
     */
    suspend fun hasValidRateSlab(pickupDistance: Double): Boolean {
        return rateSlabRepository.getRateSlabForDistance(pickupDistance) != null
    }
}
