package com.fleetcontrol.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import com.fleetcontrol.utils.AccessibilityUtils

/**
 * FleetControl Design System
 * Professional, Consistent, Premium UI Components
 */

// ============================================
// DESIGN TOKENS
// ============================================

object FleetColors {
    // Core Colors (for consistency across all screens)
    val Black = Color(0xFF000000)
    val White = Color.White
    
    // Primary Palette
    val Primary = Color(0xFF000000)
    val PrimaryLight = Color(0xFF2C2C2C)
    // Alias for Secondary Usage
    val Secondary =  Color(0xFF00C853) // Using Success green as secondary brand color
    val OnPrimary = Color.White
    
    // Accent Colors
    val Success = Color(0xFF00C853)       // Emerald Green
    val SuccessLight = Color(0xFFE8F5E9)
    val Warning = Color(0xFFFF9800)       // Amber
    val WarningLight = Color(0xFFFFF3E0)
    val Error = Color(0xFFE53935)         // Red
    val ErrorLight = Color(0xFFFFEBEE)
    val Info = Color(0xFF2196F3)          // Blue
    val InfoLight = Color(0xFFE3F2FD)
    
    // Neutral Palette
    val Surface = Color(0xFFFAFAFA)
    val SurfaceElevated = Color.White
    val SurfaceVariant = Color(0xFFF5F5F5)
    val Border = Color(0xFFE0E0E0)
    val BorderLight = Color(0xFFF0F0F0)
    val Divider = Color(0xFFEEEEEE)       // Light gray for dividers
    val CardBackground = Color(0xFFFAFAFA)
    
    // Text Colors
    val TextPrimary = Color(0xFF212121)
    val TextSecondary = Color(0xFF757575)
    val TextTertiary = Color(0xFFBDBDBD)
    val TextOnDark = Color.White
    val TextOnDarkSecondary = Color(0xFFB0B0B0)
    
    // Gradient
    val GradientPrimary = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
    )
    val GradientSuccess = Brush.horizontalGradient(
        colors = listOf(Color(0xFF00C853), Color(0xFF009624))
    )
}

object FleetDimens {
    val CornerSmall = 8.dp
    val CornerMedium = 12.dp
    val CornerLarge = 16.dp
    val CornerXLarge = 24.dp
    val CornerRound = 50.dp
    
    val SpacingXSmall = 4.dp
    val SpacingExtraSmall = 4.dp // Alias for compatibility
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val SpacingXLarge = 32.dp
    
    val IconMicro = 12.dp
    val IconSmall = 18.dp
    val IconMedium = 24.dp
    val IconLarge = 32.dp
    val IconXLarge = 48.dp
    val IconXXLarge = 64.dp
    val IconHero = 100.dp
    
    val ElevationExtraSmall = 1.dp
    val ElevationSmall = 2.dp
    val ElevationMedium = 4.dp
    val ElevationLarge = 8.dp
    
    // Text Sizes
    val TextSizeSmall = 12.sp
    val TextSizeLarge = 16.sp
    val TextSizeTitle = 24.sp
    val TextSizeHeader = 28.sp
    val TextSizeMedium = 14.sp
    
    // Compatibility Aliases
    val ElevationLow = ElevationSmall
    val SpacingExtraLarge = SpacingXLarge
    val ButtonHeight = 56.dp
}

// ============================================
// PREMIUM CARD COMPONENTS
// ============================================

/**
 * Premium Stats Card - for displaying key metrics
 */
@Composable
fun StatsCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = FleetColors.Success,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    
    // Stats cards are display-only: no elevation, subtle border
    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // No shadow
        border = androidx.compose.foundation.BorderStroke(1.dp, FleetColors.BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = FleetColors.TextSecondary
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = FleetColors.TextPrimary
            )
            
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingXSmall))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextTertiary
                )
            }
        }
    }
}

/**
 * Hero Stats Card - Dark themed for primary metrics
 */
@Composable
fun HeroStatsCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    trend: String? = null,
    isPositive: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(FleetDimens.CornerXLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.Primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = FleetColors.TextOnDarkSecondary
                )
                if (trend != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = if (isPositive) "Trending up" else "Trending down",
                            tint = if (isPositive) FleetColors.Success else FleetColors.Error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = trend,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPositive) FleetColors.Success else FleetColors.Error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
            
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = FleetColors.TextOnDark
            )
            
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FleetColors.TextOnDarkSecondary
                )
            }
        }
    }
}

/**
 * Premium Section Card with optional header
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FleetDimens.SpacingMedium)
                        .padding(top = FleetDimens.SpacingMedium, bottom = FleetDimens.SpacingSmall),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FleetColors.TextPrimary
                    )
                    action?.invoke()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FleetDimens.SpacingMedium,
                        end = FleetDimens.SpacingMedium,
                        bottom = FleetDimens.SpacingMedium,
                        top = if (title == null) FleetDimens.SpacingMedium else 0.dp
                    ),
                content = content
            )
        }
    }
}

// ============================================
// NAVIGATION COMPONENTS
// ============================================

/**
 * Premium Navigation Tile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    badge: String? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = FleetColors.SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FleetColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = FleetColors.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (badge != null) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = FleetColors.Error
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = FleetColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * List Item with premium styling
 */
@Composable
fun PremiumListItem(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = FleetColors.TextSecondary,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else Modifier
            )
            .padding(vertical = FleetDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = FleetColors.TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
        }
        
        trailing?.invoke()
        
        if (onClick != null && trailing == null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = FleetColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================
// BUTTON COMPONENTS
// ============================================

/**
 * Primary Button - Premium styling
 */
@Composable
fun PrimaryButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Button(
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth(),
        onClick = {
            if (!isLoading) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = FleetColors.Primary,
            contentColor = FleetColors.OnPrimary,
            disabledContainerColor = FleetColors.TextTertiary,
            disabledContentColor = FleetColors.TextOnDarkSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = FleetDimens.ElevationLow,
            pressedElevation = 0.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = FleetColors.OnPrimary,
                strokeWidth = 2.dp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = AccessibilityUtils.infoIcon,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Secondary/Outlined Button
 */
@Composable
fun SecondaryButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    OutlinedButton(
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, FleetColors.Primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = FleetColors.Primary
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = AccessibilityUtils.iconDescription("Action"),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Danger Button for destructive actions
 */
@Composable
fun DangerButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Button(
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(FleetDimens.CornerMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = FleetColors.Error,
            contentColor = Color.White
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================
// INPUT COMPONENTS
// ============================================

/**
 * Premium TextField with consistent styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = if (placeholder != null) {{ Text(placeholder) }} else null,
            leadingIcon = if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null) }
            } else null,
            trailingIcon = trailingIcon,
            isError = isError,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(FleetDimens.CornerMedium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FleetColors.Primary,
                unfocusedBorderColor = FleetColors.Border,
                errorBorderColor = FleetColors.Error,
                focusedLabelColor = FleetColors.Primary,
                unfocusedLabelColor = FleetColors.TextSecondary,
                cursorColor = FleetColors.Primary
            )
        )
        
        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = FleetColors.Error,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

// ============================================
// STATUS & FEEDBACK COMPONENTS
// ============================================

/**
 * Status Badge
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBadge(
    modifier: Modifier = Modifier,
    text: String,
    type: StatusType = StatusType.Info
) {
    val (backgroundColor, textColor) = when (type) {
        StatusType.Success -> FleetColors.SuccessLight to FleetColors.Success
        StatusType.Warning -> FleetColors.WarningLight to FleetColors.Warning
        StatusType.Error -> FleetColors.ErrorLight to FleetColors.Error
        StatusType.Info -> FleetColors.InfoLight to FleetColors.Info
        StatusType.Neutral -> FleetColors.SurfaceVariant to FleetColors.TextSecondary
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FleetDimens.CornerSmall),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

enum class StatusType {
    Success, Warning, Error, Info, Neutral
}

/**
 * Empty State with illustration
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FleetDimens.SpacingXLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(FleetColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FleetColors.TextTertiary,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FleetColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = FleetColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        
        if (action != null) {
            Spacer(modifier = Modifier.height(FleetDimens.SpacingLarge))
            action()
        }
    }
}

/**
 * Loading Indicator with message
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading..."
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FleetDimens.SpacingXLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = FleetColors.Primary,
            strokeWidth = 3.dp
        )
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = FleetColors.TextSecondary
        )
    }
}

// ============================================
// DIVIDERS & SPACERS
// ============================================

/**
 * Premium Divider
 */
@Composable
fun PremiumDivider(
    modifier: Modifier = Modifier,
    color: Color = FleetColors.BorderLight
) {
    Divider(
        modifier = modifier.padding(vertical = FleetDimens.SpacingSmall),
        thickness = 1.dp,
        color = color
    )
}

/**
 * Section Header
 */
@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FleetDimens.SpacingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = FleetColors.TextSecondary
        )
        action?.invoke()
    }
}

// ============================================
// TOP APP BAR
// ============================================

/**
 * Premium Top App Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTopAppBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FleetColors.TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = FleetColors.TextPrimary
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = FleetColors.Surface,
            titleContentColor = FleetColors.TextPrimary
        )
    )
}

// ============================================
// ANIMATED COMPONENTS
// ============================================

/**
 * Animated Counter for numbers
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = FleetColors.TextPrimary
) {
    var oldValue by remember { mutableIntStateOf(targetValue) }
    
    SideEffect {
        oldValue = targetValue
    }
    
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "counter"
    )
    
    Text(
        text = animatedValue.toString(),
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

/**
 * Shimmer Loading Effect
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(FleetDimens.CornerMedium)
) {
    val shimmerColors = listOf(
        FleetColors.SurfaceVariant,
        FleetColors.Border,
        FleetColors.SurfaceVariant
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnim - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, 0f)
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

// ============================================
// NETWORK STATUS BANNER
// ============================================

/**
 * Network Offline Banner - Shows when device is offline
 */
@Composable
fun NetworkOfflineBanner(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(FleetColors.Warning)
                .padding(horizontal = FleetDimens.SpacingMedium, vertical = FleetDimens.SpacingSmall),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = FleetColors.Black
            )
            Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
            Text(
                "No internet connection",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = FleetColors.Black
            )
        }
    }
}

/**
 * Network status with retry action
 */
@Composable
fun NetworkStatusCard(
    isOffline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            shape = RoundedCornerShape(FleetDimens.CornerMedium),
            colors = CardDefaults.cardColors(containerColor = FleetColors.WarningLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FleetDimens.SpacingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = FleetColors.Warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "You're offline",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FleetColors.TextPrimary
                    )
                    Text(
                        "Check your connection and try again",
                        style = MaterialTheme.typography.bodySmall,
                        color = FleetColors.TextSecondary
                    )
                }
                TextButton(onClick = onRetry) {
                    Text("Retry", color = FleetColors.Warning, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================
// PULL TO REFRESH INDICATOR
// ============================================

/**
 * Simple refresh indicator for lists
 */
@Composable
fun RefreshIndicator(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRefreshing,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = FleetColors.Primary
                )
                Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                Text(
                    "Refreshing...",
                    style = MaterialTheme.typography.bodySmall,
                    color = FleetColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Swipe refresh container wrapper
 * Note: For full Material3 pull-to-refresh, use with ExperimentalMaterial3Api
 */
@Composable
fun RefreshableContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        
        // Show refresh indicator at top when refreshing
        RefreshIndicator(
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

// ============================================
// SKELETON LOADERS
// ============================================

/**
 * Skeleton Card for list items
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FleetDimens.CornerLarge),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = FleetDimens.ElevationLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FleetDimens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar skeleton
            ShimmerBox(
                modifier = Modifier.size(FleetDimens.IconXLarge),
                shape = CircleShape
            )
            
            Spacer(modifier = Modifier.width(FleetDimens.SpacingMedium))
            
            Column(modifier = Modifier.weight(1f)) {
                // Title skeleton
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(FleetDimens.SpacingMedium)
                )
                
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                
                // Subtitle skeleton
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                )
            }
        }
    }
}

/**
 * Skeleton List for loading states
 */
@Composable
fun SkeletonList(
    itemCount: Int = 5,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(itemCount) {
            SkeletonCard()
        }
    }
}