package com.fleetcontrol.domain.entitlements

/**
 * Subscription Plans
 * Implements Section 12 of BUSINESS_LOGIC_SPEC.md
 * 
 * Tiers from PROJECT_REFERENCE.md:
 * - Free: 2 drivers, 3 companies, no export
 * - Basic (₹199/mo): 10 drivers, 10 companies, CSV export
 * - Premium (₹499/mo): Unlimited, CSV+PDF export, analytics
 */
enum class SubscriptionPlan {
    FREE,
    BASIC,
    PREMIUM;
    
    val maxDrivers: Int
        get() = when (this) {
            FREE -> 2
            BASIC -> 10
            PREMIUM -> Int.MAX_VALUE
        }
    
    val maxCompanies: Int
        get() = when (this) {
            FREE -> 3
            BASIC -> 10
            PREMIUM -> Int.MAX_VALUE
        }
    
    val hasExport: Boolean
        get() = this != FREE
    
    val hasPdfExport: Boolean
        get() = this == PREMIUM
    
    val hasBackup: Boolean
        get() = this != FREE
    
    val hasAnalytics: Boolean
        get() = this == PREMIUM
    
    val hasPrioritySupport: Boolean
        get() = this == PREMIUM
    
    val displayName: String
        get() = when (this) {
            FREE -> "Free"
            BASIC -> "Basic"
            PREMIUM -> "Premium"
        }
    
    val monthlyPrice: String
        get() = when (this) {
            FREE -> "₹0"
            BASIC -> "₹199/mo"
            PREMIUM -> "₹499/mo"
        }
}

/**
 * Product IDs for Google Play Billing
 */
object SubscriptionProductIds {
    const val BASIC_MONTHLY = "com.fleetcontrol.subscription.basic.monthly"
    const val BASIC_YEARLY = "com.fleetcontrol.subscription.basic.yearly"
    const val PREMIUM_MONTHLY = "com.fleetcontrol.subscription.premium.monthly"
    const val PREMIUM_YEARLY = "com.fleetcontrol.subscription.premium.yearly"
}
