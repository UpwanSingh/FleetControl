package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.OwnerEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Owner operations
 * 
 * Multi-Tenancy: Used to cache owner profile locally
 */
@Dao
interface OwnerDao {
    
    @Query("SELECT * FROM owners WHERE firebaseUid = :uid")
    suspend fun getOwnerByUid(uid: String): OwnerEntity?
    
    @Query("SELECT * FROM owners WHERE firebaseUid = :uid")
    fun getOwnerByUidFlow(uid: String): Flow<OwnerEntity?>
    
    @Query("SELECT * FROM owners LIMIT 1")
    suspend fun getCurrentOwner(): OwnerEntity?
    
    @Query("SELECT * FROM owners LIMIT 1")
    fun getCurrentOwnerFlow(): Flow<OwnerEntity?>
    
    @Query("SELECT COUNT(*) FROM owners")
    suspend fun getOwnerCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(owner: OwnerEntity)
    
    @Update
    suspend fun update(owner: OwnerEntity)
    
    @Query("DELETE FROM owners")
    suspend fun deleteAll()
}
