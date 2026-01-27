package com.fleetcontrol.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fleetcontrol.FleetControlApplication
import com.fleetcontrol.ui.auth.LoginScreen
import com.fleetcontrol.ui.auth.OwnerAuthScreen
import com.fleetcontrol.ui.driver.DriverSummaryScreen
import com.fleetcontrol.ui.driver.DriverTripScreen
import com.fleetcontrol.ui.driver.FuelEntryScreen
import com.fleetcontrol.ui.screens.driver.DriverReportsScreen
import com.fleetcontrol.ui.owner.OwnerDashboardScreen
import com.fleetcontrol.ui.owner.DriverStatusScreen
import com.fleetcontrol.ui.owner.DriverDetailScreen
import com.fleetcontrol.ui.owner.MonthlyProfitScreen
import com.fleetcontrol.ui.owner.CompanyScreen
import com.fleetcontrol.ui.owner.PickupScreen
import com.fleetcontrol.ui.owner.ClientManagementScreen
import com.fleetcontrol.ui.owner.ReportsScreen
import com.fleetcontrol.ui.settings.SubscriptionScreen
import com.fleetcontrol.ui.settings.HelpLegalScreen
import com.fleetcontrol.ui.settings.BackupRestoreScreen
import com.fleetcontrol.ui.settings.AboutScreen
import com.fleetcontrol.ui.settings.SecurityScreen
import com.fleetcontrol.viewmodel.auth.LoginViewModel
import com.fleetcontrol.viewmodel.owner.OwnerDashboardViewModel
import com.fleetcontrol.viewmodel.owner.DriverManagementViewModel
import com.fleetcontrol.viewmodel.owner.ProfitViewModel
import com.fleetcontrol.viewmodel.owner.ReportsViewModel
import com.fleetcontrol.viewmodel.driver.DriverTripViewModel
import com.fleetcontrol.viewmodel.driver.DriverFuelViewModel
import com.fleetcontrol.viewmodel.driver.DriverEarningViewModel

/**
 * Main navigation graph with full screen implementations
 */
@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    loginViewModel: LoginViewModel,
    ownerDashboardViewModel: OwnerDashboardViewModel,
    driverManagementViewModel: DriverManagementViewModel,
    profitViewModel: ProfitViewModel,
    reportsViewModel: ReportsViewModel,
    driverTripViewModel: DriverTripViewModel,
    driverFuelViewModel: DriverFuelViewModel,
    driverEarningViewModel: DriverEarningViewModel,
    companyViewModel: com.fleetcontrol.viewmodel.owner.CompanyViewModel,
    pickupViewModel: com.fleetcontrol.viewmodel.owner.PickupViewModel,
    clientManagementViewModel: com.fleetcontrol.viewmodel.owner.ClientManagementViewModel
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FleetControlApplication
    val authService = app.container.authService
    val appSettings = app.container.appSettings
    
    // Check if first launch (for onboarding)
    val isFirstLaunch by appSettings.isFirstLaunch.collectAsState(initial = false)
    
    // Check if driver already linked via invite code (for session restoration)
    val isDriverAccessGranted by appSettings.isDriverAccessGranted.collectAsState(initial = false)
    val linkedDriverId by appSettings.linkedDriverId.collectAsState(initial = null)
    val linkedOwnerId by appSettings.linkedOwnerId.collectAsState(initial = null)
    
    // Restore Driver Session on App Restart
    LaunchedEffect(isDriverAccessGranted, linkedDriverId, linkedOwnerId) {
        val currentDriverId = linkedDriverId
        val currentOwnerId = linkedOwnerId
        
        if (isDriverAccessGranted && currentDriverId != null && currentOwnerId != null) {
            // Restore session from saved settings
            app.container.sessionManager.setDriverSession(currentDriverId, currentOwnerId)
            app.container.cloudMasterDataRepository.setOwnerId(currentOwnerId)
            app.container.cloudTripRepository.setOwnerId(currentOwnerId)
            android.util.Log.d("NavGraph", "Restored driver session: driverId=$currentDriverId, ownerId=$currentOwnerId")
        }
    }
    
    // Multi-Tenancy: Determine start destination based on state
    val startDestination = when {
        isFirstLaunch -> Routes.ONBOARDING  // Show onboarding first
        isDriverAccessGranted && linkedDriverId != null -> Routes.DRIVER_HOME  // Driver already linked - go directly to driver home
        authService.isSignedIn && !authService.isAnonymous -> Routes.LOGIN  // Already authenticated owner - go to PIN login
        else -> Routes.OWNER_AUTH  // Need owner email login first
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // First-launch Onboarding
        composable(Routes.ONBOARDING) {
            com.fleetcontrol.ui.onboarding.OnboardingScreen(
                onComplete = {
                    // Mark onboarding as complete
                    kotlinx.coroutines.runBlocking {
                        appSettings.setFirstLaunchComplete()
                    }
                    // Navigate to auth flow
                    navController.navigate(Routes.OWNER_AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        
        // Multi-Tenancy: Owner Email/Password Auth
        composable(Routes.OWNER_AUTH) {
            OwnerAuthScreen(
                authService = authService,
                onAuthSuccess = {
                    // Sync ownerId to repositories
                    app.container.syncOwnerId()
                    // Navigate to PIN login
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.OWNER_AUTH) { inclusive = true }
                    }
                },
                onJoinAsDriver = {
                    navController.navigate(Routes.DRIVER_JOIN)
                }
            )
        }
        
        // Driver Join (Invite Code Entry)
        composable(Routes.DRIVER_JOIN) {
            com.fleetcontrol.ui.auth.DriverJoinScreen(
                onJoinSuccess = { ownerId, firestoreDriverId, driverName ->
                    // 1. Set Cloud repo ownerId - needed for all cloud operations
                    app.container.cloudMasterDataRepository.setOwnerId(ownerId)
                    app.container.cloudTripRepository.setOwnerId(ownerId)
                    
                    // 2. EXPLICIT: Fetch ALL data from Firestore and populate local DB
                    kotlinx.coroutines.runBlocking {
                        android.util.Log.d("NavGraph", "=== SYNCING ALL DATA FOR DRIVER ===")
                        
                        // 2a. Fetch and insert driver
                        val cloudDriver = app.container.cloudMasterDataRepository.getDriverByFirestoreId(firestoreDriverId)
                        val localDriverId: Long
                        if (cloudDriver != null) {
                            val localDriver = com.fleetcontrol.data.entities.DriverEntity(
                                firestoreId = firestoreDriverId,
                                ownerId = ownerId,
                                name = cloudDriver.name,
                                phone = cloudDriver.phone,
                                pin = cloudDriver.pin,
                                isActive = cloudDriver.isActive
                            )
                            localDriverId = app.container.driverRepository.insertRaw(localDriver)
                            android.util.Log.d("NavGraph", "Driver synced: ${cloudDriver.name}")
                        } else {
                            val fallbackDriver = com.fleetcontrol.data.entities.DriverEntity(
                                firestoreId = firestoreDriverId,
                                ownerId = ownerId,
                                name = driverName,
                                phone = "",
                                pin = "",
                                isActive = true
                            )
                            localDriverId = app.container.driverRepository.insertRaw(fallbackDriver)
                        }
                        
                        // 2b. Fetch and insert all COMPANIES
                        val companies = app.container.cloudMasterDataRepository.getAllCompaniesNow()
                        android.util.Log.d("NavGraph", "Syncing ${companies.size} companies...")
                        companies.forEach { fComp ->
                            val existing = app.container.companyRepository.getCompanyByFirestoreId(fComp.id)
                            if (existing == null) {
                                app.container.companyRepository.insertRaw(com.fleetcontrol.data.entities.CompanyEntity(
                                    firestoreId = fComp.id,
                                    ownerId = ownerId,
                                    name = fComp.name,
                                    contactPerson = fComp.contactPerson,
                                    contactPhone = fComp.contactPhone,
                                    perBagRate = fComp.perBagRate,
                                    isActive = fComp.isActive
                                ))
                            }
                        }
                        
                        // 2c. Fetch and insert all CLIENTS
                        val clients = app.container.cloudMasterDataRepository.getAllClientsNow()
                        android.util.Log.d("NavGraph", "Syncing ${clients.size} clients...")
                        clients.forEach { fClient ->
                            val existing = app.container.clientRepository.getClientByFirestoreId(fClient.id)
                            if (existing == null) {
                                app.container.clientRepository.insertRaw(com.fleetcontrol.data.entities.ClientEntity(
                                    firestoreId = fClient.id,
                                    ownerId = ownerId,
                                    name = fClient.name,
                                    address = fClient.address,
                                    contactPerson = fClient.contactPerson,
                                    contactPhone = fClient.contactPhone,
                                    notes = fClient.notes,
                                    isActive = fClient.isActive
                                ))
                            }
                        }
                        
                        // 2d. Fetch and insert all PICKUP LOCATIONS
                        val locations = app.container.cloudMasterDataRepository.getAllLocationsNow()
                        android.util.Log.d("NavGraph", "Syncing ${locations.size} pickup locations...")
                        locations.forEach { fLoc ->
                            val existing = app.container.pickupRepository.getLocationByFirestoreId(fLoc.id)
                            if (existing == null) {
                                app.container.pickupRepository.insertRaw(com.fleetcontrol.data.entities.PickupLocationEntity(
                                    firestoreId = fLoc.id,
                                    ownerId = ownerId,
                                    name = fLoc.name,
                                    distanceFromBase = fLoc.distanceFromBase,
                                    isActive = fLoc.isActive
                                ))
                            }
                        }
                        
                        // 2e. Fetch and insert all RATE SLABS
                        val slabs = app.container.cloudMasterDataRepository.getAllRateSlabsNow()
                        android.util.Log.d("NavGraph", "Syncing ${slabs.size} rate slabs...")
                        slabs.forEach { fSlab ->
                            val existing = app.container.rateSlabRepository.getRateSlabByFirestoreId(fSlab.id)
                            if (existing == null) {
                                app.container.rateSlabRepository.insertRaw(com.fleetcontrol.data.entities.DriverRateSlabEntity(
                                    firestoreId = fSlab.id,
                                    ownerId = ownerId,
                                    minDistance = fSlab.minDistance,
                                    maxDistance = fSlab.maxDistance,
                                    ratePerBag = fSlab.ratePerBag,
                                    isActive = fSlab.isActive
                                ))
                            }
                        }
                        
                        android.util.Log.d("NavGraph", "=== SYNC COMPLETE ===")
                        
                        // 3. Save driver access to settings
                        appSettings.setDriverAccessGranted(true, localDriverId, ownerId)
                        
                        // 4. Set driver session
                        app.container.sessionManager.setDriverSession(localDriverId, ownerId)
                    }
                    
                    // Navigate to driver home
                    navController.navigate(Routes.DRIVER_HOME) {
                        popUpTo(Routes.DRIVER_JOIN) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // PIN-based Auth
        composable(Routes.LOGIN) {
            // Ensure ownerId is synced in case we arrived from a fresh login
            LaunchedEffect(Unit) {
                app.container.syncOwnerId()
            }
            
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = { role, _ ->
                    if (role == com.fleetcontrol.data.entities.UserRole.OWNER) {
                        navController.navigate(Routes.OWNER_DASHBOARD) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.DRIVER_HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }
        
        // Owner screens
        composable(Routes.OWNER_DASHBOARD) {
            OwnerDashboardScreen(
                viewModel = ownerDashboardViewModel,
                onNavigateToDrivers = { navController.navigate(Routes.OWNER_DRIVERS) },
                onNavigateToCompanies = { navController.navigate(Routes.OWNER_COMPANIES) },
                onNavigateToPickups = { navController.navigate(Routes.OWNER_PICKUPS) },
                onNavigateToClients = { navController.navigate(Routes.OWNER_CLIENTS) },
                onNavigateToProfit = { navController.navigate(Routes.OWNER_PROFIT) },
                onNavigateToReports = { navController.navigate(Routes.OWNER_REPORTS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToPendingTrips = { navController.navigate(Routes.PENDING_TRIPS) },
                onNavigateToPendingFuel = { navController.navigate(Routes.PENDING_FUEL_REQUESTS) }
            )
        }
        
        composable(Routes.OWNER_DRIVERS) {
            DriverStatusScreen(
                viewModel = driverManagementViewModel,
                onDriverClick = { driverId -> 
                    navController.navigate(Routes.driverDetail(driverId)) 
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Routes.OWNER_DRIVER_DETAIL,
            arguments = listOf(navArgument("driverId") { type = NavType.LongType })
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getLong("driverId") ?: 0L
            DriverDetailScreen(
                viewModel = driverManagementViewModel,
                driverId = driverId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.OWNER_COMPANIES) {
            CompanyScreen(
                viewModel = companyViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.OWNER_PICKUPS) {
            PickupScreen(
                viewModel = pickupViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.OWNER_CLIENTS) {
            ClientManagementScreen(
                viewModel = clientManagementViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.OWNER_PROFIT) {
            MonthlyProfitScreen(
                viewModel = profitViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.OWNER_REPORTS) {
            ReportsScreen(
                viewModel = reportsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Pending Trips Approval (Security Hardening)
        composable(Routes.PENDING_TRIPS) {
            // Collect pending trips as state
            val pendingTripsFlow = remember { app.container.cloudTripRepository.getPendingTripsFlow() }
            val pendingTrips by pendingTripsFlow.collectAsState(initial = emptyList())
            val isLoadingState by ownerDashboardViewModel.isLoading.collectAsState()
            
            com.fleetcontrol.ui.owner.PendingTripsScreenSimple(
                pendingTrips = pendingTrips,
                isLoading = isLoadingState,
                onApprove = { tripId -> ownerDashboardViewModel.approveTrip(tripId) },
                onReject = { tripId, reason -> ownerDashboardViewModel.rejectTrip(tripId, reason) },
                onBack = { navController.popBackStack() }
            )
        }
        
        // Pending Fuel Requests (P1 Feature)
        composable(Routes.PENDING_FUEL_REQUESTS) {
            com.fleetcontrol.ui.owner.PendingFuelRequestsScreen(
                viewModel = ownerDashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Driver screens
        composable(Routes.DRIVER_HOME) {
            DriverSummaryScreen(
                viewModel = driverEarningViewModel,
                onNavigateToTrips = { navController.navigate(Routes.DRIVER_TRIPS) },
                onNavigateToFuel = { navController.navigate(Routes.DRIVER_FUEL) },
                onNavigateToHistory = { navController.navigate(Routes.DRIVER_HISTORY) },
                onNavigateToReports = { navController.navigate(Routes.DRIVER_REPORTS) },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP) },
                onLogout = {
                    loginViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DRIVER_HOME) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Routes.DRIVER_TRIPS) {
            DriverTripScreen(
                viewModel = driverTripViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.DRIVER_FUEL) {
            FuelEntryScreen(
                viewModel = driverFuelViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.DRIVER_HISTORY) {
            com.fleetcontrol.ui.driver.DriverHistoryScreen(
                tripViewModel = driverTripViewModel,
                fuelViewModel = driverFuelViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.DRIVER_REPORTS) {
            DriverReportsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DRIVER_EARNINGS) {
            DriverSummaryScreen(
                viewModel = driverEarningViewModel,
                onNavigateToTrips = { navController.navigate(Routes.DRIVER_TRIPS) },
                onNavigateToFuel = { navController.navigate(Routes.DRIVER_FUEL) },
                onNavigateToHistory = { navController.navigate(Routes.DRIVER_HISTORY) },
                onNavigateToReports = { navController.navigate(Routes.DRIVER_REPORTS) },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP) },
                onLogout = {
                    loginViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DRIVER_EARNINGS) { inclusive = true }
                    }
                }
            )
        }
        
        // Settings screens
        composable(Routes.SETTINGS) {
            AboutScreen(
                onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP) },
                onNavigateToSecurity = { navController.navigate(Routes.SECURITY) },
                onNavigateToRateSettings = { navController.navigate(Routes.RATE_SETTINGS) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onLogout = {
                    loginViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.SUBSCRIPTION) {
            SubscriptionScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.BACKUP) {
            BackupRestoreScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.ABOUT) {
            HelpLegalScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.SECURITY) {
            SecurityScreen(
                viewModel = viewModel(factory = com.fleetcontrol.ui.AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.RATE_SETTINGS) {
            com.fleetcontrol.ui.settings.RateSettingsScreen(
                viewModel = viewModel(factory = com.fleetcontrol.ui.AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
