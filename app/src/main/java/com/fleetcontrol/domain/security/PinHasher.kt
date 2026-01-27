package com.fleetcontrol.domain.security

import java.security.MessageDigest

/**
 * PIN Hasher Utility
 * Uses SHA-256 for secure one-way hashing of PINs.
 * 
 * Security Features:
 * - One-way hash (cannot be reversed)
 * - Consistent output for same input
 * - 64-character hex string output
 */
object PinHasher {
    
    private const val ALGORITHM = "SHA-256"
    private const val SALT = "FLEET_CONTROL_PIN_SALT_V1" // App-specific salt
    
    /**
     * Hash a PIN using SHA-256 with a salt.
     * @param plainPin The plaintext PIN to hash
     * @return 64-character hexadecimal hash string
     */
    fun hash(plainPin: String): String {
        val saltedPin = "$SALT$plainPin"
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hashBytes = digest.digest(saltedPin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify a plaintext PIN against a stored hash.
     * @param plainPin The plaintext PIN entered by user
     * @param storedHash The hash stored in the database
     * @return true if the PIN matches the hash
     * 
     * Backward Compatibility:
     * - Short strings (< 32 chars): Legacy plaintext PIN
     * - 64-char hex: Could be salted (new) or unsalted (legacy) hash
     */
    fun verify(plainPin: String, storedHash: String): Boolean {
        // If stored hash is short (e.g., "1234"), it's legacy plaintext
        if (storedHash.length < 32) {
            return plainPin == storedHash
        }
        
        // Try new salted hash first
        if (hash(plainPin) == storedHash) {
            return true
        }
        
        // Fallback: Try legacy unsalted hash (for existing users before salt was added)
        val legacyHash = hashLegacy(plainPin)
        return legacyHash == storedHash
    }
    
    /**
     * Legacy unsalted hash (for backward compatibility with pre-salt users)
     */
    private fun hashLegacy(plainPin: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hashBytes = digest.digest(plainPin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Check if a stored PIN is already hashed.
     * Hashed PINs are 64 characters (SHA-256 output).
     */
    fun isHashed(storedPin: String): Boolean {
        return storedPin.length == 64 && storedPin.all { it.isDigit() || it in 'a'..'f' }
    }
}
