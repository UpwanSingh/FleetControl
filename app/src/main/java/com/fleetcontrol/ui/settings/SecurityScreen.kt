package com.fleetcontrol.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.settings.SecurityViewModel

/**
 * Security Settings Screen - Premium Polished Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    onBack: () -> Unit
) {
    val hasPinSet by viewModel.hasPinSet.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    
    var showChangePinDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadPinStatus()
    }
    
    Scaffold(
        containerColor = FleetColors.Surface,
        topBar = {
            TopAppBar(
                title = { Text("Security", fontWeight = FontWeight.Bold, color = FleetColors.TextPrimary) },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
            
            // Security Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(FleetColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
            
            // Owner PIN Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerXLarge),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(FleetDimens.SpacingLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Owner PIN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (hasPinSet) FleetColors.Success else FleetColors.TextTertiary)
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(
                            if (hasPinSet) "PIN is configured" else "Using default: 0000",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FleetColors.TextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
                    
                    // PIN Display (dots)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(FleetColors.Primary)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
                    
                    Button(
                        onClick = { showChangePinDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(FleetDimens.CornerLarge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FleetColors.Primary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit PIN")
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(
                            if (hasPinSet) "Change PIN" else "Set PIN",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                colors = CardDefaults.cardColors(containerColor = FleetColors.WarningLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(FleetDimens.SpacingMedium),
                    horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = FleetColors.Warning
                    )
                    Text(
                        "The Owner PIN protects access to the Owner Dashboard. " +
                        "Keep it secure and never share it with drivers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextPrimary
                    )
                }
            }
            
            // Message Snackbar
            message?.let { msg ->
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.SuccessLight)
                ) {
                    Row(
                        modifier = Modifier.padding(FleetDimens.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = FleetColors.Success
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                        Text(msg, color = FleetColors.TextPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text("OK", color = FleetColors.Success)
                        }
                    }
                }
            }
        }
    }
    
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onConfirm = { newPin ->
                viewModel.setOwnerPin(newPin)
                showChangePinDialog = false
            }
        )
    }
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = FleetColors.TextPrimary,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = FleetColors.Primary) },
        title = { Text("Set Owner PIN", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { 
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            newPin = it
                            error = null
                        }
                    },
                    label = { Text("New PIN (4 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    )
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { 
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            confirmPin = it
                            error = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FleetColors.Primary,
                        focusedLabelColor = FleetColors.Primary
                    )
                )
                error?.let {
                    Text(
                        it,
                        color = FleetColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPin.length != 4 -> error = "PIN must be 4 digits"
                        newPin != confirmPin -> error = "PINs don't match"
                        else -> onConfirm(newPin)
                    }
                },
                enabled = newPin.isNotEmpty() && confirmPin.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.TextSecondary)
            }
        }
    )
}
