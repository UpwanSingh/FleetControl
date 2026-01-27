package com.fleetcontrol.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User entity for role-based access
 * Implements Section 2.1, 2.4, 9 of BUSINESS_LOGIC_SPEC.md
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val pin: String,
    val role: String,  // "owner", "driver", "manager"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * User roles
 */
object UserRole {
    const val OWNER = "owner"
    const val DRIVER = "driver"
    const val MANAGER = "manager"
}
