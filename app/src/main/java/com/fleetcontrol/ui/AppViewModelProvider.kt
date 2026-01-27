package com.fleetcontrol.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fleetcontrol.FleetControlApplication
import com.fleetcontrol.viewmodel.auth.LoginViewModel
import com.fleetcontrol.viewmodel.driver.DriverEarningViewModel
import com.fleetcontrol.viewmodel.driver.DriverFuelViewModel
import com.fleetcontrol.viewmodel.driver.DriverTripViewModel
import com.fleetcontrol.viewmodel.driver.DriverReportsViewModel
import com.fleetcontrol.viewmodel.owner.ClientManagementViewModel
import com.fleetcontrol.viewmodel.owner.CompanyViewModel
import com.fleetcontrol.viewmodel.owner.DriverManagementViewModel
import com.fleetcontrol.viewmodel.owner.OwnerDashboardViewModel
import com.fleetcontrol.viewmodel.owner.PickupViewModel
import com.fleetcontrol.viewmodel.owner.ProfitViewModel
import com.fleetcontrol.viewmodel.owner.ReportsViewModel
import com.fleetcontrol.viewmodel.settings.SubscriptionViewModel
import com.fleetcontrol.viewmodel.settings.SecurityViewModel
import com.fleetcontrol.viewmodel.settings.BackupViewModel
import com.fleetcontrol.viewmodel.settings.RateSettingsViewModel
import com.fleetcontrol.viewmodel.settings.MigrationViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            LoginViewModel(
                fleetControlApplication().container.driverRepository,
                fleetControlApplication().container.sessionManager,
                fleetControlApplication().container.appSettings,
                fleetControlApplication().container.cloudMasterDataRepository,
                fleetControlApplication().container.cloudTripRepository
            )
        }
        
        initializer {
            OwnerDashboardViewModel(
                fleetControlApplication().container.tripRepository,
                fleetControlApplication().container.driverRepository,
                fleetControlApplication().container.monthlyAggregationCalculator,
                fleetControlApplication().container.cloudTripRepository,
                fleetControlApplication().container.cloudFuelRepository
            )
        }
        
        initializer {
            DriverManagementViewModel(
                fleetControlApplication().container.driverRepository,
                fleetControlApplication().container.tripRepository,
                fleetControlApplication().container.advanceRepository,
                fleetControlApplication().container.driverEarningCalculator,
                fleetControlApplication().container.cloudTripRepository
            )
        }
        
        initializer {
            CompanyViewModel(
                fleetControlApplication().container.companyRepository
            )
        }
        
        initializer {
            PickupViewModel(
                fleetControlApplication().container.pickupRepository
            )
        }
        
        initializer {
            ClientManagementViewModel(
                fleetControlApplication().container.clientRepository,
                fleetControlApplication().container.pickupClientDistanceRepository,
                fleetControlApplication().container.pickupRepository
            )
        }
        
        initializer {
            ProfitViewModel(
                fleetControlApplication().container.monthlyAggregationCalculator,
                fleetControlApplication().container.tripRepository
            )
        }
        
        initializer {
            ReportsViewModel(
                fleetControlApplication().container.tripRepository,
                fleetControlApplication().container.featureGate,
                fleetControlApplication().container.csvExportService,
                fleetControlApplication().container.pdfExportService
            )
        }
        
        initializer {
            DriverTripViewModel(
                fleetControlApplication().container.tripRepository,
                fleetControlApplication().container.tripAttachmentRepository,
                fleetControlApplication().container.companyRepository,
                fleetControlApplication().container.clientRepository,
                fleetControlApplication().container.pickupRepository,
                fleetControlApplication().container.pickupClientDistanceRepository,
                fleetControlApplication().container.rateSlabResolver,
                fleetControlApplication().container.labourCostDao,
                fleetControlApplication().container.sessionManager,
                fleetControlApplication().container.driverRepository,
                fleetControlApplication().container.imageCaptureService,
                fleetControlApplication().container.cloudTripRepository
            )
        }
        
        initializer {
            DriverFuelViewModel(
                fleetControlApplication().container.fuelRepository,
                fleetControlApplication().container.cloudFuelRepository,
                fleetControlApplication().container.driverRepository,
                fleetControlApplication().container.sessionManager
            )
        }
        
        initializer {
            DriverEarningViewModel(
                fleetControlApplication().container.driverEarningCalculator,
                fleetControlApplication().container.sessionManager,
                fleetControlApplication().container.tripRepository
            )
        }
        
        initializer {
            DriverReportsViewModel(
                fleetControlApplication().container.tripRepository,
                fleetControlApplication().container.fuelRepository,
                fleetControlApplication().container.sessionManager,
                fleetControlApplication().container.driverCsvExportService,
                fleetControlApplication().container.driverPdfExportService
            )
        }
        
        initializer {
            SubscriptionViewModel(
                fleetControlApplication().container.billingService
            )
        }
        
        initializer {
            SecurityViewModel(
                fleetControlApplication().container.appSettings
            )
        }
        
        initializer {
            BackupViewModel(
                fleetControlApplication().container.backupService
            )
        }
        initializer {
            com.fleetcontrol.viewmodel.settings.RateSettingsViewModel(
                fleetControlApplication().container.rateSlabRepository,
                fleetControlApplication().container.labourCostDao
            )
        }
        
        initializer {
            com.fleetcontrol.viewmodel.settings.MigrationViewModel(
                fleetControlApplication().container.dataMigrationManager
            )
        }
    }
}

fun CreationExtras.fleetControlApplication(): FleetControlApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FleetControlApplication)
