package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AuditLog operations
 * Implements Section 11 of BUSINESS_LOGIC_SPEC.md
 * 
 * Transaction Safety (Section 11.2 & 13):
 * - Audit logs MUST be created atomically with the action being audited
 * - No override without audit trail
 * - Audit logs are append-only (no updates/deletes except cleanup)
 * 
 * Note: For atomic audit + action, use TransactionManager
 */
@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType ORDER BY createdAt DESC")
    fun getLogsByEntityType(entityType: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC")
    fun getLogsForEntity(entityType: String, entityId: Long): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getLogsByDateRange(startDate: Long, endDate: Long): Flow<List<AuditLogEntity>>
    
    @Query("SELECT COUNT(*) FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun getLogCountForEntity(entityType: String, entityId: Long): Int

    /**
     * Insert audit log - Should be called in same transaction as the audited action
     * Per Section 11.2: No override without audit
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity): Long

    @Query("DELETE FROM audit_logs WHERE createdAt < :timestamp")
    suspend fun deleteOldLogs(timestamp: Long)
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch insert audit logs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>): List<Long>
}
