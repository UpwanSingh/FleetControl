package com.fleetcontrol.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

/**
 * Currency formatting utilities
 */
object CurrencyUtils {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val compactFormat = DecimalFormat("#,##,###.##")
    
    /**
     * Format amount as full currency string (e.g., ₹1,23,456.00)
     */
    fun format(amount: Double): String {
        return "₹${compactFormat.format(amount)}"
    }
    
    /**
     * Format amount in compact form (e.g., ₹1.2L, ₹50K)
     */
    fun formatCompact(amount: Double): String {
        return when {
            amount >= 10000000 -> "₹${String.format("%.1f", amount / 10000000)}Cr"
            amount >= 100000 -> "₹${String.format("%.1f", amount / 100000)}L"
            amount >= 1000 -> "₹${String.format("%.1f", amount / 1000)}K"
            else -> "₹${compactFormat.format(amount)}"
        }
    }
    
    /**
     * Format as rate per bag
     */
    fun formatRate(rate: Double): String {
        return "₹${compactFormat.format(rate)}/bag"
    }
}
