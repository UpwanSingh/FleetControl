package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import android.util.Log
import com.fleetcontrol.data.entities.DriverEntity
import com.fleetcontrol.data.repositories.AdvanceRepository
import com.fleetcontrol.data.repositories.DriverRepository
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.domain.calculators.DriverEarningCalculator
import com.fleetcontrol.domain.calculators.DriverPayableSummary
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for driver management
 * Implements Section 9.2 (Owner permissions) of BUSINESS_LOGIC_SPEC.md
 * 
 * Owner CAN:
 * - Add / delete drivers
 * - View ALL drivers (active & inactive) per Section 10
 * - Deactivate and Reactivate drivers
 * 
 * REACTIVE UI: Uses Flow to automatically update when data changes
 * - Driver list updates when drivers change
 * - Earnings update when trips change
 */
class DriverManagementViewModel(
    private val driverRepository: DriverRepository,
    private val tripRepository: TripRepository,
    private val advanceRepository: AdvanceRepository,
    private val driverEarningCalculator: DriverEarningCalculator,
    private val cloudTripRepository: com.fleetcontrol.data.repositories.CloudTripRepository
) : BaseViewModel() {
    
    private val _drivers = MutableStateFlow<List<DriverWithStats>>(emptyList())
    val drivers: StateFlow<List<DriverWithStats>> = _drivers.asStateFlow()
    
    private val _inactiveDrivers = MutableStateFlow<List<DriverWithStats>>(emptyList())
    val inactiveDrivers: StateFlow<List<DriverWithStats>> = _inactiveDrivers.asStateFlow()
    
    // Filter: All, Active, Inactive
    private val _driverFilter = MutableStateFlow(DriverFilter.ACTIVE)
    val driverFilter: StateFlow<DriverFilter> = _driverFilter.asStateFlow()
    
    private val _advances = MutableStateFlow<List<com.fleetcontrol.data.entities.AdvanceEntity>>(emptyList())
    val advances: StateFlow<List<com.fleetcontrol.data.entities.AdvanceEntity>> = _advances.asStateFlow()
    
    private val _selectedDriver = MutableStateFlow<DriverEntity?>(null)
    val selectedDriver: StateFlow<DriverEntity?> = _selectedDriver.asStateFlow()
    
    private val _driverDetail = MutableStateFlow<DriverDetailState?>(null)
    val driverDetail: StateFlow<DriverDetailState?> = _driverDetail.asStateFlow()
    
    // Month boundaries for current month earnings
    private val monthStart = DateUtils.getStartOfMonth(
        DateUtils.getCurrentYear(),
        DateUtils.getCurrentMonth()
    )
    private val monthEnd = DateUtils.getEndOfMonth(
        DateUtils.getCurrentYear(),
        DateUtils.getCurrentMonth()
    )
    
    init {
        // REACTIVE: Observe drivers with Cloud Stats
        observeActiveDriversWithCloudStats()
        observeInactiveDriversWithCloudStats()
    }
    
    /**
     * REACTIVE: Observe active drivers AND Cloud Stats together
     * Uses Unified Cloud Source for consistency with Dashboard
     */
    private fun observeActiveDriversWithCloudStats() {
        viewModelScope.launch {
            val currentYear = DateUtils.getCurrentYear()
            val currentMonth = DateUtils.getCurrentMonth() + 1 // FIX: 1-based index (Match OwnerDashboard)
            
            combine(
                driverRepository.getActiveDrivers(),
                cloudTripRepository.getDriverStatsFlow(currentYear, currentMonth)
            ) { driverList, cloudStats ->
                Pair(driverList, cloudStats)
            }.collect { (driverList, cloudStats) ->
                updateDriversWithStats(driverList, cloudStats, isActive = true)
            }
        }
    }
    
    /**
     * REACTIVE: Observe inactive drivers AND Cloud Stats together
     */
    private fun observeInactiveDriversWithCloudStats() {
        viewModelScope.launch {
            val currentYear = DateUtils.getCurrentYear()
            val currentMonth = DateUtils.getCurrentMonth() + 1 // FIX: 1-based index
            
            combine(
                driverRepository.getInactiveDrivers(),
                cloudTripRepository.getDriverStatsFlow(currentYear, currentMonth)
            ) { driverList, cloudStats ->
                Pair(driverList, cloudStats)
            }.collect { (driverList, cloudStats) ->
                updateDriversWithStats(driverList, cloudStats, isActive = false)
            }
        }
    }
    
    /**
     * Update drivers with their stats using Cloud Data (Live)
     * @param cloudStats List of stats from Cloud. If null (Manual Refresh), fallback to Local Calculator.
     */
    private suspend fun updateDriversWithStats(
        driverList: List<DriverEntity>,
        cloudStats: List<com.fleetcontrol.data.entities.DriverStats>? = null,
        isActive: Boolean
    ) {
        val driversWithStats = driverList.map { driver ->
            val earnings = if (cloudStats != null) {
                // Cloud Mode: Fast & Accurate
                val driverStat = cloudStats.find { it.driverId == driver.firestoreId || it.driverId == driver.id.toString() }
                driverStat?.totalDriverEarnings ?: 0.0
            } else {
                // Local Fallback Mode (Manual Refresh without Cloud Data)
                driverEarningCalculator.calculateGrossEarnings(
                    driver.id, monthStart, monthEnd
                )
            }
            
            val advanceBalance = advanceRepository.getOutstandingBalance(driver.id)
            
            DriverWithStats(
                driver = driver,
                monthlyEarnings = earnings,
                outstandingAdvance = advanceBalance
            )
        }
        
        if (isActive) {
            _drivers.value = driversWithStats
        } else {
            _inactiveDrivers.value = driversWithStats
        }
        _isLoading.value = false
    }
    
    /**
     * Set driver filter
     */
    fun setDriverFilter(filter: DriverFilter) {
        _driverFilter.value = filter
    }
    
    /**
     * Load all drivers - for manual refresh
     */
    fun loadDrivers() {
        _isLoading.value = true
        // The reactive flows will automatically pick up any changes
        // Just trigger a re-collection by forcing a state update
        viewModelScope.launch {
            try {
                // Force refresh by re-collecting
                val activeDrivers = driverRepository.getActiveDriversOnce()
                updateDriversWithStats(activeDrivers, isActive = true)
                
                val inactiveDrivers = driverRepository.getInactiveDriversOnce()
                updateDriversWithStats(inactiveDrivers, isActive = false)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load drivers"
            }
            _isLoading.value = false
        }
    }
    
    /**
     * Add new driver
     */
    fun addDriver(name: String, phone: String, pin: String) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            try {
                val driver = DriverEntity(
                    name = name.trim(),
                    phone = phone.trim(),
                    pin = pin.trim(),
                    isActive = true
                )
                driverRepository.insert(driver)
                loadDrivers()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add driver"
            }
        }
    }
    
    /**
     * Update driver
     */
    fun updateDriver(driver: DriverEntity) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            try {
                driverRepository.update(driver)
                loadDrivers()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update driver"
            }
        }
    }
    
    /**
     * Deactivate driver (soft delete)
     */
    fun deactivateDriver(driverId: Long) {
        viewModelScope.launch {
            try {
                driverRepository.deactivate(driverId)
                // REACTIVE: Flow will auto-update, no manual loadDrivers() needed
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to deactivate driver"
            }
        }
    }
    
    /**
     * Reactivate driver
     */
    fun reactivateDriver(driverId: Long) {
        viewModelScope.launch {
            try {
                driverRepository.reactivate(driverId)
                // REACTIVE: Flow will auto-update, no manual loadDrivers() needed
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to reactivate driver"
            }
        }
    }
    
    /**
     * Load driver detail with settlement summary
     */
    fun loadDriverDetail(driverId: Long) {
        viewModelScope.launch {
            try {
                // Load advances history
                launch {
                    advanceRepository.getAdvancesByDriver(driverId).collect {
                        _advances.value = it
                    }
                }

                val driver = driverRepository.getDriverById(driverId)
                driver?.let {
                    val monthStart = DateUtils.getStartOfMonth(
                        DateUtils.getCurrentYear(),
                        DateUtils.getCurrentMonth()
                    )
                    val monthEnd = DateUtils.getEndOfMonth(
                        DateUtils.getCurrentYear(),
                        DateUtils.getCurrentMonth()
                    )
                    
                    val payableSummary = driverEarningCalculator.calculateNetPayable(
                        driverId, monthStart, monthEnd
                    )
                    
                    val tripCount = tripRepository.getTripCountByDriver(driverId, monthStart, monthEnd)
                    
                    _driverDetail.value = DriverDetailState(
                        driver = it,
                        monthlyPayable = payableSummary,
                        totalTrips = tripCount
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load driver detail"
            }
        }
    }

    /**
     * Add advance payment for driver
     */
    fun addAdvance(driverId: Long, amount: Double, notes: String) {
        viewModelScope.launch {
            try {
                // Section 7.1: Advance is recorded as a negative entry in driver ledger
                // or specific advance entity
                advanceRepository.addAdvance(driverId, amount, notes)
                loadDriverDetail(driverId)
                loadDrivers() // Refresh list stats too
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add advance"
            }
        }
    }
}

data class DriverWithStats(
    val driver: DriverEntity,
    val monthlyEarnings: Double,
    val outstandingAdvance: Double
)

data class DriverDetailState(
    val driver: DriverEntity,
    val monthlyPayable: DriverPayableSummary,
    val totalTrips: Int = 0
)

enum class DriverFilter {
    ALL,
    ACTIVE,
    INACTIVE
}
