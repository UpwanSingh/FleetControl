package com.fleetcontrol.ui.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleetcontrol.services.billing.BillingService
import com.fleetcontrol.services.billing.BillingState
import com.fleetcontrol.services.billing.SubscriptionPlan
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.settings.SubscriptionViewModel

/**
 * Subscription Screen with Google Play Billing
 * Allows users to purchase Basic or Premium plans
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentPlan by viewModel.currentPlan.collectAsState()
    val billingState by viewModel.billingState.collectAsState()
    val basicPrice by viewModel.basicPrice.collectAsState()
    val premiumPrice by viewModel.premiumPrice.collectAsState()
    val purchaseError by viewModel.purchaseError.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedPlan by remember { mutableStateOf<String?>(null) }
    
    // Dismiss error after showing
    LaunchedEffect(purchaseError) {
        if (purchaseError != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Subscription", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    navigationIconContentColor = FleetColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(FleetDimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
        ) {
            // Current Plan Card
            CurrentPlanCard(currentPlan)
            
            // Error message
            purchaseError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = FleetColors.Error.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = FleetColors.Error)
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = FleetColors.Error)
                    }
                }
            }
            
            // Billing state warning
            if (billingState != BillingState.CONNECTED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = FleetColors.Warning.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = FleetColors.Warning)
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(
                            "Connecting to Google Play...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextPrimary
                        )
                    }
                }
            }
            
            if (currentPlan == SubscriptionPlan.FREE) {
                Text(
                    "Upgrade to unlock more features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
                
                // Basic Plan
                PlanCard(
                    title = "Basic",
                    price = basicPrice ?: "₹99/month",
                    features = listOf(
                        "Up to 10 drivers",
                        "CSV export",
                        "Backup & restore",
                        "Email support"
                    ),
                    isSelected = selectedPlan == "basic",
                    isPremium = false,
                    isCurrentPlan = false,
                    onClick = { selectedPlan = "basic" }
                )
                
                // Premium Plan
                PlanCard(
                    title = "Premium",
                    price = premiumPrice ?: "₹199/month",
                    features = listOf(
                        "Unlimited drivers",
                        "CSV + PDF export",
                        "Backup & restore",
                        "Advanced analytics",
                        "Trend forecasting",
                        "Priority support"
                    ),
                    isSelected = selectedPlan == "premium",
                    isPremium = true,
                    isCurrentPlan = false,
                    onClick = { selectedPlan = "premium" }
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Subscribe Button
                Button(
                    onClick = {
                        val activity = context as? Activity
                        selectedPlan?.let { plan ->
                            if (activity != null) {
                                viewModel.purchasePlan(activity, plan)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary),
                    enabled = selectedPlan != null && billingState == BillingState.CONNECTED && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(FleetDimens.IconSmall), // 18-20dp
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                    }
                    Text(
                        if (selectedPlan != null) 
                            "Subscribe to ${selectedPlan?.replaceFirstChar { it.uppercase() }}"
                        else 
                            "Select a plan",
                        color = Color.White
                    )
                }
            } else {
                // Already subscribed - show current plan features
                Text(
                    "Your Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FleetColors.TextPrimary
                )
                
                val features = if (currentPlan == SubscriptionPlan.PREMIUM) {
                    listOf(
                        "Unlimited drivers" to Icons.Default.People,
                        "CSV + PDF export" to Icons.Default.Description,
                        "Backup & restore" to Icons.Default.CloudUpload,
                        "Advanced analytics" to Icons.Default.Analytics,
                        "Trend forecasting" to Icons.Default.TrendingUp,
                        "Priority support" to Icons.Default.Support
                    )
                } else {
                    listOf(
                        "Up to 10 drivers" to Icons.Default.People,
                        "CSV export" to Icons.Default.Description,
                        "Backup & restore" to Icons.Default.CloudUpload,
                        "Email support" to Icons.Default.Email
                    )
                }
                
                features.forEach { (feature, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = FleetColors.Success,
                            modifier = Modifier.size(FleetDimens.IconMedium)
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.CornerMedium)) // 12dp for spacing
                        Text(feature, style = MaterialTheme.typography.bodyMedium, color = FleetColors.TextPrimary)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { viewModel.manageSubscription(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FleetColors.Primary)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                    Text("Manage Subscription")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Subscriptions are managed through Google Play. You can cancel anytime from Play Store settings.",
                style = MaterialTheme.typography.bodySmall,
                color = FleetColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CurrentPlanCard(currentPlan: SubscriptionPlan) {
    val (planName, gradientColors) = when (currentPlan) {
        SubscriptionPlan.PREMIUM -> "PREMIUM" to listOf(
            Color(0xFF6200EE),
            Color(0xFFBB86FC)
        )
        SubscriptionPlan.BASIC -> "BASIC" to listOf(
            Color(0xFF03DAC5),
            Color(0xFF018786)
        )
        SubscriptionPlan.FREE -> "FREE" to listOf(
            Color(0xFF757575),
            Color(0xFF424242)
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Current Plan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingXSmall))
                    Text(
                        planName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                if (currentPlan != SubscriptionPlan.FREE) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(FleetDimens.IconLarge) // 32dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanCard(
    title: String,
    price: String,
    features: List<String>,
    isSelected: Boolean,
    isPremium: Boolean,
    isCurrentPlan: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, FleetColors.Primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentPlan -> FleetColors.Primary.copy(alpha = 0.1f)
                isSelected -> FleetColors.Primary.copy(alpha = 0.05f)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(FleetDimens.SpacingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FleetColors.TextPrimary
                        )
                        if (isPremium) {
                            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FleetColors.Success
                            ) {
                                Text(
                                    "BEST VALUE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        price,
                        style = MaterialTheme.typography.titleSmall,
                        color = FleetColors.Success
                    )
                }
                
                when {
                    isCurrentPlan -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Current Plan",
                        tint = FleetColors.Success
                    )
                    isSelected -> Icon(
                        Icons.Default.RadioButtonChecked,
                        contentDescription = "Selected",
                        tint = FleetColors.Primary
                    )
                    else -> Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Not Selected",
                        tint = FleetColors.TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.CornerMedium)) // 12dp
            
            features.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(FleetDimens.IconSmall), // 18dp close to 16dp
                        tint = FleetColors.Success
                    )
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                    Text(
                        feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextPrimary
                    )
                }
            }
        }
    }
}
