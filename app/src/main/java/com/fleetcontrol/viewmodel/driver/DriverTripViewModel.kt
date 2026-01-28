package com.fleetcontrol.viewmodel.driver

import android.net.Uri
import androidx.lifecycle.viewModelScope
import android.util.Log
import java.util.UUID
import androidx.paging.cachedIn
import com.fleetcontrol.data.entities.AttachmentType
import com.fleetcontrol.data.entities.ClientEntity
import com.fleetcontrol.data.entities.CompanyEntity
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.data.entities.TripAttachmentEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.entities.TripStatus
import com.fleetcontrol.data.repositories.ClientRepository
import com.fleetcontrol.data.repositories.CompanyRepository
import com.fleetcontrol.data.repositories.PickupClientDistanceRepository
import com.fleetcontrol.data.repositories.PickupLocationRepository
import com.fleetcontrol.data.repositories.TripAttachmentRepository
import com.fleetcontrol.data.repositories.TripRepository
import com.fleetcontrol.domain.rulesengine.RateSlabResolver
import com.fleetcontrol.data.dao.LabourCostDao
import com.fleetcontrol.core.SessionManager
import com.fleetcontrol.services.ImageCaptureService
import com.fleetcontrol.services.ImageSaveResult
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for driver trip entry
 * Implements Section 4, 9.1 of BUSINESS_LOGIC_SPEC.md
 *
 * Driver CAN (Section 9.1):
 * - Add trips
 * - View own trips
 * 
 * Trip Flow:
 * 1. Select Company (product supplier)
 * 2. Select Client (delivery destination)
 * 3. Select Pickup Location (or use suggested nearest/preferred)
 * 4. Enter Bag Count
 * 5. Distance is auto-looked up from Pickup-Client mapping
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverTripViewModel(
    private val tripRepository: TripRepository,
    private val tripAttachmentRepository: TripAttachmentRepository,
    private val companyRepository: CompanyRepository,
    private val clientRepository: ClientRepository,
    private val pickupLocationRepository: PickupLocationRepository,
    private val pickupClientDistanceRepository: PickupClientDistanceRepository,
    private val rateSlabResolver: RateSlabResolver,
    private val labourCostDao: LabourCostDao,
    private val sessionManager: SessionManager,
    private val driverRepository: com.fleetcontrol.data.repositories.DriverRepository,
    private val imageCaptureService: ImageCaptureService,
    private val cloudTripRepository: com.fleetcontrol.data.repositories.CloudTripRepository
) : BaseViewModel() {
    
    private val _companies = MutableStateFlow<List<CompanyEntity>>(emptyList())
    val companies: StateFlow<List<CompanyEntity>> = _companies.asStateFlow()
    
    private val _clients = MutableStateFlow<List<ClientEntity>>(emptyList())
    val clients: StateFlow<List<ClientEntity>> = _clients.asStateFlow()
    
    private val _pickupLocations = MutableStateFlow<List<PickupLocationEntity>>(emptyList())
    val pickupLocations: StateFlow<List<PickupLocationEntity>> = _pickupLocations.asStateFlow()
    
    // Available pickup options for selected client (with distances)
    private val _pickupOptionsForClient = MutableStateFlow<List<PickupClientDistanceEntity>>(emptyList())
    val pickupOptionsForClient: StateFlow<List<PickupClientDistanceEntity>> = _pickupOptionsForClient.asStateFlow()
    
    // Suggested/recommended pickup for selected client
    private val _suggestedPickup = MutableStateFlow<PickupClientDistanceEntity?>(null)
    val suggestedPickup: StateFlow<PickupClientDistanceEntity?> = _suggestedPickup.asStateFlow()
    
    // Paging Data Flow - uses a dynamic flow based on current session
    val pagedTrips: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<TripEntity>> = 
        sessionManager.currentSession.flatMapLatest { session ->
            val driverId = (session as? com.fleetcontrol.core.SessionState.DriverSession)?.driverId ?: 0L
            if (driverId > 0) {
                tripRepository.getPagedTripsByDriver(driverId)
            } else {
                // Return empty paging data when not logged in
                flowOf(androidx.paging.PagingData.empty())
            }
        }.cachedIn(viewModelScope)
    
    private val _tripSaved = MutableStateFlow(false)
    val tripSaved: StateFlow<Boolean> = _tripSaved.asStateFlow()
    
    init {
        loadFormData()
    }
    
    private fun loadFormData() {
        viewModelScope.launch {
            companyRepository.getAllActiveCompanies().collect { _companies.value = it }
        }
        viewModelScope.launch {
            clientRepository.getActiveClients().collect { _clients.value = it }
        }
        viewModelScope.launch {
            pickupLocationRepository.getAllActiveLocations().collect { _pickupLocations.value = it }
        }
    }
    
    /**
     * When a client is selected, load available pickup options
     * Sorted by distance (nearest first)
     */
    fun onClientSelected(clientId: Long) {
        viewModelScope.launch {
            // Get available pickup locations for this client
            pickupClientDistanceRepository.getDistancesByClient(clientId).collect { distances ->
                _pickupOptionsForClient.value = distances
            }
        }
        
        // Get suggested pickup (preferred or nearest)
        viewModelScope.launch {
            val suggested = pickupClientDistanceRepository.getBestPickupForClient(clientId)
            _suggestedPickup.value = suggested
        }
    }
    
    /**
     * Clear pickup options when client selection is cleared
     */
    fun clearClientSelection() {
        _pickupOptionsForClient.value = emptyList()
        _suggestedPickup.value = null
    }
    
    /**
     * Add new trip with client ID
     * Per Section 4.1: Trip MUST include driver, company, client, pickup, bagCount, timestamp
     * Per Section 5: Rates are snapshotted at trip time
     */
    fun addTrip(
        companyId: Long,
        clientId: Long,
        pickupLocationId: Long,
        bagCount: Int
    ) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _error.value = null
            
            try {
                val driverId = sessionManager.getCurrentDriverId()
                    ?: throw IllegalStateException("Driver not logged in")
                
                // Validate bag count per Section 4.1
                if (bagCount <= 0) {
                    throw IllegalArgumentException("Bag count must be greater than 0")
                }
                
                // Safety Check 1: Verify Driver Active Status (Security)
                val driver = driverRepository.getDriverById(driverId)
                    ?: throw IllegalStateException("Driver not found")
                
                if (!driver.isActive) {
                    throw IllegalStateException("Driver account is inactive. Please contact owner.")
                }
                
                // Safety Check 2: Offline Queue Limit (Data Integrity)
                val pendingCount = tripRepository.getPendingTripCount()
                if (pendingCount >= 100) {
                    throw IllegalStateException("Offline queue full (100 trips). Please connect to internet and sync.")
                }
                
                // Get company for rate snapshot
                val company = companyRepository.getCompanyById(companyId)
                    ?: throw IllegalStateException("Company not found")
                
                // Get client for the trip
                val client = clientRepository.getClientById(clientId)
                    ?: throw IllegalStateException("Client not found")
                
                // Get pickup location (validate it exists)
                pickupLocationRepository.getLocationById(pickupLocationId)
                    ?: throw IllegalStateException("Pickup location not found")
                
                // Get distance from pickup to client
                val distanceKm = pickupClientDistanceRepository.getDistanceKm(pickupLocationId, clientId)
                    ?: throw IllegalStateException("Distance not set for this pickup-client combination. Please contact owner to set up distance.")
                
                // Resolve driver rate based on distance (Section 3.2)
                val driverRate = rateSlabResolver.resolveDriverRate(distanceKm)
                
                // Get default labour cost
                val labourCostRule = labourCostDao.getDefaultRule()
                val labourCostPerBag = labourCostRule?.costPerBag ?: 0.0
                
                // Driver already fetched above for validation
                // val driver = driverRepository.getDriverById(driverId) ...

                // Create trip with rate snapshots (Section 13: snapshot for historical accuracy)
                val trip = TripEntity(
                    uuid = UUID.randomUUID().toString(),
                    driverId = driverId,
                    companyId = companyId,
                    pickupLocationId = pickupLocationId,
                    clientId = clientId,
                    clientName = client.name, // Store client name for display/legacy support
                    bagCount = bagCount,
                    snapshotDistanceKm = distanceKm,
                    snapshotDriverRate = driverRate,
                    snapshotCompanyRate = company.perBagRate,
                    snapshotLabourCostPerBag = labourCostPerBag,
                    tripDate = System.currentTimeMillis(),
                    status = TripStatus.COMPLETED
                )
                
                // Local Write (Room) - TripRepository will handle cloud sync
                tripRepository.insert(trip)
                
                _tripSaved.value = true
                
            } catch (e: Exception) {
                // Only fail if Local DB insert fails
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.message ?: "Failed to add trip"
            } finally {
                _isLoading.value = false
            }
        }
    }
    

    
    fun resetTripSaved() {
        _tripSaved.value = false
    }
    
    // === Attachment Support ===
    
    private val _selectedTripForAttachment = MutableStateFlow<TripEntity?>(null)
    val selectedTripForAttachment: StateFlow<TripEntity?> = _selectedTripForAttachment.asStateFlow()
    
    private val _tripAttachments = MutableStateFlow<List<TripAttachmentEntity>>(emptyList())
    val tripAttachments: StateFlow<List<TripAttachmentEntity>> = _tripAttachments.asStateFlow()
    
    private val _attachmentAdded = MutableStateFlow(false)
    val attachmentAdded: StateFlow<Boolean> = _attachmentAdded.asStateFlow()
    
    /**
     * Select a trip to view/add attachments
     */
    fun selectTripForAttachments(trip: TripEntity) {
        _selectedTripForAttachment.value = trip
        loadAttachmentsForTrip(trip.id)
    }
    
    /**
     * Clear selected trip
     */
    fun clearSelectedTrip() {
        _selectedTripForAttachment.value = null
        _tripAttachments.value = emptyList()
    }
    
    /**
     * Load attachments for a trip
     */
    private fun loadAttachmentsForTrip(tripId: Long) {
        viewModelScope.launch {
            tripAttachmentRepository.getAttachmentsForTrip(tripId).collect { attachments ->
                _tripAttachments.value = attachments
            }
        }
    }
    
    /**
     * Add attachment from gallery URI
     */
    fun addAttachmentFromGallery(uri: Uri, tripId: Long, type: AttachmentType, caption: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val prefix = when (type) {
                    AttachmentType.LOADING_PHOTO -> "LOAD"
                    AttachmentType.DELIVERY_PROOF -> "PROOF"
                    AttachmentType.RECEIPT_CHALLAN -> "RECEIPT"
                    AttachmentType.DAMAGE_REPORT -> "DAMAGE"
                    AttachmentType.VEHICLE_ISSUE -> "VEHICLE"
                    AttachmentType.OTHER -> "DOC"
                }
                
                when (val result = imageCaptureService.processAndSaveImage(uri, tripId, prefix)) {
                    is ImageSaveResult.Success -> {
                        val attachment = TripAttachmentEntity(
                            tripId = tripId,
                            attachmentType = type,
                            filePath = result.filePath,
                            fileName = result.fileName,
                            mimeType = "image/jpeg",
                            fileSize = result.fileSize,
                            caption = caption
                        )
                        tripAttachmentRepository.addAttachment(attachment)
                        _attachmentAdded.value = true
                    }
                    is ImageSaveResult.Error -> {
                        _error.value = result.message
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save attachment"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Add attachment from camera file
     */
    fun addAttachmentFromCamera(file: File, tripId: Long, type: AttachmentType, caption: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val prefix = when (type) {
                    AttachmentType.LOADING_PHOTO -> "LOAD"
                    AttachmentType.DELIVERY_PROOF -> "PROOF"
                    AttachmentType.RECEIPT_CHALLAN -> "RECEIPT"
                    AttachmentType.DAMAGE_REPORT -> "DAMAGE"
                    AttachmentType.VEHICLE_ISSUE -> "VEHICLE"
                    AttachmentType.OTHER -> "DOC"
                }
                
                when (val result = imageCaptureService.processAndSaveCameraImage(file, tripId, prefix)) {
                    is ImageSaveResult.Success -> {
                        val attachment = TripAttachmentEntity(
                            tripId = tripId,
                            attachmentType = type,
                            filePath = result.filePath,
                            fileName = result.fileName,
                            mimeType = "image/jpeg",
                            fileSize = result.fileSize,
                            caption = caption
                        )
                        tripAttachmentRepository.addAttachment(attachment)
                        _attachmentAdded.value = true
                    }
                    is ImageSaveResult.Error -> {
                        _error.value = result.message
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save camera image"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete an attachment
     */
    fun deleteAttachment(attachment: TripAttachmentEntity) {
        viewModelScope.launch {
            try {
                // Delete file from storage
                imageCaptureService.deleteImage(attachment.filePath)
                // Delete from database
                tripAttachmentRepository.deleteAttachment(attachment)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete attachment"
            }
        }
    }
    
    /**
     * Get attachment count for a trip
     */
    suspend fun getAttachmentCount(tripId: Long): Int {
        return tripAttachmentRepository.getAttachmentCount(tripId)
    }
    
    fun resetAttachmentAdded() {
        _attachmentAdded.value = false
    }
    
    fun retrySync(trip: TripEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                tripRepository.retrySync(trip.id)
            } catch (e: Exception) {
                _error.value = "Retry failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
