package com.fleetcontrol.viewmodel.auth

import androidx.lifecycle.viewModelScope
import android.content.Context
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.core.AppSettings
import com.fleetcontrol.data.entities.DriverEntity
import com.fleetcontrol.data.entities.UserRole
import com.fleetcontrol.data.repositories.DriverRepository
import com.fleetcontrol.domain.security.LicenseManager
import com.fleetcontrol.domain.security.PinHasher
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * ViewModel for login/role selection
 * Implements Section 9 of BUSINESS_LOGIC_SPEC.md - Access Control
 * 
 * Uses PIN-based authentication (no Firebase Phone Auth required)
 */
class LoginViewModel(
    private val driverRepository: DriverRepository,
    private val sessionManager: SessionManager,
    private val appSettings: AppSettings,
    private val cloudMasterDataRepository: com.fleetcontrol.data.repositories.CloudMasterDataRepository,
    private val cloudTripRepository: com.fleetcontrol.data.repositories.CloudTripRepository
) : BaseViewModel() {

    // ========================================
    // LICENSE STATE
    // ========================================
    
    val isLicenseActivated: StateFlow<Boolean> = appSettings.isLicenseActivated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDriverAccessGranted: StateFlow<Boolean> = appSettings.isDriverAccessGranted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        
    val linkedDriverId: StateFlow<Long?> = appSettings.linkedDriverId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activationError = MutableStateFlow<String?>(null)
    val activationError: StateFlow<String?> = _activationError.asStateFlow()

    fun activateLicense(context: Context, key: String) {
        if (LicenseManager.validateKey(context, key)) {
            viewModelScope.launch {
                appSettings.setLicenseActivated(true)
                _activationError.value = null
            }
        } else {
            _activationError.value = "Invalid License Key"
        }
    }

    fun clearActivationError() {
        _activationError.value = null
    }

    // ========================================
    // PIN CONFIGURATION STATE
    // ========================================
    
    private val _isPinConfigured = MutableStateFlow(false)
    val isPinConfigured: StateFlow<Boolean> = _isPinConfigured.asStateFlow()
    
    init {
        // Check if PIN is already configured
        viewModelScope.launch {
            val hash = appSettings.ownerPinHash.first()
            _isPinConfigured.value = !hash.isNullOrEmpty()
        }
    }
    
    /**
     * Setup Owner PIN for the first time
     */
    fun setupOwnerPin(pin: String) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            try {
                appSettings.setOwnerPin(pin)
                _isPinConfigured.value = true
            } catch (e: Exception) {
                _error.value = "Failed to save PIN"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========================================
    // DRIVERS LIST (for driver selection)
    // ========================================
    
    private val _drivers = MutableStateFlow<List<DriverEntity>>(emptyList())
    val drivers: StateFlow<List<DriverEntity>> = _drivers.asStateFlow()
    
    fun loadDrivers() {
        viewModelScope.launch {
            try {
                // Use Global list for selection before Owner context is known
                driverRepository.getGlobalActiveDrivers().collect { driverList ->
                    _drivers.value = driverList
                }
            } catch (e: Exception) {
                _error.value = "Failed to load drivers"
            }
        }
    }

    // ========================================
    // PIN-BASED LOGIN
    // ========================================
    
    private val _pinLoginSuccess = MutableStateFlow<Pair<String, Long?>?>(null)
    val pinLoginSuccess: StateFlow<Pair<String, Long?>?> = _pinLoginSuccess.asStateFlow()
    
    /**
     * Verify Owner PIN and login
     */
    fun verifyOwnerPin(pin: String) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _error.value = null
            
            try {
                // Check if PIN is set, otherwise use default "0000"
                val storedHash = appSettings.ownerPinHash.first()
                
                val isValid = if (storedHash.isNullOrEmpty()) {
                    // No PIN set, use default "0000"
                    pin == "0000"
                } else {
                    // Verify against stored hash
                    appSettings.verifyOwnerPin(pin)
                }
                
                if (isValid) {
                    sessionManager.setOwnerSession()
                    _pinLoginSuccess.value = Pair(UserRole.OWNER, null)
                } else {
                    _error.value = "Invalid PIN"
                }
            } catch (e: Exception) {
                _error.value = "Login failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Verify Driver PIN and login
     */
    fun verifyDriverPin(driverId: Long, pin: String) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _error.value = null
            
            try {
                val driver = driverRepository.getDriverById(driverId)
                
                if (driver == null) {
                    _error.value = "Driver not found"
                    return@launch
                }
                
                // Check PIN using PinHasher
                if (driver.pin.isEmpty() || PinHasher.verify(pin, driver.pin)) {
                    // MULTI-TENANCY CRITICAL:
                    // 1. Get Owner ID from Driver Entity
                    val ownerId = driver.ownerId
                    
                    // 2. Set Session
                    sessionManager.setDriverSession(driverId, ownerId)
                    
                    // 3. Set Cloud Scope (So Master Data and Trips read/write to correct tenant)
                    cloudMasterDataRepository.setOwnerId(ownerId)
                    cloudTripRepository.setOwnerId(ownerId)
                    
                    _pinLoginSuccess.value = Pair(UserRole.DRIVER, driverId)
                } else {
                    _error.value = "Invalid PIN"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "Login failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun resetPinLoginSuccess() {
        _pinLoginSuccess.value = null
    }


    // ========================================
    // LOGOUT
    // ========================================
    
    fun logout() {
        sessionManager.logout()
        
        // Clear Cloud Scopes
        cloudMasterDataRepository.setOwnerId("")
        cloudTripRepository.setOwnerId("")
        
        _pinLoginSuccess.value = null
        _error.value = null
    }
}
