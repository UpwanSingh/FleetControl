package com.fleetcontrol.data.repositories

import android.util.Log
import com.fleetcontrol.data.entities.FirestoreFuel
import com.fleetcontrol.data.entities.FirestoreFuelRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CloudFuelRepository
 * Handles the Fuel Request Workflow with Multi-Tenant Support
 * 
 * Multi-Tenancy: Uses /tenants/{ownerId}/fuelRequests and /tenants/{ownerId}/fuel
 * 
 * Flow:
 * 1. Driver creates request (status=PENDING)
 * 2. Owner sees pending requests
 * 3. Owner approves -> Creates actual Fuel Entry & updates Request status
 * 4. Owner rejects -> Updates Request status
 */
@Singleton
class CloudFuelRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val TAG = "CloudFuelRepo"
    
    // Multi-Tenancy: Explicit ownerId management (same pattern as CloudTripRepository)
    private val _ownerId = MutableStateFlow("")
    val ownerId: StateFlow<String> = _ownerId.asStateFlow()
    
    val currentOwnerId: String
        get() = _ownerId.value.ifEmpty { auth.currentUser?.uid ?: "" }
    
    /**
     * Multi-Tenancy: Set ownerId from AppContainer.syncOwnerId()
     */
    fun setOwnerId(id: String) {
        _ownerId.value = id
        Log.d(TAG, "OwnerId set to: $id")
    }
    
    // Tenant-based collection references
    private fun fuelRequestsRef() = firestore.collection("tenants")
        .document(currentOwnerId)
        .collection("fuelRequests")
    
    private fun fuelRef() = firestore.collection("tenants")
        .document(currentOwnerId)
        .collection("fuel")

    /**
     * Driver: Create a new fuel request
     */
    suspend fun createFuelRequest(request: FirestoreFuelRequest): Boolean {
        if (currentOwnerId.isEmpty()) {
            Log.e(TAG, "Cannot create fuel request: No ownerId set")
            return false
        }
        
        return try {
            val docRef = fuelRequestsRef().document()
            val newRequest = request.copy(
                id = docRef.id,
                ownerId = currentOwnerId  // Ensure ownerId is set
            )
            docRef.set(newRequest).await()
            Log.d(TAG, "Fuel request created: ${docRef.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fuel request", e)
            false
        }
    }

    /**
     * Owner: Get flow of PENDING fuel requests
     */
    fun getPendingFuelRequestsFlow(): Flow<List<FirestoreFuelRequest>> = callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = fuelRequestsRef()
            .whereEqualTo("status", "PENDING")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to requests. MISSING INDEX? Check log url.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requests = snapshot.toObjects(FirestoreFuelRequest::class.java)
                    trySend(requests)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Owner: Approve a request
     * - Creates the actual FirestoreFuel record
     * - Updates Request status to APPROVED
     */
    suspend fun approveFuelRequest(request: FirestoreFuelRequest): Boolean {
        if (currentOwnerId.isEmpty()) {
            Log.e(TAG, "Cannot approve: No ownerId set")
            return false
        }
        
        return try {
            firestore.runTransaction { transaction ->
                val requestRef = fuelRequestsRef().document(request.id)
                
                // 1. Create actual Fuel Entry
                val newFuelRef = fuelRef().document()
                val fuelEntry = FirestoreFuel(
                    id = newFuelRef.id,
                    ownerId = currentOwnerId,
                    driverId = request.driverId,
                    vehicleId = "",
                    amount = request.amount,
                    date = request.date,
                    notes = request.notes
                )
                
                transaction.set(newFuelRef, fuelEntry)
                
                // 2. Update Request Status
                transaction.update(requestRef, 
                    mapOf(
                        "status" to "APPROVED",
                        "reviewedAt" to System.currentTimeMillis(),
                        "reviewedBy" to currentOwnerId
                    )
                )
            }.await()
            Log.d(TAG, "Fuel request approved: ${request.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error approving request", e)
            false
        }
    }

    /**
     * Owner: Reject a request
     */
    suspend fun rejectFuelRequest(requestId: String, reason: String = ""): Boolean {
        if (currentOwnerId.isEmpty()) {
            Log.e(TAG, "Cannot reject: No ownerId set")
            return false
        }
        
        return try {
            val requestRef = fuelRequestsRef().document(requestId)
            requestRef.update(
                mapOf(
                    "status" to "REJECTED",
                    "reviewedAt" to System.currentTimeMillis(),
                    "reviewedBy" to currentOwnerId,
                    "rejectionReason" to reason
                )
            ).await()
            Log.d(TAG, "Fuel request rejected: $requestId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting request", e)
            false
        }
    }
}
