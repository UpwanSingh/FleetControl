package com.fleetcontrol.domain.security

import android.util.Base64
import android.util.Log
import com.fleetcontrol.data.entities.ClientEntity
import com.fleetcontrol.data.entities.CompanyEntity
import com.fleetcontrol.data.entities.DriverRateSlabEntity
import com.fleetcontrol.data.entities.DriverEntity
import com.fleetcontrol.data.entities.LabourCostRuleEntity
import com.fleetcontrol.data.entities.PickupClientDistanceEntity
import com.fleetcontrol.data.entities.PickupLocationEntity
import com.fleetcontrol.data.repositories.ClientRepository
import com.fleetcontrol.data.repositories.CompanyRepository
import com.fleetcontrol.data.repositories.PickupClientDistanceRepository
import com.fleetcontrol.data.repositories.PickupLocationRepository
import com.fleetcontrol.data.repositories.RateSlabRepository
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Handles generation and validation of comprehensive .fleet configuration files.
 * Includes driver credentials AND full company setup data.
 * Uses HMAC-SHA256 to ensure authenticity.
 */
object FleetConfigManager {
    
    private const val TAG = "FleetConfigManager"
    private const val SECRET_KEY = "FLEET_CONTROL_CONFIG_SECRET_V2"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val VERSION = 2

    /**
     * Complete fleet configuration data - EVERYTHING driver needs
     */
    data class FleetConfig(
        val version: Int,
        val driverId: Long,
        val driverName: String,
        val driverPhone: String,
        val driverPin: String,
        val companies: List<CompanyEntity>,
        val clients: List<ClientEntity>,
        val pickupLocations: List<PickupLocationEntity>,
        val rateSlabs: List<DriverRateSlabEntity>,
        val clientDistances: List<PickupClientDistanceEntity>,
        val labourCostRules: List<LabourCostRuleEntity>,
        val timestamp: Long
    )

    /**
     * Generates a signed JSON configuration string with full company data.
     */
    suspend fun generateFullConfig(
        driver: DriverEntity,
        companyRepository: CompanyRepository,
        clientRepository: ClientRepository,
        pickupLocationRepository: PickupLocationRepository,
        rateSlabRepository: RateSlabRepository,
        pickupClientDistanceRepository: PickupClientDistanceRepository,
        labourCostDao: com.fleetcontrol.data.dao.LabourCostDao
    ): String {
        val timestamp = System.currentTimeMillis()
        
        // Fetch ALL company data - driver needs EVERYTHING
        val companies = companyRepository.getAllCompaniesOnce()
        val clients = clientRepository.getAllClientsOnce()
        val pickupLocations = pickupLocationRepository.getAllPickupLocationsOnce()
        val rateSlabs = rateSlabRepository.getAllRateSlabsOnce()
        val clientDistances = pickupClientDistanceRepository.getAllDistancesOnce()
        val labourCostRules = labourCostDao.getAllRulesOnce()
        
        Log.d(TAG, "Generating config with: ${companies.size} companies, ${clients.size} clients, ${pickupLocations.size} pickups, ${rateSlabs.size} slabs, ${clientDistances.size} distances, ${labourCostRules.size} labour rules")
        
        // Build JSON payload
        val payload = JSONObject()
        payload.put("version", VERSION)
        payload.put("driverId", driver.id)
        payload.put("driverName", driver.name)
        payload.put("driverPhone", driver.phone)
        payload.put("driverPin", driver.pin)
        payload.put("ts", timestamp)
        
        // Companies - ALL of them (CompanyEntity has: name, contactPerson, contactPhone, perBagRate)
        val companiesArray = JSONArray()
        for (company in companies) {
            val companyJson = JSONObject()
            companyJson.put("id", company.id)
            companyJson.put("name", company.name)
            companyJson.put("contactPerson", company.contactPerson ?: "")
            companyJson.put("contactPhone", company.contactPhone ?: "")
            companyJson.put("perBagRate", company.perBagRate)
            companyJson.put("isActive", company.isActive)
            companiesArray.put(companyJson)
        }
        payload.put("companies", companiesArray)
        
        // Clients (ClientEntity has: name, address, contactPerson, contactPhone, notes)
        val clientsArray = JSONArray()
        for (client in clients) {
            val clientJson = JSONObject()
            clientJson.put("id", client.id)
            clientJson.put("name", client.name)
            clientJson.put("address", client.address ?: "")
            clientJson.put("contactPerson", client.contactPerson ?: "")
            clientJson.put("contactPhone", client.contactPhone ?: "")
            clientJson.put("notes", client.notes ?: "")
            clientJson.put("isActive", client.isActive)
            clientsArray.put(clientJson)
        }
        payload.put("clients", clientsArray)
        
        // Pickup Locations (PickupLocationEntity has: name, distanceFromBase)
        val pickupsArray = JSONArray()
        for (pickup in pickupLocations) {
            val pickupJson = JSONObject()
            pickupJson.put("id", pickup.id)
            pickupJson.put("name", pickup.name)
            pickupJson.put("distanceFromBase", pickup.distanceFromBase)
            pickupJson.put("isActive", pickup.isActive)
            pickupsArray.put(pickupJson)
        }
        payload.put("pickupLocations", pickupsArray)
        
        // Rate Slabs
        val slabsArray = JSONArray()
        for (slab in rateSlabs) {
            val slabJson = JSONObject()
            slabJson.put("id", slab.id)
            slabJson.put("minDistance", slab.minDistance)
            slabJson.put("maxDistance", slab.maxDistance)
            slabJson.put("ratePerBag", slab.ratePerBag)
            slabJson.put("isActive", slab.isActive)
            slabsArray.put(slabJson)
        }
        payload.put("rateSlabs", slabsArray)
        
        // Client Distances (ALL fields from PickupClientDistanceEntity)
        val distancesArray = JSONArray()
        for (dist in clientDistances) {
            val distJson = JSONObject()
            distJson.put("id", dist.id)
            distJson.put("pickupLocationId", dist.pickupLocationId)
            distJson.put("clientId", dist.clientId)
            distJson.put("distanceKm", dist.distanceKm)
            distJson.put("estimatedTravelMinutes", dist.estimatedTravelMinutes ?: -1)
            distJson.put("isPreferred", dist.isPreferred)
            distJson.put("notes", dist.notes ?: "")
            distancesArray.put(distJson)
        }
        payload.put("clientDistances", distancesArray)
        
        // Labour Cost Rules (LabourCostRuleEntity has: name, costPerBag, isDefault)
        val rulesArray = JSONArray()
        for (rule in labourCostRules) {
            val ruleJson = JSONObject()
            ruleJson.put("id", rule.id)
            ruleJson.put("name", rule.name)
            ruleJson.put("costPerBag", rule.costPerBag)
            ruleJson.put("isDefault", rule.isDefault)
            ruleJson.put("isActive", rule.isActive)
            rulesArray.put(ruleJson)
        }
        payload.put("labourCostRules", rulesArray)
        
        val payloadString = payload.toString()
        val signature = sign(payloadString)
        
        val finalObject = JSONObject()
        finalObject.put("payload", payloadString)
        finalObject.put("signature", signature)
        
        return finalObject.toString()
    }

    /**
     * Validates and parses a config string.
     */
    fun validateConfig(configJson: String): FleetConfig? {
        try {
            val root = JSONObject(configJson)
            if (!root.has("payload") || !root.has("signature")) {
                Log.e(TAG, "Missing payload or signature")
                return null
            }
            
            val payloadString = root.getString("payload")
            val signature = root.getString("signature")
            
            // Verify signature
            val expectedSignature = sign(payloadString)
            if (signature != expectedSignature) {
                Log.e(TAG, "Signature mismatch - file tampered")
                return null
            }
            
            val payload = JSONObject(payloadString)
            val version = payload.optInt("version", 1)
            
            // Parse driver info
            val driverId = payload.getLong("driverId")
            val driverName = payload.getString("driverName")
            val driverPhone = payload.optString("driverPhone", "")
            val driverPin = payload.optString("driverPin", "")
            val timestamp = payload.getLong("ts")
            
            // Parse companies - ALL of them
            val companies = mutableListOf<CompanyEntity>()
            if (payload.has("companies")) {
                val arr = payload.getJSONArray("companies")
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    companies.add(CompanyEntity(
                        id = c.getLong("id"),
                        name = c.getString("name"),
                        contactPerson = c.optString("contactPerson", null),
                        contactPhone = c.optString("contactPhone", null),
                        perBagRate = c.getDouble("perBagRate"),
                        isActive = c.optBoolean("isActive", true),
                        createdAt = timestamp
                    ))
                }
            } else if (payload.has("company")) {
                // Backward compatibility - single company
                val c = payload.getJSONObject("company")
                companies.add(CompanyEntity(
                    id = c.getLong("id"),
                    name = c.getString("name"),
                    contactPerson = c.optString("contactPerson", null),
                    contactPhone = c.optString("contactPhone", null),
                    perBagRate = c.getDouble("perBagRate"),
                    isActive = c.optBoolean("isActive", true),
                    createdAt = timestamp
                ))
            }
            
            // Parse clients
            val clients = mutableListOf<ClientEntity>()
            if (payload.has("clients")) {
                val arr = payload.getJSONArray("clients")
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    clients.add(ClientEntity(
                        id = c.getLong("id"),
                        name = c.getString("name"),
                        address = c.optString("address", null),
                        contactPerson = c.optString("contactPerson", null),
                        contactPhone = c.optString("contactPhone", null),
                        notes = c.optString("notes", null),
                        isActive = c.optBoolean("isActive", true),
                        createdAt = timestamp
                    ))
                }
            }
            
            // Parse pickup locations
            val pickupLocations = mutableListOf<PickupLocationEntity>()
            if (payload.has("pickupLocations")) {
                val arr = payload.getJSONArray("pickupLocations")
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    pickupLocations.add(PickupLocationEntity(
                        id = p.getLong("id"),
                        name = p.getString("name"),
                        distanceFromBase = p.optDouble("distanceFromBase", 0.0),
                        isActive = p.optBoolean("isActive", true),
                        createdAt = timestamp
                    ))
                }
            }
            
            // Parse rate slabs
            val rateSlabs = mutableListOf<DriverRateSlabEntity>()
            if (payload.has("rateSlabs")) {
                val arr = payload.getJSONArray("rateSlabs")
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    rateSlabs.add(DriverRateSlabEntity(
                        id = s.getLong("id"),
                        minDistance = s.getDouble("minDistance"),
                        maxDistance = s.getDouble("maxDistance"),
                        ratePerBag = s.getDouble("ratePerBag"),
                        isActive = s.optBoolean("isActive", true),
                        createdAt = timestamp
                    ))
                }
            }
            
            // Parse client distances (ALL fields)
            val clientDistances = mutableListOf<PickupClientDistanceEntity>()
            if (payload.has("clientDistances")) {
                val arr = payload.getJSONArray("clientDistances")
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    val travelMinutes = d.optInt("estimatedTravelMinutes", -1)
                    clientDistances.add(PickupClientDistanceEntity(
                        id = d.getLong("id"),
                        pickupLocationId = d.getLong("pickupLocationId"),
                        clientId = d.getLong("clientId"),
                        distanceKm = d.getDouble("distanceKm"),
                        estimatedTravelMinutes = if (travelMinutes >= 0) travelMinutes else null,
                        isPreferred = d.optBoolean("isPreferred", false),
                        notes = d.optString("notes", null),
                        createdAt = timestamp,
                        updatedAt = timestamp
                    ))
                }
            }
            
            // Parse labour cost rules
            val labourCostRules = mutableListOf<LabourCostRuleEntity>()
            if (payload.has("labourCostRules")) {
                val arr = payload.getJSONArray("labourCostRules")
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    labourCostRules.add(LabourCostRuleEntity(
                        id = r.getLong("id"),
                        name = r.optString("name", "Labour"),
                        costPerBag = r.getDouble("costPerBag"),
                        isDefault = r.optBoolean("isDefault", false),
                        isActive = r.optBoolean("isActive", true),
                        createdAt = timestamp
                    ))
                }
            }
            
            Log.d(TAG, "Parsed config: ${companies.size} companies, ${clients.size} clients, ${pickupLocations.size} pickups, ${rateSlabs.size} slabs, ${clientDistances.size} distances, ${labourCostRules.size} labour rules")
            
            return FleetConfig(
                version = version,
                driverId = driverId,
                driverName = driverName,
                driverPhone = driverPhone,
                driverPin = driverPin,
                companies = companies,
                clients = clients,
                pickupLocations = pickupLocations,
                rateSlabs = rateSlabs,
                clientDistances = clientDistances,
                labourCostRules = labourCostRules,
                timestamp = timestamp
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config", e)
            Log.e("FleetConfigManager", "Error loading config", e)
            return null
        }
    }

    private fun sign(data: String): String {
        val sha256HMAC = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(SECRET_KEY.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)
        sha256HMAC.init(secretKey)
        
        val bytes = sha256HMAC.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
