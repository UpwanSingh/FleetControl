package com.fleetcontrol.ui.navigation

/**
 * Navigation routes for FleetControl app
 */
object Routes {
    // Onboarding
    const val ONBOARDING = "onboarding"
    
    // Auth
    const val OWNER_AUTH = "owner_auth"  // Multi-Tenancy: Owner email login
    const val LOGIN = "login"             // PIN-based login
    const val DRIVER_JOIN = "driver_join" // Driver invite code entry
    
    // Owner screens
    const val OWNER_DASHBOARD = "owner/dashboard"
    const val OWNER_DRIVERS = "owner/drivers"
    const val OWNER_DRIVER_DETAIL = "owner/driver/{driverId}"
    const val OWNER_COMPANIES = "owner/companies"
    const val OWNER_PICKUPS = "owner/pickups"
    const val OWNER_CLIENTS = "owner/clients"
    const val OWNER_PROFIT = "owner/profit"
    const val OWNER_REPORTS = "owner/reports"
    const val PENDING_TRIPS = "owner/pending_trips"  // Security hardening: approval workflow
    const val PENDING_FUEL_REQUESTS = "owner/pending_fuel"  // Security hardening: fuel approval
    
    // Driver screens
    const val DRIVER_HOME = "driver/home"
    const val DRIVER_TRIPS = "driver/trips"
    const val DRIVER_FUEL = "driver/fuel"
    const val DRIVER_EARNINGS = "driver/earnings"
    const val DRIVER_HISTORY = "driver/history"
    const val DRIVER_REPORTS = "driver/reports"
    
    // Settings
    const val SETTINGS = "settings"
    const val SUBSCRIPTION = "settings/subscription"
    const val BACKUP = "settings/backup"
    const val SECURITY = "settings/security"
    const val RATE_SETTINGS = "settings/rate_settings"
    const val ABOUT = "settings/about"
    
    // Helper functions
    fun driverDetail(driverId: Long) = "owner/driver/$driverId"
}
