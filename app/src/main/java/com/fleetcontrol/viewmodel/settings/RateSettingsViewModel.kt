package com.fleetcontrol.viewmodel.settings

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.DriverRateSlabEntity
import com.fleetcontrol.data.repositories.RateSlabRepository
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Driver Rate Slabs
 * Allows Owner to have "Supreme Authority" over pricing
 */
class RateSettingsViewModel(
    private val repository: RateSlabRepository,
    private val labourCostDao: com.fleetcontrol.data.dao.LabourCostDao
) : BaseViewModel() {

    private val _slabs = MutableStateFlow<List<DriverRateSlabEntity>>(emptyList())
    val slabs: StateFlow<List<DriverRateSlabEntity>> = _slabs.asStateFlow()

    private val _labourCost = MutableStateFlow<Double>(0.0)
    val labourCost: StateFlow<Double> = _labourCost.asStateFlow()

    init {
        loadSlabs()
        loadLabourCost()
    }

    private fun loadSlabs() {
        viewModelScope.launch {
            repository.getAllActiveRateSlabs().collect {
                _slabs.value = it
            }
        }
    }

    private fun loadLabourCost() {
        viewModelScope.launch {
            labourCostDao.getAllActiveRules().collect { rules ->
                val defaultRule = rules.find { it.isDefault } ?: rules.firstOrNull()
                _labourCost.value = defaultRule?.costPerBag ?: 0.0
            }
        }
    }

    fun setLabourCost(cost: Double) {
        viewModelScope.launch {
            try {
                // Deactivate all existing rules first (simplified approach for "Global Default")
                // In a real app we might want to keep history, but for this "Supreme Authority" update:
                // We just want one active rule.
                
                // Since we don't have a sophisticated rule engine for labour yet, we just insert a new default rule
                // and maybe should deactivate old ones? 
                // Or just insert/replace if we had a single row ID?
                // The DAO has `getDefaultRule` which LIMIT 1.
                
                // Let's insert a new rule and mark it default.
                // ideally we should mark others inactive but let's keep it simple:
                // `getDefaultRule` picks LIMIT 1.
                
                // Better approach: Check if exists, update it, or insert.
                val currentRule = labourCostDao.getDefaultRule()
                if (currentRule != null) {
                    labourCostDao.update(currentRule.copy(costPerBag = cost))
                } else {
                    labourCostDao.insert(
                        com.fleetcontrol.data.entities.LabourCostRuleEntity(
                            name = "Standard Labour",
                            costPerBag = cost,
                            isDefault = true,
                            isActive = true
                        )
                    )
                }
            } catch (e: Exception) {
                _error.value = "Failed to set labour cost: ${e.message}"
            }
        }
    }

    fun addSlab(minDistance: Double, maxDistance: Double, rate: Double) {
        viewModelScope.launch {
            try {
                val newSlab = DriverRateSlabEntity(
                    minDistance = minDistance,
                    maxDistance = maxDistance,
                    ratePerBag = rate,
                    isActive = true
                )
                repository.insert(newSlab)
            } catch (e: Exception) {
                _error.value = "Failed to add slab: ${e.message}"
            }
        }
    }

    fun deleteSlab(slab: DriverRateSlabEntity) {
        viewModelScope.launch {
            try {
                // Soft delete to preserve historical rate data
                repository.update(slab.copy(isActive = false))
            } catch (e: Exception) {
                _error.value = "Failed to delete slab: ${e.message}"
            }
        }
    }

    fun updateSlab(slab: DriverRateSlabEntity, newRate: Double) {
        viewModelScope.launch {
            try {
                repository.update(slab.copy(ratePerBag = newRate))
            } catch (e: Exception) {
                _error.value = "Failed to update rate: ${e.message}"
            }
        }
    }
    
    fun resetToDefaults() {
         viewModelScope.launch {
            // Logic to reset would go here if needed, but for now we just allow editing
         }
    }
}
