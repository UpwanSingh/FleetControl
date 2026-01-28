package com.fleetcontrol.viewmodel.driver

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.models.DriverExportResult
import com.fleetcontrol.data.models.DriverReportSummary
import com.fleetcontrol.data.repositories.FuelRepository
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.core.SessionState
import com.fleetcontrol.services.export.DriverCsvExportService
import com.fleetcontrol.services.export.DriverPdfExportService
import com.fleetcontrol.utils.DateUtils
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for Driver Reports
 * Provides trip/fuel history with search, pagination, and export functionality
 */
class DriverReportsViewModel(
    private val tripRepository: TripRepository,
    private val fuelRepository: FuelRepository,
    private val sessionManager: SessionManager,
    private val csvExportService: DriverCsvExportService,
    private val pdfExportService: DriverPdfExportService
) : BaseViewModel() {
    
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Current driver ID from session
    @OptIn(ExperimentalCoroutinesApi::class)
    private val driverId: StateFlow<Long?> = sessionManager.currentSession
        .map { session -> (session as? SessionState.DriverSession)?.driverId }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    // Trips for the selected month
    private val _monthlyTrips = MutableStateFlow<List<TripEntity>>(emptyList())
    val monthlyTrips: StateFlow<List<TripEntity>> = _monthlyTrips.asStateFlow()
    
    // Fuel entries for the selected month
    private val _monthlyFuel = MutableStateFlow<List<FuelEntryEntity>>(emptyList())
    val monthlyFuel: StateFlow<List<FuelEntryEntity>> = _monthlyFuel.asStateFlow()
    
    // Filtered trips based on search (using paging)
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val filteredTrips: Flow<PagingData<TripEntity>> = combine(
        driverId.filterNotNull(),
        _selectedYear,
        _selectedMonth,
        _searchQuery.debounce(300L)
    ) { id, year, month, query ->
        Triple(id, Pair(year, month), query)
    }.flatMapLatest { (id, yearMonth, query) ->
        val (year, month) = yearMonth
        val startDate = DateUtils.getStartOfMonth(year, month)
        val endDate = DateUtils.getEndOfMonth(year, month)
        
        Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            pagingSourceFactory = {
                if (query.isBlank()) {
                    tripRepository.getPagedTripsByDriverAndDateRange(id, startDate, endDate)
                } else {
                    tripRepository.getPagedTripsByDriverAndDateRangeFiltered(id, startDate, endDate, query)
                }
            }
        ).flow
    }.cachedIn(viewModelScope)
    
    // Filtered fuel entries (using paging)
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredFuelEntries: Flow<PagingData<FuelEntryEntity>> = combine(
        driverId.filterNotNull(),
        _selectedYear,
        _selectedMonth
    ) { id, year, month ->
        Triple(id, year, month)
    }.flatMapLatest { (id, year, month) ->
        val startDate = DateUtils.getStartOfMonth(year, month)
        val endDate = DateUtils.getEndOfMonth(year, month)
        
        Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            pagingSourceFactory = {
                fuelRepository.getPagedFuelByDriverAndDateRange(id, startDate, endDate)
            }
        ).flow
    }.cachedIn(viewModelScope)
    
    // Summary statistics
    private val _summary = MutableStateFlow(DriverReportSummary())
    val summary: StateFlow<DriverReportSummary> = _summary.asStateFlow()
    
    private val _exportResult = MutableStateFlow<DriverExportResult?>(null)
    val exportResult: StateFlow<DriverExportResult?> = _exportResult.asStateFlow()
    
    init {
        loadReportData()
    }
    
    /**
     * Load report data for the selected month
     */
    fun loadReportData() {
        viewModelScope.launch {
            val id = driverId.value ?: run {
                // Wait for driver ID
                driverId.filterNotNull().first()
            }
            _isLoading.value = true
            
            try {
                val startDate = DateUtils.getStartOfMonth(_selectedYear.value, _selectedMonth.value)
                val endDate = DateUtils.getEndOfMonth(_selectedYear.value, _selectedMonth.value)
                
                // Load trips
                tripRepository.getTripsByDriverAndDateRange(id, startDate, endDate).collect { trips ->
                    _monthlyTrips.value = trips
                    
                    // Load fuel entries
                    fuelRepository.getEntriesByDriverAndDateRange(id, startDate, endDate).collect { fuel ->
                        _monthlyFuel.value = fuel
                        
                        // Calculate summary
                        calculateSummary(trips, fuel)
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load report data"
                _isLoading.value = false
            }
        }
    }
    
    private fun calculateSummary(trips: List<TripEntity>, fuel: List<FuelEntryEntity>) {
        val totalBags = trips.sumOf { it.bagCount }
        val grossEarnings = trips.sumOf { it.bagCount * it.snapshotDriverRate }
        val totalFuelCost = fuel.sumOf { it.amount }
        val netEarnings = grossEarnings - totalFuelCost
        val totalDistance = trips.sumOf { it.snapshotDistanceKm }
        
        _summary.value = DriverReportSummary(
            totalTrips = trips.size,
            totalBags = totalBags,
            grossEarnings = grossEarnings,
            fuelCost = totalFuelCost,
            netEarnings = netEarnings,
            totalFuelEntries = fuel.size,
            totalDistanceKm = totalDistance
        )
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        loadReportData()
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
        // Don't go beyond current month
        val currentCal = Calendar.getInstance()
        if (calendar.after(currentCal)) return
        
        setMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }
    
    // Export file names for SAF
    fun getCsvFileName(): String = csvExportService.getSuggestedFileName(_selectedYear.value, _selectedMonth.value)
    fun getPdfFileName(): String = pdfExportService.getSuggestedFileName(_selectedYear.value, _selectedMonth.value)
    
    /**
     * Export report to external location using SAF (CSV)
     */
    suspend fun exportToExternalCsv(contentResolver: ContentResolver, uri: Uri): DriverExportResult {
        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                csvExportService.writeCsvToStream(
                    outputStream = outputStream,
                    trips = _monthlyTrips.value,
                    fuelEntries = _monthlyFuel.value,
                    summary = _summary.value
                )
            }
            DriverExportResult.Success(uri.toString())
        } catch (e: Exception) {
            DriverExportResult.Error(e.message ?: "Export failed")
        }
    }
    
    /**
     * Export report to external location using SAF (PDF)
     */
    suspend fun exportToExternalPdf(contentResolver: ContentResolver, uri: Uri): DriverExportResult {
        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                pdfExportService.writePdfToStream(
                    outputStream = outputStream,
                    trips = _monthlyTrips.value,
                    fuelEntries = _monthlyFuel.value,
                    summary = _summary.value,
                    year = _selectedYear.value,
                    month = _selectedMonth.value
                )
            }
            DriverExportResult.Success(uri.toString())
        } catch (e: Exception) {
            DriverExportResult.Error(e.message ?: "Export failed")
        }
    }
    
    /**
     * Export report as CSV (fallback to internal storage)
     */
    fun exportCsv() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val filePath = csvExportService.exportDriverReport(
                    trips = _monthlyTrips.value,
                    fuelEntries = _monthlyFuel.value,
                    summary = _summary.value,
                    year = _selectedYear.value,
                    month = _selectedMonth.value
                )
                _exportResult.value = DriverExportResult.Success(filePath)
            } catch (e: Exception) {
                _exportResult.value = DriverExportResult.Error(e.message ?: "Export failed")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Export report as PDF (fallback to internal storage)
     */
    fun exportPdf() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val filePath = pdfExportService.exportDriverReport(
                    trips = _monthlyTrips.value,
                    fuelEntries = _monthlyFuel.value,
                    summary = _summary.value,
                    year = _selectedYear.value,
                    month = _selectedMonth.value
                )
                _exportResult.value = DriverExportResult.Success(filePath)
            } catch (e: Exception) {
                _exportResult.value = DriverExportResult.Error(e.message ?: "Export failed")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearExportResult() {
        _exportResult.value = null
    }
}
