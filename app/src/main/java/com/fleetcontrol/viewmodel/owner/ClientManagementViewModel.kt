package com.fleetcontrol.viewmodel.owner

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.entities.ClientEntity
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.data.repositories.ClientRepository
import com.fleetcontrol.data.repositories.PickupClientDistanceRepository
import com.fleetcontrol.data.repositories.PickupLocationRepository
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing clients and their distances from pickup locations
 * 
 * Responsibilities:
 * - CRUD operations for clients
 * - Managing pickup-client distance mappings
 * - Finding nearest/preferred pickups for clients
 */
class ClientManagementViewModel(
    private val clientRepository: ClientRepository,
    private val pickupClientDistanceRepository: PickupClientDistanceRepository,
    private val pickupRepository: PickupLocationRepository
) : BaseViewModel() {
    
    private val _clients = MutableStateFlow<List<ClientEntity>>(emptyList())
    val clients: StateFlow<List<ClientEntity>> = _clients.asStateFlow()
    
    private val _pickupLocations = MutableStateFlow<List<PickupLocationEntity>>(emptyList())
    val pickupLocations: StateFlow<List<PickupLocationEntity>> = _pickupLocations.asStateFlow()
    
    private val _selectedClient = MutableStateFlow<ClientEntity?>(null)
    val selectedClient: StateFlow<ClientEntity?> = _selectedClient.asStateFlow()
    
    private val _clientDistances = MutableStateFlow<List<PickupClientDistanceEntity>>(emptyList())
    val clientDistances: StateFlow<List<PickupClientDistanceEntity>> = _clientDistances.asStateFlow()
    
    private val _clientSaved = MutableStateFlow(false)
    val clientSaved: StateFlow<Boolean> = _clientSaved.asStateFlow()
    
    private val _distanceSaved = MutableStateFlow(false)
    val distanceSaved: StateFlow<Boolean> = _distanceSaved.asStateFlow()
    
    init {
        loadClients()
        loadPickupLocations()
    }
    
    /**
     * Load all clients
     */
    fun loadClients() {
        viewModelScope.launch {
            clientRepository.getActiveClients().collect { clientList ->
                _clients.value = clientList
            }
        }
    }
    
    /**
     * Load all pickup locations
     */
    private fun loadPickupLocations() {
        viewModelScope.launch {
            pickupRepository.getAllActiveLocations().collect { locations ->
                _pickupLocations.value = locations
            }
        }
    }
    
    /**
     * Add a new client
     */
    fun addClient(
        name: String,
        address: String? = null,
        contactPerson: String? = null,
        contactPhone: String? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _error.value = null
            
            try {
                if (name.isBlank()) {
                    throw IllegalArgumentException("Client name is required")
                }
                
                val client = ClientEntity(
                    name = name.trim(),
                    address = address?.trim(),
                    contactPerson = contactPerson?.trim(),
                    contactPhone = contactPhone?.trim(),
                    notes = notes?.trim()
                )
                
                clientRepository.insert(client)
                _clientSaved.value = true
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add client"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update an existing client
     */
    fun updateClient(
        clientId: Long,
        name: String,
        address: String? = null,
        contactPerson: String? = null,
        contactPhone: String? = null,
        notes: String? = null,
        isActive: Boolean = true
    ) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _error.value = null
            
            try {
                if (name.isBlank()) {
                    throw IllegalArgumentException("Client name is required")
                }
                
                val existingClient = clientRepository.getClientById(clientId)
                    ?: throw IllegalArgumentException("Client not found")
                
                val updatedClient = existingClient.copy(
                    name = name.trim(),
                    address = address?.trim(),
                    contactPerson = contactPerson?.trim(),
                    contactPhone = contactPhone?.trim(),
                    notes = notes?.trim(),
                    isActive = isActive
                )
                
                clientRepository.update(updatedClient)
                _clientSaved.value = true
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update client"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Deactivate a client
     */
    fun deactivateClient(clientId: Long) {
        viewModelScope.launch {
            try {
                clientRepository.setClientActive(clientId, false)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to deactivate client"
            }
        }
    }
    
    /**
     * Select a client to view/edit distances
     */
    fun selectClient(client: ClientEntity) {
        _selectedClient.value = client
        loadClientDistances(client.id)
    }
    
    /**
     * Load distances for a specific client
     */
    fun loadClientDistances(clientId: Long) {
        viewModelScope.launch {
            pickupClientDistanceRepository.getDistancesByClient(clientId).collect { distances ->
                _clientDistances.value = distances
            }
        }
    }
    
    /**
     * Add or update distance from a pickup location to a client
     */
    fun setDistance(
        pickupLocationId: Long,
        clientId: Long,
        distanceKm: Double,
        estimatedTravelMinutes: Int? = null,
        isPreferred: Boolean = false,
        notes: String? = null
    ) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _error.value = null
            
            try {
                if (distanceKm < 0) {
                    throw IllegalArgumentException("Distance must be positive")
                }
                
                val existingDistance = pickupClientDistanceRepository.getDistance(pickupLocationId, clientId)
                
                val distance = if (existingDistance != null) {
                    existingDistance.copy(
                        distanceKm = distanceKm,
                        estimatedTravelMinutes = estimatedTravelMinutes,
                        isPreferred = isPreferred,
                        notes = notes,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    PickupClientDistanceEntity(
                        pickupLocationId = pickupLocationId,
                        clientId = clientId,
                        distanceKm = distanceKm,
                        estimatedTravelMinutes = estimatedTravelMinutes,
                        isPreferred = isPreferred,
                        notes = notes
                    )
                }
                
                pickupClientDistanceRepository.insert(distance)
                
                // If this is preferred, clear other preferred flags
                if (isPreferred) {
                    pickupClientDistanceRepository.setPreferredPickup(pickupLocationId, clientId)
                }
                
                _distanceSaved.value = true
                loadClientDistances(clientId)
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to set distance"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Remove distance mapping
     */
    fun removeDistance(pickupLocationId: Long, clientId: Long) {
        viewModelScope.launch {
            try {
                val distance = pickupClientDistanceRepository.getDistance(pickupLocationId, clientId)
                distance?.let {
                    pickupClientDistanceRepository.delete(it)
                    loadClientDistances(clientId)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to remove distance"
            }
        }
    }
    
    /**
     * Set a pickup as preferred for a client
     */
    fun setPreferredPickup(pickupLocationId: Long, clientId: Long) {
        viewModelScope.launch {
            try {
                pickupClientDistanceRepository.setPreferredPickup(pickupLocationId, clientId)
                loadClientDistances(clientId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to set preferred pickup"
            }
        }
    }
    
    /**
     * Reset client saved flag
     */
    fun resetClientSaved() {
        _clientSaved.value = false
    }
    
    /**
     * Reset distance saved flag
     */
    fun resetDistanceSaved() {
        _distanceSaved.value = false
    }
    
    /**
     * Clear selected client
     */
    fun clearSelectedClient() {
        _selectedClient.value = null
        _clientDistances.value = emptyList()
    }
}
