package com.fleetcontrol.utils

/**
 * Validation utilities with integrated sanitization
 */
object ValidationUtils {
    
    fun isEmpty(value: String): Boolean = value.isBlank()
    
    fun isValidAmount(amount: String): Boolean {
        val sanitized = InputSanitizer.sanitizeAmount(amount)
        return sanitized.toDoubleOrNull()?.let { it > 0 } ?: false
    }
    
    fun isNotEmpty(value: String): Boolean = value.isNotBlank()
    
    /**
     * Validate and sanitize name input
     */
    fun validateName(name: String): ValidationResult {
        val sanitized = InputSanitizer.sanitizeName(name)
        return when {
            name.isBlank() -> ValidationResult.Error("Name is required")
            sanitized.isEmpty() -> ValidationResult.Error("Invalid name format")
            sanitized.length < 2 -> ValidationResult.Error("Name must be at least 2 characters")
            else -> ValidationResult.Success(sanitized)
        }
    }
    
    /**
     * Validate and sanitize email input
     */
    fun validateEmail(email: String): ValidationResult {
        val sanitized = InputSanitizer.sanitizeEmail(email)
        return when {
            email.isBlank() -> ValidationResult.Error("Email is required")
            !sanitized.contains("@") -> ValidationResult.Error("Invalid email format")
            sanitized.length < 5 -> ValidationResult.Error("Invalid email format")
            else -> ValidationResult.Success(sanitized)
        }
    }
    
    /**
     * Validate and sanitize phone input
     */
    fun validatePhone(phone: String): ValidationResult {
        val sanitized = InputSanitizer.sanitizePhone(phone)
        return when {
            phone.isBlank() -> ValidationResult.Error("Phone number is required")
            sanitized.length < 10 -> ValidationResult.Error("Phone number must be at least 10 digits")
            else -> ValidationResult.Success(sanitized)
        }
    }
    
    /**
     * Validate and sanitize notes input
     */
    fun validateNotes(notes: String): ValidationResult {
        val sanitized = InputSanitizer.sanitizeNotes(notes)
        return when {
            sanitized.length > 1000 -> ValidationResult.Error("Notes too long (max 1000 characters)")
            else -> ValidationResult.Success(sanitized)
        }
    }
    
    /**
     * Validate driver name
     */
    fun validateDriverName(name: String): ValidationResult {
        val sanitized = InputSanitizer.sanitizeName(name)
        return when {
            name.isBlank() -> ValidationResult.Error("Name is required")
            sanitized.length < 2 -> ValidationResult.Error("Name must be at least 2 characters")
            sanitized.length > 50 -> ValidationResult.Error("Name must be less than 50 characters")
            else -> ValidationResult.Success(sanitized)
        }
    }
    
    /**
     * Validate PIN
     */
    fun validatePin(pin: String): ValidationResult {
        val sanitized = InputSanitizer.sanitizePin(pin)
        return when {
            pin.isBlank() -> ValidationResult.Error("PIN is required")
            sanitized.length != 4 -> ValidationResult.Error("PIN must be exactly 4 digits")
            else -> ValidationResult.Success(sanitized)
        }
    }
    
    /**
     * Validate bag count
     */
    fun validateBagCount(count: String): ValidationResult {
        val bags = count.toIntOrNull()
        return when {
            count.isBlank() -> ValidationResult.Error("Bag count is required")
            bags == null -> ValidationResult.Error("Invalid number")
            bags <= 0 -> ValidationResult.Error("Must be greater than 0")
            bags > 1000 -> ValidationResult.Error("Count seems too high")
            else -> ValidationResult.Success()
        }
    }
    
    /**
     * Validate amount (currency)
     */
    fun validateAmount(amount: String): ValidationResult {
        val value = amount.toDoubleOrNull()
        return when {
            amount.isBlank() -> ValidationResult.Error("Amount is required")
            value == null -> ValidationResult.Error("Invalid amount")
            value < 0 -> ValidationResult.Error("Cannot be negative")
            value > 10000000 -> ValidationResult.Error("Amount seems too high")
            else -> ValidationResult.Success()
        }
    }
    
    /**
     * Validate company name
     */
    fun validateCompanyName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Company name is required")
            name.length < 2 -> ValidationResult.Error("Must be at least 2 characters")
            name.length > 100 -> ValidationResult.Error("Must be less than 100 characters")
            else -> ValidationResult.Success()
        }
    }
    
    /**
     * Validate per bag rate
     */
    fun validateRate(rate: String): ValidationResult {
        val value = rate.toDoubleOrNull()
        return when {
            rate.isBlank() -> ValidationResult.Error("Rate is required")
            value == null -> ValidationResult.Error("Invalid rate")
            value <= 0 -> ValidationResult.Error("Must be greater than 0")
            value > 10000 -> ValidationResult.Error("Rate seems too high")
            else -> ValidationResult.Success()
        }
    }
    
    /**
     * Validate distance
     */
    fun validateDistance(distance: String): ValidationResult {
        val value = distance.toDoubleOrNull()
        return when {
            distance.isBlank() -> ValidationResult.Error("Distance is required")
            value == null -> ValidationResult.Error("Invalid distance")
            value < 0 -> ValidationResult.Error("Cannot be negative")
            value > 1000 -> ValidationResult.Error("Distance seems too high")
            else -> ValidationResult.Success()
        }
    }
}

/**
 * Validation result sealed class
 */
sealed class ValidationResult {
    data class Success(val value: String = "") : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    
    val isValid: Boolean get() = this is Success
    val errorMessage: String? get() = (this as? Error)?.message
}
