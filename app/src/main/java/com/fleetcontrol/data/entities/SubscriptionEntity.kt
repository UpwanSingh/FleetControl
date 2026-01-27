package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Subscription entity for tracking user subscription
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: String,
    val purchaseToken: String = "",
    val isActive: Boolean = false,
    val expiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
