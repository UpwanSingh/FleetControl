package com.fleetcontrol.services.payments

import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Purchase Observer
 * Observes and broadcasts purchase events
 */
class PurchaseObserver {
    
    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>()
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()
    
    /**
     * Notify about a successful purchase
     */
    suspend fun notifyPurchaseCompleted(purchase: Purchase) {
        _purchaseEvents.emit(PurchaseEvent.Completed(purchase))
    }
    
    /**
     * Notify about a cancelled purchase
     */
    suspend fun notifyPurchaseCancelled() {
        _purchaseEvents.emit(PurchaseEvent.Cancelled)
    }
    
    /**
     * Notify about a purchase error
     */
    suspend fun notifyPurchaseError(errorMessage: String) {
        _purchaseEvents.emit(PurchaseEvent.Error(errorMessage))
    }
    
    /**
     * Notify about pending purchase
     */
    suspend fun notifyPurchasePending(purchase: Purchase) {
        _purchaseEvents.emit(PurchaseEvent.Pending(purchase))
    }
    
    /**
     * Notify subscription restored
     */
    suspend fun notifySubscriptionRestored(purchase: Purchase) {
        _purchaseEvents.emit(PurchaseEvent.Restored(purchase))
    }
}

sealed class PurchaseEvent {
    data class Completed(val purchase: Purchase) : PurchaseEvent()
    data class Pending(val purchase: Purchase) : PurchaseEvent()
    data class Restored(val purchase: Purchase) : PurchaseEvent()
    object Cancelled : PurchaseEvent()
    data class Error(val message: String) : PurchaseEvent()
}
