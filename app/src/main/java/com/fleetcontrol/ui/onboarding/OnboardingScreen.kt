package com.fleetcontrol.ui.onboarding

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fleetcontrol.ui.components.FleetColors
import com.fleetcontrol.ui.components.FleetDimens
import kotlinx.coroutines.launch

/**
 * First-launch onboarding screen with swipeable pages
 * STRICTLY follows FleetControl "Swiss Monochrome" design system:
 * - Black header/background for premium sections
 * - White surface for content
 * - FleetColors.Primary (Black) for accents
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.LocalShipping,
            title = "Track Every Trip",
            description = "Record trips with a single tap. Automatic rate calculation, distance tracking, and instant earnings visibility."
        ),
        OnboardingPage(
            icon = Icons.Default.AttachMoney,
            title = "Smart Financial Control",
            description = "See your profits in real-time. Manage driver advances, fuel costs, and get detailed monthly reports."
        ),
        OnboardingPage(
            icon = Icons.Default.CloudSync,
            title = "Sync Across Devices",
            description = "Your data syncs to the cloud automatically. Access from any device, anytime. Works offline too!"
        ),
        OnboardingPage(
            icon = Icons.Default.Security,
            title = "Secure & Private",
            description = "Your business data is encrypted and isolated. Only you and your team can access it."
        )
    )
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Enforce Status Bar (Black + White Icons) - Matches LoginScreen
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = FleetColors.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FleetColors.Surface) // White background like LoginScreen
    ) {
        // === BLACK HEADER (Matches LoginScreen header) ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FleetColors.Black)
                .padding(vertical = FleetDimens.IconXLarge, horizontal = FleetDimens.SpacingLarge),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FLEET CONTROL",
                    fontSize = FleetDimens.TextSizeHeader,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(FleetDimens.SpacingSmall))
                Text(
                    text = "MANAGE YOUR FLEET",
                    fontSize = FleetDimens.TextSizeSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )
            }
        }
        
        // Skip button - matches app's text button style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FleetDimens.SpacingLarge, vertical = FleetDimens.SpacingMedium),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onComplete) {
                Text(
                    "Skip",
                    color = FleetColors.TextSecondary,
                    fontSize = FleetDimens.TextSizeMedium
                )
            }
        }
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPageContent(pages[page])
        }
        
        // Page Indicators - uses FleetColors.Primary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FleetDimens.SpacingLarge),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pages.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) 
                                FleetColors.Primary 
                            else 
                                FleetColors.TextTertiary.copy(alpha = 0.3f)
                        )
                )
            }
        }
        
        // Navigation Buttons - matches app's button style
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FleetDimens.SpacingLarge)
                .padding(bottom = FleetDimens.SpacingXLarge),
            verticalArrangement = Arrangement.spacedBy(FleetDimens.CornerMedium)
        ) {
            // Next / Get Started button - Primary style
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FleetDimens.ButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FleetColors.Primary,
                    contentColor = FleetColors.OnPrimary
                ),
                shape = RoundedCornerShape(FleetDimens.CornerLarge)
            ) {
                Text(
                    if (pagerState.currentPage < pages.size - 1) "Next" else "Get Started",
                    fontWeight = FontWeight.Bold,
                    fontSize = FleetDimens.TextSizeLarge
                )
                if (pagerState.currentPage < pages.size - 1) {
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                }
            }
            
            // Back button (visible after first page) - Outlined style
            AnimatedVisibility(
                visible = pagerState.currentPage > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FleetDimens.ButtonHeight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FleetColors.Primary
                    ),
                    shape = RoundedCornerShape(FleetDimens.CornerLarge)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
                    Spacer(modifier = Modifier.width(FleetDimens.SpacingSmall))
                    Text("Back", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FleetDimens.SpacingXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon in circle - uses FleetColors
        Box(
            modifier = Modifier
                .size(FleetDimens.IconHero)
                .clip(CircleShape)
                .background(FleetColors.Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = page.title,
                modifier = Modifier.size(FleetDimens.IconXLarge),
                tint = FleetColors.Primary
            )
        }
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingXLarge))
        
        Text(
            text = page.title,
            fontSize = FleetDimens.TextSizeTitle,
            fontWeight = FontWeight.Bold,
            color = FleetColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(FleetDimens.SpacingMedium))
        
        Text(
            text = page.description,
            fontSize = FleetDimens.TextSizeLarge,
            color = FleetColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)
