package com.fleetcontrol.core

/**
 * Represents the current subscription state
 */
sealed class SubscriptionState {
    object Free : SubscriptionState()
    object Premium : SubscriptionState()
    object Expired : SubscriptionState()
}
