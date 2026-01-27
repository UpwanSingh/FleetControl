package com.fleetcontrol.services.payments

import android.content.Context
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Subscription Manager
 * Manages subscription state and entitlements
 */
class SubscriptionManager(private val context: Context) {
    
    private val _currentPlan = MutableStateFlow<SubscriptionPlanType>(SubscriptionPlanType.FREE)
    val currentPlan: StateFlow<SubscriptionPlanType> = _currentPlan.asStateFlow()
    
    private val _expirationDate = MutableStateFlow<Date?>(null)
    val expirationDate: StateFlow<Date?> = _expirationDate.asStateFlow()
    
    /**
     * Update subscription status from a purchase
     */
    fun updateFromPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        
        val plan = when {
            productId.contains("premium") -> SubscriptionPlanType.PREMIUM
            productId.contains("basic") -> SubscriptionPlanType.BASIC
            else -> SubscriptionPlanType.FREE
        }
        
        _currentPlan.value = plan
        
        // Set expiration based on purchase
        val calendar = Calendar.getInstance()
        if (productId.contains("yearly")) {
            calendar.add(Calendar.YEAR, 1)
        } else {
            calendar.add(Calendar.MONTH, 1)
        }
        _expirationDate.value = calendar.time
    }
    
    /**
     * Check if user has a specific feature
     */
    fun hasFeature(feature: Feature): Boolean {
        return when (feature) {
            Feature.UNLIMITED_DRIVERS -> _currentPlan.value == SubscriptionPlanType.PREMIUM
            Feature.CSV_EXPORT -> _currentPlan.value != SubscriptionPlanType.FREE
            Feature.PDF_EXPORT -> _currentPlan.value == SubscriptionPlanType.PREMIUM
            Feature.BACKUP_RESTORE -> _currentPlan.value != SubscriptionPlanType.FREE
            Feature.ADVANCED_ANALYTICS -> _currentPlan.value == SubscriptionPlanType.PREMIUM
            Feature.PRIORITY_SUPPORT -> _currentPlan.value == SubscriptionPlanType.PREMIUM
        }
    }
    
    /**
     * Get driver limit for current plan
     */
    fun getDriverLimit(): Int {
        return when (_currentPlan.value) {
            SubscriptionPlanType.FREE -> 2
            SubscriptionPlanType.BASIC -> 10
            SubscriptionPlanType.PREMIUM -> Int.MAX_VALUE
        }
    }
    
    /**
     * Get company limit for current plan
     */
    fun getCompanyLimit(): Int {
        return when (_currentPlan.value) {
            SubscriptionPlanType.FREE -> 3
            SubscriptionPlanType.BASIC -> 20
            SubscriptionPlanType.PREMIUM -> Int.MAX_VALUE
        }
    }
    
    /**
     * Check if subscription is active
     */
    fun isSubscriptionActive(): Boolean {
        val expiry = _expirationDate.value ?: return _currentPlan.value == SubscriptionPlanType.FREE
        return Date().before(expiry)
    }
    
    /**
     * Reset to free plan
     */
    fun resetToFree() {
        _currentPlan.value = SubscriptionPlanType.FREE
        _expirationDate.value = null
    }
}

enum class SubscriptionPlanType {
    FREE,
    BASIC,
    PREMIUM
}

enum class Feature {
    UNLIMITED_DRIVERS,
    CSV_EXPORT,
    PDF_EXPORT,
    BACKUP_RESTORE,
    ADVANCED_ANALYTICS,
    PRIORITY_SUPPORT
}
