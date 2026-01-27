package com.fleetcontrol.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import com.fleetcontrol.ui.components.FleetColors

private val LightColorScheme = lightColorScheme(
    primary = FleetColors.Primary,
    onPrimary = FleetColors.OnPrimary,
    primaryContainer = FleetColors.PrimaryLight,
    onPrimaryContainer = FleetColors.TextOnDark,
    secondary = FleetColors.Success,
    onSecondary = FleetColors.White,
    secondaryContainer = FleetColors.SuccessLight,
    onSecondaryContainer = FleetColors.Black,
    tertiary = FleetColors.Info,
    onTertiary = FleetColors.White,
    error = FleetColors.Error,
    onError = FleetColors.White,
    background = FleetColors.Surface,
    onBackground = FleetColors.TextPrimary,
    surface = FleetColors.Surface,
    onSurface = FleetColors.TextPrimary,
    surfaceVariant = FleetColors.SurfaceVariant,
    onSurfaceVariant = FleetColors.TextSecondary,
    outline = FleetColors.Border
)

private val DarkColorScheme = darkColorScheme(
    primary = FleetColors.Primary, // Even in dark mode, stick to brand identity or adapt? keeping Primary
    onPrimary = FleetColors.OnPrimary,
    primaryContainer = FleetColors.PrimaryLight,
    onPrimaryContainer = FleetColors.White,
    secondary = FleetColors.Success,
    onSecondary = FleetColors.Black,
    tertiary = FleetColors.Info,
    onTertiary = FleetColors.White,
    error = FleetColors.Error,
    onError = FleetColors.Black,
    background = FleetColors.Black,
    onBackground = FleetColors.White,
    surface = Color(0xFF121212),
    onSurface = FleetColors.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = FleetColors.TextTertiary,
    outline = Color(0xFF444444)
)

@Composable
fun FleetControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color for consistent minimal look
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // FORCE LIGHT THEME PREFERENCE
    // User requested "works best in every condition" and explicitly liked the current (Light) theme.
    // We enforce Light Scheme to ensure consistent high-contrast readability across all devices.
    val colorScheme = LightColorScheme
    
    // val colorScheme = when {
    //    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
    //        val context = LocalContext.current
    //        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    //    }
    //    darkTheme -> DarkColorScheme
    //    else -> LightColorScheme
    // }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use surface color for status bar - more elegant
            window.statusBarColor = colorScheme.surface.toArgb()
            // Force Light Status Bar Appearance (Dark Icons) since we have a Light Background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
