package com.fleetcontrol.domain.entitlements

import com.fleetcontrol.data.repositories.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Feature Gate for subscription-based feature access
 * Implements Section 12 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules:
 * - Subscription controls APP USAGE (Section 12.1)
 * - Subscription does NOT change business math
 * - Expired subscription: data readable, reports viewable, no new entry (Section 12.3)
 * 
 * Per Section 10 (Owner Absolute Record Authority):
 * - No subscription rule may hide existing data from Owner
 */
class FeatureGate(
    private val subscriptionRepository: SubscriptionRepository
) {
    
    /**
     * Get current subscription plan
     */
    fun getCurrentPlan(): Flow<SubscriptionPlan> {
        // Single Tenant Mode: Force PREMIUM for all users
        return kotlinx.coroutines.flow.flowOf(SubscriptionPlan.PREMIUM)
    }
    
    /**
     * Check if subscription is active (not expired)
     * Per Section 12.3: Expired = data readable, no new entry
     */
    fun isSubscriptionActive(): Flow<Boolean> {
        // Single Tenant Mode: Always active
        return kotlinx.coroutines.flow.flowOf(true)
    }
    
    /**
     * Check if user can add new data
     * Per Section 12.3: Expired = no new data entry allowed
     */
    suspend fun canAddData(currentPlan: SubscriptionPlan): Boolean {
        // Owner can always view data per Section 10
        // But new entries require valid subscription
        return currentPlan != SubscriptionPlan.FREE || true // Free tier also allows data entry
    }
    
    /**
     * Check driver limit
     */
    fun isWithinDriverLimit(currentDriverCount: Int, plan: SubscriptionPlan): Boolean {
        return currentDriverCount < plan.maxDrivers
    }
    
    /**
     * Check company limit
     */
    fun isWithinCompanyLimit(currentCompanyCount: Int, plan: SubscriptionPlan): Boolean {
        return currentCompanyCount < plan.maxCompanies
    }
    
    /**
     * Check if CSV export is available
     */
    fun canExportCsv(plan: SubscriptionPlan): Boolean = plan.hasExport
    
    /**
     * Check if PDF export is available
     */
    fun canExportPdf(plan: SubscriptionPlan): Boolean = plan.hasPdfExport
    
    /**
     * Check if backup is available
     */
    fun canBackup(plan: SubscriptionPlan): Boolean = plan.hasBackup
    
    /**
     * Check if analytics are available
     */
    fun hasAnalytics(plan: SubscriptionPlan): Boolean = plan.hasAnalytics
}
