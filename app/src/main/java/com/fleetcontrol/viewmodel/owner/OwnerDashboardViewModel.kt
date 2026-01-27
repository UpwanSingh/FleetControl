package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.domain.calculators.MonthlyAggregationCalculator
import com.fleetcontrol.domain.calculators.OwnerProfitSummary
import com.fleetcontrol.data.repositories.DriverRepository
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * ViewModel for owner dashboard
 * Implements Section 9.2, 10 of BUSINESS_LOGIC_SPEC.md
 * 
 * Owner has full visibility into all records per Section 10
 * 
 * REACTIVE: Automatically updates when trips change
 */
class OwnerDashboardViewModel(
    private val tripRepository: TripRepository,
    private val driverRepository: DriverRepository,
    private val aggregationCalculator: MonthlyAggregationCalculator,
    private val cloudTripRepository: com.fleetcontrol.data.repositories.CloudTripRepository,
    private val cloudFuelRepository: com.fleetcontrol.data.repositories.CloudFuelRepository
) : BaseViewModel() {
    
    // Dashboard state
    private val _dashboardState = MutableStateFlow(OwnerDashboardState())
    val dashboardState: StateFlow<OwnerDashboardState> = _dashboardState.asStateFlow()
    
    // PENDING APPROVALS (Security Hardening)
    private val _pendingTripsCount = MutableStateFlow(0)
    val pendingTripsCount: StateFlow<Int> = _pendingTripsCount.asStateFlow()
    
    private val _pendingFuelRequestsCount = MutableStateFlow(0)
    val pendingFuelRequestsCount: StateFlow<Int> = _pendingFuelRequestsCount.asStateFlow()
    
    private val _pendingFuelRequests = MutableStateFlow<List<com.fleetcontrol.data.entities.FirestoreFuelRequest>>(emptyList())
    val pendingFuelRequests: StateFlow<List<com.fleetcontrol.data.entities.FirestoreFuelRequest>> = _pendingFuelRequests.asStateFlow()
    
    init {
        // Start reactive observation of trips
        observeTripsAndUpdateDashboard()
        
        // Observe pending fuel requests count (Single source of truth)
        viewModelScope.launch {
            cloudFuelRepository.getPendingFuelRequestsFlow()
                .catch { e ->
                    android.util.Log.e("Dashboard", "Pending fuel error: ${e.message}")
                }
                .collect { requests ->
                    _pendingFuelRequests.value = requests
                    // Update state-based count
                    _dashboardState.value = _dashboardState.value.copy(
                        pendingFuelRequestsCount = requests.size
                    )
                    // Update flow-based count
                    _pendingFuelRequestsCount.value = requests.size
                }
        }
    }
    
    /**
     * REACTIVE: Observe trips and automatically recalculate dashboard stats
     * HYBRID: Observes Local Trips (for safety) AND Cloud Stats (for real-time)
     */
    private fun observeTripsAndUpdateDashboard() {
        // 1. Listen to Real-Time Cloud Updates (The "Seamless" Part)
        viewModelScope.launch {
            try {
                val currentYear = DateUtils.getCurrentYear()
                // FIX: Firestore stats are stored 1-based (Jan=1), but DateUtils returns 0-based.
                val currentMonth = DateUtils.getCurrentMonth() + 1
                
                // Combine Monthly Stats (Profit) and Today's Trip Count (Activity)
                kotlinx.coroutines.flow.combine(
                    cloudTripRepository.getDriverStatsFlow(currentYear, currentMonth),
                    cloudTripRepository.getTodayTripCountFlow(),
                    driverRepository.getActiveDrivers() // Flow<List<DriverEntity>>
                ) { cloudStats, todayCount, activeDrivers ->
                    Triple(cloudStats, todayCount, activeDrivers.size)
                }
                .catch { e -> 
                    // Firestore query failed (missing index, no network, etc.)
                    // Fall back to empty state, don't crash
                    android.util.Log.e("OwnerDashboardVM", "Error observing dashboard", e)
                    emit(Triple(emptyList(), 0, 0))
                }
                .collect { (cloudStats, todayCount, activeDriverCount) ->
                    val monthlyProfit = aggregationCalculator.calculateOwnerProfit(cloudStats)
                    
                    _dashboardState.value = _dashboardState.value.copy(
                        todayTripCount = todayCount,
                        todayProfit = monthlyProfit.netProfit / 30, // Approximate for today if needed, or use monthly
                        activeDriverCount = activeDriverCount,
                        monthlyProfitSummary = monthlyProfit,
                        isLoaded = true,
                        // Update sync timestamp on every real-time update
                        lastSyncedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                // Catch any initialization errors
                android.util.Log.e("Dashboard", "Failed to observe stats: ${e.message}")
                _error.value = "Cloud sync unavailable"
            }
        }
        
        // Separate flow for Pending Trips (Approval Queue)
        viewModelScope.launch {
            cloudTripRepository.getPendingTripsFlow().collect { pendingTrips ->
                _dashboardState.value = _dashboardState.value.copy(
                    pendingTripsCount = pendingTrips.size
                )
                // Keep standalone flow in sync
                _pendingTripsCount.value = pendingTrips.size
            }
        }
    }
    
    /**
     * Calculate and update dashboard data
     * @param providedCloudStats Optimization: use pushed stats
     * @param providedTodayCount Optimization: use pushed count
     * @param providedDriverCount Optimization: use pushed count
     */
    private suspend fun calculateAndUpdateDashboard(
        providedCloudStats: List<com.fleetcontrol.data.entities.DriverStats>? = null,
        providedTodayCount: Int? = null,
        providedDriverCount: Int? = null
    ) {
        try {
            // HYBRID/CLOUD Calculation
            // FIX: removed unused variables
            
            val cloudStats = providedCloudStats ?: emptyList()
            val todayTripCount = providedTodayCount ?: 0
            
            val totalMonthlyRevenue = cloudStats.sumOf { it.totalOwnerGross }
            val totalMonthlyLabour = cloudStats.sumOf { it.totalLabourCost }
            val totalMonthlyDriverEarnings = cloudStats.sumOf { it.totalDriverEarnings }
            
            // Net Profit = Revenue - DriverCost - LabourCost
            val totalMonthlyNetProfit = totalMonthlyRevenue - totalMonthlyDriverEarnings - totalMonthlyLabour

            val profitMargin = if (totalMonthlyRevenue > 0) {
                (totalMonthlyNetProfit / totalMonthlyRevenue) * 100
            } else {
                0.0
            }

            // 2. Fetch Active Driver Count (Reactive or Fallback)
            val activeDriverCount = providedDriverCount ?: driverRepository.getActiveDriverCount()
            
            // Map to UI Model
            val monthlyProfitSummary = OwnerProfitSummary(
                grossRevenue = totalMonthlyRevenue,
                driverEarnings = totalMonthlyDriverEarnings,
                labourCost = totalMonthlyLabour,
                netProfit = totalMonthlyNetProfit,
                tripCount = cloudStats.sumOf { it.totalTrips }.toInt(),
                totalBags = cloudStats.sumOf { it.totalBags }.toInt(),
                profitMargin = profitMargin
            )

            _dashboardState.value = OwnerDashboardState(
                todayTripCount = todayTripCount,
                todayProfit = monthlyProfitSummary.netProfit / 30, // Rough estimate
                activeDriverCount = activeDriverCount,
                monthlyProfitSummary = monthlyProfitSummary,
                isLoaded = true
            )
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load dashboard"
        }
        _isLoading.value = false
    }
    

    
    /**
     * Load all dashboard data - manual refresh
     * Per Section 10: Owner has full visibility into all records
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            calculateAndUpdateDashboard()
        }
    }
    
    /**
     * Refresh dashboard data
     */
    fun refresh() {
        loadDashboardData()
    }
    
    // ========================================
    // PENDING APPROVALS (Security Hardening)
    // ========================================
    
    // PENDING APPROVALS section moved to top to prevent initialization NPE
    
    /**
     * Approve a pending trip
     */
    fun approveTrip(tripId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = cloudTripRepository.approveTrip(tripId)
            if (!success) {
                _error.value = "Failed to approve trip"
            }
            _isLoading.value = false
        }
    }
    
    /**
     * Reject a pending trip
     */
    fun rejectTrip(tripId: String, reason: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            val success = cloudTripRepository.rejectTrip(tripId, reason)
            if (!success) {
                _error.value = "Failed to reject trip"
            }
            _isLoading.value = false
        }
    }
    /**
     * Approve a pending fuel request
     */
    fun approveFuelRequest(request: com.fleetcontrol.data.entities.FirestoreFuelRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = cloudFuelRepository.approveFuelRequest(request)
            if (!success) {
                _error.value = "Failed to approve fuel request"
            }
            _isLoading.value = false
        }
    }

    /**
     * Reject a pending fuel request
     */
    fun rejectFuelRequest(requestId: String, reason: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            val success = cloudFuelRepository.rejectFuelRequest(requestId, reason)
            if (!success) {
                _error.value = "Failed to reject fuel request"
            }
            _isLoading.value = false
        }
    }
}

/**
 * Dashboard UI state
 */
data class OwnerDashboardState(
    val todayTripCount: Int = 0,
    val todayProfit: Double = 0.0,
    val activeDriverCount: Int = 0,
    val monthlyProfitSummary: OwnerProfitSummary? = null,
    val isLoaded: Boolean = false,
    // Security Hardening: Pending approval counts
    val pendingTripsCount: Int = 0,
    val pendingFuelRequestsCount: Int = 0,
    // Global App Health
    val lastSyncedAt: Long? = null
)

