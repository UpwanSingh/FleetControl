package com.fleetcontrol.data.managers

import android.util.Log
import com.fleetcontrol.data.dao.TripDao
import com.fleetcontrol.data.entities.*
import com.fleetcontrol.data.repositories.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataMigrationManager @Inject constructor(
    private val tripDao: TripDao,
    private val cloudTripRepository: CloudTripRepository,
    private val driverRepository: DriverRepository,
    private val companyRepository: CompanyRepository,
    private val pickupLocationRepository: PickupLocationRepository,
    private val clientRepository: ClientRepository,
    private val cloudMasterDataRepository: CloudMasterDataRepository,
    private val rateSlabRepository: RateSlabRepository,
    private val pickupClientDistanceRepository: PickupClientDistanceRepository,
    private val fuelRepository: FuelRepository,
    private val advanceRepository: AdvanceRepository
) {
    // Migration Status State
    private val _migrationStatus = MutableStateFlow<MigrationStatus>(MigrationStatus.Idle)
    val migrationStatus = _migrationStatus.asStateFlow()

    sealed class MigrationStatus {
        object Idle : MigrationStatus()
        object InProgress : MigrationStatus()
        data class Success(val count: Int) : MigrationStatus()
        data class Error(val message: String) : MigrationStatus()
    }

    suspend fun startMigration() {
        _migrationStatus.value = MigrationStatus.InProgress
        
        withContext(Dispatchers.IO) {
            try {
                // SECURITY AUDIT: Prevent migration if no owner is logged in
                // This prevents "Ghost Data" being uploaded with empty ownerId during background execution
                val ownerId = cloudMasterDataRepository.currentOwnerId
                if (ownerId.isEmpty()) {
                    Log.e("Migration", "Security Block: Attempted migration without active Owner Session")
                    _migrationStatus.value = MigrationStatus.Error("Security Block: Please Login First")
                    return@withContext
                }
                
                var successCount = 0

                // 1. Migrate Master Data (Drivers, Companies, etc.)
                // Only push if firesoreId is null
                
                // Drivers
                val drivers = driverRepository.getAllDriversRaw().first()
                for (driver in drivers) {
                    if (driver.firestoreId == null) {
                       try {
                           driverRepository.update(driver) // Trigger sync logic in Repo
                           // Explicit push if needed:
                           val fDriver = FirestoreDriver(
                               id = "",
                               name = driver.name,
                               phone = driver.phone,
                               pin = driver.pin ?: "",
                               isActive = driver.isActive,
                               ownerId = cloudMasterDataRepository.currentOwnerId // Explicitly set for Cloud
                           )
                           val newId = cloudMasterDataRepository.addDriver(fDriver)
                           // CLAIM DATA LOCALLY: Update with new Cloud ID AND Owner ID
                           driverRepository.update(driver.copy(
                               firestoreId = newId,
                               ownerId = cloudMasterDataRepository.currentOwnerId
                           ))
                           successCount++
                       } catch (e: Exception) { Log.e("Migration", "Driver sync failed: ${e.message}") }
                    }
                }

                // Companies
                val companies = companyRepository.getAllCompaniesRaw().first()
                for (company in companies) {
                    if (company.firestoreId == null) {
                        try {
                            val fCompany = FirestoreCompany(
                                id = "",
                                name = company.name,
                                contactPerson = company.contactPerson ?: "",
                                contactPhone = company.contactPhone ?: "",
                                perBagRate = company.perBagRate,
                                isActive = company.isActive,
                                ownerId = cloudMasterDataRepository.currentOwnerId
                            )
                            val newId = cloudMasterDataRepository.addCompany(fCompany)
                            // CLAIM DATA LOCALLY
                            companyRepository.update(company.copy(
                                firestoreId = newId,
                                ownerId = cloudMasterDataRepository.currentOwnerId
                            ))
                             successCount++
                        } catch(e: Exception) { Log.e("Migration", "Company sync failed") }
                    }
                }

                // Clients
                val clients = clientRepository.getAllClientsRaw().first()
                for (client in clients) {
                    if (client.firestoreId == null) {
                        try {
                           val fClient = FirestoreClient(
                               id = "",
                               name = client.name,
                               address = client.address ?: "",
                               contactPerson = client.contactPerson ?: "",
                               contactPhone = client.contactPhone ?: "",
                               notes = client.notes ?: "",
                               isActive = client.isActive,
                               ownerId = cloudMasterDataRepository.currentOwnerId
                           )
                           val newId = cloudMasterDataRepository.addClient(fClient)
                           // CLAIM DATA LOCALLY
                           clientRepository.update(client.copy(
                               firestoreId = newId,
                               ownerId = cloudMasterDataRepository.currentOwnerId
                           ))
                           successCount++
                        } catch(e: Exception) { Log.e("Migration", "Client sync failed") }
                    }
                }

                // Pickup Locations
                 val locations = pickupLocationRepository.getAllLocationsRaw().first()
                 for (loc in locations) {
                    if (loc.firestoreId == null) {
                        try {
                           val fLoc = FirestoreLocation(
                               id = "",
                               name = loc.name,
                               distanceFromBase = loc.distanceFromBase,
                               isActive = loc.isActive,
                               ownerId = cloudMasterDataRepository.currentOwnerId
                           )
                           val newId = cloudMasterDataRepository.addLocation(fLoc)
                           // CLAIM DATA LOCALLY
                           pickupLocationRepository.update(loc.copy(
                               firestoreId = newId,
                               ownerId = cloudMasterDataRepository.currentOwnerId
                           ))
                           successCount++
                        } catch(e: Exception) { Log.e("Migration", "Location sync failed") }
                    }
                }
                
                // Rate Slabs
                // Rate Slabs
                val slabs = rateSlabRepository.getAllRateSlabsRaw().first()
                for (slab in slabs) {
                    if (slab.firestoreId == null) {
                        try {
                            // Helper method triggers sync
                            rateSlabRepository.upsert(slab)
                            successCount++
                        } catch(e: Exception) { Log.e("Migration", "Slab sync failed") }
                    }
                }
                
                // Fuel
                val fuels = fuelRepository.getAllFuelEntriesRaw().first()
                for (fuel in fuels) {
                   if (fuel.firestoreId == null) {
                       // CLAIM DATA LOCALLY
                       fuelRepository.update(fuel.copy(ownerId = cloudMasterDataRepository.currentOwnerId))
                   }
                }
                
                // Advances
                val advances = advanceRepository.getAllAdvancesRaw().first()
                for (adv in advances) {
                    if (adv.firestoreId == null) {
                        // CLAIM DATA LOCALLY
                        advanceRepository.update(adv.copy(ownerId = cloudMasterDataRepository.currentOwnerId))
                    }
                }
                
                 // Routes (Repo handles complex logic)
                 val distances = pickupClientDistanceRepository.getAllDistancesRaw().first()
                 for (dist in distances) {
                     if (dist.firestoreId == null) {
                         // CLAIM DATA LOCALLY
                         pickupClientDistanceRepository.upsert(dist.copy(ownerId = cloudMasterDataRepository.currentOwnerId))
                     }
                 }

                // 2. Migrate TRIPS (Existing logic)
                val unsyncedTrips = tripDao.getAllUnsyncedTripsRaw()
                unsyncedTrips.forEach { trip ->
                    try {
                        // CRITICAL: Lookup driver firestoreId for cross-device sync
                        val driver = driverRepository.getDriverById(trip.driverId)
                        val driverFirestoreId = driver?.firestoreId ?: trip.driverId.toString()
                        val driverName = driver?.name ?: "Unknown Driver"
                        val clientName = clientRepository.getClientName(trip.clientId) ?: "Unknown Client"
                        
                        val firestoreTrip = FirestoreTrip(
                            id = "", 
                            driverId = driverFirestoreId, 
                            driverName = driverName,
                            clientId = trip.clientId,
                            clientName = clientName,
                            timestamp = trip.tripDate,
                            bags = trip.bagCount,
                            totalAmount = trip.calculateDriverEarning(),
                            distance = trip.snapshotDistanceKm,
                            ownerGross = trip.calculateOwnerGross(),
                            labourCost = trip.calculateLabourCost(),
                            vehicleNumber = ""
                        )

                        cloudTripRepository.addTrip(firestoreTrip)
                        // CLAIM DATA LOCALLY (Update ownerId) + Sync Status
                        val claimedTrip = trip.copy(
                            ownerId = cloudMasterDataRepository.currentOwnerId,
                            isSynced = true // Mark as synced manually since we are updating entity
                        )
                        tripDao.update(claimedTrip)
                        // tripDao.markTripAsSynced(trip.id) // Redundant if we update entity above with isSynced=true
                        successCount++
                    } catch (e: Exception) {
                        Log.e("Migration", "Failed to migrate trip ${trip.id}: ${e.message}")
                    }
                }

                _migrationStatus.value = MigrationStatus.Success(successCount)
                
            } catch (e: Exception) {
                _migrationStatus.value = MigrationStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Complete Data Check & Integrity Verification
     * - Scans for orphans (Local Database Integrity)
     * - Checks Sync Status (Cloud Integrity)
     */
    suspend fun verifyDataIntegrity(): Map<String, Int> {
        val results = mutableMapOf<String, Int>()
        
        withContext(Dispatchers.IO) {
            // 1. Check for Orphaned Trips (Data Integrity)
            // Note: Room enforces FKs mostly, but manual queries verify logic
            val allTrips = tripDao.getTripsForDateRangeSync(0, Long.MAX_VALUE)
            var invalidDriverCount = 0
            var invalidCompanyCount = 0
            
            allTrips.forEach { trip ->
                if (driverRepository.getDriverById(trip.driverId) == null) invalidDriverCount++
                if (companyRepository.getCompanyById(trip.companyId) == null) invalidCompanyCount++
            }
            results["Orphaned_Driver_Trips"] = invalidDriverCount
            results["Orphaned_Company_Trips"] = invalidCompanyCount
            
            // 2. Sync Completeness
            val unsyncedTrips = tripDao.getAllUnsyncedTripsRaw().size
            results["Pending_Uploads_Trips"] = unsyncedTrips
            
            val drivers = driverRepository.getAllDrivers().first()
            val unsyncedDrivers = drivers.count { it.firestoreId == null }
            results["Pending_Uploads_Drivers"] = unsyncedDrivers
            
            Log.d("IntegrityCheck", "Orphans: D=$invalidDriverCount C=$invalidCompanyCount | Pending: T=$unsyncedTrips D=$unsyncedDrivers")
        }
        
        return results
    }
}
