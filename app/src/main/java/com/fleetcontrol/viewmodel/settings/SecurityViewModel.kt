package com.fleetcontrol.viewmodel.settings

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.core.AppSettings
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for Security Settings
 */
class SecurityViewModel(
    private val appSettings: AppSettings
) : BaseViewModel() {
    
    private val _hasPinSet = MutableStateFlow<Boolean>(false)
    val hasPinSet: StateFlow<Boolean> = _hasPinSet.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    /**
     * Load current owner PIN status (check if a PIN hash exists)
     */
    fun loadPinStatus() {
        viewModelScope.launch {
            try {
                val hash = appSettings.ownerPinHash.first()
                _hasPinSet.value = hash != null && hash.isNotEmpty()
            } catch (e: Exception) {
                _hasPinSet.value = false
            }
        }
    }
    
    /**
     * Verify if entered PIN matches stored hash
     */
    suspend fun verifyPin(inputPin: String): Boolean {
        return appSettings.verifyOwnerPin(inputPin)
    }
    
    /**
     * Set new owner PIN (will be hashed before storage)
     */
    fun setOwnerPin(pin: String) {
        viewModelScope.launch {
            try {
                appSettings.setOwnerPin(pin)
                _hasPinSet.value = true
                _message.value = "PIN updated successfully"
            } catch (e: Exception) {
                _message.value = "Failed to update PIN"
            }
        }
    }
    
    /**
     * Clear message
     */
    fun clearMessage() {
        _message.value = null
    }
}
