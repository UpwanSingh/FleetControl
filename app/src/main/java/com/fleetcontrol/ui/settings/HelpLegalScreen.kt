package com.fleetcontrol.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens

/**
 * Help & Legal Screen with FAQ and functional legal links
 * Opens URLs in browser for Terms, Privacy, Licenses
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpLegalScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Help & Legal", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                .verticalScroll(scrollState)
                .padding(FleetDimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
        ) {
            // FAQ Section
            Text(
                "Frequently Asked Questions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FleetColors.TextPrimary
            )
            
            FaqItem(
                question = "How are driver earnings calculated?",
                answer = "Driver earnings are calculated as: Bags × Per-Bag Rate. The rate is determined by the pickup location's distance zone:\n\n• Zone A (0-10km): Lower rate\n• Zone B (10-25km): Medium rate\n• Zone C (25km+): Higher rate"
            )
            
            FaqItem(
                question = "How is owner profit calculated?",
                answer = "Owner profit = Gross Revenue - Driver Earnings - Labour Costs.\n\nGross Revenue comes from company per-bag rates multiplied by bags delivered. Driver earnings are based on your rate slabs. Labour costs are additional expenses you define per pickup."
            )
            
            FaqItem(
                question = "What happens to driver advances?",
                answer = "Advances are tracked per driver. When calculating driver payable:\n\n1. Take gross earnings\n2. Subtract fuel costs\n3. Subtract advance deduction (auto-calculated based on your recovery percentage)\n\nRemaining advance balance carries forward until fully recovered."
            )
            
            FaqItem(
                question = "How do I set up pickup locations?",
                answer = "Go to Dashboard → Pickups → Add Location. Enter:\n\n• Location name\n• Distance from base (km)\n\nThe distance determines which rate zone applies to trips from that pickup."
            )
            
            FaqItem(
                question = "Can multiple drivers use the app?",
                answer = "Yes! Each driver logs in with their own PIN. They can:\n\n• Log trips with bag counts\n• Add fuel entries\n• View their earnings\n\nAs owner, you see everything in your dashboard."
            )
            
            Divider(modifier = Modifier.padding(vertical = FleetDimens.SpacingSmall), color = FleetColors.Divider)
            
            // Legal Section
            Text(
                "Legal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FleetColors.TextPrimary
            )
            
            LegalLink(
                title = "Terms of Service",
                icon = Icons.Default.Description,
                onClick = { openUrl(context, "https://fleetcontrol.app/terms") }
            )
            
            LegalLink(
                title = "Privacy Policy",
                icon = Icons.Default.PrivacyTip,
                onClick = { openUrl(context, "https://fleetcontrol.app/privacy") }
            )
            
            LegalLink(
                title = "Open Source Licenses",
                icon = Icons.Default.Code,
                onClick = { openUrl(context, "https://fleetcontrol.app/licenses") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Contact Section
            Text(
                "Contact Us",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FleetColors.TextPrimary
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerMedium),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ContactItem(
                        icon = Icons.Default.Email,
                        label = "Email Support",
                        value = "upwansingh2004@gmail.com",
                        onClick = { 
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:upwansingh2004@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "FleetControl Support Request")
                            }
                            context.startActivity(intent)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    ContactItem(
                        icon = Icons.Default.Phone,
                        label = "Phone Support",
                        value = "+91 9455807373",
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:+919455807373")
                            }
                            context.startActivity(intent)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    ContactItem(
                        icon = Icons.Default.Language,
                        label = "Website",
                        value = "fleetcontrol.app",
                        onClick = { openUrl(context, "https://fleetcontrol.app") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // App Version
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerMedium),
                colors = CardDefaults.cardColors(
                    containerColor = FleetColors.Primary
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FleetDimens.SpacingMedium),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "FleetControl",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        "© 2026 FleetControl. All rights reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(FleetDimens.SpacingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = FleetColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = FleetColors.TextPrimary
                    )
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FleetColors.TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalLink(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = FleetColors.Primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = FleetColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = FleetColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ContactItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = FleetColors.Primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = FleetColors.TextSecondary
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = FleetColors.TextPrimary
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = FleetColors.TextSecondary
            )
        }
    }
}
