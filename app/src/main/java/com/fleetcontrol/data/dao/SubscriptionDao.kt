package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Subscription operations
 */
@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE isActive = 1 LIMIT 1")
    fun getActiveSubscription(): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET isActive = 0")
    suspend fun deactivateAll()
}
