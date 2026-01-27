package com.fleetcontrol.data

import android.content.Context
import com.fleetcontrol.data.database.AppDatabase
import com.fleetcontrol.data.repositories.*
import com.fleetcontrol.data.dao.LabourCostDao
import com.fleetcontrol.domain.calculators.DriverEarningCalculator
import com.fleetcontrol.domain.calculators.MonthlyAggregationCalculator
import com.fleetcontrol.domain.calculators.OwnerProfitCalculator
import com.fleetcontrol.domain.entitlements.FeatureGate
import com.fleetcontrol.domain.rulesengine.RateSlabResolver
import com.fleetcontrol.services.export.CsvExportService
import com.fleetcontrol.services.export.PdfExportService
import com.fleetcontrol.services.export.DriverCsvExportService
import com.fleetcontrol.services.export.DriverPdfExportService
import com.fleetcontrol.services.billing.BillingService
import com.fleetcontrol.services.backup.BackupService
import com.fleetcontrol.services.notification.NotificationService
import com.fleetcontrol.services.ImageCaptureService
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.core.AppSettings
import com.fleetcontrol.data.managers.DataMigrationManager
import kotlinx.coroutines.flow.first

/**
 * App Container for Dependency Injection
 */
interface AppContainer {
    // Repositories
    val companyRepository: CompanyRepository
    val clientRepository: ClientRepository
    val driverRepository: DriverRepository
    val pickupRepository: PickupLocationRepository
    val pickupClientDistanceRepository: PickupClientDistanceRepository
    val tripRepository: TripRepository
    val tripAttachmentRepository: TripAttachmentRepository
    val fuelRepository: FuelRepository
    val advanceRepository: AdvanceRepository
    val profitRepository: ProfitRepository
    val rateSlabRepository: RateSlabRepository
    val subscriptionRepository: SubscriptionRepository
    
    // DAOs
    val labourCostDao: LabourCostDao
    
    // Calculators
    val driverEarningCalculator: DriverEarningCalculator
    val ownerProfitCalculator: OwnerProfitCalculator
    val monthlyAggregationCalculator: MonthlyAggregationCalculator
    
    // Domain Services
    val rateSlabResolver: RateSlabResolver
    val featureGate: FeatureGate
    
    // WorkManager
    val syncWorkManager: com.fleetcontrol.work.SyncWorkManager
    
    // Core & Services
    val sessionManager: SessionManager
    val csvExportService: CsvExportService
    val pdfExportService: PdfExportService
    val driverCsvExportService: DriverCsvExportService
    val driverPdfExportService: DriverPdfExportService
    val imageCaptureService: ImageCaptureService
    val billingService: BillingService
    val notificationService: NotificationService
    val backupService: BackupService
    val appSettings: AppSettings
    val cloudTripRepository: CloudTripRepository
    val cloudFuelRepository: CloudFuelRepository
    val cloudMasterDataRepository: CloudMasterDataRepository
    val dataMigrationManager: com.fleetcontrol.data.managers.DataMigrationManager
    val authService: com.fleetcontrol.services.AuthService
    val firestore: com.google.firebase.firestore.FirebaseFirestore
    
    /**
     * Multi-Tenancy: Sync the current owner's ID from AuthService to all cloud repositories.
     * MUST be called after owner login before any data operations.
     */
    fun syncOwnerId()
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val cloudMasterDataRepository: CloudMasterDataRepository by lazy {
        CloudMasterDataRepository()
    }

    override val companyRepository: CompanyRepository by lazy {
        CompanyRepository(database.companyDao(), cloudMasterDataRepository)
    }
    
    override val clientRepository: ClientRepository by lazy {
        ClientRepository(database.clientDao(), cloudMasterDataRepository)
    }

    override val driverRepository: DriverRepository by lazy {
        DriverRepository(database.driverDao(), cloudMasterDataRepository)
    }

    override val pickupRepository: PickupLocationRepository by lazy {
        PickupLocationRepository(database.pickupLocationDao(), cloudMasterDataRepository)
    }
    
    override val pickupClientDistanceRepository: PickupClientDistanceRepository by lazy {
        PickupClientDistanceRepository(
            database.pickupClientDistanceDao(),
            cloudMasterDataRepository,
            database.pickupLocationDao(),
            database.clientDao()
        )
    }
    
    override val tripRepository: TripRepository by lazy {
        TripRepository(
            database.tripDao(),
            cloudTripRepository,
            driverRepository,
            companyRepository,
            pickupRepository
        )
    }
    
    override val tripAttachmentRepository: TripAttachmentRepository by lazy {
        TripAttachmentRepository(database.tripAttachmentDao())
    }
    
    override val fuelRepository: FuelRepository by lazy {
        FuelRepository(database.fuelDao(), cloudMasterDataRepository, database.driverDao())
    }
    
    override val advanceRepository: AdvanceRepository by lazy {
        AdvanceRepository(database.advanceDao(), cloudMasterDataRepository, database.driverDao())
    }

    override val profitRepository: ProfitRepository by lazy {
        ProfitRepository(database.tripDao())
    }
    
    override val rateSlabRepository: RateSlabRepository by lazy {
        RateSlabRepository(database.rateSlabDao(), cloudMasterDataRepository)
    }
    
    override val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(database.subscriptionDao())
    }
    
    override val labourCostDao: LabourCostDao by lazy {
        database.labourCostDao()
    }
    
    // Domain Services
    override val driverEarningCalculator: DriverEarningCalculator by lazy {
        DriverEarningCalculator(tripRepository, fuelRepository, advanceRepository)
    }
    
    override val ownerProfitCalculator: OwnerProfitCalculator by lazy {
        OwnerProfitCalculator(tripRepository)
    }
    
    override val monthlyAggregationCalculator: MonthlyAggregationCalculator by lazy {
        MonthlyAggregationCalculator(ownerProfitCalculator, driverEarningCalculator)
    }
    
    override val rateSlabResolver: RateSlabResolver by lazy {
        RateSlabResolver(rateSlabRepository)
    }
    
    override val featureGate: FeatureGate by lazy {
        FeatureGate(subscriptionRepository)
    }
    
    override val sessionManager: SessionManager by lazy {
        SessionManager(cloudMasterDataRepository, cloudTripRepository)
    }
    
    override val csvExportService: CsvExportService by lazy {
        CsvExportService(context.getExternalFilesDir(null) ?: context.filesDir)
    }
    
    override val pdfExportService: PdfExportService by lazy {
        PdfExportService(context, context.getExternalFilesDir(null) ?: context.filesDir)
    }
    
    override val driverCsvExportService: DriverCsvExportService by lazy {
        DriverCsvExportService(context.getExternalFilesDir(null) ?: context.filesDir)
    }
    
    override val driverPdfExportService: DriverPdfExportService by lazy {
        DriverPdfExportService(context, context.getExternalFilesDir(null) ?: context.filesDir)
    }
    
    override val imageCaptureService: ImageCaptureService by lazy {
        ImageCaptureService(context)
    }
    
    override val billingService: BillingService by lazy {
        BillingService(context)
    }
    
    override val backupService: BackupService by lazy {
        BackupService(context)
    }
    
    override val notificationService: NotificationService by lazy {
        NotificationService(context)
    }
    
    override val appSettings: AppSettings by lazy {
        AppSettings(context)
    }
    
    override val syncWorkManager: com.fleetcontrol.work.SyncWorkManager by lazy {
        com.fleetcontrol.work.SyncWorkManager(context)
    }

    override val cloudTripRepository: CloudTripRepository by lazy {
        CloudTripRepository()
    }
    
    override val cloudFuelRepository: CloudFuelRepository by lazy {
        CloudFuelRepository(firestore, com.google.firebase.auth.FirebaseAuth.getInstance())
    }
    
    override val dataMigrationManager: DataMigrationManager by lazy {
        DataMigrationManager(
            tripDao = database.tripDao(),
            cloudTripRepository = cloudTripRepository,
            driverRepository = driverRepository,
            companyRepository = companyRepository,
            pickupLocationRepository = pickupRepository,
            clientRepository = clientRepository,
            cloudMasterDataRepository = cloudMasterDataRepository,
            rateSlabRepository = rateSlabRepository,
            pickupClientDistanceRepository = pickupClientDistanceRepository,
            fuelRepository = fuelRepository,
            advanceRepository = advanceRepository
        )
    }

    override val authService: com.fleetcontrol.services.AuthService by lazy {
        com.fleetcontrol.services.AuthService()
    }
    
    override val firestore: com.google.firebase.firestore.FirebaseFirestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }
    
    /**
     * Multi-Tenancy: Sync ownerId from AuthService to all cloud repositories.
     * This MUST be called after owner login and before any data operations.
     */
    override fun syncOwnerId() {
        val ownerId = authService.currentOwnerId ?: kotlinx.coroutines.runBlocking { 
            appSettings.linkedOwnerId.first()
        } ?: ""
        
        if (ownerId.isNotEmpty()) {
            cloudMasterDataRepository.setOwnerId(ownerId)
            cloudTripRepository.setOwnerId(ownerId)
            cloudFuelRepository.setOwnerId(ownerId)
            android.util.Log.d("AppContainer", "Synced Owner ID: $ownerId")
        } else {
            android.util.Log.w("AppContainer", "No Owner ID found to sync")
        }
    }
}
