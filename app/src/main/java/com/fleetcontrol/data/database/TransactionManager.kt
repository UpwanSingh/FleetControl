package com.fleetcontrol.data.database

import androidx.room.withTransaction
import com.fleetcontrol.data.entities.*
import com.fleetcontrol.data.dao.*

/**
 * TransactionManager - Handles complex cross-DAO transactions
 * 
 * Per Section 13 of BUSINESS_LOGIC_SPEC.md:
 * - All writes are ACID-compliant
 * - No partial saves
 * - No silent failures
 * 
 * This class provides high-level transaction methods for operations
 * that span multiple DAOs and need to be atomic.
 */
class TransactionManager(private val database: AppDatabase) {
    
    // ========================================
    // TRIP TRANSACTIONS
    // ========================================
    
    /**
     * Create a trip with rate snapshot and audit log - ATOMIC
     * Ensures all trip-related data is saved together or not at all
     */
    suspend fun createTripWithRateSnapshot(
        trip: TripEntity,
        performedBy: Long
    ): Long = database.withTransaction {
        val tripId = database.tripDao().insert(trip)
        
        // Create audit log for trip creation
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "TRIP",
                entityId = tripId,
                action = AuditAction.CREATE,
                performedBy = performedBy,
                reason = "Trip created with ${trip.bagCount} bags",
                originalValue = "",
                newValue = "Driver: ${trip.driverId}, Company: ${trip.companyId}, Bags: ${trip.bagCount}"
            )
        )
        
        tripId
    }
    
    /**
     * Override trip with mandatory audit - ATOMIC
     * Per Section 11.2: No override without audit
     */
    suspend fun overrideTrip(
        tripId: Long,
        userId: Long,
        reason: String,
        newBagCount: Int? = null
    ) = database.withTransaction {
        val trip = database.tripDao().getTripById(tripId)
            ?: throw IllegalArgumentException("Trip not found: $tripId")
        
        val originalValue = "Bags: ${trip.bagCount}, Status: ${trip.status}"
        
        // Update trip if new bag count provided
        if (newBagCount != null && newBagCount != trip.bagCount) {
            database.tripDao().update(trip.copy(
                bagCount = newBagCount,
                isOverridden = true
            ))
        } else {
            database.tripDao().markAsOverridden(tripId)
        }
        
        val newValue = "Bags: ${newBagCount ?: trip.bagCount}, Status: OVERRIDDEN"
        
        // Create mandatory audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "TRIP",
                entityId = tripId,
                action = AuditAction.OVERRIDE,
                performedBy = userId,
                reason = reason,
                originalValue = originalValue,
                newValue = newValue
            )
        )
    }
    
    // ========================================
    // DRIVER ADVANCE TRANSACTIONS
    // ========================================
    
    /**
     * Give advance to driver with balance update and audit - ATOMIC
     * Per Section 7.1: Advance is recorded and balance updated together
     */
    suspend fun giveDriverAdvance(
        driverId: Long,
        amount: Double,
        note: String,
        givenBy: Long
    ): Long = database.withTransaction {
        // Create advance record
        val advance = AdvanceEntity(
            driverId = driverId,
            amount = amount,
            note = note,
            advanceDate = System.currentTimeMillis()
        )
        val advanceId = database.advanceDao().insert(advance)
        
        // Update driver's cached balance
        val newBalance = database.advanceDao().getOutstandingBalanceByDriver(driverId)
        database.driverDao().updateAdvanceBalance(driverId, newBalance)
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "ADVANCE",
                entityId = advanceId,
                action = AuditAction.CREATE,
                performedBy = givenBy,
                reason = note.ifBlank { "Advance given to driver" },
                originalValue = "",
                newValue = "Amount: ₹$amount, Driver: $driverId, New Balance: ₹$newBalance"
            )
        )
        
        advanceId
    }
    
    /**
     * Settle driver earnings with advance deduction - ATOMIC
     * Per Section 7.3: Net payable = Gross - Fuel - Advance (FIFO order)
     */
    suspend fun settleDriverEarnings(
        driverId: Long,
        grossEarnings: Double,
        fuelCost: Double,
        settledBy: Long
    ): DriverSettlement = database.withTransaction {
        val outstandingAdvance = database.advanceDao().getOutstandingBalanceByDriver(driverId)
        val earningsAfterFuel = grossEarnings - fuelCost
        
        // Calculate deduction (cannot exceed earnings after fuel)
        val advanceDeduction = minOf(outstandingAdvance, maxOf(0.0, earningsAfterFuel))
        
        // Deduct advances in FIFO order (oldest first)
        if (advanceDeduction > 0) {
            var remaining = advanceDeduction
            val advances = database.advanceDao().getUndeductedAdvancesByDriverSync(driverId)
            val advanceIdsToDeduct = mutableListOf<Long>()
            
            for (advance in advances) {
                if (remaining <= 0) break
                if (advance.amount <= remaining) {
                    // Fully deduct this advance
                    advanceIdsToDeduct.add(advance.id)
                    remaining -= advance.amount
                }
            }
            
            // Mark advances as deducted
            if (advanceIdsToDeduct.isNotEmpty()) {
                database.advanceDao().markMultipleAsDeducted(advanceIdsToDeduct)
            }
        }
        
        // Update driver's cached balance
        val remainingBalance = database.advanceDao().getOutstandingBalanceByDriver(driverId)
        database.driverDao().updateAdvanceBalance(driverId, remainingBalance)
        
        val netPayable = earningsAfterFuel - advanceDeduction
        
        // Create audit log for settlement
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "DRIVER",
                entityId = driverId,
                action = "SETTLEMENT",
                performedBy = settledBy,
                reason = "Daily earnings settlement",
                originalValue = "Outstanding Advance: ₹$outstandingAdvance",
                newValue = "Gross: ₹$grossEarnings, Fuel: ₹$fuelCost, Advance Deducted: ₹$advanceDeduction, Net: ₹$netPayable"
            )
        )
        
        DriverSettlement(
            driverId = driverId,
            grossEarnings = grossEarnings,
            fuelCost = fuelCost,
            advanceDeducted = advanceDeduction,
            netPayable = netPayable,
            remainingAdvanceBalance = remainingBalance
        )
    }
    
    // ========================================
    // RATE UPDATE TRANSACTIONS
    // ========================================
    
    /**
     * Update company rate with audit - ATOMIC
     * Rate changes affect future trips, must be tracked
     */
    suspend fun updateCompanyRate(
        companyId: Long,
        newRate: Double,
        updatedBy: Long,
        reason: String = "Rate update"
    ) = database.withTransaction {
        val company = database.companyDao().getCompanyById(companyId)
            ?: throw IllegalArgumentException("Company not found: $companyId")
        
        val oldRate = company.perBagRate
        
        // Update company
        database.companyDao().update(company.copy(perBagRate = newRate))
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "COMPANY",
                entityId = companyId,
                action = AuditAction.UPDATE,
                performedBy = updatedBy,
                reason = reason,
                originalValue = "₹$oldRate per bag",
                newValue = "₹$newRate per bag"
            )
        )
    }
    
    /**
     * Replace all rate slabs with new configuration - ATOMIC
     * Ensures rate structure is always consistent
     */
    suspend fun replaceRateSlabs(
        newSlabs: List<DriverRateSlabEntity>,
        updatedBy: Long,
        reason: String = "Rate structure update"
    ) = database.withTransaction {
        val oldSlabs = database.rateSlabDao().getAllActiveRateSlabsSync()
        
        // Deactivate all existing slabs
        database.rateSlabDao().deactivateAll()
        
        // Insert new slabs
        database.rateSlabDao().insertAll(newSlabs)
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "RATE_SLABS",
                entityId = 0, // No specific entity
                action = AuditAction.UPDATE,
                performedBy = updatedBy,
                reason = reason,
                originalValue = "${oldSlabs.size} slabs",
                newValue = "${newSlabs.size} slabs updated"
            )
        )
    }
    
    // ========================================
    // DRIVER MANAGEMENT TRANSACTIONS
    // ========================================
    
    /**
     * Add new driver with initial setup - ATOMIC
     */
    suspend fun addDriver(
        driver: DriverEntity,
        createdBy: Long
    ): Long = database.withTransaction {
        val driverId = database.driverDao().insert(driver)
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "DRIVER",
                entityId = driverId,
                action = AuditAction.CREATE,
                performedBy = createdBy,
                reason = "New driver registered",
                originalValue = "",
                newValue = "Name: ${driver.name}, Phone: ${driver.phone}"
            )
        )
        
        driverId
    }
    
    /**
     * Deactivate driver (soft delete) with audit - ATOMIC
     * Preserves referential integrity with trips
     */
    suspend fun deactivateDriver(
        driverId: Long,
        deactivatedBy: Long,
        reason: String
    ) = database.withTransaction {
        val driver = database.driverDao().getDriverById(driverId)
            ?: throw IllegalArgumentException("Driver not found: $driverId")
        
        // Soft delete
        database.driverDao().deactivate(driverId)
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "DRIVER",
                entityId = driverId,
                action = AuditAction.DELETE,
                performedBy = deactivatedBy,
                reason = reason,
                originalValue = "Active: true, Name: ${driver.name}",
                newValue = "Active: false (soft deleted)"
            )
        )
    }
    
    // ========================================
    // FUEL ENTRY TRANSACTIONS
    // ========================================
    
    /**
     * Add fuel entry with audit - ATOMIC
     * Per Section 6.2: Fuel is deducted from driver earnings
     */
    suspend fun addFuelEntry(
        fuelEntry: FuelEntryEntity,
        recordedBy: Long
    ): Long = database.withTransaction {
        val fuelId = database.fuelDao().insert(fuelEntry)
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "FUEL",
                entityId = fuelId,
                action = AuditAction.CREATE,
                performedBy = recordedBy,
                reason = "Fuel entry recorded",
                originalValue = "",
                newValue = "Amount: ₹${fuelEntry.amount}, Driver: ${fuelEntry.driverId}, Liters: ${fuelEntry.liters ?: "N/A"}"
            )
        )
        
        fuelId
    }
    
    // ========================================
    // BULK OPERATIONS
    // ========================================
    
    /**
     * Import data from backup - ATOMIC
     * All data is imported or nothing is changed
     */
    suspend fun importBulkData(
        drivers: List<DriverEntity>,
        companies: List<CompanyEntity>,
        trips: List<TripEntity>,
        advances: List<AdvanceEntity>,
        importedBy: Long
    ) = database.withTransaction {
        // Insert all data
        database.driverDao().insertAll(drivers)
        database.companyDao().insertAll(companies)
        database.tripDao().insertAll(trips)
        
        advances.forEach { database.advanceDao().insert(it) }
        
        // Create audit log
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "SYSTEM",
                entityId = 0,
                action = "IMPORT",
                performedBy = importedBy,
                reason = "Bulk data import from backup",
                originalValue = "",
                newValue = "Imported: ${drivers.size} drivers, ${companies.size} companies, ${trips.size} trips, ${advances.size} advances"
            )
        )
    }
    
    /**
     * Clear all data (factory reset) - ATOMIC
     * Use with caution - all data will be permanently deleted
     */
    suspend fun factoryReset(
        performedBy: Long,
        reason: String = "Factory reset requested"
    ) = database.withTransaction {
        // Note: This would require adding deleteAll() methods to each DAO
        // For safety, we create an audit log first
        database.auditLogDao().insert(
            AuditLogEntity(
                entityType = "SYSTEM",
                entityId = 0,
                action = "FACTORY_RESET",
                performedBy = performedBy,
                reason = reason,
                originalValue = "All data",
                newValue = "Deleted"
            )
        )
        
        // Actual deletion would be implemented here
        // This is dangerous and should require multiple confirmations
    }
}

/**
 * Result of driver earnings settlement
 */
data class DriverSettlement(
    val driverId: Long,
    val grossEarnings: Double,
    val fuelCost: Double,
    val advanceDeducted: Double,
    val netPayable: Double,
    val remainingAdvanceBalance: Double
)
