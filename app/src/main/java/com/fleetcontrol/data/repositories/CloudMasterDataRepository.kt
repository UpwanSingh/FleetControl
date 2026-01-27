package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.entities.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await



open class CloudMasterDataRepository {

    private val db = FirebaseFirestore.getInstance()
    
    /**
     * Owner ID Flow for Reactive Sync
     */
    private val _ownerId = kotlinx.coroutines.flow.MutableStateFlow("")
    open val ownerId = _ownerId.asStateFlow()

    /**
     * Current owner's Firebase UID for multi-tenancy.
     * MUST be set before any operations.
     * All queries will be filtered by this ownerId.
     * All writes will include this ownerId.
     */
    open var currentOwnerId: String = ""
        // private set
    
    // Tenant ID is the same as Owner ID for now (owner creates their own tenant)
    val currentTenantId: String
        get() = currentOwnerId

    fun setOwnerId(id: String) {
        _ownerId.value = id
        currentOwnerId = id
    }

    // Tenant-based collection references
    // All data is under /tenants/{tenantId}/...
    private fun tenantsRef() = db.collection("tenants")
    private fun tenantDoc() = tenantsRef().document(currentTenantId)
    
    private fun driversRef() = tenantDoc().collection("drivers")
    private fun companiesRef() = tenantDoc().collection("companies")
    private fun clientsRef() = tenantDoc().collection("clients")
    private fun locationsRef() = tenantDoc().collection("locations")
    private fun rateSlabsRef() = tenantDoc().collection("rate_slabs")
    private fun distancesRef() = tenantDoc().collection("pickup_client_distances")
    private fun fuelRef() = tenantDoc().collection("fuel")
    private fun advancesRef() = tenantDoc().collection("advances")

    // ========================================
    // DRIVERS
    // ========================================
    
    /**
     * Find or Create driver - prevents duplicates by checking name first
     * Multi-Tenancy: Filters by currentOwnerId
     */
    suspend fun addDriver(driver: FirestoreDriver): String {
        val driverWithOwner = driver.copy(ownerId = currentOwnerId)
        
        // If ID already provided, use it directly (update existing)
        if (driver.id.isNotEmpty()) {
            val doc = driversRef().document(driver.id)
            doc.set(driverWithOwner).await()
            return doc.id
        }
        
        // Check if driver with same name already exists FOR THIS OWNER
        val existing = driversRef()
            
            .whereEqualTo("name", driver.name)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        
        if (existing != null) {
            // Return existing ID, don't create duplicate
            return existing.id
        }
        
        // Create new driver
        val doc = driversRef().document()
        val driverWithId = driverWithOwner.copy(id = doc.id)
        doc.set(driverWithId).await()
        return doc.id
    }

    /**
     * Get all drivers for current owner (real-time)
     */
    fun getDriversFlow(): Flow<List<FirestoreDriver>> = callbackFlow {
        val listener = driversRef()
            
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val drivers = snapshot?.toObjects(FirestoreDriver::class.java) ?: emptyList()
                trySend(drivers)
            }
        awaitClose { listener.remove() }
    }
    
    /**
     * Fetch a specific driver by Firestore document ID
     * Used for explicit driver fetch during join flow
     */
    suspend fun getDriverByFirestoreId(firestoreId: String): FirestoreDriver? {
        return try {
            val doc = driversRef().document(firestoreId).get().await()
            doc.toObject(FirestoreDriver::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error fetching driver: ${e.message}")
            null
        }
    }

    // ========================================
    // COMPANIES
    // ========================================

    /**
     * Find or Create company - prevents duplicates by checking name first
     * Multi-Tenancy: Filters by currentOwnerId
     */
    suspend fun addCompany(company: FirestoreCompany): String {
        val companyWithOwner = company.copy(ownerId = currentOwnerId)
        
        if (company.id.isNotEmpty()) {
            val doc = companiesRef().document(company.id)
            doc.set(companyWithOwner).await()
            return doc.id
        }
        
        val existing = companiesRef()
            
            .whereEqualTo("name", company.name)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        
        if (existing != null) {
            return existing.id
        }
        
        val doc = companiesRef().document()
        val companyWithId = companyWithOwner.copy(id = doc.id)
        doc.set(companyWithId).await()
        return doc.id
    }

    fun getCompaniesFlow(): Flow<List<FirestoreCompany>> = callbackFlow {
        val listener = companiesRef()
            
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val companies = snapshot?.toObjects(FirestoreCompany::class.java) ?: emptyList()
                trySend(companies)
            }
        awaitClose { listener.remove() }
    }

    // ========================================
    // CLIENTS
    // ========================================

    /**
     * Find or Create client - prevents duplicates by checking name first
     * Multi-Tenancy: Filters by currentOwnerId
     */
    suspend fun addClient(client: FirestoreClient): String {
        val clientWithOwner = client.copy(ownerId = currentOwnerId)
        
        if (client.id.isNotEmpty()) {
            val doc = clientsRef().document(client.id)
            doc.set(clientWithOwner).await()
            return doc.id
        }
        
        val existing = clientsRef()
            
            .whereEqualTo("name", client.name)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        
        if (existing != null) {
            return existing.id
        }
        
        val doc = clientsRef().document()
        val clientWithId = clientWithOwner.copy(id = doc.id)
        doc.set(clientWithId).await()
        return doc.id
    }

    fun getClientsFlow(): Flow<List<FirestoreClient>> = callbackFlow {
        val listener = clientsRef()
            
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val clients = snapshot?.toObjects(FirestoreClient::class.java) ?: emptyList()
                trySend(clients)
            }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // LOCATIONS (PICKUPS)
    // ========================================
    
    /**
     * Find or Create location - prevents duplicates by checking name first
     * Multi-Tenancy: Filters by currentOwnerId
     */
    suspend fun addLocation(location: FirestoreLocation): String {
        val locWithOwner = location.copy(ownerId = currentOwnerId)
        
        if (location.id.isNotEmpty()) {
            val doc = locationsRef().document(location.id)
            doc.set(locWithOwner).await()
            return doc.id
        }
        
        val existing = locationsRef()
            
            .whereEqualTo("name", location.name)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        
        if (existing != null) {
            return existing.id
        }
        
        val doc = locationsRef().document()
        val locWithId = locWithOwner.copy(id = doc.id)
        doc.set(locWithId).await()
        return doc.id
    }
    
    fun getLocationsFlow(): Flow<List<FirestoreLocation>> = callbackFlow {
        val listener = locationsRef()
            
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val locs = snapshot?.toObjects(FirestoreLocation::class.java) ?: emptyList()
                trySend(locs)
            }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // RATE SLABS
    // ========================================
    
    suspend fun addRateSlab(slab: FirestoreDriverRateSlab): String {
        val slabWithOwner = slab.copy(ownerId = currentOwnerId)
        val doc = if (slab.id.isEmpty()) rateSlabsRef().document() else rateSlabsRef().document(slab.id)
        val slabWithId = slabWithOwner.copy(id = doc.id)
        doc.set(slabWithId).await()
        return doc.id
    }
    
    fun getRateSlabsFlow(): Flow<List<FirestoreDriverRateSlab>> = callbackFlow {
        val listener = rateSlabsRef()
            
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(FirestoreDriverRateSlab::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // PICKUP CLIENT DISTANCES
    // ========================================
    
    suspend fun addDistance(dist: FirestorePickupClientDistance): String {
        val distWithOwner = dist.copy(ownerId = currentOwnerId)
        val doc = if (dist.id.isEmpty()) distancesRef().document() else distancesRef().document(dist.id)
        val distWithId = distWithOwner.copy(id = doc.id)
        doc.set(distWithId).await()
        return doc.id
    }
    
    fun getDistancesFlow(): Flow<List<FirestorePickupClientDistance>> = callbackFlow {
        val listener = distancesRef()
            
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(FirestorePickupClientDistance::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // FUEL
    // ========================================
    
    suspend fun addFuel(fuel: FirestoreFuel): String {
        val fuelWithOwner = fuel.copy(ownerId = currentOwnerId)
        
        if (fuel.id.isNotEmpty()) {
            val doc = fuelRef().document(fuel.id)
            doc.set(fuelWithOwner).await()
            return doc.id
        }
        
        val doc = fuelRef().document()
        val fuelWithId = fuelWithOwner.copy(id = doc.id)
        doc.set(fuelWithId).await()
        return doc.id
    }
    
    fun getFuelFlow(driverId: String? = null): Flow<List<FirestoreFuel>> = callbackFlow {
        var query: com.google.firebase.firestore.Query = fuelRef()
        if (driverId != null) {
            query = query.whereEqualTo("driverId", driverId)
        }
        
        val listener = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(FirestoreFuel::class.java) ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // ADVANCES
    // ========================================
    
    suspend fun addAdvance(advance: FirestoreAdvance): String {
        val advWithOwner = advance.copy(ownerId = currentOwnerId)
        
        if (advance.id.isNotEmpty()) {
            val doc = advancesRef().document(advance.id)
            doc.set(advWithOwner).await()
            return doc.id
        }
        
        val doc = advancesRef().document()
        val advWithId = advWithOwner.copy(id = doc.id)
        doc.set(advWithId).await()
        return doc.id
    }
    
    fun getAdvancesFlow(driverId: String? = null): Flow<List<FirestoreAdvance>> = callbackFlow {
        var query: com.google.firebase.firestore.Query = advancesRef()
        if (driverId != null) {
            query = query.whereEqualTo("driverId", driverId)
        }
        
        val listener = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(FirestoreAdvance::class.java) ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // EXPLICIT FETCH METHODS FOR DRIVER JOIN
    // ========================================
    
    /**
     * Fetch all companies for current owner (one-time, not reactive)
     */
    suspend fun getAllCompaniesNow(): List<FirestoreCompany> {
        return try {
            companiesRef()
                .get().await()
                .toObjects(FirestoreCompany::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error fetching companies: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Fetch all clients for current owner (one-time, not reactive)
     */
    suspend fun getAllClientsNow(): List<FirestoreClient> {
        return try {
            clientsRef()
                .get().await()
                .toObjects(FirestoreClient::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error fetching clients: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Fetch all pickup locations for current owner (one-time, not reactive)
     */
    suspend fun getAllLocationsNow(): List<FirestoreLocation> {
        return try {
            locationsRef()
                .get().await()
                .toObjects(FirestoreLocation::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error fetching locations: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Fetch all rate slabs for current owner (one-time, not reactive)
     */
    suspend fun getAllRateSlabsNow(): List<FirestoreDriverRateSlab> {
        return try {
            rateSlabsRef()
                .get().await()
                .toObjects(FirestoreDriverRateSlab::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error fetching rate slabs: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Fetch all drivers for current owner (one-time, not reactive)
     */
    suspend fun getAllDriversNow(): List<FirestoreDriver> {
        return try {
            driversRef()
                .get().await()
                .toObjects(FirestoreDriver::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error fetching drivers: ${e.message}")
            emptyList()
        }
    }
    
    // ========================================
    // FUEL REQUESTS (Security Hardening)
    // Drivers create requests, Owners approve
    // ========================================
    
    private fun fuelRequestsRef() = tenantDoc().collection("fuelRequests")
    
    /**
     * Create a fuel request (Driver submits, status=PENDING)
     */
    suspend fun createFuelRequest(request: FirestoreFuelRequest): String {
        val requestWithOwner = request.copy(ownerId = currentOwnerId)
        val doc = fuelRequestsRef().document()
        val requestWithId = requestWithOwner.copy(id = doc.id)
        doc.set(requestWithId).await()
        return doc.id
    }
    
    /**
     * Get all pending fuel requests (for Owner dashboard)
     */
    fun getPendingFuelRequestsFlow(): Flow<List<FirestoreFuelRequest>> = callbackFlow {
        val listener = fuelRequestsRef()
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val requests = snapshot?.toObjects(FirestoreFuelRequest::class.java) ?: emptyList()
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }
    
    /**
     * Get all fuel requests (for history)
     */
    fun getAllFuelRequestsFlow(): Flow<List<FirestoreFuelRequest>> = callbackFlow {
        val listener = fuelRequestsRef()
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val requests = snapshot?.toObjects(FirestoreFuelRequest::class.java) ?: emptyList()
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }
    
    /**
     * Approve a fuel request (Owner only)
     * - Updates request status to APPROVED
     * - Creates actual fuel entry
     */
    suspend fun approveFuelRequest(requestId: String): Boolean {
        return try {
            val requestDoc = fuelRequestsRef().document(requestId)
            val request = requestDoc.get().await().toObject(FirestoreFuelRequest::class.java)
                ?: return false
            
            // Update request status
            requestDoc.update(mapOf(
                "status" to "APPROVED",
                "reviewedAt" to System.currentTimeMillis(),
                "reviewedBy" to currentOwnerId
            )).await()
            
            // Create actual fuel entry
            val fuel = FirestoreFuel(
                driverId = request.driverId,
                amount = request.amount,
                date = request.date,
                notes = request.notes
            )
            addFuel(fuel)
            
            true
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error approving fuel request: ${e.message}")
            false
        }
    }
    
    /**
     * Reject a fuel request (Owner only)
     */
    suspend fun rejectFuelRequest(requestId: String, reason: String = ""): Boolean {
        return try {
            fuelRequestsRef().document(requestId).update(mapOf(
                "status" to "REJECTED",
                "reviewedAt" to System.currentTimeMillis(),
                "reviewedBy" to currentOwnerId,
                "notes" to reason
            )).await()
            true
        } catch (e: Exception) {
            android.util.Log.e("CloudMasterDataRepo", "Error rejecting fuel request: ${e.message}")
            false
        }
    }
}
