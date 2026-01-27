package com.fleetcontrol.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ValidationUtils
 */
class ValidationUtilsTest {
    
    @Test
    fun `validateDriverName fails for blank name`() {
        val result = ValidationUtils.validateDriverName("")
        assertFalse(result.isValid)
        assertEquals("Name is required", result.errorMessage)
    }
    
    @Test
    fun `validateDriverName fails for short name`() {
        val result = ValidationUtils.validateDriverName("A")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validateDriverName succeeds for valid name`() {
        val result = ValidationUtils.validateDriverName("John Doe")
        assertTrue(result.isValid)
    }
    
    @Test
    fun `validatePhone fails for blank phone`() {
        val result = ValidationUtils.validatePhone("")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validatePhone fails for short phone`() {
        val result = ValidationUtils.validatePhone("12345")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validatePhone succeeds for valid phone`() {
        val result = ValidationUtils.validatePhone("9876543210")
        assertTrue(result.isValid)
    }
    
    @Test
    fun `validatePin fails for wrong length`() {
        val result = ValidationUtils.validatePin("123")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validatePin fails for non-digits`() {
        val result = ValidationUtils.validatePin("12ab")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validatePin succeeds for 4 digits`() {
        val result = ValidationUtils.validatePin("1234")
        assertTrue(result.isValid)
    }
    
    @Test
    fun `validateBagCount fails for zero`() {
        val result = ValidationUtils.validateBagCount("0")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validateBagCount fails for negative`() {
        val result = ValidationUtils.validateBagCount("-5")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validateBagCount succeeds for positive number`() {
        val result = ValidationUtils.validateBagCount("50")
        assertTrue(result.isValid)
    }
    
    @Test
    fun `validateAmount fails for negative`() {
        val result = ValidationUtils.validateAmount("-100")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validateAmount succeeds for zero`() {
        val result = ValidationUtils.validateAmount("0")
        assertTrue(result.isValid)
    }
    
    @Test
    fun `validateRate fails for zero`() {
        val result = ValidationUtils.validateRate("0")
        assertFalse(result.isValid)
    }
    
    @Test
    fun `validateRate succeeds for positive`() {
        val result = ValidationUtils.validateRate("5.5")
        assertTrue(result.isValid)
    }
}
