package com.fleetcontrol.data.models

/**
 * Summary of driver's earnings for a report period
 */
data class DriverReportSummary(
    val totalTrips: Int = 0,
    val totalBags: Int = 0,
    val grossEarnings: Double = 0.0,
    val fuelCost: Double = 0.0,
    val netEarnings: Double = 0.0,
    val totalFuelEntries: Int = 0,
    val totalDistanceKm: Double = 0.0
)

/**
 * Result of an export operation
 */
sealed class DriverExportResult {
    data class Success(val filePath: String) : DriverExportResult()
    data class Error(val message: String) : DriverExportResult()
}
