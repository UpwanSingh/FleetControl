package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.ClientEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Client operations
 * 
 * Transaction Safety (Section 13):
 * - Client operations are ACID-compliant
 * - Bulk operations are atomic
 */
@Dao
interface ClientDao {
    
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<ClientEntity>>
    
    @Query("SELECT * FROM clients WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveClients(): Flow<List<ClientEntity>>
    
    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: Long): ClientEntity?

    @Query("SELECT * FROM clients WHERE firestoreId = :fid LIMIT 1")
    suspend fun getClientByFirestoreId(fid: String): ClientEntity?
    
    /**
     * Find client by name for deduplication (catches orphan entries)
     */
    @Query("SELECT * FROM clients WHERE name = :name LIMIT 1")
    suspend fun getClientByName(name: String): ClientEntity?
    
    @Query("SELECT * FROM clients WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchClients(query: String): Flow<List<ClientEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity): Long

    @Update
    suspend fun update(client: ClientEntity)

    @Delete
    suspend fun delete(client: ClientEntity)
    
    @Query("UPDATE clients SET isActive = :isActive WHERE id = :id")
    suspend fun setClientActive(id: Long, isActive: Boolean)
    
    // ========================================
    // TRANSACTION OPERATIONS
    // Per Section 13: ACID-compliant bulk operations
    // ========================================
    
    /**
     * Batch insert clients - ATOMIC TRANSACTION
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(clients: List<ClientEntity>): List<Long>
    
    /**
     * Get count of active clients
     */
    @Query("SELECT COUNT(*) FROM clients WHERE isActive = 1")
    suspend fun getActiveClientCount(): Int
    
    /**
     * Get all clients as one-shot list (not Flow)
     */
    @Query("SELECT * FROM clients ORDER BY name ASC")
    suspend fun getAllClientsOnce(): List<ClientEntity>
}
