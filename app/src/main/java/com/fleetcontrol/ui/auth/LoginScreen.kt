package com.fleetcontrol.ui.auth

import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.animation.*
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import com.fleetcontrol.R
import com.fleetcontrol.data.entities.DriverEntity
import com.fleetcontrol.data.entities.UserRole
import com.fleetcontrol.ui.AppViewModelProvider
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.viewmodel.auth.LoginViewModel
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

/**
 * Login Screen with PIN-based authentication
 * No Firebase Phone Auth required - works completely offline!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, Long?) -> Unit, 
    viewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val isLicenseActivated by viewModel.isLicenseActivated.collectAsState(initial = false)
    val isDriverAccessGranted by viewModel.isDriverAccessGranted.collectAsState(initial = false)
    val isPinConfigured by viewModel.isPinConfigured.collectAsState()

    if (!isLicenseActivated && !isDriverAccessGranted) {
        ActivationLockScreen(viewModel)
    } else if (!isPinConfigured) {
        // First time setup - force Owner to set PIN
        PinSetupScreen(viewModel)
    } else {
        PinLoginContent(onLoginSuccess, viewModel)
    }
}

/**
 * PIN-based login - Role selection then PIN entry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinLoginContent(
    onLoginSuccess: (String, Long?) -> Unit,
    viewModel: LoginViewModel
) {
    val drivers by viewModel.drivers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val loginSuccess by viewModel.pinLoginSuccess.collectAsState()
    
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var selectedDriver by remember { mutableStateOf<DriverEntity?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var showForgotPinDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Navigate on success
    LaunchedEffect(loginSuccess) {
        loginSuccess?.let { (role, driverId) ->
            onLoginSuccess(role, driverId)
            viewModel.resetPinLoginSuccess()
        }
    }
    
    // Status bar styling
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
            .imePadding() // Critical for keyboard handling
            .verticalScroll(rememberScrollState()) // Single scroll container for EVERYTHING
    ) {
        // Header (Now scrolls with content)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black) // Premium Monochrome
                .padding(top = FleetDimens.SpacingExtraLarge, bottom = FleetDimens.SpacingExtraLarge),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo),
                    contentDescription = "FleetControl Logo",
                    modifier = Modifier.size(80.dp)
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
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FleetDimens.SpacingLarge), // Remove vertical padding here to control spacing better
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
            
            // Role not selected - show role buttons
            if (selectedRole == null) {
                Text(
                    "Select Your Role",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                Text(
                    "Choose how you want to login",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
                
                // Owner Button
                RoleCard(
                    icon = Icons.Outlined.Business,
                    title = "Owner",
                    subtitle = "Full access to all features",
                    color = FleetColors.Primary,
                    onClick = { selectedRole = UserRole.OWNER }
                )
                
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                
                // Driver Button
                RoleCard(
                    icon = Icons.Outlined.LocalShipping,
                    title = "Driver",
                    subtitle = "Log trips and view earnings",
                    color = FleetColors.Primary, // Monochrome - same as Owner
                    onClick = { 
                        selectedRole = UserRole.DRIVER
                        viewModel.loadDrivers()
                    }
                )
            }
            // Owner selected - show PIN entry
            else if (selectedRole == UserRole.OWNER) {
                OwnerPinEntry(
                    pinInput = pinInput,
                    onPinChange = { if (it.length <= 4) pinInput = it },
                    isLoading = isLoading,
                    error = error,
                    onVerify = { viewModel.verifyOwnerPin(pinInput) },
                    onBack = { 
                        selectedRole = null
                        pinInput = ""
                        viewModel.clearError()
                    },
                    onForgotPin = { showForgotPinDialog = true }
                )
            }
            // Driver selected but no driver chosen - show driver list
            else if (selectedRole == UserRole.DRIVER && selectedDriver == null) {
                DriverSelection(
                    drivers = drivers,
                    onDriverSelect = { selectedDriver = it },
                    onBack = { 
                        selectedRole = null
                        viewModel.clearError()
                    }
                )
            }
            // Driver chosen - show PIN entry
            else if (selectedRole == UserRole.DRIVER && selectedDriver != null) {
                DriverPinEntry(
                    driver = selectedDriver!!,
                    pinInput = pinInput,
                    onPinChange = { if (it.length <= 4) pinInput = it },
                    isLoading = isLoading,
                    error = error,
                    onVerify = { selectedDriver?.let { viewModel.verifyDriverPin(it.id, pinInput) } },
                    onBack = { 
                        selectedDriver = null
                        pinInput = ""
                        viewModel.clearError()
                    }
                )
            }
            
             // Extra bottom padding for scrolling
             Spacer(modifier = Modifier.height(FleetDimens.SpacingExtraLarge))
        }
    }
    
    // Forgot PIN Dialog
    if (showForgotPinDialog) {
        ForgotPinDialog(
            context = context,
            onDismiss = { showForgotPinDialog = false },
            onPinReset = { newPin ->
                viewModel.setupOwnerPin(newPin)
                showForgotPinDialog = false
                pinInput = ""
            }
        )
    }
}

@Composable
private fun RoleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(FleetDimens.IconXLarge)
                    .clip(RoundedCornerShape(FleetDimens.CornerSmall))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(FleetDimens.IconMedium))
            }
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = color)
        }
    }
}

@Composable
private fun OwnerPinEntry(
    pinInput: String,
    onPinChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onVerify: () -> Unit,
    onBack: () -> Unit,
    onForgotPin: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
            Text("Owner Login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
        
        Icon(
            Icons.Default.Business,
            contentDescription = null,
            modifier = Modifier.size(FleetDimens.IconXXLarge),
            tint = FleetColors.Primary
        )
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
        
        Text("Enter Owner PIN", style = MaterialTheme.typography.titleMedium)
        Text(
            "Use the PIN you configured during setup",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
        
        OutlinedTextField(
            value = pinInput,
            onValueChange = onPinChange,
            label = { Text("4-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.6f),
            isError = error != null,
            shape = RoundedCornerShape(FleetDimens.CornerSmall)
        )
        
        error?.let {
            Spacer(modifier = Modifier.height(FleetDimens.SpacingExtraSmall))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
        
        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            enabled = pinInput.length == 4 && !isLoading,
            shape = RoundedCornerShape(FleetDimens.CornerSmall),
            colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(FleetDimens.IconSmall), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Login as Owner", modifier = Modifier.padding(vertical = FleetDimens.SpacingExtraSmall))
            }
        }
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
        
        TextButton(onClick = onForgotPin) {
            Text(
                "Forgot PIN?",
                style = MaterialTheme.typography.bodyMedium,
                color = FleetColors.Primary
            )
        }
    }
}

@Composable
private fun DriverSelection(
    drivers: List<DriverEntity>,
    onDriverSelect: (DriverEntity) -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
            Text("Select Driver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
        
        if (drivers.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PersonOff,
                    contentDescription = null,
                    modifier = Modifier.size(FleetDimens.IconXXLarge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
                Text("No drivers found", fontWeight = FontWeight.Medium)
                Text(
                    "Ask the owner to add drivers first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            drivers.filter { it.isActive }.forEach { driver ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FleetDimens.SpacingExtraSmall)
                        .clickable { onDriverSelect(driver) },
                    shape = RoundedCornerShape(FleetDimens.CornerSmall),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationExtraSmall)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FleetDimens.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(FleetDimens.IconXLarge)
                                .clip(RoundedCornerShape(FleetDimens.CornerLarge))
                                .background(FleetColors.Success.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                driver.name.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = FleetColors.Success
                            )
                        }
                        Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(driver.name, fontWeight = FontWeight.Medium)
                            if (driver.phone.isNotEmpty()) {
                                Text(
                                    driver.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FleetColors.Success)
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverPinEntry(
    driver: DriverEntity,
    pinInput: String,
    onPinChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Driver Login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(FleetColors.Success.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                driver.name.take(2).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = FleetColors.Success
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Enter Your PIN", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = pinInput,
            onValueChange = onPinChange,
            label = { Text("4-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.6f),
            isError = error != null,
            shape = RoundedCornerShape(12.dp)
        )
        
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            enabled = pinInput.length == 4 && !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Success)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Login as ${driver.name}", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/**
 * Activation Lock Screen (unchanged)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationLockScreen(viewModel: LoginViewModel) {
    val context = LocalContext.current
    val deviceId = remember { com.fleetcontrol.domain.security.LicenseManager.getDeviceId(context) }
    var inputKey by remember { mutableStateOf("") }
    val activationError by viewModel.activationError.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Device Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "This app is licensed for authorized devices only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Device ID Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = {
                    clipboardManager.setText(AnnotatedString(deviceId))
                    Toast.makeText(context, "ID Copied", Toast.LENGTH_SHORT).show()
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "YOUR DEVICE ID",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        deviceId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "(Tap to Copy)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = inputKey,
                onValueChange = { 
                    inputKey = it.uppercase()
                    viewModel.clearActivationError()
                },
                label = { Text("Enter License Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = activationError != null
            )
            
            if (activationError != null) {
                Text(
                    text = activationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { viewModel.activateLicense(context, inputKey) },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputKey.length >= 8
            ) {
                Text("ACTIVATE APP")
            }
        }
    }
}

/**
 * PIN Setup Screen - First time configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupScreen(viewModel: LoginViewModel) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsState()
    
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
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(top = 48.dp, bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Setup Owner PIN",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "FIRST TIME SETUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp), // Remove vertical padding here to control spacing better
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Create a 4-digit PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "This PIN will be required to access the Owner Dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // New PIN
            OutlinedTextField(
                value = newPin,
                onValueChange = { 
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        newPin = it
                        error = null
                    }
                },
                label = { Text("Enter 4-digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Confirm PIN
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
                modifier = Modifier.fillMaxWidth(0.7f),
                isError = error != null,
                shape = RoundedCornerShape(12.dp)
            )
            
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    when {
                        newPin.length != 4 -> error = "PIN must be 4 digits"
                        newPin != confirmPin -> error = "PINs don't match"
                        else -> viewModel.setupOwnerPin(newPin)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = newPin.length == 4 && confirmPin.length == 4 && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Save PIN & Continue", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = FleetColors.WarningLight)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = FleetColors.Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Remember this PIN! You'll need it every time you login as Owner. " +
                        "If forgotten, you can reset it using your License Key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Forgot PIN Dialog - Verify License Key to reset PIN
 */
@Composable
private fun ForgotPinDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPinReset: (String) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1 = Enter License Key, 2 = Set New PIN
    var licenseKey by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                if (step == 1) Icons.Default.Key else Icons.Default.Lock,
                contentDescription = null,
                tint = FleetColors.Primary
            )
        },
        title = {
            Text(
                if (step == 1) "Verify Your Identity" else "Set New PIN",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == 1) {
                    Text(
                        "Enter your License Key to reset PIN",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = licenseKey,
                        onValueChange = { 
                            licenseKey = it.uppercase()
                            error = null
                        },
                        label = { Text("License Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = error != null
                    )
                } else {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                newPin = it
                                error = null
                            }
                        },
                        label = { Text("New 4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
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
                        shape = RoundedCornerShape(12.dp),
                        isError = error != null
                    )
                }
                
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        // Verify License Key
                        if (com.fleetcontrol.domain.security.LicenseManager.validateKey(context, licenseKey)) {
                            step = 2
                            error = null
                        } else {
                            error = "Invalid License Key"
                        }
                    } else {
                        // Validate and set new PIN
                        when {
                            newPin.length != 4 -> error = "PIN must be 4 digits"
                            newPin != confirmPin -> error = "PINs don't match"
                            else -> onPinReset(newPin)
                        }
                    }
                },
                enabled = if (step == 1) licenseKey.length >= 8 else newPin.length == 4 && confirmPin.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = FleetColors.Primary)
            ) {
                Text(if (step == 1) "Verify" else "Reset PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
