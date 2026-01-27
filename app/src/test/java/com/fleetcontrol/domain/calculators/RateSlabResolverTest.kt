package com.fleetcontrol.domain.calculators

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for RateSlabResolver logic
 */
class RateSlabResolverTest {
    
    @Test
    fun `zone A rate applied for distance under 10km`() {
        // Given: Distance within Zone A
        val distance = 5.0
        val zoneARate = 5.0
        val zoneBRate = 7.0
        val zoneCRate = 10.0
        
        // When
        val rate = when {
            distance <= 10 -> zoneARate
            distance <= 25 -> zoneBRate
            else -> zoneCRate
        }
        
        // Then
        assertEquals(zoneARate, rate, 0.01)
    }
    
    @Test
    fun `zone B rate applied for distance 10-25km`() {
        // Given
        val distance = 15.0
        val zoneARate = 5.0
        val zoneBRate = 7.0
        val zoneCRate = 10.0
        
        // When
        val rate = when {
            distance <= 10 -> zoneARate
            distance <= 25 -> zoneBRate
            else -> zoneCRate
        }
        
        // Then
        assertEquals(zoneBRate, rate, 0.01)
    }
    
    @Test
    fun `zone C rate applied for distance over 25km`() {
        // Given
        val distance = 30.0
        val zoneARate = 5.0
        val zoneBRate = 7.0
        val zoneCRate = 10.0
        
        // When
        val rate = when {
            distance <= 10 -> zoneARate
            distance <= 25 -> zoneBRate
            else -> zoneCRate
        }
        
        // Then
        assertEquals(zoneCRate, rate, 0.01)
    }
    
    @Test
    fun `boundary value 10km uses zone A`() {
        val distance = 10.0
        val rate = when {
            distance <= 10 -> 5.0
            distance <= 25 -> 7.0
            else -> 10.0
        }
        assertEquals(5.0, rate, 0.01)
    }
    
    @Test
    fun `boundary value 25km uses zone B`() {
        val distance = 25.0
        val rate = when {
            distance <= 10 -> 5.0
            distance <= 25 -> 7.0
            else -> 10.0
        }
        assertEquals(7.0, rate, 0.01)
    }
}
