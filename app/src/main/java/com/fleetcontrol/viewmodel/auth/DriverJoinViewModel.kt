package com.fleetcontrol.viewmodel.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcontrol.core.AppSettings
import com.fleetcontrol.core.InviteCodeManager
import com.fleetcontrol.core.InviteCodeResult
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.data.entities.*
import com.fleetcontrol.data.repositories.*
import com.fleetcontrol.services.AuthService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DriverJoinViewModel(
    private val authService: AuthService,
    private val appSettings: AppSettings,
    private val sessionManager: SessionManager,
    private val cloudMasterDataRepository: CloudMasterDataRepository,
    private val cloudTripRepository: CloudTripRepository,
    private val driverRepository: DriverRepository,
    private val companyRepository: CompanyRepository,
    private val clientRepository: ClientRepository,
    private val pickupRepository: PickupLocationRepository,
    private val rateSlabRepository: RateSlabRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val inviteCodeManager = InviteCodeManager(firestore)

    private val _uiState = MutableStateFlow<DriverJoinUiState>(DriverJoinUiState.Idle)
    val uiState: StateFlow<DriverJoinUiState> = _uiState.asStateFlow()

    fun validateCode(code: String) {
        viewModelScope.launch {
            _uiState.value = DriverJoinUiState.Loading("Validating...")
            
            // Ensure Anonymous Auth
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                if (!authService.signInAnonymously()) {
                    _uiState.value = DriverJoinUiState.Error("Authentication failed. Please retry.")
                    return@launch
                }
            }

            when (val result = inviteCodeManager.validateCode(code)) {
                is InviteCodeResult.Valid -> {
                    _uiState.value = DriverJoinUiState.CodeValid(result)
                }
                is InviteCodeResult.Invalid -> {
                    _uiState.value = DriverJoinUiState.Error(result.message)
                }
            }
        }
    }

    fun joinFleet(validResult: InviteCodeResult.Valid, code: String) {
        viewModelScope.launch {
            _uiState.value = DriverJoinUiState.Loading("Joining Fleet...")
            
            try {
                val auth = FirebaseAuth.getInstance()
                val driverUid = auth.currentUser?.uid
                if (driverUid.isNullOrBlank()) {
                    _uiState.value = DriverJoinUiState.Error("No device identity found. Please retry.")
                    return@launch
                }

                // Consume Code
                val normalizedCode = code.uppercase().trim()
                val consumed = inviteCodeManager.consumeCode(normalizedCode, driverUid)
                if (!consumed) {
                    _uiState.value = DriverJoinUiState.Error("Failed to consume invite code. It may have expired or been used.")
                    return@launch
                }

                // Create User Document
                val userCreated = authService.createDriverUserDocument(
                    driverUid = driverUid,
                    tenantId = validResult.ownerId,
                    inviteCode = normalizedCode
                )
                if (!userCreated) {
                    _uiState.value = DriverJoinUiState.Error("Failed to join fleet. Please retry.")
                    return@launch
                }

                // SYNC ALL DATA (Async)
                syncAllData(validResult.ownerId, validResult.firestoreDriverId, validResult.driverName)

            } catch (e: Exception) {
                _uiState.value = DriverJoinUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun syncAllData(ownerId: String, firestoreDriverId: String, driverName: String) {
        _uiState.value = DriverJoinUiState.Loading("Syncing Data...")
        
        try {
            // 1. Set Cloud repo ownerId
            cloudMasterDataRepository.setOwnerId(ownerId)
            cloudTripRepository.setOwnerId(ownerId)

            Log.d("DriverJoinViewModel", "=== SYNCING ALL DATA FOR DRIVER ===")

            // 2a. Fetch and insert driver
            val cloudDriver = cloudMasterDataRepository.getDriverByFirestoreId(firestoreDriverId)
            val localDriverId: Long
            if (cloudDriver != null) {
                val localDriver = DriverEntity(
                    firestoreId = firestoreDriverId,
                    ownerId = ownerId,
                    name = cloudDriver.name,
                    phone = cloudDriver.phone,
                    pin = cloudDriver.pin,
                    isActive = cloudDriver.isActive
                )
                // Use Raw insert to avoid double-sync (we are down-syncing)
                localDriverId = driverRepository.insertRaw(localDriver)
                Log.d("DriverJoinViewModel", "Driver synced: ${cloudDriver.name}")
            } else {
                val fallbackDriver = DriverEntity(
                    firestoreId = firestoreDriverId,
                    ownerId = ownerId,
                    name = driverName,
                    phone = "",
                    pin = "",
                    isActive = true
                )
                localDriverId = driverRepository.insertRaw(fallbackDriver)
            }

            // 2b. Fetch and insert all COMPANIES
            val companies = cloudMasterDataRepository.getAllCompaniesNow()
            Log.d("DriverJoinViewModel", "Syncing ${companies.size} companies...")
            companies.forEach { fComp ->
                val existing = companyRepository.getCompanyByFirestoreId(fComp.id)
                if (existing == null) {
                    companyRepository.insertRaw(CompanyEntity(
                        firestoreId = fComp.id,
                        ownerId = ownerId,
                        name = fComp.name,
                        contactPerson = fComp.contactPerson,
                        contactPhone = fComp.contactPhone,
                        perBagRate = fComp.perBagRate,
                        isActive = fComp.isActive
                    ))
                }
            }

            // 2c. Fetch and insert all CLIENTS
            val clients = cloudMasterDataRepository.getAllClientsNow()
            Log.d("DriverJoinViewModel", "Syncing ${clients.size} clients...")
            clients.forEach { fClient ->
                val existing = clientRepository.getClientByFirestoreId(fClient.id)
                if (existing == null) {
                    clientRepository.insertRaw(ClientEntity(
                        firestoreId = fClient.id,
                        ownerId = ownerId,
                        name = fClient.name,
                        address = fClient.address,
                        contactPerson = fClient.contactPerson,
                        contactPhone = fClient.contactPhone,
                        notes = fClient.notes,
                        isActive = fClient.isActive
                    ))
                }
            }

            // 2d. Fetch and insert all PICKUP LOCATIONS
            val locations = cloudMasterDataRepository.getAllLocationsNow()
            Log.d("DriverJoinViewModel", "Syncing ${locations.size} pickup locations...")
            locations.forEach { fLoc ->
                val existing = pickupRepository.getLocationByFirestoreId(fLoc.id)
                if (existing == null) {
                    pickupRepository.insertRaw(PickupLocationEntity(
                        firestoreId = fLoc.id,
                        ownerId = ownerId,
                        name = fLoc.name,
                        distanceFromBase = fLoc.distanceFromBase,
                        isActive = fLoc.isActive
                    ))
                }
            }

            // 2e. Fetch and insert all RATE SLABS
            val slabs = cloudMasterDataRepository.getAllRateSlabsNow()
            Log.d("DriverJoinViewModel", "Syncing ${slabs.size} rate slabs...")
            slabs.forEach { fSlab ->
                val existing = rateSlabRepository.getRateSlabByFirestoreId(fSlab.id)
                if (existing == null) {
                    rateSlabRepository.insertRaw(DriverRateSlabEntity(
                        firestoreId = fSlab.id,
                        ownerId = ownerId,
                        minDistance = fSlab.minDistance,
                        maxDistance = fSlab.maxDistance,
                        ratePerBag = fSlab.ratePerBag,
                        isActive = fSlab.isActive
                    ))
                }
            }
            
            Log.d("DriverJoinViewModel", "=== SYNC COMPLETE ===")

            // 3. Save driver access to settings
            appSettings.setDriverAccessGranted(true, localDriverId, ownerId)

            // 4. Set driver session
            sessionManager.setDriverSession(localDriverId, ownerId)

            _uiState.value = DriverJoinUiState.Success
            
        } catch (e: Exception) {
            Log.e("DriverJoinViewModel", "Sync failed", e)
            _uiState.value = DriverJoinUiState.Error("Data sync failed: ${e.message}")
        }
    }
    
    fun resetError() {
        if (_uiState.value is DriverJoinUiState.Error) {
             // Preserve prior state if possible, or go back to Idle
             _uiState.value = DriverJoinUiState.Idle
        }
    }
}

sealed class DriverJoinUiState {
    object Idle : DriverJoinUiState()
    data class Loading(val message: String) : DriverJoinUiState()
    data class CodeValid(val result: InviteCodeResult.Valid) : DriverJoinUiState()
    object Success : DriverJoinUiState()
    data class Error(val message: String) : DriverJoinUiState()
}
