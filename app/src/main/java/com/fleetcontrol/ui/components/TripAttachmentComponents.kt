package com.fleetcontrol.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fleetcontrol.data.entities.AttachmentType
import com.fleetcontrol.data.entities.TripAttachmentEntity
import com.fleetcontrol.utils.DateUtils
import java.io.File

/**
 * Attachment Type Selector Dialog
 */
@Composable
fun AttachmentTypeSelector(
    onDismiss: () -> Unit,
    onSelectType: (AttachmentType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FleetColors.White,
        title = { 
            Text(
                "What are you capturing?", 
                color = FleetColors.Black, 
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FleetDimens.SpacingSmall)) {
                AttachmentType.values().forEach { type ->
                    val (icon, label) = when (type) {
                        AttachmentType.LOADING_PHOTO -> Icons.Outlined.LocalShipping to "Loading Photo"
                        AttachmentType.DELIVERY_PROOF -> Icons.Outlined.CheckCircle to "Delivery Proof"
                        AttachmentType.RECEIPT_CHALLAN -> Icons.Outlined.Receipt to "Receipt/Challan"
                        AttachmentType.DAMAGE_REPORT -> Icons.Outlined.Warning to "Damage Report"
                        AttachmentType.VEHICLE_ISSUE -> Icons.Outlined.DirectionsCar to "Vehicle Issue"
                        AttachmentType.OTHER -> Icons.Outlined.Attachment to "Other Document"
                    }
                    
                    Surface(
                        onClick = { onSelectType(type) },
                        shape = RoundedCornerShape(12.dp),
                        color = FleetColors.CardBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(FleetDimens.SpacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingMedium)
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = FleetColors.Black
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = FleetColors.TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.Black)
            }
        }
    )
}

/**
 * Image Source Selector (Camera or Gallery)
 */
@Composable
fun ImageSourceSelector(
    onDismiss: () -> Unit,
    onSelectCamera: () -> Unit,
    onSelectGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FleetColors.White,
        title = { 
            Text(
                "Choose Image Source", 
                color = FleetColors.Black,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Camera Option
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectCamera() }
                        .padding(FleetDimens.SpacingLarge), // 24.dp
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(FleetColors.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = FleetColors.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                    Text("Camera", fontWeight = FontWeight.Medium)
                }
                
                // Gallery Option
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(FleetDimens.CornerMedium)) // 16dp -> CornerMedium/Large? Using 16.dp
                        .clickable { onSelectGallery() }
                        .padding(FleetDimens.SpacingLarge), // 24.dp
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(FleetColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = FleetColors.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                    Text("Gallery", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FleetColors.Black)
            }
        }
    )
}

/**
 * Compact attachment thumbnail with delete option
 */
@Composable
fun AttachmentThumbnail(
    attachment: TripAttachmentEntity,
    onView: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val typeLabel = when (attachment.attachmentType) {
        AttachmentType.LOADING_PHOTO -> "Loading"
        AttachmentType.DELIVERY_PROOF -> "Proof"
        AttachmentType.RECEIPT_CHALLAN -> "Receipt"
        AttachmentType.DAMAGE_REPORT -> "Damage"
        AttachmentType.VEHICLE_ISSUE -> "Vehicle"
        AttachmentType.OTHER -> "Other"
    }
    
    Box(
        modifier = modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onView() }
    ) {
        // Image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(attachment.filePath))
                .crossfade(true)
                .build(),
            contentDescription = typeLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Type badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(FleetColors.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = FleetColors.White
            )
        }
        
        // Delete button
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(4.dp)
                    .background(FleetColors.Error.copy(alpha = 0.9f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = FleetColors.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Unviewed indicator for owner
        if (!attachment.viewedByOwner) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(12.dp)
                    .background(FleetColors.Primary, CircleShape)
            )
        }
    }
}

/**
 * Full-screen image viewer
 */
@Composable
fun ImageViewerDialog(
    attachment: TripAttachmentEntity,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FleetColors.White)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FleetColors.Black)
                    .padding(FleetDimens.SpacingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        when (attachment.attachmentType) {
                            AttachmentType.LOADING_PHOTO -> "Loading Photo"
                            AttachmentType.DELIVERY_PROOF -> "Delivery Proof"
                            AttachmentType.RECEIPT_CHALLAN -> "Receipt/Challan"
                            AttachmentType.DAMAGE_REPORT -> "Damage Report"
                            AttachmentType.VEHICLE_ISSUE -> "Vehicle Issue"
                            AttachmentType.OTHER -> "Other Document"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FleetColors.White
                    )
                    Text(
                        DateUtils.formatDateTime(attachment.capturedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = FleetColors.White
                    )
                }
            }
            
            // Image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(attachment.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Attachment image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
            )
            
            // Caption if present
            attachment.caption?.let { caption ->
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FleetColors.TextSecondary,
                    modifier = Modifier.padding(FleetDimens.SpacingMedium)
                )
            }
        }
    }
}

/**
 * Horizontal scrollable list of attachments (for trip detail)
 */
@Composable
fun TripAttachmentsList(
    attachments: List<TripAttachmentEntity>,
    onViewAttachment: (TripAttachmentEntity) -> Unit,
    onDeleteAttachment: ((TripAttachmentEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No attachments",
                style = MaterialTheme.typography.bodyMedium,
                color = FleetColors.TextTertiary
            )
        }
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = FleetDimens.SpacingMedium),
            horizontalArrangement = Arrangement.spacedBy(FleetDimens.CornerMedium) // 12.dp
        ) {
            items(attachments, key = { it.id }) { attachment ->
                AttachmentThumbnail(
                    attachment = attachment,
                    onView = { onViewAttachment(attachment) },
                    onDelete = onDeleteAttachment?.let { { it(attachment) } }
                )
            }
        }
    }
}

/**
 * Add attachment button with camera/gallery picker
 */
@Composable
fun AddAttachmentButton(
    tripId: Long,
    onImageCaptured: (Uri, AttachmentType) -> Unit,
    onCameraFilePrepared: (File, Uri, AttachmentType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showTypeSelector by remember { mutableStateOf(false) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<AttachmentType?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && selectedType != null) {
            showSourceSelector = true
        }
    }
    
    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            selectedType?.let { type ->
                onImageCaptured(it, type)
            }
        }
        selectedType = null
    }
    
    // Camera capture
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val currentType = selectedType
        val currentFile = tempCameraFile
        val currentUri = tempCameraUri
        
        if (success && currentFile != null && currentUri != null && currentType != null) {
            onCameraFilePrepared(currentFile, currentUri, currentType)
        }
        selectedType = null
        tempCameraFile = null
        tempCameraUri = null
    }
    
    // Add button
    OutlinedButton(
        onClick = { showTypeSelector = true },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = FleetColors.Black
        )
    ) {
        Icon(Icons.Default.AddAPhoto, contentDescription = null)
        Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
        Text("Add Photo")
    }
    
    // Type selector dialog
    if (showTypeSelector) {
        AttachmentTypeSelector(
            onDismiss = { showTypeSelector = false },
            onSelectType = { type ->
                selectedType = type
                showTypeSelector = false
                
                if (!hasCameraPermission) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    showSourceSelector = true
                }
            }
        )
    }
    
    // Source selector dialog
    if (showSourceSelector) {
        ImageSourceSelector(
            onDismiss = { 
                showSourceSelector = false
                selectedType = null
            },
            onSelectCamera = {
                showSourceSelector = false
                // Create temp file for camera
                try {
                    val timestamp = System.currentTimeMillis()
                    val file = File(context.cacheDir, "temp_camera_$timestamp.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, 
                        "com.fleetcontrol.provider", 
                        file
                    )
                    tempCameraFile = file
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    // Handle error
                    selectedType = null
                }
            },
            onSelectGallery = {
                showSourceSelector = false
                galleryLauncher.launch("image/*")
            }
        )
    }
}

/**
 * Attachment badge showing count
 */
@Composable
fun AttachmentBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        Row(
            modifier = modifier
                .background(FleetColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FleetDimens.SpacingXSmall)
        ) {
            Icon(
                Icons.Default.Attachment,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = FleetColors.Primary
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = FleetColors.Primary
            )
        }
    }
}
