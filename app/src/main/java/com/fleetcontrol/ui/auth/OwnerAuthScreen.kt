package com.fleetcontrol.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fleetcontrol.R
import com.fleetcontrol.services.AuthService
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import kotlinx.coroutines.launch

/**
 * Owner Authentication Screen for Multi-Tenancy
 * 
 * Follows existing design patterns:
 * - Same header as LoginScreen
 * - Same card styles, colors, spacing
 * - Uses FleetColors and FleetDimens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerAuthScreen(
    authService: AuthService,
    onAuthSuccess: () -> Unit,
    onJoinAsDriver: () -> Unit = {}
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Status bar styling (same as LoginScreen)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header (Same as LoginScreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(top = FleetDimens.SpacingXLarge, bottom = FleetDimens.SpacingXLarge),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo),
                    contentDescription = "FleetControl Logo",
                    modifier = Modifier.size(80.dp) // Keep 80dp as standard logo size
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                Text(
                    "FleetControl",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "PREMIUM TRANSPORT MANAGEMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )
            }
        }
        
        // Auth Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Title
            Icon(
                Icons.Default.Business,
                contentDescription = "FleetControl Business Application",
                modifier = Modifier.size(56.dp),
                tint = FleetColors.Primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                if (isLoginMode) "Owner Login" else "Create Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                if (isLoginMode) "Sign in to access your fleet data" 
                else "Register to start managing your fleet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Error message
            AnimatedVisibility(visible = error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FleetColors.ErrorLight),
                    shape = RoundedCornerShape(FleetDimens.CornerMedium)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error: Invalid input",
                            tint = FleetColors.Error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            error ?: "",
                            color = FleetColors.Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Business Name (Registration only)
            AnimatedVisibility(visible = !isLoginMode) {
                Column {
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Business Name") },
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = "Business name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FleetDimens.CornerMedium),
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingXLarge)) // 32dp
                }
            }
            
            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { 
                    email = it
                    error = null 
                },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerMedium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FleetColors.Secondary,
                    unfocusedBorderColor = FleetColors.Border
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    error = null 
                },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FleetDimens.CornerMedium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FleetColors.Secondary,
                    unfocusedBorderColor = FleetColors.Border
                ),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = FleetColors.TextSecondary
                        )
                    }
                },
                enabled = !isLoading
            )
            
            // Confirm Password (Registration only)
            AnimatedVisibility(visible = !isLoginMode) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FleetDimens.CornerMedium),
                        enabled = !isLoading
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Submit Button
            Button(
                onClick = {
                    error = null
                    
                    // Validation
                    if (email.isBlank() || password.isBlank()) {
                        error = "Please enter email and password"
                        return@Button
                    }
                    
                    if (!isLoginMode) {
                        if (businessName.isBlank()) {
                            error = "Please enter your business name"
                            return@Button
                        }
                        if (password != confirmPassword) {
                            error = "Passwords do not match"
                            return@Button
                        }
                        if (password.length < 6) {
                            error = "Password must be at least 6 characters"
                            return@Button
                        }
                    }
                    
                    isLoading = true
                    scope.launch {
                        val result = if (isLoginMode) {
                            authService.loginOwner(email.trim(), password)
                        } else {
                            authService.registerOwner(email.trim(), password)
                        }
                        
                        result.onSuccess {
                            onAuthSuccess()
                        }.onFailure { e ->
                            error = e.message ?: "Authentication failed"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(FleetDimens.CornerMedium),
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (isLoginMode) Icons.Default.Login else Icons.Default.PersonAdd,
                        contentDescription = if (isLoginMode) "Login" else "Create account"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isLoginMode) "Sign In" else "Create Account",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Toggle Login/Register
            TextButton(
                onClick = {
                    isLoginMode = !isLoginMode
                    error = null
                },
                enabled = !isLoading
            ) {
                Text(
                    if (isLoginMode) "Don't have an account? Register" else "Already have an account? Sign In",
                    color = FleetColors.Primary
                )
            }
            
            // Forgot Password (Login mode only)
            if (isLoginMode) {
                TextButton(
                    onClick = {
                        if (email.isBlank()) {
                            error = "Enter your email to reset password"
                            return@TextButton
                        }
                        isLoading = true
                        scope.launch {
                            authService.sendPasswordResetEmail(email.trim())
                                .onSuccess {
                                    error = null
                                    // Show success message (using error field temporarily)
                                    error = "Password reset email sent!"
                                    isLoading = false
                                }
                                .onFailure { e ->
                                    error = e.message ?: "Failed to send reset email"
                                    isLoading = false
                                }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(
                        "Forgot Password?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Divider with "OR"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = FleetColors.Divider)
                Text(
                    " OR ",
                    color = FleetColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Divider(modifier = Modifier.weight(1f), color = FleetColors.Divider)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Join as Driver Button
            OutlinedButton(
                onClick = onJoinAsDriver,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = FleetColors.Primary
                )
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = "Driver")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Join as Driver",
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Have an invite code from your fleet owner?",
                color = FleetColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
