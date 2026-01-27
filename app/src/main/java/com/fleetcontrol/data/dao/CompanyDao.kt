package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.CompanyEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Company operations
 * 
 * Transaction Safety (Section 13):
 * - Company operations are ACID-compliant
 * - Bulk operations are atomic
 * - Soft deletes preserve referential integrity with trips
 */
@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun getAllCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getCompanyById(id: Long): CompanyEntity?

    @Query("SELECT * FROM companies WHERE firestoreId = :fid LIMIT 1")
    suspend fun getCompanyByFirestoreId(fid: String): CompanyEntity?
    
    /**
     * Find company by name for deduplication (catches orphan entries)
     */
    @Query("SELECT * FROM companies WHERE name = :name LIMIT 1")
    suspend fun getCompanyByName(name: String): CompanyEntity?

    @Query("SELECT * FROM companies WHERE isActive = 1 LIMIT 1")
    suspend fun getCompanyOnce(): CompanyEntity?
    
    @Query("SELECT * FROM companies WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllCompaniesOnce(): List<CompanyEntity>

    @Query("SELECT * FROM companies WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCompanies(query: String): Flow<List<CompanyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(company: CompanyEntity): Long

    @Update
    suspend fun update(company: CompanyEntity)

    @Delete
    suspend fun delete(company: CompanyEntity)

    @Query("UPDATE companies SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
    
    // ========================================
    // BATCH OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // Note: Complex multi-DAO transactions should use TransactionManager
    // ========================================
    
    /**
     * Batch insert companies - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(companies: List<CompanyEntity>): List<Long>
}
