package com.fleetcontrol.data.entities

import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Firestore Models (Cloud Source of Truth)
 * Mirror local Room entities but use String IDs
 * 
 * Multi-Tenancy: All models include ownerId for data isolation
 */

data class FirestoreDriver(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("phone") val phone: String = "",
    @get:PropertyName("pin") val pin: String = "",
    @get:PropertyName("isActive") val isActive: Boolean = true
)

data class FirestoreCompany(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("contactPerson") val contactPerson: String = "",
    @get:PropertyName("contactPhone") val contactPhone: String = "",
    @get:PropertyName("perBagRate") val perBagRate: Double = 0.0,
    @get:PropertyName("isActive") val isActive: Boolean = true
)

data class FirestoreClient(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("address") val address: String = "",
    @get:PropertyName("contactPerson") val contactPerson: String = "",
    @get:PropertyName("contactPhone") val contactPhone: String = "",
    @get:PropertyName("notes") val notes: String = "",
    @get:PropertyName("isActive") val isActive: Boolean = true
)

data class FirestoreLocation(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("distanceFromBase") val distanceFromBase: Double = 0.0,
    @get:PropertyName("isActive") val isActive: Boolean = true
)

data class FirestoreDriverRateSlab(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("minDistance") val minDistance: Double = 0.0,
    @get:PropertyName("maxDistance") val maxDistance: Double = 0.0,
    @get:PropertyName("ratePerBag") val ratePerBag: Double = 0.0,
    @get:PropertyName("isActive") val isActive: Boolean = true
)

data class FirestorePickupClientDistance(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("pickupId") val pickupId: String = "",
    @get:PropertyName("clientId") val clientId: String = "",
    @get:PropertyName("distanceKm") val distanceKm: Double = 0.0,
    @get:PropertyName("estimatedTravelMinutes") val estimatedTravelMinutes: Int = 0,
    @get:PropertyName("isPreferred") val isPreferred: Boolean = false,
    @get:PropertyName("notes") val notes: String = ""
)

data class FirestoreTrip(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("driverId") val driverId: String = "",
    @get:PropertyName("driverName") val driverName: String = "",
    @get:PropertyName("vehicleNumber") val vehicleNumber: String = "",
    @get:PropertyName("companyId") val companyId: String = "",
    @get:PropertyName("pickupId") val pickupId: String = "",
    @get:PropertyName("clientId") val clientId: Long = 0,
    @get:PropertyName("clientName") val clientName: String = "",
    @get:PropertyName("timestamp") val timestamp: Long = 0,
    @get:PropertyName("bags") val bags: Int = 0,
    @get:PropertyName("rate") val rate: Double = 0.0,
    @get:PropertyName("totalAmount") val totalAmount: Double = 0.0,
    @get:PropertyName("distance") val distance: Double = 0.0,
    @get:PropertyName("ownerGross") val ownerGross: Double = 0.0,
    @get:PropertyName("labourCost") val labourCost: Double = 0.0,
    @get:PropertyName("status") val status: String = "PENDING", // PENDING, APPROVED, REJECTED, COMPLETED
    
    // Audit fields for tracking
    @get:PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("createdBy") val createdBy: String = "", // UID of creator
    @get:PropertyName("modifiedAt") val modifiedAt: Long? = null,
    @get:PropertyName("modifiedBy") val modifiedBy: String? = null, // UID of last modifier
    
    // Version for conflict detection (Sync Audit Fix)
    @get:PropertyName("version") val version: Long = 1L
)

/**
 * Transactional Data
 */
data class FirestoreFuel(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("driverId") val driverId: String = "",
    @get:PropertyName("vehicleId") val vehicleId: String = "",
    @get:PropertyName("amount") val amount: Double = 0.0,
    @get:PropertyName("date") val date: Long = 0,
    @get:PropertyName("odometer") val odometer: Double = 0.0,
    @get:PropertyName("notes") val notes: String = ""
)

data class FirestoreAdvance(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("driverId") val driverId: String = "",
    @get:PropertyName("amount") val amount: Double = 0.0,
    @get:PropertyName("date") val date: Long = 0,
    @get:PropertyName("reason") val reason: String = "",
    @get:PropertyName("isDeducted") val isDeducted: Boolean = false
)

/**
 * Fuel Request Entity
 * Drivers create with status=PENDING, Owners approve/reject
 * Part of security hardening - drivers cannot create fuel directly
 */
data class FirestoreFuelRequest(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("driverId") val driverId: String = "",
    @get:PropertyName("driverName") val driverName: String = "",
    @get:PropertyName("amount") val amount: Double = 0.0,
    @get:PropertyName("date") val date: Long = 0,
    @get:PropertyName("notes") val notes: String = "",
    @get:PropertyName("status") val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    @get:PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("reviewedAt") val reviewedAt: Long? = null,
    @get:PropertyName("reviewedBy") val reviewedBy: String? = null
)

/**
 * Stats Aggregation (For Dashboard)
 * One document per Driver per Month.
 */
data class DriverStats(
    @get:PropertyName("docId") val docId: String = "",
    @get:PropertyName("ownerId") val ownerId: String = "",
    @get:PropertyName("driverId") val driverId: String = "",
    @get:PropertyName("driverName") val driverName: String = "",
    @get:PropertyName("year") val year: Int = 0,
    @get:PropertyName("month") val month: Int = 0,
    @get:PropertyName("totalTrips") val totalTrips: Long = 0,
    @get:PropertyName("totalBags") val totalBags: Long = 0,
    @get:PropertyName("totalDriverEarnings") val totalDriverEarnings: Double = 0.0,
    @get:PropertyName("totalOwnerGross") val totalOwnerGross: Double = 0.0,
    @get:PropertyName("totalLabourCost") val totalLabourCost: Double = 0.0,
    @get:PropertyName("lastUpdated") val lastUpdated: Date? = null
)

