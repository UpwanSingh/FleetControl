package com.fleetcontrol.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fleetcontrol.FleetControlApplication
import com.fleetcontrol.core.InviteCodeManager
import com.fleetcontrol.core.InviteCodeResult
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import kotlinx.coroutines.launch

/**
 * Driver Join Screen
 * Allows drivers to enter an invite code to link their device to an owner's account
 * 
 * Follows Swiss Monochrome design system
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverJoinScreen(
    viewModel: com.fleetcontrol.viewmodel.auth.DriverJoinViewModel,
    onJoinSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var code by remember { mutableStateOf("") }
    
    // Side Effect: Navigation on Success
    LaunchedEffect(uiState) {
        if (uiState is com.fleetcontrol.viewmodel.auth.DriverJoinUiState.Success) {
            onJoinSuccess()
        }
    }

    // Enforce Status Bar (Black + White Icons)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = FleetColors.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    
    // Extract State values for UI
    val isLoading = uiState is com.fleetcontrol.viewmodel.auth.DriverJoinUiState.Loading
    val loadingMessage = (uiState as? com.fleetcontrol.viewmodel.auth.DriverJoinUiState.Loading)?.message
    val error = (uiState as? com.fleetcontrol.viewmodel.auth.DriverJoinUiState.Error)?.message
    val validationResult = (uiState as? com.fleetcontrol.viewmodel.auth.DriverJoinUiState.CodeValid)?.result
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FleetColors.Surface)
    ) {
        // === BLACK HEADER ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FleetColors.Black)
                .padding(vertical = FleetDimens.SpacingXLarge, horizontal = FleetDimens.SpacingLarge)
        ) {
            Column {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-FleetDimens.CornerMedium))
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                
                Text(
                    text = "Join as Driver",
                    fontSize = FleetDimens.TextSizeHeader,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                Text(
                    text = "Enter the invite code from your fleet owner",
                    fontSize = FleetDimens.TextSizeMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        
        // === CONTENT ===
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(FleetDimens.SpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(FleetDimens.SpacingXLarge))
            
            // Code Input
            OutlinedTextField(
                value = code,
                onValueChange = { 
                    if (it.length <= 6) {
                        code = it.uppercase()
                        if (error != null) viewModel.resetError()
                    }
                },
                label = { Text("Invite Code") },
                placeholder = { Text("A7X9K2") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = FleetDimens.TextSizeTitle,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FleetColors.Primary,
                    unfocusedBorderColor = FleetColors.TextTertiary
                ),
                shape = RoundedCornerShape(FleetDimens.CornerLarge),
                isError = error != null,
                enabled = !isLoading
            )
            
            // Error message
            if (error != null) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                Text(
                    text = error,
                    color = FleetColors.Error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Loading Indicator
            if (isLoading) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                CircularProgressIndicator(color = FleetColors.Primary)
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                Text(text = loadingMessage ?: "Loading...", style = MaterialTheme.typography.bodySmall)
            }
            
            // Validation Success
            if (validationResult != null) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FleetDimens.CornerLarge),
                    colors = CardDefaults.cardColors(
                        containerColor = FleetColors.Success.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FleetDimens.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = FleetColors.Success,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
                        Column {
                            Text(
                                text = "Welcome, ${validationResult.driverName}!",
                                fontWeight = FontWeight.Bold,
                                color = FleetColors.TextPrimary
                            )
                            Text(
                                text = "Ready to join this fleet",
                                color = FleetColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Validate / Join Button
            Button(
                onClick = {
                    if (validationResult == null) {
                        viewModel.validateCode(code)
                    } else {
                        viewModel.joinFleet(validationResult, code)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FleetDimens.ButtonHeight),
                enabled = code.length == 6 && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FleetColors.Primary,
                    contentColor = FleetColors.OnPrimary
                ),
                shape = RoundedCornerShape(FleetDimens.CornerLarge)
            ) {
                Text(
                    text = if (validationResult == null) "Validate Code" else "Join Fleet",
                    fontWeight = FontWeight.Bold,
                    fontSize = FleetDimens.TextSizeLarge
                )
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            // Help text
            Text(
                text = "Ask your fleet owner to generate an invite code for you in the Drivers section",
                textAlign = TextAlign.Center,
                color = FleetColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingXLarge))
        }
    }
}
