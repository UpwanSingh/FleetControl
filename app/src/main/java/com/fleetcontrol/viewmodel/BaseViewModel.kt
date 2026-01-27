package com.fleetcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel with common functionality
 */
abstract class BaseViewModel : ViewModel() {
    
    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    protected val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun clearError() {
        _error.value = null
    }

    protected fun launchCatching(
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                android.util.Log.e("BaseViewModel", "Error: ${e.message}", e)
                _error.value = e.message ?: "An unexpected error occurred"
            }
        }
    }
}
