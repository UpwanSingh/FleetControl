package com.fleetcontrol.utils

/**
 * Input sanitization utilities for security
 * 
 * Protects against:
 * - XSS attacks
 * - SQL injection (though we use Room which is safe)
 * - Script injection
 * - Excessive input length
 * - Special characters that could cause issues
 */
object InputSanitizer {
    
    /**
     * Sanitize text input for safe storage and display
     */
    fun sanitizeTextInput(input: String): String {
        if (input.isBlank()) return input
        
        return input
            .trim()
            .replace(Regex("[<>\"'&]"), "") // Remove HTML/XML special chars
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "") // Remove control characters
            .take(500) // Limit length
    }
    
    /**
     * Sanitize names (people, companies, locations)
     */
    fun sanitizeName(input: String): String {
        if (input.isBlank()) return input
        
        return input
            .trim()
            .replace(Regex("[<>\"'&]"), "")
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s\\-.]"), "") // Allow letters, numbers, spaces, hyphens, dots
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .take(100)
    }
    
    /**
     * Sanitize email addresses
     */
    fun sanitizeEmail(input: String): String {
        if (input.isBlank()) return input
        
        return input
            .trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}@._\\-]"), "")
            .take(254) // RFC 5321 limit
    }
    
    /**
     * Sanitize phone numbers
     */
    fun sanitizePhone(input: String): String {
        if (input.isBlank()) return input
        
        return input
            .trim()
            .replace(Regex("[^\\p{N}\\s\\-+().]"), "") // Allow numbers, spaces, common phone chars
            .replace(Regex("\\s+"), " ")
            .take(20)
    }
    
    /**
     * Sanitize notes/description fields
     */
    fun sanitizeNotes(input: String): String {
        if (input.isBlank()) return input
        
        return input
            .trim()
            .replace(Regex("[<>\"'&]"), "")
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "")
            .replace(Regex("\\s+"), " ")
            .take(1000)
    }
    
    /**
     * Sanitize PIN codes (numeric only)
     */
    fun sanitizePin(input: String): String {
        return input
            .replace(Regex("[^0-9]"), "")
            .take(4)
    }
    
    /**
     * Sanitize invite codes
     */
    fun sanitizeInviteCode(input: String): String {
        if (input.isBlank()) return input
        
        return input
            .trim()
            .replace(Regex("[^A-Z0-9]"), "") // Only uppercase letters and numbers
            .take(10)
    }
    
    /**
     * Validate if input contains only safe characters
     */
    fun isSafeInput(input: String): Boolean {
        return !input.contains(Regex("[<>\"'&]")) && 
               !input.contains(Regex("[\u0000-\u001F\u007F-\u009F]"))
    }
    
    /**
     * Sanitize monetary amounts
     */
    fun sanitizeAmount(input: String): String {
        return input
            .replace(Regex("[^0-9.]"), "")
            .let { 
                if (it.count { char -> char == '.' } > 1) {
                    it.substringBeforeLast('.')
                } else it
            }
            .take(10)
    }
}
