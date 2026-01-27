package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.AdvanceEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Advance operations
 * Implements queries for Section 7 - Driver Advance Rules
 * 
 * Transaction Safety (Section 13):
 * - All advance operations are ACID-compliant
 * - Driver balance updates are atomic
 * - No partial saves
 * 
 * Note: Complex multi-DAO transactions should use TransactionManager
 */
@Dao
interface AdvanceDao {
    @Query("SELECT * FROM advances ORDER BY advanceDate DESC")
    fun getAllAdvances(): Flow<List<AdvanceEntity>>

    @Query("SELECT * FROM advances WHERE driverId = :driverId ORDER BY advanceDate DESC")
    fun getAdvancesByDriver(driverId: Long): Flow<List<AdvanceEntity>>

    @Query("SELECT * FROM advances WHERE driverId = :driverId AND isDeducted = 0 ORDER BY advanceDate ASC")
    fun getUndeductedAdvancesByDriver(driverId: Long): Flow<List<AdvanceEntity>>
    
    @Query("SELECT * FROM advances WHERE driverId = :driverId AND isDeducted = 0 ORDER BY advanceDate ASC")
    suspend fun getUndeductedAdvancesByDriverSync(driverId: Long): List<AdvanceEntity>

    /**
     * Get total outstanding advance balance for a driver
     * Per Section 7.3: Balance can be zero, never negative
     */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM advances WHERE driverId = :driverId AND isDeducted = 0")
    suspend fun getOutstandingBalanceByDriver(driverId: Long): Double

    /**
     * Get advances given in a date range
     */
    @Query("SELECT * FROM advances WHERE advanceDate BETWEEN :startDate AND :endDate ORDER BY advanceDate DESC")
    fun getAdvancesByDateRange(startDate: Long, endDate: Long): Flow<List<AdvanceEntity>>

    @Query("SELECT * FROM advances WHERE id = :id")
    suspend fun getAdvanceById(id: Long): AdvanceEntity?
    
    @Query("SELECT * FROM advances WHERE firestoreId = :fid")
    suspend fun getAdvanceByFirestoreId(fid: String): AdvanceEntity?
    
    /**
     * Find entry by logical key for deduplication
     * Used to detect orphan entries that haven't synced their firestoreId yet
     */
    @Query("SELECT * FROM advances WHERE driverId = :driverId AND advanceDate = :date AND amount = :amount LIMIT 1")
    suspend fun getAdvanceByLogicalKey(driverId: Long, date: Long, amount: Double): AdvanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(advance: AdvanceEntity): Long

    @Update
    suspend fun update(advance: AdvanceEntity)

    @Query("UPDATE advances SET isDeducted = 1 WHERE id = :id")
    suspend fun markAsDeducted(id: Long)
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch mark advances as deducted
     */
    @Query("UPDATE advances SET isDeducted = 1 WHERE id IN (:advanceIds)")
    suspend fun markMultipleAsDeducted(advanceIds: List<Long>)
}
