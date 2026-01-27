package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.domain.calculators.MonthlyAggregationCalculator
import com.fleetcontrol.domain.calculators.OwnerProfitSummary
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for owner profit view
 * Implements Section 5.4, 8.2, 8.3 of BUSINESS_LOGIC_SPEC.md
 * 
 * Formulas:
 * - OwnerDailyProfit = sum(OwnerNet(all trips by all drivers)) (Section 8.2)
 * - OwnerMonthlyProfit = sum(OwnerDailyProfit(all days in month)) (Section 8.3)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfitViewModel(
    private val aggregationCalculator: MonthlyAggregationCalculator,
    private val tripRepository: com.fleetcontrol.data.repositories.TripRepository // Added for Strict Reactivity
) : BaseViewModel() {
    
    // Selection state
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    
    // Output state
    private val _profitSummary = MutableStateFlow<OwnerProfitSummary?>(null)
    val profitSummary: StateFlow<OwnerProfitSummary?> = _profitSummary.asStateFlow()
    
    // UI Header derived state
    private val _currentMonth = MutableStateFlow<Date>(Date())
    val currentMonth: StateFlow<Date> = _currentMonth.asStateFlow()
    
    init {
        // STRICT REACTIVITY: Pipeline setup
        observeProfitData()
    }
    
    /**
     * REACTIVE PIPELINE
     * 1. Observes Year/Month selection
     * 2. Switches to Data Stream for that range
     * 3. Calculates profit on-the-fly when DB changes
     */
    private fun observeProfitData() {
        viewModelScope.launch {
            // Combine inputs
            combine(_selectedYear, _selectedMonth) { year, month ->
                // Update header date (side effect)
                updateCurrentMonthDate(year, month)
                
                // Return date range
                val start = com.fleetcontrol.utils.DateUtils.getStartOfMonth(year, month)
                val end = com.fleetcontrol.utils.DateUtils.getEndOfMonth(year, month)
                Pair(start, end)
            }.flatMapLatest { (start, end) ->
                // Switch to Database Stream
                // This ensures we get a NEW emission whenever the DB changes (Trip added, edited, synced)
                _isLoading.value = true
                tripRepository.getTripsByDateRange(start, end)
            }.collect { trips: List<TripEntity> ->
                // Calculate Summary In-Memory (Fast & Reactive)
                try {
                    val grossRevenue = trips.sumOf { it.bagCount * it.snapshotCompanyRate }
                    val driverEarnings = trips.sumOf { it.bagCount * it.snapshotDriverRate }
                    val labourCost = trips.sumOf { it.bagCount * it.snapshotLabourCostPerBag }
                    
                    // Net Profit = Gross - Driver - Labour (Section 5.4)
                    // Note: Fuel/Advances are recovered, not expenses.
                    val netProfit = grossRevenue - driverEarnings - labourCost
                    
                    val summary = OwnerProfitSummary(
                        grossRevenue = grossRevenue,
                        driverEarnings = driverEarnings,
                        labourCost = labourCost,
                        netProfit = netProfit,
                        tripCount = trips.size,
                        totalBags = trips.sumOf { it.bagCount },
                        profitMargin = if (grossRevenue > 0) (netProfit / grossRevenue) * 100 else 0.0
                    )
                    
                    _profitSummary.value = summary
                } catch (e: Exception) {
                    _error.value = "Calculation error: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * Re-fetch trigger (now just a no-op or explicit refresh if needed, but not required)
     */
    fun loadProfitData() {
        // No-op for Reactive; pipeline handles it. 
        // Could be used to force-sync if we add SyncManager trigger later.
    }
    
    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
    }
    
    fun previousMonth() {
        if (_selectedMonth.value == 0) {
            _selectedMonth.value = 11
            _selectedYear.value = _selectedYear.value - 1
        } else {
            _selectedMonth.value = _selectedMonth.value - 1
        }
    }
    
    fun nextMonth() {
        if (_selectedMonth.value == 11) {
            _selectedMonth.value = 0
            _selectedYear.value = _selectedYear.value + 1
        } else {
            _selectedMonth.value = _selectedMonth.value + 1
        }
    }
    
    private fun updateCurrentMonthDate(year: Int, month: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        _currentMonth.value = calendar.time
    }
}
