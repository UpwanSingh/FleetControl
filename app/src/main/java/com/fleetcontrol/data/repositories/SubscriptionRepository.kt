package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.SubscriptionDao
import com.fleetcontrol.data.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Subscription data operations
 */
class SubscriptionRepository(private val subscriptionDao: SubscriptionDao) {
    
    fun getActiveSubscription(): Flow<SubscriptionEntity?> = subscriptionDao.getActiveSubscription()
    
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()
    
    suspend fun insert(subscription: SubscriptionEntity): Long = subscriptionDao.insert(subscription)
    
    suspend fun update(subscription: SubscriptionEntity) = subscriptionDao.update(subscription)
    
    suspend fun deactivateAll() = subscriptionDao.deactivateAll()
}
