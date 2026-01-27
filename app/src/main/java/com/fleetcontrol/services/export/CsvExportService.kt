package com.fleetcontrol.services.export

import android.net.Uri
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.utils.DateUtils
import java.io.File
import java.io.FileWriter
import java.io.OutputStream

/**
 * CSV Export Service
 * Requires Basic+ subscription
 */
class CsvExportService(private val exportDir: File) {
    
    /**
     * Generate CSV content as string (for user-selected location)
     */
    fun generateCsvContent(trips: List<TripEntity>): String {
        return buildString {
            // Header
            appendLine("Date,Driver ID,Company ID,Client,Pickup ID,Bags,Driver Rate,Company Rate,Driver Earning,Revenue,Labour Cost,Profit")
            
            // Data rows
            trips.forEach { trip ->
                val driverEarning = trip.bagCount * trip.snapshotDriverRate
                val revenue = trip.bagCount * trip.snapshotCompanyRate
                val labourCost = trip.bagCount * trip.snapshotLabourCostPerBag
                val profit = revenue - driverEarning - labourCost
                
                appendLine(buildString {
                    append(DateUtils.formatDate(trip.tripDate))
                    append(",")
                    append(trip.driverId)
                    append(",")
                    append(trip.companyId)
                    append(",")
                    append(trip.clientName.replace(",", ";"))
                    append(",")
                    append(trip.pickupLocationId)
                    append(",")
                    append(trip.bagCount)
                    append(",")
                    append(trip.snapshotDriverRate)
                    append(",")
                    append(trip.snapshotCompanyRate)
                    append(",")
                    append(driverEarning)
                    append(",")
                    append(revenue)
                    append(",")
                    append(labourCost)
                    append(",")
                    append(profit)
                })
            }
        }
    }
    
    /**
     * Write CSV content to an OutputStream (for SAF)
     */
    fun writeCsvToStream(outputStream: OutputStream, trips: List<TripEntity>) {
        outputStream.bufferedWriter().use { writer ->
            writer.write(generateCsvContent(trips))
        }
    }
    
    /**
     * Get suggested filename for CSV export
     */
    fun getSuggestedFileName(year: Int, month: Int): String {
        return "fleetcontrol_report_${year}_${month + 1}.csv"
    }
    
    /**
     * Export trips to CSV file (fallback to internal storage)
     * Returns the file path
     */
    suspend fun exportTrips(trips: List<TripEntity>, year: Int, month: Int): String {
        val fileName = getSuggestedFileName(year, month)
        val file = File(exportDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.write(generateCsvContent(trips))
        }
        
        return file.absolutePath
    }
}
