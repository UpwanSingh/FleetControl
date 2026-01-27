package com.fleetcontrol.services.export

import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.models.DriverReportSummary
import com.fleetcontrol.utils.DateUtils
import java.io.File
import java.io.FileWriter
import java.io.OutputStream

/**
 * CSV Export Service for Driver Reports
 * Exports driver's trips, fuel, and earnings data
 */
class DriverCsvExportService(private val exportDir: File) {
    
    /**
     * Generate CSV content as string (for user-selected location)
     */
    fun generateCsvContent(
        trips: List<TripEntity>,
        fuelEntries: List<FuelEntryEntity>,
        summary: DriverReportSummary
    ): String {
        return buildString {
            // Summary Section
            appendLine("=== EARNINGS SUMMARY ===")
            appendLine("Total Trips,${summary.totalTrips}")
            appendLine("Total Bags,${summary.totalBags}")
            appendLine("Gross Earnings,${summary.grossEarnings}")
            appendLine("Fuel Cost,${summary.fuelCost}")
            appendLine("Net Earnings,${summary.netEarnings}")
            appendLine("Total Distance (km),${summary.totalDistanceKm}")
            appendLine()
            
            // Trips Section
            appendLine("=== TRIP RECORDS ===")
            appendLine("Date,Client,Bags,Rate per Bag,Earnings,Distance (km)")
            
            trips.forEach { trip ->
                val earnings = trip.bagCount * trip.snapshotDriverRate
                appendLine(buildString {
                    append(DateUtils.formatDate(trip.tripDate))
                    append(",")
                    append(trip.clientName.replace(",", ";"))
                    append(",")
                    append(trip.bagCount)
                    append(",")
                    append(trip.snapshotDriverRate)
                    append(",")
                    append(earnings)
                    append(",")
                    append(trip.snapshotDistanceKm ?: 0.0)
                })
            }
            
            appendLine()
            
            // Fuel Section
            appendLine("=== FUEL RECORDS ===")
            appendLine("Date,Amount,Liters,Station")
            
            fuelEntries.forEach { fuel ->
                appendLine(buildString {
                    append(DateUtils.formatDate(fuel.entryDate))
                    append(",")
                    append(fuel.amount)
                    append(",")
                    append(if (fuel.liters > 0) fuel.liters else "N/A")
                    append(",")
                    append((fuel.fuelStation ?: "N/A").replace(",", ";"))
                })
            }
        }
    }
    
    /**
     * Write CSV content to an OutputStream (for SAF)
     */
    fun writeCsvToStream(
        outputStream: OutputStream,
        trips: List<TripEntity>,
        fuelEntries: List<FuelEntryEntity>,
        summary: DriverReportSummary
    ) {
        outputStream.bufferedWriter().use { writer ->
            writer.write(generateCsvContent(trips, fuelEntries, summary))
        }
    }
    
    /**
     * Get suggested filename for CSV export
     */
    fun getSuggestedFileName(year: Int, month: Int): String {
        return "driver_earnings_${year}_${month + 1}.csv"
    }
    
    /**
     * Export driver report to CSV file (fallback to internal storage)
     * Returns the file path
     */
    suspend fun exportDriverReport(
        trips: List<TripEntity>,
        fuelEntries: List<FuelEntryEntity>,
        summary: DriverReportSummary,
        year: Int,
        month: Int
    ): String {
        val fileName = getSuggestedFileName(year, month)
        val file = File(exportDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.write(generateCsvContent(trips, fuelEntries, summary))
        }
        
        return file.absolutePath
    }
}
