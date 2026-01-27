package com.fleetcontrol.core

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.random.Random

/**
 * Invite Code Manager
 * Handles generation, validation, and consumption of driver invite codes
 * 
 * Flow:
 * 1. Owner generates code for a driver → stored in Firestore
 * 2. Driver enters code on their device → validated + consumed
 * 3. Driver device is linked to owner's account
 */
class InviteCodeManager(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION_INVITE_CODES = "inviteCodes"
        private const val CODE_LENGTH = 6
        private const val CODE_EXPIRY_MINUTES = 30L
        
        // Characters for code generation (no confusing chars like 0/O, 1/I/L)
        private val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
    
    /**
     * Generate a new invite code for a driver
     * @param ownerId The owner's Firebase UID
     * @param firestoreDriverId The driver's Firestore document ID (for cross-device lookup)
     * @param driverName The driver's name (for display)
     * @return The generated 6-digit code
     */
    suspend fun generateCode(
        ownerId: String,
        firestoreDriverId: String,
        driverName: String
    ): String {
        val code = generateRandomCode()
        val now = Date()
        val expiresAt = Date(now.time + CODE_EXPIRY_MINUTES * 60 * 1000)
        
        val inviteData = hashMapOf(
            "code" to code,
            "ownerId" to ownerId,
            "firestoreDriverId" to firestoreDriverId,
            "driverName" to driverName,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "used" to false
        )
        
        // Store with code as document ID for easy lookup
        firestore.collection(COLLECTION_INVITE_CODES)
            .document(code)
            .set(inviteData)
            .await()
        
        return code
    }
    
    /**
     * Validate an invite code
     * @return InviteCodeResult with owner/driver info if valid
     */
    suspend fun validateCode(code: String): InviteCodeResult {
        val normalizedCode = code.uppercase().trim()
        
        if (normalizedCode.length != CODE_LENGTH) {
            return InviteCodeResult.Invalid("Code must be $CODE_LENGTH characters")
        }
        
        val docSnapshot = firestore.collection(COLLECTION_INVITE_CODES)
            .document(normalizedCode)
            .get()
            .await()
        
        if (!docSnapshot.exists()) {
            return InviteCodeResult.Invalid("Invalid code")
        }
        
        val used = docSnapshot.getBoolean("used") ?: false
        if (used) {
            return InviteCodeResult.Invalid("This code has already been used")
        }
        
        val expiresAt = docSnapshot.getDate("expiresAt")
        if (expiresAt != null && expiresAt.before(Date())) {
            return InviteCodeResult.Invalid("This code has expired")
        }
        
        val ownerId = docSnapshot.getString("ownerId") ?: return InviteCodeResult.Invalid("Invalid code data")
        val firestoreDriverId = docSnapshot.getString("firestoreDriverId") ?: return InviteCodeResult.Invalid("Invalid code data")
        val driverName = docSnapshot.getString("driverName") ?: "Driver"
        
        return InviteCodeResult.Valid(
            ownerId = ownerId,
            firestoreDriverId = firestoreDriverId,
            driverName = driverName
        )
    }
    
    /**
     * Consume an invite code (mark as used)
     * Call this after successful driver linking
     */
    suspend fun consumeCode(code: String, usedByUid: String): Boolean {
        val normalizedCode = code.uppercase().trim()
        
        return try {
            firestore.collection(COLLECTION_INVITE_CODES)
                .document(normalizedCode)
                .update(
                    mapOf(
                        "used" to true,
                        "usedBy" to usedByUid,
                        "usedAt" to Date()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create a driver link document in Firestore
     * This allows the Firestore security rules to verify that a driver
     * is linked to a specific owner and can read their data.
     * 
     * @param driverUid The Firebase UID of the driver's device
     * @param ownerId The Firebase UID of the owner
     * @param firestoreDriverId The Firestore ID of the driver document
     */
    suspend fun createDriverLink(driverUid: String, ownerId: String, firestoreDriverId: String): Boolean {
        return try {
            val linkData = hashMapOf(
                "linkedOwnerId" to ownerId,
                "firestoreDriverId" to firestoreDriverId,
                "createdAt" to Date()
            )
            
            firestore.collection("driverLinks")
                .document(driverUid)
                .set(linkData)
                .await()
            
            android.util.Log.d("InviteCodeManager", "Created driver link: $driverUid -> $ownerId")
            true
        } catch (e: Exception) {
            android.util.Log.e("InviteCodeManager", "Failed to create driver link: ${e.message}")
            false
        }
    }
    
    /**
     * Generate a random alphanumeric code
     */
    private fun generateRandomCode(): String {
        return (1..CODE_LENGTH)
            .map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }
            .joinToString("")
    }
}

/**
 * Result of invite code validation
 */
sealed class InviteCodeResult {
    data class Valid(
        val ownerId: String,
        val firestoreDriverId: String,
        val driverName: String
    ) : InviteCodeResult()
    
    data class Invalid(val message: String) : InviteCodeResult()
}
