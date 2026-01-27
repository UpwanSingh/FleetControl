package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.DriverEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Driver operations
 * 
 * Transaction Safety (Section 13):
 * - Driver creation/updates are ACID-compliant
 * - Balance updates are atomic
 * - Soft deletes preserve referential integrity
 * 
 * Note: Complex multi-DAO transactions should use TransactionManager
 */
@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveDrivers(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers ORDER BY name ASC")
    fun getAllDrivers(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers WHERE id = :id")
    suspend fun getDriverById(id: Long): DriverEntity?

    @Query("SELECT * FROM drivers WHERE firestoreId = :fid LIMIT 1")
    suspend fun getDriverByFirestoreId(fid: String): DriverEntity?
    
    /**
     * Find driver by name for deduplication (catches orphan entries)
     */
    @Query("SELECT * FROM drivers WHERE name = :name LIMIT 1")
    suspend fun getDriverByName(name: String): DriverEntity?

    @Query("SELECT * FROM drivers WHERE phone = :phone AND pin = :pin LIMIT 1")
    suspend fun login(phone: String, pin: String): DriverEntity?

    @Query("SELECT COUNT(*) FROM drivers WHERE isActive = 1")
    suspend fun getActiveDriverCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(driver: DriverEntity): Long

    @Update
    suspend fun update(driver: DriverEntity)

    @Delete
    suspend fun delete(driver: DriverEntity)

    @Query("UPDATE drivers SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE drivers SET isActive = 1 WHERE id = :id")
    suspend fun reactivate(id: Long)
    
    @Query("SELECT * FROM drivers WHERE isActive = 0 ORDER BY name ASC")
    fun getAllInactiveDrivers(): Flow<List<DriverEntity>>

    @Query("UPDATE drivers SET currentAdvanceBalance = :balance WHERE id = :driverId")
    suspend fun updateAdvanceBalance(driverId: Long, balance: Double)
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch insert drivers - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(drivers: List<DriverEntity>): List<Long>
}
