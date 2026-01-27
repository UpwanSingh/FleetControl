package com.fleetcontrol

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.navigation.NavGraph
import com.fleetcontrol.ui.theme.FleetControlTheme
import com.fleetcontrol.viewmodel.auth.LoginViewModel
import com.fleetcontrol.viewmodel.driver.DriverEarningViewModel
import com.fleetcontrol.viewmodel.driver.DriverFuelViewModel
import com.fleetcontrol.viewmodel.driver.DriverTripViewModel
import com.fleetcontrol.viewmodel.owner.DriverManagementViewModel
import com.fleetcontrol.viewmodel.owner.OwnerDashboardViewModel
import com.fleetcontrol.viewmodel.owner.ProfitViewModel
import com.fleetcontrol.viewmodel.owner.ReportsViewModel

/**
 * Main Activity - Entry point for the app
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // CHECK 1: Global Application Startup Error
            if (com.fleetcontrol.FleetControlApplication.startupError != null) {
                throw RuntimeException("Application Init Failed", com.fleetcontrol.FleetControlApplication.startupError)
            }

            val app = application as com.fleetcontrol.FleetControlApplication

            // DIAGNOSTIC: Pre-flight check of all dependencies to catch startup crashes
            val container = app.container
            val diagnostics = listOf(
                "TripRepo" to { container.tripRepository },
                "DriverRepo" to { container.driverRepository },
                "CompanyRepo" to { container.companyRepository },
                "Settings" to { container.appSettings },
                "ProfitCalc" to { container.monthlyAggregationCalculator }
            )
            
            diagnostics.forEach { (name, access) ->
                try {
                    access()
                } catch (e: Exception) {
                    throw RuntimeException("Failed to init $name: ${e.message}", e)
                }
            }
            
            setContent {
                FleetControlTheme {
                    val focusManager = LocalFocusManager.current
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus()
                                })
                            },
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // ViewModels
                        val loginViewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val ownerDashboardViewModel: OwnerDashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val driverManagementViewModel: DriverManagementViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val profitViewModel: ProfitViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val reportsViewModel: ReportsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val driverTripViewModel: DriverTripViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val driverFuelViewModel: DriverFuelViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val driverEarningViewModel: DriverEarningViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val companyViewModel: com.fleetcontrol.viewmodel.owner.CompanyViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val pickupViewModel: com.fleetcontrol.viewmodel.owner.PickupViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val clientManagementViewModel: com.fleetcontrol.viewmodel.owner.ClientManagementViewModel = viewModel(factory = AppViewModelProvider.Factory)

                        NavGraph(
                            loginViewModel = loginViewModel,
                            ownerDashboardViewModel = ownerDashboardViewModel,
                            driverManagementViewModel = driverManagementViewModel,
                            profitViewModel = profitViewModel,
                            reportsViewModel = reportsViewModel,
                            driverTripViewModel = driverTripViewModel,
                            driverFuelViewModel = driverFuelViewModel,
                            driverEarningViewModel = driverEarningViewModel,
                            companyViewModel = companyViewModel,
                            pickupViewModel = pickupViewModel,
                            clientManagementViewModel = clientManagementViewModel
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback for critical initialization failure
            setContent {
                MaterialTheme {
                    ErrorDisplay(e)
                }
            }
        }
    }
}

@Composable
fun ErrorDisplay(e: Throwable) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Text(
            text = "CRITICAL STARTUP ERROR",
            style = MaterialTheme.typography.headlineMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Text(
            text = if (BuildConfig.DEBUG) (e.message ?: "Unknown Error") else "Something went wrong while starting the app.",
            style = MaterialTheme.typography.bodyLarge
        )
        if (BuildConfig.DEBUG) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = e.stackTraceToString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
