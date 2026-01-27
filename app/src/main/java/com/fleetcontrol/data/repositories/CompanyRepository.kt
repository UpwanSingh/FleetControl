package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.dao.CompanyDao
import com.fleetcontrol.data.entities.CompanyEntity
import com.fleetcontrol.data.entities.FirestoreCompany
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for Company data operations
 * Implements Section 11 of BUSINESS_LOGIC_SPEC.md with Sync
 */
open class CompanyRepository(
    private val companyDao: CompanyDao,
    private val cloudRepo: CloudMasterDataRepository
) {
    
    // Sync Scope
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start Listening for Cloud Updates
        val companiesFlow: Flow<List<FirestoreCompany>> = cloudRepo.ownerId.flatMapLatest { ownerId: String ->
            if (ownerId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<FirestoreCompany>())
            else cloudRepo.getCompaniesFlow()
        }
        
        companiesFlow.onEach { cloudCompanies ->
            processCloudCompanies(cloudCompanies)
        }
        .catch { e: Throwable ->
            Log.e("CompanyRepository", "Error syncing company", e)
        }
        .launchIn(scope)
    }
    
    private suspend fun processCloudCompanies(cloudCompanies: List<FirestoreCompany>) {
        cloudCompanies.forEach { fComp ->
            // DEDUP STEP 1: Check by Firestore ID
            val localByFirestoreId = companyDao.getCompanyByFirestoreId(fComp.id)
            
            if (localByFirestoreId != null) {
                // Already synced - update if needed
                val updated = localByFirestoreId.copy(
                    name = fComp.name,
                    contactPerson = fComp.contactPerson,
                    contactPhone = fComp.contactPhone,
                    perBagRate = fComp.perBagRate,
                    isActive = fComp.isActive
                )
                companyDao.update(updated)
            } else {
                // DEDUP STEP 2: Check by logical key (name)
                val localByName = companyDao.getCompanyByName(fComp.name)
                
                if (localByName != null) {
                    // Orphan entry found - link it to cloud ID
                    companyDao.update(localByName.copy(
                        firestoreId = fComp.id,
                        contactPerson = fComp.contactPerson,
                        contactPhone = fComp.contactPhone,
                        perBagRate = fComp.perBagRate,
                        isActive = fComp.isActive
                    ))
                } else {
                    // Truly new entry - insert
                    companyDao.insert(CompanyEntity(
                        firestoreId = fComp.id,
                        name = fComp.name,
                        contactPerson = fComp.contactPerson,
                        contactPhone = fComp.contactPhone,
                        perBagRate = fComp.perBagRate,
                        isActive = fComp.isActive
                    ))
                }
            }
        }
    }
    
    /**
     * Get ALL companies (Raw access for Migration/Admin)
     */
    fun getAllCompaniesRaw(): Flow<List<CompanyEntity>> = companyDao.getAllCompanies()

    fun getAllActiveCompanies(): Flow<List<CompanyEntity>> = 
        companyDao.getAllActiveCompanies()
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    fun getAllCompanies(): Flow<List<CompanyEntity>> = 
        companyDao.getAllCompanies()
            .map { list -> list.filter { it.ownerId == cloudRepo.currentOwnerId } }
    
    suspend fun getCompanyById(id: Long): CompanyEntity? = 
        companyDao.getCompanyById(id)
    
    suspend fun getCompanyByFirestoreId(firestoreId: String): CompanyEntity? = 
        companyDao.getCompanyByFirestoreId(firestoreId)
    
    /**
     * Insert company locally only (no cloud push).
     * Used for driver sync when driver joins.
     */
    suspend fun insertRaw(company: CompanyEntity): Long {
        return companyDao.insert(company)
    }
    
    suspend fun getCompanyOnce(): CompanyEntity? =
        companyDao.getCompanyOnce()
    
    suspend fun getAllCompaniesOnce(): List<CompanyEntity> =
        companyDao.getAllCompaniesOnce()
    
    fun searchCompanies(query: String): Flow<List<CompanyEntity>> =
        companyDao.searchCompanies(query)
    
    suspend fun insert(company: CompanyEntity): Long {
        // Multi-Tenancy: Set ownerId from cloud repository
        val companyWithOwner = company.copy(ownerId = cloudRepo.currentOwnerId)
        val id = companyDao.insert(companyWithOwner)
        
        // Push to Cloud with retry
        scope.launch {
            val fComp = FirestoreCompany(
                id = companyWithOwner.firestoreId ?: "",
                name = companyWithOwner.name,
                contactPerson = companyWithOwner.contactPerson ?: "",
                contactPhone = companyWithOwner.contactPhone ?: "",
                perBagRate = companyWithOwner.perBagRate,
                isActive = companyWithOwner.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "addCompany",
                operation = { cloudRepo.addCompany(fComp) }
            )
            
            if (cloudId != null && companyWithOwner.firestoreId == null) {
                val inserted = companyDao.getCompanyById(id)
                if (inserted != null) {
                    companyDao.update(inserted.copy(firestoreId = cloudId))
                    Log.d("CompanyRepository", "Company synced to cloud with ID: $cloudId")
                }
            }
        }
        return id
    }
    
    suspend fun update(company: CompanyEntity) {
        companyDao.update(company)
        
        // Push to Cloud with retry
        scope.launch {
            val fComp = FirestoreCompany(
                id = company.firestoreId ?: "",
                name = company.name,
                contactPerson = company.contactPerson ?: "",
                contactPhone = company.contactPhone ?: "",
                perBagRate = company.perBagRate,
                isActive = company.isActive
            )
            
            val cloudId = com.fleetcontrol.data.sync.CloudSyncHelper.pushWithRetry(
                operationName = "updateCompany",
                operation = { cloudRepo.addCompany(fComp) }
            )
            
            if (cloudId != null && company.firestoreId == null) {
                val current = companyDao.getCompanyById(company.id)
                if (current != null) {
                    companyDao.update(current.copy(firestoreId = cloudId))
                }
            }
        }
    }
    
    suspend fun delete(company: CompanyEntity) = companyDao.delete(company)
    
    suspend fun deactivate(id: Long) {
        companyDao.deactivate(id)
        
        scope.launch {
            val comp = companyDao.getCompanyById(id)
            if (comp?.firestoreId != null) {
                update(comp.copy(isActive = false))
            }
        }
    }
    
    /**
     * Insert or update company (for import)
     */
    suspend fun upsert(company: CompanyEntity) {
        val existing = companyDao.getCompanyById(company.id)
        if (existing != null) {
            update(company)
        } else {
            insert(company)
        }
    }
    
    suspend fun getCompanyName(id: Long): String? {
        return companyDao.getCompanyById(id)?.name
    }
}
