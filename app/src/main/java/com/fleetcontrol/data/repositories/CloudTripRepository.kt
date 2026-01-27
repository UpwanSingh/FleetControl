package com.fleetcontrol.data.repositories

import com.fleetcontrol.data.entities.FirestoreTrip
import com.fleetcontrol.data.entities.DriverStats
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class CloudTripRepository @Inject constructor() {

    private val db = FirebaseFirestore.getInstance()
    
    /**
     * Owner ID Flow for Reactive Sync
     */
    /**
     * Owner ID Flow for Reactive Sync
     */
    private val _ownerId = kotlinx.coroutines.flow.MutableStateFlow("")
    open val ownerId = _ownerId.asStateFlow()

    /**
     * Current owner's Firebase UID for multi-tenancy.
     * MUST be set before any operations.
     */
    open var currentOwnerId: String = ""
        // private set // Removed private set to allow open modification for testing
    
    // Tenant ID is the same as Owner ID for now
    val currentTenantId: String
        get() = currentOwnerId

    fun setOwnerId(id: String) {
        _ownerId.value = id
        currentOwnerId = id
    }
    
    // Tenant-based collection references
    // Tenant-based collection references
    private fun tenantsRef() = db.collection("tenants")
    
    /**
     * Safe access to tenant document. 
     * Throws IllegalStateException if ownerId is not set, preventing cryptic Firestore errors.
     */
    private fun tenantDoc(): com.google.firebase.firestore.DocumentReference {
        if (currentOwnerId.isEmpty()) throw IllegalStateException("OwnerID not set in CloudTripRepository")
        return tenantsRef().document(currentTenantId)
    }
    
    private fun tripsRef() = tenantDoc().collection("trips")
    private fun statsRef() = tenantDoc().collection("stats")

    /**
     * CORE LOGIC: Add Trip to Firestore
     * 
     * SECURITY: Stats aggregation removed - now computed on read.
     * This prevents client-side poisoning of aggregated values.
     * Multi-Tenancy: Includes ownerId in trip.
     */
    suspend fun addTrip(trip: FirestoreTrip): String {
        val tripWithOwner = trip.copy(ownerId = currentOwnerId)
        
        // Use provided ID for idempotent sync, or generate if empty
        val tripId = if (trip.id.isNotEmpty()) trip.id else tripsRef().document().id
        val tripWithId = tripWithOwner.copy(id = tripId)
        
        // Simple write - no stats aggregation (compute on read)
        tripsRef().document(tripId).set(tripWithId).await()
        
        return tripId
    }
    
    /**
     * COMPUTE-ON-READ: Get Driver Monthly Stats
     * 
     * Instead of trusting pre-aggregated values, we recalculate
     * from raw trip data. More reads, but guaranteed accuracy.
     * SECURITY: Only counts APPROVED trips (owner-verified)
     */
    suspend fun computeDriverMonthlyStats(driverId: String, monthStart: Long, monthEnd: Long): DriverStats {
        val trips = tripsRef()
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", "APPROVED") // Only APPROVED trips!
            .whereGreaterThanOrEqualTo("timestamp", monthStart)
            .whereLessThan("timestamp", monthEnd)
            .get()
            .await()
            .toObjects(FirestoreTrip::class.java)
        
        // Recalculate from raw data to prevent client manipulation
        var totalTrips = 0L
        var totalBags = 0L
        var totalDriverEarnings = 0.0
        var totalOwnerGross = 0.0
        var totalLabourCost = 0.0
        var driverName = ""
        
        trips.forEach { t ->
            totalTrips++
            totalBags += t.bags
            // Recalculate from bags * rate (not trusting stored totalAmount)
            totalDriverEarnings += t.bags * t.rate
            totalOwnerGross += t.ownerGross
            totalLabourCost += t.labourCost
            if (driverName.isEmpty()) driverName = t.driverName
        }
        
        val cal = Calendar.getInstance()
        cal.timeInMillis = monthStart
        
        return DriverStats(
            docId = "${driverId}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH) + 1}",
            ownerId = currentOwnerId,
            driverId = driverId,
            driverName = driverName,
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            totalTrips = totalTrips,
            totalBags = totalBags,
            totalDriverEarnings = totalDriverEarnings,
            totalOwnerGross = totalOwnerGross,
            totalLabourCost = totalLabourCost,
            lastUpdated = Date()
        )
    }

    /**
     * Get Trips for a Driver (Live Flow)
     * Ordered by Timestamp Descending
     * Multi-Tenancy: Filters by currentOwnerId
     */
    fun getTripsByDriver(driverId: String): kotlinx.coroutines.flow.Flow<List<FirestoreTrip>> = kotlinx.coroutines.flow.callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        
        val listener = tripsRef()
            .whereEqualTo("driverId", driverId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CloudTripRepo", "Error fetching driver trips", e)
                    // Don't crash, just log. Most likely index error or permission.
                    return@addSnapshotListener
                }
                
                val trips = snapshot?.toObjects(FirestoreTrip::class.java) ?: emptyList()
                trySend(trips)
            }
            
        awaitClose { listener.remove() }
    }

    /**
     * Get Aggregated Stats for a specific Month
     * Reads ONE document instead of summing thousands.
     */
    suspend fun getDriverMonthlyStats(driverId: String, year: Int, month: Int): DriverStats? {
        val statsDocId = "${driverId}_${year}_${month}"
        return try {
            statsRef().document(statsDocId).get().await().toObject(DriverStats::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get Stream of ALL Driver Stats for a Month (Real-Time Owner Dashboard)
     * LISTENS to updates. Any driver adding a trip triggers this flow instantly.
     * Multi-Tenancy: Filters by currentOwnerId
     */
    fun getDriverStatsFlow(year: Int, month: Int): kotlinx.coroutines.flow.Flow<List<DriverStats>> = kotlinx.coroutines.flow.callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = statsRef()
            .whereEqualTo("year", year)
            .whereEqualTo("month", month)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CloudTripRepo", "Error fetching driver stats", e)
                    return@addSnapshotListener
                }
                val stats = snapshot?.toObjects(DriverStats::class.java) ?: emptyList()
                trySend(stats)
            }
            
        awaitClose { listener.remove() }
    }
    
    /**
     * Get Stream of Today's Trip Count (Global for Owner)
     * Queries trips where timestamp >= Start of Today
     * Multi-Tenancy: Filters by currentOwnerId
     */
    fun getTodayTripCountFlow(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(0)
            awaitClose { }
            return@callbackFlow
        }

        val startOfToday = com.fleetcontrol.utils.DateUtils.getStartOfToday()
        
        val listener = tripsRef()
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CloudTripRepo", "Error fetching today trip count", e)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
            
        awaitClose { listener.remove() }
    }
    
    /**
     * Get Stream of ALL Trips for Sync (Global for Owner)
     * Used by TripRepository to hydrate Local DB.
     * Ordered by Timestamp Descending.
     * Multi-Tenancy: Filters by currentOwnerId
     */
    fun getAllTripsFlow(): kotlinx.coroutines.flow.Flow<List<FirestoreTrip>> = kotlinx.coroutines.flow.callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = tripsRef()
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(2000)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CloudTripRepo", "Error fetching all trips", e)
                    return@addSnapshotListener
                }
                val trips = snapshot?.toObjects(FirestoreTrip::class.java) ?: emptyList()
                trySend(trips)
            }
        awaitClose { listener.remove() }
    }
    
    // ========================================
    // TRIP APPROVAL WORKFLOW (Security Hardening)
    // ========================================
    
    /**
     * Get all pending trips for owner approval
     */
    fun getPendingTripsFlow(): kotlinx.coroutines.flow.Flow<List<FirestoreTrip>> = kotlinx.coroutines.flow.callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = tripsRef()
            .whereEqualTo("status", "PENDING")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CloudTripRepo", "Error fetching pending trips. MISSING INDEX? Check log for link.", e)
                    return@addSnapshotListener
                }
                val trips = snapshot?.toObjects(FirestoreTrip::class.java) ?: emptyList()
                trySend(trips)
            }
        awaitClose { listener.remove() }
    }
    
    /**
     * Get count of pending trips (for badges/notifications)
     */
    fun getPendingTripsCountFlow(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.callbackFlow {
        if (currentOwnerId.isEmpty()) {
            trySend(0)
            awaitClose { }
            return@callbackFlow
        }
        val listener = tripsRef()
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                     android.util.Log.e("CloudTripRepo", "Error fetching pending count", e)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { listener.remove() }
    }
    
    /**
     * Approve a pending trip (Owner/Manager only)
     * Updates status to APPROVED and increments version for conflict detection
     */
    suspend fun approveTrip(tripId: String): Boolean {
        return try {
            // Fetch current version for conflict detection
            val tripDoc = tripsRef().document(tripId).get().await()
            val currentVersion = tripDoc.getLong("version") ?: 1L
            
            tripsRef().document(tripId).update(mapOf(
                "status" to "APPROVED",
                "modifiedAt" to System.currentTimeMillis(),
                "modifiedBy" to currentOwnerId,
                "version" to currentVersion + 1  // Increment version
            )).await()
            true
        } catch (e: Exception) {
            android.util.Log.e("CloudTripRepo", "Error approving trip: ${e.message}")
            false
        }
    }
    
    /**
     * Reject a pending trip (Owner/Manager only)
     * Updates status to REJECTED and increments version for conflict detection
     */
    suspend fun rejectTrip(tripId: String, reason: String = ""): Boolean {
        return try {
            // Fetch current version for conflict detection
            val tripDoc = tripsRef().document(tripId).get().await()
            val currentVersion = tripDoc.getLong("version") ?: 1L
            
            val updates = mutableMapOf<String, Any>(
                "status" to "REJECTED",
                "modifiedAt" to System.currentTimeMillis(),
                "modifiedBy" to currentOwnerId,
                "version" to currentVersion + 1  // Increment version
            )
            if (reason.isNotEmpty()) {
                updates["rejectionReason"] = reason
            }
            tripsRef().document(tripId).update(updates).await()
            true
        } catch (e: Exception) {
            android.util.Log.e("CloudTripRepo", "Error rejecting trip: ${e.message}")
            false
        }
    }
    
    /**
     * Get only APPROVED trips for stats calculation
     * Part of compute-on-read security hardening
     */
    suspend fun getApprovedTripsForStats(driverId: String, monthStart: Long, monthEnd: Long): List<FirestoreTrip> {
        return try {
            tripsRef()
                .whereEqualTo("driverId", driverId)
                .whereEqualTo("status", "APPROVED")
                .whereGreaterThanOrEqualTo("timestamp", monthStart)
                .whereLessThan("timestamp", monthEnd)
                .get()
                .await()
                .toObjects(FirestoreTrip::class.java)
        } catch (e: Exception) {
            android.util.Log.e("CloudTripRepo", "Error fetching approved trips: ${e.message}")
            emptyList()
        }
    }
}
