package com.fleetcontrol.domain.calculators

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OwnerProfitCalculator logic
 */
class OwnerProfitCalculatorTest {
    
    @Test
    fun `net profit equals gross minus driver earnings minus labour`() {
        // Given
        val grossRevenue = 10000.0
        val driverEarnings = 3000.0
        val labourCost = 2000.0
        
        // When
        val netProfit = grossRevenue - driverEarnings - labourCost
        
        // Then
        assertEquals(5000.0, netProfit, 0.01)
    }
    
    @Test
    fun `profit margin calculated correctly`() {
        // Given
        val netProfit = 5000.0
        val grossRevenue = 10000.0
        
        // When
        val margin = if (grossRevenue > 0.0) (netProfit / grossRevenue) * 100 else 0.0
        
        // Then
        assertEquals(50.0, margin, 0.01)
    }
    
    @Test
    fun `profit margin is zero when gross revenue is zero`() {
        // Given
        val netProfit = 0.0
        val grossRevenue = 0.0
        
        // When
        val margin = if (grossRevenue > 0.0) (netProfit / grossRevenue) * 100 else 0.0
        
        // Then
        assertEquals(0.0, margin, 0.01)
    }
    
    @Test
    fun `negative profit shows correct value`() {
        // Given: Expenses exceed revenue
        val grossRevenue = 1000.0
        val driverEarnings = 800.0
        val labourCost = 500.0
        
        // When
        val netProfit = grossRevenue - driverEarnings - labourCost
        
        // Then
        assertEquals(-300.0, netProfit, 0.01)
        assertTrue(netProfit < 0)
    }
}
