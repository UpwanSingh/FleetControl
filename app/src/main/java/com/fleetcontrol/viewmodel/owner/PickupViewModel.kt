package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.data.repositories.PickupLocationRepository
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for pickup location management
 * Implements Section 3.2 of BUSINESS_LOGIC_SPEC.md
 */
class PickupViewModel(
    private val pickupRepository: PickupLocationRepository
) : BaseViewModel() {
    
    private val _pickups = MutableStateFlow<List<PickupLocationEntity>>(emptyList())
    val pickups: StateFlow<List<PickupLocationEntity>> = _pickups.asStateFlow()
    
    // Alias for UI compatibility
    val pickupLocations: StateFlow<List<PickupLocationEntity>> = _pickups.asStateFlow()
    
    private val _selectedPickup = MutableStateFlow<PickupLocationEntity?>(null)
    val selectedPickup: StateFlow<PickupLocationEntity?> = _selectedPickup.asStateFlow()
    
    private val _pickupsByCategory = MutableStateFlow<Map<DistanceCategory, List<PickupLocationEntity>>>(emptyMap())
    val pickupsByCategory: StateFlow<Map<DistanceCategory, List<PickupLocationEntity>>> = _pickupsByCategory.asStateFlow()
    
    init {
        loadPickups()
    }
    
    /**
     * Load all active pickup locations
     */
    fun loadPickups() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                pickupRepository.getAllActiveLocations().collect { pickupList ->
                    _pickups.value = pickupList.sortedBy { it.distanceFromBase }
                    categorizePickups(pickupList)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Categorize pickups by distance
     */
    private fun categorizePickups(pickups: List<PickupLocationEntity>) {
        _pickupsByCategory.value = pickups.groupBy { pickup ->
            when {
                pickup.distanceFromBase <= 10 -> DistanceCategory.LOCAL
                pickup.distanceFromBase <= 25 -> DistanceCategory.SUBURBAN
                pickup.distanceFromBase <= 50 -> DistanceCategory.CITY
                else -> DistanceCategory.OUTSTATION
            }
        }
    }
    
    /**
     * Add a new pickup location
     * Distance is NOT set here - it's set per client in ClientManagement
     */
    fun addPickup(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pickup = PickupLocationEntity(
                    name = name.trim(),
                    distanceFromBase = 0.0 // Deprecated - distance is per client now
                )
                pickupRepository.insert(pickup)
                // List will auto-update via Flow
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update an existing pickup location (name only)
     * Distance is managed per-client in ClientManagement
     */
    fun updatePickup(pickupId: Long, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existing = _pickups.value.find { it.id == pickupId }
                if (existing != null) {
                    pickupRepository.update(
                        existing.copy(name = name.trim())
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Deactivate a pickup location
     */
    fun deactivatePickup(pickupId: Long) {
        viewModelScope.launch {
            try {
                val existing = _pickups.value.find { it.id == pickupId }
                if (existing != null) {
                    // Soft delete to avoid Foreign Key constraints if used in Trips
                    pickupRepository.update(existing.copy(isActive = false))
                    
                    // Note: We don't call delete() anymore.
                    // The 'isActive = false' will filter it out of getAllActiveLocations()
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    /**
     * Delete a pickup (alias for UI)
     */
    fun deletePickup(pickup: PickupLocationEntity) {
        deactivatePickup(pickup.id)
    }
    
    /**
     * Select a pickup for editing
     */
    fun selectPickup(pickup: PickupLocationEntity) {
        _selectedPickup.value = pickup
    }
    
    /**
     * Clear selection
     */
    fun clearSelection() {
        _selectedPickup.value = null
    }
    
    /**
     * Get pickups within a distance range
     */
    fun getPickupsByDistance(minKm: Double, maxKm: Double): List<PickupLocationEntity> {
        return _pickups.value.filter { it.distanceFromBase in minKm..maxKm }
    }
    
    /**
     * Find nearest pickup to a given distance
     */
    fun findNearestPickup(targetDistanceKm: Double): PickupLocationEntity? {
        return _pickups.value.minByOrNull { 
            kotlin.math.abs(it.distanceFromBase - targetDistanceKm) 
        }
    }
    
    /**
     * Validate pickup data
     */
    fun validatePickup(name: String, distanceFromBase: Double): PickupValidationResult {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Location name is required")
        }
        
        if (distanceFromBase <= 0) {
            errors.add("Distance must be greater than 0")
        }
        
        // Check for duplicate names
        val isDuplicate = _pickups.value.any { 
            it.name.equals(name.trim(), ignoreCase = true) && 
            it.id != _selectedPickup.value?.id 
        }
        if (isDuplicate) {
            errors.add("A location with this name already exists")
        }
        
        return PickupValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Get pickup count by category
     */
    fun getPickupStats(): PickupStats {
        val byCategory = _pickupsByCategory.value
        return PickupStats(
            total = _pickups.value.size,
            local = byCategory[DistanceCategory.LOCAL]?.size ?: 0,
            suburban = byCategory[DistanceCategory.SUBURBAN]?.size ?: 0,
            city = byCategory[DistanceCategory.CITY]?.size ?: 0,
            outstation = byCategory[DistanceCategory.OUTSTATION]?.size ?: 0
        )
    }
}

enum class DistanceCategory {
    LOCAL,      // 0-10 km
    SUBURBAN,   // 10-25 km
    CITY,       // 25-50 km
    OUTSTATION  // 50+ km
}

data class PickupValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

data class PickupStats(
    val total: Int,
    val local: Int,
    val suburban: Int,
    val city: Int,
    val outstation: Int
)
