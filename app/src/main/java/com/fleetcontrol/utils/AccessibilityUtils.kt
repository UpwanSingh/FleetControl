package com.fleetcontrol.utils

/**
 * Accessibility utilities for consistent screen reader support
 * 
 * Provides standardized content descriptions for common UI elements
 * following Android accessibility best practices.
 */
object AccessibilityUtils {
    
    // ========================================
    // BUTTON CONTENT DESCRIPTIONS
    // ========================================
    
    fun buttonDescription(action: String): String = "$action button"
    fun buttonDescription(action: String, itemType: String): String = "$action $itemType button"
    
    // Common actions
    val saveButton = buttonDescription("Save")
    val cancelButton = buttonDescription("Cancel")
    val deleteButton = buttonDescription("Delete")
    val editButton = buttonDescription("Edit")
    val addButton = buttonDescription("Add")
    val backButton = buttonDescription("Go back")
    val closeButton = buttonDescription("Close")
    val confirmButton = buttonDescription("Confirm")
    val retryButton = buttonDescription("Retry")
    
    // Specific actions
    val addTripButton = buttonDescription("Add", "trip")
    val addFuelButton = buttonDescription("Add", "fuel entry")
    val addDriverButton = buttonDescription("Add", "driver")
    val addCompanyButton = buttonDescription("Add", "company")
    val addClientButton = buttonDescription("Add", "client")
    val approveButton = buttonDescription("Approve")
    val rejectButton = buttonDescription("Reject")
    
    // ========================================
    // ICON CONTENT DESCRIPTIONS
    // ========================================
    
    fun iconDescription(purpose: String): String = "$purpose icon"
    fun iconDescription(purpose: String, state: String): String = "$purpose $state icon"
    
    // Navigation icons
    val menuIcon = iconDescription("Menu")
    val settingsIcon = iconDescription("Settings")
    val homeIcon = iconDescription("Home")
    val backIcon = iconDescription("Back")
    val forwardIcon = iconDescription("Forward")
    val closeIcon = iconDescription("Close")
    val searchIcon = iconDescription("Search")
    val filterIcon = iconDescription("Filter")
    val moreIcon = iconDescription("More options")
    
    // Status icons
    val errorIcon = iconDescription("Error")
    val warningIcon = iconDescription("Warning")
    val infoIcon = iconDescription("Information")
    val successIcon = iconDescription("Success")
    val loadingIcon = iconDescription("Loading")
    
    // Action icons
    val editIcon = iconDescription("Edit")
    val deleteIcon = iconDescription("Delete")
    val addIcon = iconDescription("Add")
    val saveIcon = iconDescription("Save")
    val shareIcon = iconDescription("Share")
    val copyIcon = iconDescription("Copy")
    val downloadIcon = iconDescription("Download")
    val uploadIcon = iconDescription("Upload")
    
    // Business icons
    val businessIcon = iconDescription("Business")
    val driverIcon = iconDescription("Driver")
    val truckIcon = iconDescription("Truck")
    val gasStationIcon = iconDescription("Gas station")
    val moneyIcon = iconDescription("Money")
    val chartIcon = iconDescription("Chart")
    
    // ========================================
    // FIELD DESCRIPTIONS
    // ========================================
    
    fun fieldDescription(field: String): String = "$field field"
    fun fieldDescription(field: String, type: String): String = "$field $type field"
    
    // Common fields
    val emailField = fieldDescription("Email")
    val passwordField = fieldDescription("Password")
    val nameField = fieldDescription("Name")
    val phoneField = fieldDescription("Phone")
    val addressField = fieldDescription("Address")
    val amountField = fieldDescription("Amount")
    val notesField = fieldDescription("Notes")
    
    // Business fields
    val businessNameField = fieldDescription("Business name")
    val companyNameField = fieldDescription("Company name")
    val clientNameField = fieldDescription("Client name")
    val driverNameField = fieldDescription("Driver name")
    val bagCountField = fieldDescription("Bag count")
    val rateField = fieldDescription("Rate")
    val distanceField = fieldDescription("Distance")
    
    // ========================================
    // STATUS DESCRIPTIONS
    // ========================================
    
    fun statusDescription(status: String): String = "Status: $status"
    fun statusDescription(item: String, status: String): String = "$item status: $status"
    
    // Common statuses
    val loadingStatus = statusDescription("Loading")
    val errorStatus = statusDescription("Error")
    val successStatus = statusDescription("Success")
    val pendingStatus = statusDescription("Pending")
    val completedStatus = statusDescription("Completed")
    val rejectedStatus = statusDescription("Rejected")
    val approvedStatus = statusDescription("Approved")
    
    // ========================================
    // LIST ITEM DESCRIPTIONS
    // ========================================
    
    fun listItemDescription(item: String, index: Int): String = "$item, item ${index + 1}"
    fun listItemDescription(item: String, detail: String): String = "$item, $detail"
    fun listItemDescription(item: String, index: Int, detail: String): String = "$item, item ${index + 1}, $detail"
    
    // ========================================
    // NAVIGATION DESCRIPTIONS
    // ========================================
    
    fun navigationDescription(destination: String): String = "Navigate to $destination"
    fun navigationDescription(action: String, destination: String): String = "$action to $destination"
    
    // Common navigation
    val navigateToHome = navigationDescription("home")
    val navigateToSettings = navigationDescription("settings")
    val navigateToBack = navigationDescription("previous screen")
    val navigateToLogin = navigationDescription("login")
    
    // ========================================
    // TAB DESCRIPTIONS
    // ========================================
    
    fun tabDescription(tab: String, isSelected: Boolean): String {
        return if (isSelected) {
            "$tab tab, currently selected"
        } else {
            "$tab tab"
        }
    }
    
    // ========================================
    // ERROR MESSAGE DESCRIPTIONS
    // ========================================
    
    fun errorMessageDescription(error: String): String = "Error: $error"
    fun errorMessageDescription(field: String, error: String): String = "$field error: $error"
    fun validationErrorDescription(field: String, error: String): String = "$field validation error: $error"
    
    // ========================================
    // EMPTY STATE DESCRIPTIONS
    // ========================================
    
    fun emptyStateDescription(itemType: String): String = "No $itemType available"
    fun emptyStateDescription(itemType: String, action: String): String = "No $itemType available. $action"
    
    // Common empty states
    val noTripsMessage = emptyStateDescription("trips")
    val noFuelEntriesMessage = emptyStateDescription("fuel entries")
    val noDriversMessage = emptyStateDescription("drivers")
    val noCompaniesMessage = emptyStateDescription("companies")
    val noClientsMessage = emptyStateDescription("clients")
    
    // ========================================
    // SEMANTIC ACTIONS
    // ========================================
    
    fun semanticAction(action: String, target: String): String = "$action $target"
    fun semanticAction(action: String, target: String, result: String): String = "$action $target, $result"
    
    // Common actions
    val selectAction = { target: String -> semanticAction("Select", target) }
    val deleteAction = { target: String -> semanticAction("Delete", target) }
    val editAction = { target: String -> semanticAction("Edit", target) }
    val viewAction = { target: String -> semanticAction("View", target) }
    
    // ========================================
    // ACCESSIBILITY HINTS
    // ========================================
    
    fun accessibilityHint(hint: String): String = hint
    fun accessibilityHint(action: String, hint: String): String = "$action. $hint"
    
    // Common hints
    val doubleTapHint = accessibilityHint("Double tap to activate")
    val swipeHint = accessibilityHint("Swipe to see more options")
    val pullToRefreshHint = accessibilityHint("Pull down to refresh")
    
    // ========================================
    // ACCESSIBILITY ROLES
    // ========================================
    
    fun buttonRole(): String = "button"
    fun imageRole(): String = "image"
    fun textRole(): String = "text"
    fun headingRole(level: Int): String = "heading level $level"
    
    // ========================================
    // CONTEXTUAL DESCRIPTIONS
    // ========================================
    
    fun contextualDescription(item: String, context: String): String = "$item, $context"
    fun contextualDescription(item: String, context: String, action: String): String = "$item, $context. $action"
    
    // Examples
    fun tripDescription(tripId: String, status: String): String = contextualDescription("Trip $tripId", status)
    fun driverDescription(driverName: String, status: String): String = contextualDescription("Driver $driverName", status)
    fun companyDescription(companyName: String, clientCount: Int): String = contextualDescription("Company $companyName", "$clientCount clients")
}
