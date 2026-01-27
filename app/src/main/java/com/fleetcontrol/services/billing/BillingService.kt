package com.fleetcontrol.services.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play Billing Service
 * Handles subscription purchases for Basic and Premium plans
 */
class BillingService(private val context: Context) : PurchasesUpdatedListener {
    
    companion object {
        const val PRODUCT_BASIC = "fleetcontrol_basic_monthly"
        const val PRODUCT_PREMIUM = "fleetcontrol_premium_monthly"
    }
    
    private var billingClient: BillingClient? = null
    
    private val _billingState = MutableStateFlow(BillingState.NOT_CONNECTED)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()
    
    private val _currentPlan = MutableStateFlow<SubscriptionPlan>(SubscriptionPlan.FREE)
    val currentPlan: StateFlow<SubscriptionPlan> = _currentPlan.asStateFlow()
    
    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts.asStateFlow()
    
    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()
    
    /**
     * Initialize billing client
     */
    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        startConnection()
    }
    
    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingState.value = BillingState.CONNECTED
                    queryProducts()
                    queryPurchases()
                } else {
                    _billingState.value = BillingState.ERROR
                    _purchaseError.value = "Billing setup failed: ${billingResult.debugMessage}"
                }
            }
            
            override fun onBillingServiceDisconnected() {
                _billingState.value = BillingState.DISCONNECTED
            }
        })
    }
    
    /**
     * Query available subscription products
     */
    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_BASIC)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PREMIUM)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _availableProducts.value = productDetailsList
            }
        }
    }
    
    /**
     * Query existing purchases
     */
    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        billingClient?.queryPurchasesAsync(params) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchaseList)
            }
        }
    }
    
    private fun processPurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Acknowledge purchase if not already
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                
                // Determine plan
                when {
                    purchase.products.contains(PRODUCT_PREMIUM) -> {
                        _currentPlan.value = SubscriptionPlan.PREMIUM
                    }
                    purchase.products.contains(PRODUCT_BASIC) -> {
                        _currentPlan.value = SubscriptionPlan.BASIC
                    }
                }
            }
        }
    }
    
    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient?.acknowledgePurchase(params) { /* Handle result */ }
    }
    
    /**
     * Launch purchase flow for a product
     */
    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val product = _availableProducts.value.find { it.productId == productId }
        if (product == null) {
            _purchaseError.value = "Product not available"
            return
        }
        
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            _purchaseError.value = "No offer available"
            return
        }
        
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        
        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }
    
    /**
     * Handle purchase updates
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { processPurchases(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseError.value = "Purchase cancelled"
            }
            else -> {
                _purchaseError.value = "Purchase failed: ${billingResult.debugMessage}"
            }
        }
    }
    
    /**
     * Get price for a product
     */
    fun getProductPrice(productId: String): String? {
        return _availableProducts.value
            .find { it.productId == productId }
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
    }
    
    /**
     * Check if user has active subscription
     */
    fun isSubscribed(): Boolean {
        return _currentPlan.value != SubscriptionPlan.FREE
    }
    
    /**
     * Check if user has premium features
     */
    fun hasPremiumFeatures(): Boolean {
        return _currentPlan.value == SubscriptionPlan.PREMIUM
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _purchaseError.value = null
    }
    
    /**
     * Disconnect billing client
     */
    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }
}

enum class BillingState {
    NOT_CONNECTED,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

enum class SubscriptionPlan {
    FREE,
    BASIC,
    PREMIUM
}
