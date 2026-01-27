package com.fleetcontrol.viewmodel.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.fleetcontrol.services.billing.BillingService
import com.fleetcontrol.services.billing.BillingState
import com.fleetcontrol.services.billing.SubscriptionPlan
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Subscription management
 * Handles billing state and purchase flows
 */
class SubscriptionViewModel(
    private val billingService: BillingService
) : BaseViewModel() {
    
    // Single Client Mode: Force PREMIUM
    val currentPlan: StateFlow<SubscriptionPlan> = MutableStateFlow(SubscriptionPlan.PREMIUM)
    val billingState: StateFlow<BillingState> = billingService.billingState
    val purchaseError: StateFlow<String?> = billingService.purchaseError
    
    private val _basicPrice = MutableStateFlow<String?>(null)
    val basicPrice: StateFlow<String?> = _basicPrice.asStateFlow()
    
    private val _premiumPrice = MutableStateFlow<String?>(null)
    val premiumPrice: StateFlow<String?> = _premiumPrice.asStateFlow()
    
    init {
        // Initialize billing and load prices
        billingService.initialize()
        loadPrices()
    }
    
    private fun loadPrices() {
        viewModelScope.launch {
            // Wait for billing to connect and products to load
            billingService.billingState.collect { state ->
                if (state == BillingState.CONNECTED) {
                    _basicPrice.value = billingService.getProductPrice(BillingService.PRODUCT_BASIC)
                        ?: "₹99/month"
                    _premiumPrice.value = billingService.getProductPrice(BillingService.PRODUCT_PREMIUM)
                        ?: "₹199/month"
                }
            }
        }
    }
    
    /**
     * Purchase a subscription plan
     */
    fun purchasePlan(activity: Activity, planType: String) {
        _isLoading.value = true
        
        val productId = when (planType.lowercase()) {
            "basic" -> BillingService.PRODUCT_BASIC
            "premium" -> BillingService.PRODUCT_PREMIUM
            else -> {
                _isLoading.value = false
                return
            }
        }
        
        viewModelScope.launch {
            try {
                billingService.launchPurchaseFlow(activity, productId)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Open Google Play subscription management
     */
    fun manageSubscription(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/account/subscriptions")
        }
        context.startActivity(intent)
    }
    
    /**
     * Check if user has premium features
     */
    fun hasPremiumFeatures(): Boolean {
        // Single Tenant Mode: Force True
        return true
    }
    
    /**
     * Check if user has any subscription
     */
    fun isSubscribed(): Boolean {
        // Single Tenant Mode: Force True
        return true
    }
    
    override fun onCleared() {
        super.onCleared()
        billingService.disconnect()
    }
}
