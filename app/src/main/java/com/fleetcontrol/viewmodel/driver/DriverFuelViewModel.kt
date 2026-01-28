package com.fleetcontrol.viewmodel.driver

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.repositories.FuelRepository
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.core.SessionState
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * ViewModel for driver fuel entry
 * Implements Section 6, 9.1 of BUSINESS_LOGIC_SPEC.md
 * 
 * Key Rules (Section 6):
 * - Entered by Driver
 * - Daily entry
 * - Monetary value only
 * - Fuel deducted ONLY from Driver earnings
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverFuelViewModel(
    private val fuelRepository: FuelRepository,
    private val cloudFuelRepository: com.fleetcontrol.data.repositories.CloudFuelRepository,
    private val driverRepository: com.fleetcontrol.data.repositories.DriverRepository,
    private val sessionManager: SessionManager
) : BaseViewModel() {
    
    private val _recentFuelEntries = MutableStateFlow<List<FuelEntryEntity>>(emptyList())
    val recentFuelEntries: StateFlow<List<FuelEntryEntity>> = _recentFuelEntries.asStateFlow()
    
    private val _entrySaved = MutableStateFlow(false)
    val entrySaved: StateFlow<Boolean> = _entrySaved.asStateFlow()
    
    init {
        // Observe session changes and load entries when driver logs in
        viewModelScope.launch {
            sessionManager.currentSession.flatMapLatest { session ->
                val driverId = (session as? SessionState.DriverSession)?.driverId
                if (driverId != null && driverId > 0) {
                    fuelRepository.getFuelEntriesByDriver(driverId)
                } else {
                    flowOf(emptyList())
                }
            }.collect { entries ->
                _recentFuelEntries.value = entries
            }
        }
    }
    
    /**
     * Add fuel entry
     * Per Section 6.1: Entered by Driver, daily entry, monetary value
     */
    fun addFuelEntry(
        amount: Double,
        liters: Double = 0.0,
        pricePerLiter: Double = 0.0,
        fuelStation: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Get session details
                val session = sessionManager.currentSession.value as? SessionState.DriverSession
                    ?: throw IllegalStateException("Driver not logged in")
                
                val driverId = session.driverId
                val ownerId = session.ownerId
                
                if (amount <= 0) {
                    throw IllegalArgumentException("Amount must be greater than 0")
                }
                
                // Get Driver Entity for Firestore ID
                val driver = driverRepository.getDriverById(driverId)
                    ?: throw IllegalStateException("Driver profile not found")
                    
                val driverFirestoreId = driver.firestoreId
                
                // Create PENDING request in Cloud
                val request = com.fleetcontrol.data.entities.FirestoreFuelRequest(
                    ownerId = ownerId,
                    driverId = driverFirestoreId,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    notes = buildString {
                        if (!fuelStation.isNullOrEmpty()) append(fuelStation).append(". ")
                        if (liters > 0) append("Liters: ").append(liters).append(". ")
                        if (pricePerLiter > 0) append("Price/L: ").append(pricePerLiter)
                    }.trim(),
                    status = "PENDING",
                    createdAt = System.currentTimeMillis()
                )
                
                val success = cloudFuelRepository.createFuelRequest(request)
                if (success) {
                    _entrySaved.value = true
                } else {
                    _error.value = "Failed to create request. Check connection."
                }
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add fuel entry"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun resetEntrySaved() {
        _entrySaved.value = false
    }
    
    /**
     * Refresh fuel entries from the database
     */
    fun refreshEntries() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val driverId = sessionManager.getCurrentDriverId() ?: return@launch
                fuelRepository.getFuelEntriesByDriver(driverId).collect { entries ->
                    _recentFuelEntries.value = entries
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh entries"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
