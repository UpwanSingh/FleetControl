package com.fleetcontrol.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user session state (Owner vs Driver mode)
 * Implements Section 9 of BUSINESS_LOGIC_SPEC.md
 */
import com.fleetcontrol.data.repositories.CloudMasterDataRepository
import com.fleetcontrol.data.repositories.CloudTripRepository

/**
 * Manages user session state (Owner vs Driver mode)
 * Implements Section 9 of BUSINESS_LOGIC_SPEC.md
 */
class SessionManager(
    private val cloudMasterDataRepo: CloudMasterDataRepository,
    private val cloudTripRepo: CloudTripRepository
) {
    
    private val _currentSession = MutableStateFlow<SessionState>(SessionState.NotLoggedIn)
    val currentSession: StateFlow<SessionState> = _currentSession.asStateFlow()
    
    /**
     * Check if user is logged in as owner
     */
    fun isOwner(): Boolean = currentSession.value is SessionState.OwnerSession
    
    /**
     * Check if user is logged in as driver
     */
    fun isDriver(): Boolean = currentSession.value is SessionState.DriverSession
    
    /**
     * Get current driver ID (if in driver mode)
     */
    fun getCurrentDriverId(): Long? = (currentSession.value as? SessionState.DriverSession)?.driverId
    
    /**
     * Set owner session
     * Per Section 9.2: Owner has full access to all records
     */
    fun setOwnerSession() {
        _currentSession.value = SessionState.OwnerSession
    }
    
    /**
     * Set driver session
     * Per Section 9.1: Driver has limited access (own data only)
     */
    fun setDriverSession(driverId: Long, ownerId: String) {
        _currentSession.value = SessionState.DriverSession(driverId, ownerId)
    }
    
    /**
     * Logout and clear session
     * Also clears Cloud Repository scopes to prevent data bleeding
     */
    fun logout() {
        _currentSession.value = SessionState.NotLoggedIn
        
        // Clear Cloud Scopes
        cloudMasterDataRepo.setOwnerId("")
        cloudTripRepo.setOwnerId("")
    }
}

/**
 * Session states
 */
sealed class SessionState {
    object NotLoggedIn : SessionState()
    object OwnerSession : SessionState()
    data class DriverSession(val driverId: Long, val ownerId: String) : SessionState()
}
