package com.fleetcontrol.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.settings.MigrationViewModel
import com.fleetcontrol.data.managers.DataMigrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateToSubscription: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToRateSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    migrationViewModel: MigrationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = com.fleetcontrol.ui.AppViewModelProvider.Factory)
) {
    val haptic = LocalHapticFeedback.current
    val migrationStatus by migrationViewModel.migrationStatus.collectAsState()
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
                .padding(FleetDimens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)
        ) {
            // App Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                colors = CardDefaults.cardColors(containerColor = FleetColors.Primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FleetDimens.SpacingLarge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = FleetColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
                    Column {
                        Text(
                            "FleetControl",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FleetColors.TextOnDark
                        )
                        Text(
                            "Version 1.0.0 • Enterprise",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextOnDarkSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
            
            // Cloud Sync Success Banner (shown only when sync is complete)
            if (migrationStatus is DataMigrationManager.MigrationStatus.Success) {
                val successStatus = migrationStatus as DataMigrationManager.MigrationStatus.Success
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.Success.copy(alpha = 0.1f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = FleetColors.Success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Cloud Sync Complete",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FleetColors.Success
                            )
                            Text(
                                "${successStatus.count} trips migrated successfully",
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            }
            
            // Settings Section
            Text(
                "GENERAL",
                style = MaterialTheme.typography.labelSmall,
                color = FleetColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    SettingsItem(
                        title = "Backup & Restore",
                        subtitle = "Protect your data",
                        icon = Icons.Default.CloudUpload,
                        iconTint = FleetColors.Info,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToBackup()
                        }
                    )
                    
                    Divider(modifier = Modifier.padding(start = 56.dp), color = FleetColors.BorderLight)
                    SettingsItem(
                        title = "Security",
                        subtitle = "Manage Owner PIN",
                        icon = Icons.Default.Lock,
                        iconTint = FleetColors.Warning,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToSecurity()
                        }
                    )
                    Divider(modifier = Modifier.padding(start = 56.dp), color = FleetColors.BorderLight)
                    SettingsItem(
                        title = "Rate Settings",
                        subtitle = "Distance-based pricing",
                        icon = Icons.Default.AttachMoney,
                        iconTint = FleetColors.Success,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToRateSettings()
                        }
                    )
                }
            }
            
            // Data Sync Section (only show actionable states, not Success which is a banner)
            if (migrationStatus !is DataMigrationManager.MigrationStatus.Success) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                
                Text(
                    "DATA SYNC",
                    style = MaterialTheme.typography.labelSmall,
                    color = FleetColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.SurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = migrationStatus is DataMigrationManager.MigrationStatus.Idle ||
                                                  migrationStatus is DataMigrationManager.MigrationStatus.Error) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                migrationViewModel.startMigration()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (icon, iconTint, title, subtitle) = when (migrationStatus) {
                            is DataMigrationManager.MigrationStatus.InProgress -> 
                                listOf(Icons.Default.Sync, FleetColors.Primary, "Syncing...", "Please wait")
                            is DataMigrationManager.MigrationStatus.Error -> 
                                listOf(Icons.Default.Warning, FleetColors.Error, "Sync Failed", "Tap to retry")
                            else -> 
                                listOf(Icons.Default.CloudSync, FleetColors.Primary, "Sync Old Data", "Upload historic trips to Cloud")
                        }
                        
                        Icon(
                            imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                            contentDescription = null,
                            tint = iconTint as Color,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title as String,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FleetColors.TextPrimary
                            )
                            Text(
                                subtitle as String,
                                style = MaterialTheme.typography.bodySmall,
                                color = FleetColors.TextSecondary
                            )
                        }
                        if (migrationStatus is DataMigrationManager.MigrationStatus.InProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = FleetColors.Primary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            Text(
                "SUPPORT",
                style = MaterialTheme.typography.labelSmall,
                color = FleetColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsItem(
                    title = "Help & Legal",
                    subtitle = "FAQ, Terms, Privacy",
                    icon = Icons.Default.Help,
                    iconTint = FleetColors.TextSecondary,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToAbout()
                    }
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Logout Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FleetColors.ErrorLight,
                    contentColor = FleetColors.Error
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout")
                Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(FleetDimens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(FleetDimens.CornerMedium))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = FleetColors.TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FleetColors.TextSecondary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = FleetColors.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
