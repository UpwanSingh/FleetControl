package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.domain.entitlements.FeatureGate
import com.fleetcontrol.domain.entitlements.SubscriptionPlan
import com.fleetcontrol.services.export.CsvExportService
import com.fleetcontrol.services.export.PdfExportService
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for reports
 * Implements Section 9.2, 10 of BUSINESS_LOGIC_SPEC.md
 * 
 * Owner CAN (Section 9.2):
 * - View ALL records, ALL history
 * - Filter by date ranges
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class ReportsViewModel(
    private val tripRepository: TripRepository,
    private val featureGate: FeatureGate,
    private val csvExportService: CsvExportService,
    private val pdfExportService: PdfExportService
) : BaseViewModel() {
    
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentPlan = MutableStateFlow(SubscriptionPlan.FREE)
    val currentPlan: StateFlow<SubscriptionPlan> = _currentPlan.asStateFlow()

    // Reactive source of all trips

    
    // Filtered trips based on search query
    val trips: StateFlow<List<TripEntity>> = _searchQuery
        .debounce(300L) // Add debounce to prevent DB spam
        .flatMapLatest { query: String ->
            if (query.isBlank()) {
                _allTrips
            } else {
                tripRepository.searchByClient(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentMonth = MutableStateFlow(Date())
    val currentMonth: StateFlow<Date> = _currentMonth.asStateFlow()
    
    // Feature availability
    // Feature availability - Reactive to plan changes
    val canExportCsv: StateFlow<Boolean> = _currentPlan
        .map { featureGate.canExportCsv(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    val canExportPdf: StateFlow<Boolean> = _currentPlan
        .map { featureGate.canExportPdf(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()
    
    init {
        loadCurrentPlan()
        // No manual loadReportData() needed - Flow wiring handles it
    }
    
    private fun loadCurrentPlan() {
        launchCatching {
            featureGate.getCurrentPlan().collect { plan ->
                _currentPlan.value = plan
            }
        }
    }
    
    /**
     * REACTIVE: Automatically fetch trips when Year/Month changes.
     * Use flatMapLatest to CANCEL previous collection when date changes.
     */
    private val _allTrips = combine(_selectedYear, _selectedMonth) { year, month ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        _isLoading.value = true
        val startDate = DateUtils.getStartOfMonth(year, month)
        val endDate = DateUtils.getEndOfMonth(year, month)
        tripRepository.getTripsByDateRange(startDate, endDate)
    }.map { trips ->
        _isLoading.value = false
        trips
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // Removed manual loadReportData() as it caused collector leaks
    
    /**
     * Export report as CSV
     * Requires Basic+ subscription per PROJECT_REFERENCE.md
     */
    fun exportCsv() {
        viewModelScope.launch {
            if (!featureGate.canExportCsv(_currentPlan.value)) {
                _exportResult.value = ExportResult.RequiresUpgrade("CSV export requires Basic subscription")
                return@launch
            }
            
            try {
                _isLoading.value = true
                val trips = _allTrips.value
                val filePath = csvExportService.exportTrips(trips, _selectedYear.value, _selectedMonth.value)
                _exportResult.value = ExportResult.Success(filePath)
            } catch (e: Exception) {
                _exportResult.value = ExportResult.Error(e.message ?: "Export failed")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Check if CSV export is allowed (for SAF flow)
     */
    fun canExportCsvNow(): Boolean {
        return featureGate.canExportCsv(_currentPlan.value)
    }
    
    /**
     * Check if PDF export is allowed (for SAF flow)
     */
    fun canExportPdfNow(): Boolean {
        return featureGate.canExportPdf(_currentPlan.value)
    }
    
    /**
     * Get suggested CSV filename
     */
    fun getCsvFileName(): String {
        return csvExportService.getSuggestedFileName(_selectedYear.value, _selectedMonth.value)
    }
    
    /**
     * Get suggested PDF filename
     */
    fun getPdfFileName(): String {
        return pdfExportService.getSuggestedFileName(_selectedYear.value, _selectedMonth.value)
    }
    
    /**
     * Get trips for export
     */
    fun getTripsForExport(): List<TripEntity> {
        return _allTrips.value
    }
    
    /**
     * Get CSV export service for writing to stream
     */
    fun getCsvExportService() = csvExportService
    
    /**
     * Get PDF export service for writing to stream  
     */
    fun getPdfExportService() = pdfExportService
    
    /**
     * Get selected year
     */
    fun getSelectedYear() = _selectedYear.value
    
    /**
     * Get selected month
     */
    fun getSelectedMonth() = _selectedMonth.value
    
    /**
     * Export report as PDF
     * Requires Premium subscription per PROJECT_REFERENCE.md
     */
    fun exportPdf() {
        viewModelScope.launch {
            if (!featureGate.canExportPdf(_currentPlan.value)) {
                _exportResult.value = ExportResult.RequiresUpgrade("PDF export requires Premium subscription")
                return@launch
            }
            
            try {
                _isLoading.value = true
                val trips = _allTrips.value
                val filePath = pdfExportService.exportTrips(trips, _selectedYear.value, _selectedMonth.value)
                _exportResult.value = ExportResult.Success(filePath)
            } catch (e: Exception) {
                _exportResult.value = ExportResult.Error(e.message ?: "Export failed")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        updateCurrentMonth()
        // loadReportData() removed - reactive flow handles update via _selectedYear/_selectedMonth
    }
    
    fun previousMonth() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value)
            add(Calendar.MONTH, -1)
        }
        setMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }
    
    fun nextMonth() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value)
            add(Calendar.MONTH, 1)
        }
        setMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }
    
    private fun updateCurrentMonth() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        _currentMonth.value = calendar.time
    }
    
    // Removed manual updateExportFlags as it is now reactive
    
    fun clearExportResult() {
        _exportResult.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}

sealed class ExportResult {
    data class Success(val filePath: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
    data class RequiresUpgrade(val message: String) : ExportResult()
}
