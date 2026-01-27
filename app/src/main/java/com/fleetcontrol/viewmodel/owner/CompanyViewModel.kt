package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.CompanyEntity
import com.fleetcontrol.data.repositories.CompanyRepository
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for company management
 * Implements Section 2.2 of BUSINESS_LOGIC_SPEC.md
 */
class CompanyViewModel(
    private val companyRepository: CompanyRepository
) : BaseViewModel() {
    
    private val _companies = MutableStateFlow<List<CompanyEntity>>(emptyList())
    val companies: StateFlow<List<CompanyEntity>> = _companies.asStateFlow()
    
    private val _selectedCompany = MutableStateFlow<CompanyEntity?>(null)
    val selectedCompany: StateFlow<CompanyEntity?> = _selectedCompany.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    init {
        loadCompanies()
    }
    
    /**
     * Load all active companies
     */
    fun loadCompanies() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                companyRepository.getAllActiveCompanies().collect { companyList ->
                    _companies.value = companyList
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    // ... (skipped some methods)
    
    /**
     * Search companies by name
     */
    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    loadCompanies()
                } else {
                    companyRepository.searchCompanies(query).collect { results ->
                        _companies.value = results
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    /**
     * Add a new company
     */
    fun addCompany(name: String, perBagRate: Double) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            try {
                val company = CompanyEntity(
                    name = name.trim(),
                    perBagRate = perBagRate
                )
                companyRepository.insert(company)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete a company (Soft Delete)
     */
    fun deleteCompany(company: CompanyEntity) {
        viewModelScope.launch {
            try {
                // Soft delete to avoid Foreign Key constraints
                // We use update instead of delete
                companyRepository.update(company.copy(isActive = false))
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    /**
     * Get company count (for quota checking)
     */
    fun getCompanyCount(): Int {
        return _companies.value.size
    }
    
    /**
     * Validate company data
     */
    fun validateCompany(name: String, perBagRate: Double): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("Company name is required")
        }
        
        if (perBagRate <= 0) {
            errors.add("Per-bag rate must be greater than 0")
        }
        
        if (perBagRate > 1000) {
            errors.add("Per-bag rate seems unusually high")
        }
        
        // Check for duplicate names
        val isDuplicate = _companies.value.any { 
            it.name.equals(name.trim(), ignoreCase = true) && 
            it.id != _selectedCompany.value?.id 
        }
        if (isDuplicate) {
            errors.add("A company with this name already exists")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)
