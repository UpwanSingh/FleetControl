package com.fleetcontrol.domain.calculators

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleetcontrol.data.dao.TripDao
import com.fleetcontrol.data.database.AppDatabase
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.entities.TripStatus
import com.fleetcontrol.data.repositories.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class DriverEarningCalculatorIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var tripRepo: TripRepository
    private lateinit var fuelRepo: FuelRepository
    private lateinit var advanceRepo: AdvanceRepository
    private lateinit var calculator: DriverEarningCalculator

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        
        // Mock dependencies requiring Cloud/Firebase
        val mockCloudTripRepo = mock(CloudTripRepository::class.java)
        // Stub ownerId to avoid flow errors
        `when`(mockCloudTripRepo.ownerId).thenReturn(kotlinx.coroutines.flow.MutableStateFlow("owner_1"))
        `when`(mockCloudTripRepo.currentOwnerId).thenReturn("owner_1")

        // We can mock other repos that are irrelevant to earnings calculation (like driver name resolution)
        val mockDriverRepo = mock(DriverRepository::class.java)
        val mockCompanyRepo = mock(CompanyRepository::class.java)
        val mockPickupRepo = mock(PickupLocationRepository::class.java)
        val mockCloudMasterDataRepo = mock(CloudMasterDataRepository::class.java)
        
        // DAO dependencies from InMemory DB
        val tripDao = db.tripDao()
        val fuelDao = db.fuelDao()
        val advanceDao = db.advanceDao()
        val driverDao = db.driverDao()

        // Initialize Real Repositories
        tripRepo = TripRepository(tripDao, mockCloudTripRepo, mockDriverRepo, mockCompanyRepo, mockPickupRepo)
        fuelRepo = FuelRepository(fuelDao, mockCloudMasterDataRepo, driverDao)
        advanceRepo = AdvanceRepository(advanceDao, mockCloudMasterDataRepo, driverDao)
        
        calculator = DriverEarningCalculator(tripRepo, fuelRepo, advanceRepo)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRealEarningCalculation() = runBlocking {
        // 1. Insert Dependencies (Foreign Keys)
        val driver = com.fleetcontrol.data.entities.DriverEntity(
            id = 101,
            firestoreId = "d1",
            name = "Test Driver",
            phone = "123",
            pin = "1234",
            isActive = true,
            ownerId = "owner_1"
        )
        db.driverDao().insert(driver)
        
        val company = com.fleetcontrol.data.entities.CompanyEntity(
            id = 1,
            firestoreId = "c1",
            name = "Test Company",
            perBagRate = 10.0,
            isActive = true,
            ownerId = "owner_1"
        )
        db.companyDao().insert(company)
        
        val client = com.fleetcontrol.data.entities.ClientEntity(
            id = 1,
            firestoreId = "cl1",
            name = "Test Client",
            ownerId = "owner_1"
        )
        db.clientDao().insert(client)
        
        val pickup = com.fleetcontrol.data.entities.PickupLocationEntity(
            id = 1,
            firestoreId = "p1",
            name = "Test Pickup",
            distanceFromBase = 10.0,
            ownerId = "owner_1"
        )
        db.pickupLocationDao().insert(pickup)
        
        // 2. Insert Trip
        val trip = TripEntity(
            driverId = 101,
            companyId = 1,
            pickupLocationId = 1,
            clientId = 1,
            bagCount = 100,
            snapshotDriverRate = 5.0,
            snapshotCompanyRate = 10.0, // ownerGross = 100 * 10 = 1000
            status = TripStatus.COMPLETED,
            tripDate = 1000L,
            ownerId = "owner_1"
        )
        db.tripDao().insert(trip)
        
        // 3. Check gross earnings
        val gross = calculator.calculateGrossEarnings(101, 0L, 2000L)
        assertEquals(500.0, gross, 0.01) // Driver Earning = 100 * 5 = 500
    }
}
