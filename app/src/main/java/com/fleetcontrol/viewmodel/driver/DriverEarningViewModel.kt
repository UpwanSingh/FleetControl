package com.fleetcontrol.viewmodel.driver

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.domain.calculators.DriverEarningCalculator
import com.fleetcontrol.domain.calculators.DriverPayableSummary
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.core.SessionState
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for driver earnings view
 * Implements Section 5.1, 7.3, 8.1, 9.1 of BUSINESS_LOGIC_SPEC.md
 * 
 * Driver CAN (Section 9.1):
 * - View own earnings
 * - View own advance balance & history (read-only)
 * 
 * REACTIVE: Automatically updates earnings when trips change
 */
class DriverEarningViewModel(
    private val driverEarningCalculator: DriverEarningCalculator,
    private val sessionManager: SessionManager,
    private val tripRepository: TripRepository
) : BaseViewModel() {
    
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    
    private val _todayPayable = MutableStateFlow<DriverPayableSummary?>(null)
    val todayPayable: StateFlow<DriverPayableSummary?> = _todayPayable.asStateFlow()
    
    private val _monthlyPayable = MutableStateFlow<DriverPayableSummary?>(null)
    val monthlyPayable: StateFlow<DriverPayableSummary?> = _monthlyPayable.asStateFlow()
    
    init {
        // Observe session changes and start reactive observation when driver logs in
        viewModelScope.launch {
            sessionManager.currentSession.collectLatest { session ->
                if (session is SessionState.DriverSession) {
                    observeTripsAndUpdateEarnings(session.driverId)
                }
            }
        }
    }
    
    /**
     * REACTIVE: Observe trips for this driver and recalculate earnings when trips change
     * This ensures earnings auto-update when a new trip is logged
     */
    private fun observeTripsAndUpdateEarnings(driverId: Long) {
        viewModelScope.launch {
            // Today's range
            val todayStart = DateUtils.getStartOfToday()
            val todayEnd = DateUtils.getEndOfToday()
            
            // Observe today's trips
            tripRepository.getTripsByDriverAndDateRange(driverId, todayStart, todayEnd)
                .distinctUntilChanged()
                .collectLatest { _ ->
                    // Trips changed - recalculate today's earnings
                    try {
                        _todayPayable.value = driverEarningCalculator.calculateDailyPayable(
                            driverId, todayStart, todayEnd
                        )
                    } catch (e: Exception) {
                        _error.value = e.message ?: "Failed to calculate today's earnings"
                    }
                }
        }
        
        // Observe monthly trips
        viewModelScope.launch {
            val monthStart = DateUtils.getStartOfMonth(_selectedYear.value, _selectedMonth.value)
            val monthEnd = DateUtils.getEndOfMonth(_selectedYear.value, _selectedMonth.value)
            
            tripRepository.getTripsByDriverAndDateRange(driverId, monthStart, monthEnd)
                .distinctUntilChanged()
                .collectLatest { _ ->
                    // Trips changed - recalculate monthly earnings
                    try {
                        _monthlyPayable.value = driverEarningCalculator.calculateNetPayable(
                            driverId, monthStart, monthEnd
                        )
                    } catch (e: Exception) {
                        _error.value = e.message ?: "Failed to calculate monthly earnings"
                    }
                }
        }
        
        _isLoading.value = false
    }
    
    /**
     * Load earnings data for current driver - manual refresh
     * Per Section 9.1: Driver can only view own earnings
     */
    fun loadEarningsData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val driverId = sessionManager.getCurrentDriverId()
                    ?: throw IllegalStateException("Driver not logged in")
                
                // Today's earnings
                val todayStart = DateUtils.getStartOfToday()
                val todayEnd = DateUtils.getEndOfToday()
                _todayPayable.value = driverEarningCalculator.calculateDailyPayable(
                    driverId, todayStart, todayEnd
                )
                
                // Monthly earnings
                val monthStart = DateUtils.getStartOfMonth(_selectedYear.value, _selectedMonth.value)
                val monthEnd = DateUtils.getEndOfMonth(_selectedYear.value, _selectedMonth.value)
                _monthlyPayable.value = driverEarningCalculator.calculateNetPayable(
                    driverId, monthStart, monthEnd
                )
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load earnings"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        // Trigger reactive reload for new month
        viewModelScope.launch {
            val driverId = sessionManager.getCurrentDriverId() ?: return@launch
            observeTripsAndUpdateEarnings(driverId)
        }
    }
    
    /**
     * Force Sync functionality (P1 Requirement)
     * Triggers manual sync of pending trips and reloads data
     */
    fun forceSync() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                tripRepository.syncPendingTrips()
                loadEarningsData()
            } catch (e: Exception) {
                _error.value = "Sync failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
