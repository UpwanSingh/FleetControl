package com.fleetcontrol.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import com.fleetcontrol.domain.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fleetcontrol_settings")

/**
 * App Settings Manager using DataStore
 */
class AppSettings(private val context: Context) {
    
    companion object {
        // Keys
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_OWNER_PIN = stringPreferencesKey("owner_pin")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val KEY_LICENSE_ACTIVATED = booleanPreferencesKey("license_activated") // Added
        private val KEY_DRIVER_ACCESS_GRANTED = booleanPreferencesKey("driver_access_granted")
        private val KEY_LINKED_DRIVER_ID = longPreferencesKey("linked_driver_id")
        private val KEY_LINKED_OWNER_ID = stringPreferencesKey("linked_owner_id")
        private val KEY_IMPORTED_DRIVER_PIN = stringPreferencesKey("imported_driver_pin")
    }
    
    // Theme Mode
    val themeMode: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_THEME_MODE] ?: "system" }
    
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }
    
    // App Lock
    val appLockEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_APP_LOCK_ENABLED] ?: false }
    
    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_LOCK_ENABLED] = enabled
        }
    }
    
    // Biometric
    val biometricEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_BIOMETRIC_ENABLED] ?: false }
    
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }
    
    // Last Backup Time
    val lastBackupTime: Flow<Long?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_LAST_BACKUP_TIME] }
    
    suspend fun setLastBackupTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_BACKUP_TIME] = timestamp
        }
    }
    
    // Auto Backup
    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_AUTO_BACKUP_ENABLED] ?: false }
    
    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_BACKUP_ENABLED] = enabled
        }
    }
    
    // Owner PIN (stored as SHA-256 hash for security)
    val ownerPinHash: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_OWNER_PIN] }
    
    /**
     * Store the PIN as a SHA-256 hash (never store plaintext)
     */
    suspend fun setOwnerPin(pin: String) {
        context.dataStore.edit { preferences ->
            // Store using salted SHA-256 hash
            preferences[KEY_OWNER_PIN] = PinHasher.hash(pin)
        }
    }
    
    /**
     * Verify a PIN against the stored hash.
     * Supports backward compatibility:
     * - New salted hashes (via PinHasher)
     * - Legacy unsalted hashes
     * - Legacy plaintext PINs (for very old users)
     */
    suspend fun verifyOwnerPin(inputPin: String): Boolean {
        val storedHash = ownerPinHash.first() ?: return false
        
        // Use PinHasher.verify() which handles:
        // 1. Plaintext legacy PINs (short strings)
        // 2. Hashed PINs (64-char hex strings)
        return PinHasher.verify(inputPin, storedHash)
    }
    
    // First Launch
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_FIRST_LAUNCH] ?: true }
    
    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_LAUNCH] = false
        }
    }

    // License Activation
    val isLicenseActivated: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_LICENSE_ACTIVATED] ?: false }

    suspend fun setLicenseActivated(activated: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LICENSE_ACTIVATED] = activated
        }
    }

    // Driver Magic Link Access
    val isDriverAccessGranted: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_DRIVER_ACCESS_GRANTED] ?: false }

    val linkedDriverId: Flow<Long?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_LINKED_DRIVER_ID] }

    suspend fun setDriverAccessGranted(granted: Boolean, driverId: Long, ownerId: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DRIVER_ACCESS_GRANTED] = granted
            preferences[KEY_LINKED_DRIVER_ID] = driverId
            if (ownerId != null) {
                preferences[KEY_LINKED_OWNER_ID] = ownerId
            }
        }
    }
    
    val linkedOwnerId: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_LINKED_OWNER_ID] }
    
    // Imported Driver PIN (from .fleet file for cross-device auth)
    val importedDriverPin: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_IMPORTED_DRIVER_PIN] }
    
    suspend fun setImportedDriverPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IMPORTED_DRIVER_PIN] = pin
        }
    }
    
    // Clear all settings
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
