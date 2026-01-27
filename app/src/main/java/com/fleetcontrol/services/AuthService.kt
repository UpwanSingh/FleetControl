package com.fleetcontrol.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fleetcontrol.security.RateLimitManager
import com.fleetcontrol.security.RateLimitExceededException
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * Authentication Service
 * 
 * Multi-Tenancy: Supports both Owner (email/password) and anonymous auth.
 * The ownerId is the Firebase UID used to isolate all data.
 */
class AuthService {
    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val TAG = "AuthService"
    private val rateLimitManager = RateLimitManager()
    
    /**
     * Get the current authenticated user's UID (ownerId for multi-tenancy)
     */
    val currentOwnerId: String?
        get() = auth.currentUser?.uid
    
    /**
     * Check if user is signed in
     */
    val isSignedIn: Boolean
        get() = auth.currentUser != null
    
    /**
     * Check if current user is anonymous
     */
    val isAnonymous: Boolean
        get() = auth.currentUser?.isAnonymous == true
    
    /**
     * Get current user's email (null for anonymous users)
     */
    val currentEmail: String?
        get() = auth.currentUser?.email
    
    // ========================================
    // OWNER AUTHENTICATION (Email/Password)
    // ========================================
    
    /**
     * Register new owner account
     */
    suspend fun registerOwner(email: String, password: String): Result<String> {
        // Sanitize inputs
        val sanitizedEmail = com.fleetcontrol.utils.InputSanitizer.sanitizeEmail(email)
        
        return try {
            val result = auth.createUserWithEmailAndPassword(sanitizedEmail, password).await()
            val uid = result.user?.uid
            if (uid != null) {
                Log.d(TAG, "Owner registered. UID: $uid")
                
                // Create user document with role
                val userDocOk = createUserDocument(uid = uid, role = "owner", tenantId = uid, inviteCode = null)
                if (!userDocOk) {
                    Log.w(TAG, "User document creation failed")
                }
                
                // Create tenant document
                createTenantDocument(uid, sanitizedEmail)
                
                Result.success(uid)
            } else {
                Result.failure(Exception("Registration failed: No UID returned"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Create /users/{uid} document for RBAC
     */
    private suspend fun createUserDocument(uid: String, role: String, tenantId: String, inviteCode: String?): Boolean {
        return try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val userData = hashMapOf(
                "role" to role,
                "tenantId" to tenantId,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            if (inviteCode != null) {
                userData["inviteCode"] = inviteCode
            }
            db.collection("users").document(uid).set(userData).await()
            Log.d(TAG, "Created user document: $uid with role=$role, tenantId=$tenantId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create user document: ${e.message}")
            false
        }
    }
    
    /**
     * Create /tenants/{tenantId} document
     */
    private suspend fun createTenantDocument(tenantId: String, ownerEmail: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val tenantData = hashMapOf(
                "ownerUid" to tenantId,
                "ownerEmail" to ownerEmail,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("tenants").document(tenantId).set(tenantData).await()
            Log.d(TAG, "Created tenant document: $tenantId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create tenant document: ${e.message}")
        }
    }
    
    /**
     * Create user document for driver (called during join flow)
     */
    suspend fun createDriverUserDocument(driverUid: String, tenantId: String, inviteCode: String): Boolean {
        return createUserDocument(uid = driverUid, role = "driver", tenantId = tenantId, inviteCode = inviteCode)
    }
    
    /**
     * Login owner with email and password
     */
    suspend fun loginOwner(email: String, password: String): Result<String> {
        // Sanitize inputs
        val sanitizedEmail = com.fleetcontrol.utils.InputSanitizer.sanitizeEmail(email)
        val userId = sanitizedEmail // Use email as identifier for rate limiting
        
        // Check rate limit
        if (!rateLimitManager.isAllowed("Login attempts", userId)) {
            val retryAfter = rateLimitManager.getRemainingTokens("Login attempts", userId)
            Log.w(TAG, "Login rate limit exceeded for: $sanitizedEmail")
            return Result.failure(RateLimitExceededException(
                "Login attempts",
                userId,
                15_000, // 15 minutes
                "Too many login attempts. Please try again later."
            ))
        }
        
        return try {
            val result = auth.signInWithEmailAndPassword(sanitizedEmail, password).await()
            val uid = result.user?.uid
            if (uid != null) {
                Log.d(TAG, "Owner logged in. UID: $uid")
                
                // Ensure /users/{uid} and /tenants/{uid} exist (for existing users before multi-tenant)
                ensureOwnerDocumentsExist(uid, sanitizedEmail)
                
                Result.success(uid)
            } else {
                Result.failure(Exception("Login failed: No UID returned"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Ensure /users/{uid} and /tenants/{uid} documents exist
     * Creates them if they don't exist (for backward compatibility)
     */
    private suspend fun ensureOwnerDocumentsExist(uid: String, email: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // Check and create /users/{uid} if missing
            val userDoc = db.collection("users").document(uid).get().await()
            if (!userDoc.exists()) {
                Log.d(TAG, "Creating missing /users/$uid document")
                createUserDocument(uid = uid, role = "owner", tenantId = uid, inviteCode = null)
            }
            
            // Check and create /tenants/{uid} if missing
            val tenantDoc = db.collection("tenants").document(uid).get().await()
            if (!tenantDoc.exists()) {
                Log.d(TAG, "Creating missing /tenants/$uid document")
                createTenantDocument(uid, email)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure owner documents exist: ${e.message}")
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent to: $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    // ========================================
    // ANONYMOUS AUTHENTICATION (Legacy/Driver)
    // ========================================
    
    /**
     * Sign in anonymously
     * Used as fallback or for driver devices that don't need owner login
     * @deprecated Use owner login for production
     */
    suspend fun signInAnonymously(): Boolean {
        return try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Log.d(TAG, "Signed in anonymously. UID: ${auth.currentUser?.uid}")
            } else {
                Log.d(TAG, "Already signed in. UID: ${auth.currentUser?.uid}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auth failed: ${e.message}")
            false
        }
    }
    
    // ========================================
    // SIGN OUT
    // ========================================
    
    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
        Log.d(TAG, "User signed out")
    }
    
    // ========================================
    // UTILITY
    // ========================================
    
    /**
     * Get user ID for backward compatibility
     */
    fun getUserId(): String? = auth.currentUser?.uid
    
    // ========================================
    // NOTE: Phone Auth Removed
    // Firebase Phone Authentication requires Blaze plan (billing account)
    // SMS verification is not available on the free Spark plan
    // Alternative: Use email/password for driver accounts if needed
    // ========================================
}
