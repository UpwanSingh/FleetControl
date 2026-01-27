package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PickupClientDistance entity - Stores distances between pickup locations and clients
 * 
 * Key Concepts:
 * - Each pickup location can serve multiple clients
 * - Each client can be served from multiple pickup locations
 * - Distance determines which pickup is "nearest" to a client
 * - Distance is used for calculating driver rates (rate slabs)
 * 
 * Business Flow:
 * - Owner sets up distances: Pickup Location A → Client X = 15 km
 * - When creating a trip, distance is looked up from this table
 * - Owner can choose which pickup to use (nearest or preferred)
 */
@Entity(
    tableName = "pickup_client_distances",
    foreignKeys = [
        ForeignKey(
            entity = PickupLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["pickupLocationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pickupLocationId"]),
        Index(value = ["clientId"]),
        Index(value = ["pickupLocationId", "clientId"], unique = true)
    ]
)
data class PickupClientDistanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val firestoreId: String? = null,
    
    /**
     * Owner's Firebase UID - Multi-Tenancy isolation
     */
    val ownerId: String = "",
    
    /**
     * Reference to the pickup location
     */
    val pickupLocationId: Long,
    
    /**
     * Reference to the client
     */
    val clientId: Long,
    
    /**
     * Distance in kilometers from this pickup location to this client
     * Used for:
     * - Rate slab lookup (driver earnings calculation)
     * - Finding nearest pickup for a client
     */
    val distanceKm: Double,
    
    /**
     * Optional estimated travel time in minutes
     */
    val estimatedTravelMinutes: Int? = null,
    
    /**
     * Whether this route is preferred/recommended by owner
     */
    val isPreferred: Boolean = false,
    
    /**
     * Notes about this route (e.g., road conditions, best time to travel)
     */
    val notes: String? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
