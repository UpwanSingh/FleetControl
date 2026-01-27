package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.ClientDao
import com.fleetcontrol.data.entities.ClientEntity
import com.fleetcontrol.data.entities.FirestoreClient
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for Client operations
 * Synchronizes with Firestore (Master Data)
 */
class ClientRepository(
    private val clientDao: ClientDao,
    private val cloudRepo: CloudMasterDataRepository
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        val clientsFlow: Flow<List<FirestoreClient>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreClient>())
            else cloudRepo.getClientsFlow()
        }
        
        clientsFlow.onEach { cloudClients ->
            processCloudClients(cloudClients)
        }
        .catch { e: Throwable ->
            Log.e("ClientRepository", "Error syncing client", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudClients(cloudClients: List<FirestoreClient>) {
        cloudClients.forEach { fClient ->
            // DEDUP STEP 1: Check by Firestore ID
            val localByFirestoreId = clientDao.getClientByFirestoreId(fClient.id)
            
            if (localByFirestoreId != null) {
                // Already synced - update if needed
                val updated = localByFirestoreId.copy(
                    name = fClient.name,
                    address = fClient.address,
                    contactPerson = fClient.contactPerson,
                    contactPhone = fClient.contactPhone,
                    notes = fClient.notes,
                    isActive = fClient.isActive
                )
                clientDao.update(updated)
            } else {
                // DEDUP STEP 2: Check by logical key (name)
                val localByName = clientDao.getClientByName(fClient.name)
                
                if (localByName != null) {
                    // Orphan entry found - link it to cloud ID
                    clientDao.update(localByName.copy(
                        firestoreId = fClient.id,
                        address = fClient.address,
                        contactPerson = fClient.contactPerson,
                        contactPhone = fClient.contactPhone,
                        notes = fClient.notes,
                        isActive = fClient.isActive
                    ))
                } else {
                    // Truly new entry - insert
                    clientDao.insert(ClientEntity(
                        firestoreId = fClient.id,
                        name = fClient.name,
                        address = fClient.address,
                        contactPerson = fClient.contactPerson,
                        contactPhone = fClient.contactPhone,
                        notes = fClient.notes,
                        isActive = fClient.isActive
                    ))
                }
            }
        }
    }
    
    /**
     * Get ALL clients (Raw access for Migration/Admin)
     */
    fun getAllClientsRaw(): Flow<List<ClientEntity>> = clientDao.getAllClients()

    fun getAllClients(): Flow<List<ClientEntity>> = clientDao.getAllClients()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getActiveClients(): Flow<List<ClientEntity>> = clientDao.getActiveClients()
        .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    suspend fun getClientById(id: Long): ClientEntity? = clientDao.getClientById(id)
    
    fun searchClients(query: String): Flow<List<ClientEntity>> = clientDao.searchClients(query)
    
    suspend fun getClientByFirestoreId(firestoreId: String): ClientEntity? = 
        clientDao.getClientByFirestoreId(firestoreId)
    
    /**
     * Insert client locally only (no cloud push).
     * Used for driver sync when driver joins.
     */
    suspend fun insertRaw(client: ClientEntity): Long {
        return clientDao.insert(client)
    }
    
    suspend fun insert(client: ClientEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val clientWithOwner = client.copy(ownerId = cloudRepo.currentOwnerId)
        val id = clientDao.insert(clientWithOwner)
        
        // Push to Cloud with retry
        scope.launch {
            val fClient = FirestoreClient(
                id = clientWithOwner.firestoreId ?: "",
                name = clientWithOwner.name,
                address = clientWithOwner.address ?: "",
                contactPerson = clientWithOwner.contactPerson ?: "",
                contactPhone = clientWithOwner.contactPhone ?: "",
                notes = clientWithOwner.notes ?: "",
                isActive = clientWithOwner.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "addClient",
                operation = { cloudRepo.addClient(fClient) }
            )
            
            if (cloudId != null && clientWithOwner.firestoreId == null) {
                val inserted = clientDao.getClientById(id)
                if (inserted != null) {
                    clientDao.update(inserted.copy(firestoreId = cloudId))
                }
            }
        }
        return id
    }
    
    suspend fun update(client: ClientEntity) {
        clientDao.update(client)
        
        // Push to Cloud with retry
        scope.launch {
            val fClient = FirestoreClient(
                id = client.firestoreId ?: "",
                name = client.name,
                address = client.address ?: "",
                contactPerson = client.contactPerson ?: "",
                contactPhone = client.contactPhone ?: "",
                notes = client.notes ?: "",
                isActive = client.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "updateClient",
                operation = { cloudRepo.addClient(fClient) }
            )
            
            if (cloudId != null && client.firestoreId == null) {
                val current = clientDao.getClientById(client.id)
                if (current != null) {
                    clientDao.update(current.copy(firestoreId = cloudId))
                }
            }
        }
    }
    
    suspend fun delete(client: ClientEntity) = clientDao.delete(client)
    
    suspend fun setClientActive(id: Long, isActive: Boolean) {
        clientDao.setClientActive(id, isActive)
        
        scope.launch {
            val client = clientDao.getClientById(id)
            if (client?.firestoreId != null) {
                update(client.copy(isActive = isActive))
            }
        }
    }
    
    suspend fun insertAll(clients: List<ClientEntity>): List<Long> = clientDao.insertAll(clients)
    
    suspend fun getActiveClientCount(): Int = clientDao.getActiveClientCount()
    
    suspend fun getAllClientsOnce(): List<ClientEntity> = clientDao.getAllClientsOnce()
    
    /**
     * Insert or update client (for import)
     */
    suspend fun upsert(client: ClientEntity) {
        val existing = clientDao.getClientById(client.id)
        if (existing != null) {
            update(client)
        } else {
            insert(client)
        }
    }
    
    suspend fun getClientName(id: Long): String? {
        return clientDao.getClientById(id)?.name
    }
}
