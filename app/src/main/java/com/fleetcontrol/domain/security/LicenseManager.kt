package com.fleetcontrol.domain.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object LicenseManager {
    private const val SECRET_SALT = "FLEET_CONTROL_2026_SUPER_SECRET_KEY_99"

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
    }

    fun validateKey(context: Context, inputKey: String): Boolean {
        val deviceId = getDeviceId(context)
        val expectedKey = generateHash(deviceId + SECRET_SALT)
        
        // Compare input (normalized) with expected (first 8 chars)
        return inputKey.trim().uppercase() == expectedKey
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val hexString = bytes.joinToString("") { "%02x".format(it) }
        return hexString.take(8).uppercase()
    }
}
