package com.fleetcontrol.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.AdvanceEntity
import com.fleetcontrol.data.entities.DriverEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data Export Utility
 * Security Hardening: Enables external auditing of data
 * Part of audit item #16
 */
object DataExportUtil {
    
    private const val TAG = "DataExportUtil"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    /**
     * Export trips to CSV file
     */
    fun exportTripsToCSV(context: Context, trips: List<TripEntity>): Uri? {
        return try {
            val fileName = "trips_export_${timestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            
            FileWriter(file).use { writer ->
                // Header
                writer.append("ID,Date,Driver ID,Company ID,Pickup ID,Client,Bags,Rate,Total Amount,Status,Synced\n")
                
                // Data rows
                trips.forEach { trip ->
                    writer.append("${trip.id},")
                    writer.append("${dateFormat.format(Date(trip.tripDate))},")
                    writer.append("${trip.driverId},")
                    writer.append("${trip.companyId},")
                    writer.append("${trip.pickupLocationId},")
                    writer.append("\"${trip.clientName.replace("\"", "\"\"")}\",")
                    writer.append("${trip.bagCount},")
                    writer.append("${trip.snapshotDriverRate},")
                    writer.append("${trip.bagCount * trip.snapshotDriverRate},")
                    writer.append("${trip.status},")
                    writer.append("${trip.isSynced}\n")
                }
            }
            
            Log.d(TAG, "Exported ${trips.size} trips to $fileName")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export trips: ${e.message}")
            null
        }
    }
    
    /**
     * Export fuel entries to CSV file
     */
    fun exportFuelToCSV(context: Context, fuelEntries: List<FuelEntryEntity>): Uri? {
        return try {
            val fileName = "fuel_export_${timestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            
            FileWriter(file).use { writer ->
                // Header
                writer.append("ID,Date,Driver ID,Amount,Liters,Price Per Liter,Station\n")
                
                // Data rows
                fuelEntries.forEach { fuel ->
                    writer.append("${fuel.id},")
                    writer.append("${dateFormat.format(Date(fuel.entryDate))},")
                    writer.append("${fuel.driverId},")
                    writer.append("${fuel.amount},")
                    writer.append("${fuel.liters},")
                    writer.append("${fuel.pricePerLiter},")
                    writer.append("\"${(fuel.fuelStation ?: "").replace("\"", "\"\"")}\"\n")
                }
            }
            
            Log.d(TAG, "Exported ${fuelEntries.size} fuel entries to $fileName")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export fuel: ${e.message}")
            null
        }
    }
    
    /**
     * Export advances to CSV file
     */
    fun exportAdvancesToCSV(context: Context, advances: List<AdvanceEntity>): Uri? {
        return try {
            val fileName = "advances_export_${timestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            
            FileWriter(file).use { writer ->
                // Header
                writer.append("ID,Date,Driver ID,Amount,Note,Is Deducted\n")
                
                // Data rows
                advances.forEach { advance ->
                    writer.append("${advance.id},")
                    writer.append("${dateFormat.format(Date(advance.advanceDate))},")
                    writer.append("${advance.driverId},")
                    writer.append("${advance.amount},")
                    writer.append("\"${(advance.note ?: "").replace("\"", "\"\"")}\",")
                    writer.append("${advance.isDeducted}\n")
                }
            }
            
            Log.d(TAG, "Exported ${advances.size} advances to $fileName")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export advances: ${e.message}")
            null
        }
    }
    
    /**
     * Export drivers to CSV file
     */
    fun exportDriversToCSV(context: Context, drivers: List<DriverEntity>): Uri? {
        return try {
            val fileName = "drivers_export_${timestampFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            
            FileWriter(file).use { writer ->
                // Header
                writer.append("ID,Name,Phone,Is Active,Firestore ID\n")
                
                // Data rows
                drivers.forEach { driver ->
                    writer.append("${driver.id},")
                    writer.append("\"${driver.name.replace("\"", "\"\"")}\",")
                    writer.append("\"${driver.phone.replace("\"", "\"\"")}\",")
                    writer.append("${driver.isActive},")
                    writer.append("${driver.firestoreId ?: ""}\n")
                }
            }
            
            Log.d(TAG, "Exported ${drivers.size} drivers to $fileName")
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export drivers: ${e.message}")
            null
        }
    }
    
    /**
     * Share exported file via Android sharesheet
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String = "text/csv") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Data"))
    }
}

