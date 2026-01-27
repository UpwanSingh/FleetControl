package com.fleetcontrol.domain.rulesengine

import com.fleetcontrol.data.entities.DriverEntity

/**
 * Action Item Generator
 * Generates actionable recommendations based on business state
 */
class ActionItemGenerator {
    
    /**
     * Generate action items based on current business state
     */
    fun generate(
        drivers: List<DriverEntity>,
        healthReports: List<DriverHealthReport>,
        moneyLeakReport: MoneyLeakReport,
        monthlyProfit: Double,
        previousMonthProfit: Double
    ): List<ActionItem> {
        val items = mutableListOf<ActionItem>()
        
        // Driver-related actions
        val inactiveDrivers = drivers.filter { !it.isActive }
        if (inactiveDrivers.isNotEmpty()) {
            items.add(ActionItem(
                priority = Priority.LOW,
                category = ActionCategory.DRIVER_MANAGEMENT,
                title = "Review Inactive Drivers",
                description = "${inactiveDrivers.size} drivers are currently inactive",
                action = "Consider reactivating or removing from system"
            ))
        }
        
        // Health report actions
        val criticalDrivers = healthReports.filter { it.status == HealthStatus.CRITICAL }
        if (criticalDrivers.isNotEmpty()) {
            items.add(ActionItem(
                priority = Priority.HIGH,
                category = ActionCategory.DRIVER_MANAGEMENT,
                title = "Drivers Need Attention",
                description = "${criticalDrivers.size} drivers have critical health status",
                action = "Review driver activity and outstanding advances"
            ))
        }
        
        val atRiskDrivers = healthReports.filter { it.status == HealthStatus.AT_RISK }
        if (atRiskDrivers.isNotEmpty()) {
            items.add(ActionItem(
                priority = Priority.MEDIUM,
                category = ActionCategory.DRIVER_MANAGEMENT,
                title = "At-Risk Drivers",
                description = "${atRiskDrivers.size} drivers showing declining performance",
                action = "Schedule check-in with these drivers"
            ))
        }
        
        // Money leak actions
        if (moneyLeakReport.riskLevel == RiskLevel.HIGH) {
            items.add(ActionItem(
                priority = Priority.HIGH,
                category = ActionCategory.FINANCIAL,
                title = "High Revenue Leakage Detected",
                description = "Estimated loss: ₹${moneyLeakReport.totalLeakageEstimate.toLong()}",
                action = "Review pricing and route efficiency"
            ))
        } else if (moneyLeakReport.riskLevel == RiskLevel.MEDIUM) {
            items.add(ActionItem(
                priority = Priority.MEDIUM,
                category = ActionCategory.FINANCIAL,
                title = "Revenue Optimization Opportunity",
                description = "Potential savings of ₹${moneyLeakReport.totalLeakageEstimate.toLong()}",
                action = "Review the money leak report details"
            ))
        }
        
        // Profit trend actions
        if (monthlyProfit < previousMonthProfit * 0.8) {
            items.add(ActionItem(
                priority = Priority.HIGH,
                category = ActionCategory.FINANCIAL,
                title = "Profit Decline",
                description = "Profit dropped more than 20% from last month",
                action = "Analyze expense increases or revenue drops"
            ))
        } else if (monthlyProfit > previousMonthProfit * 1.2) {
            items.add(ActionItem(
                priority = Priority.LOW,
                category = ActionCategory.FINANCIAL,
                title = "Strong Growth",
                description = "Profit increased by over 20%",
                action = "Consider reinvesting in fleet expansion"
            ))
        }
        
        // High outstanding advances
        // High outstanding advances check removed as DriverEntity does not contain balance info directly
        // Future improvement: Pass advance balance map to this generator
        
        return items.sortedByDescending { it.priority.ordinal }
    }
}

data class ActionItem(
    val priority: Priority,
    val category: ActionCategory,
    val title: String,
    val description: String,
    val action: String
)

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

enum class ActionCategory {
    DRIVER_MANAGEMENT,
    FINANCIAL,
    OPERATIONS,
    COMPLIANCE
}
