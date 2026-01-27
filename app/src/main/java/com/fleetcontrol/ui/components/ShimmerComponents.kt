package com.fleetcontrol.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer effect for loading states
 * Replaces CircularProgressIndicator with a more polished skeleton animation
 */
@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    widthOfShadowBrush: Int = 500,
    angleOfAxisY: Float = 270f,
    durationMillis: Int = 1000
) {
    val shimmerColors = listOf(
        FleetColors.SurfaceVariant.copy(alpha = 0.3f),
        FleetColors.SurfaceVariant.copy(alpha = 0.5f),
        FleetColors.SurfaceVariant.copy(alpha = 0.3f),
        FleetColors.SurfaceVariant.copy(alpha = 0.5f),
        FleetColors.SurfaceVariant.copy(alpha = 0.3f)
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = (widthOfShadowBrush + durationMillis).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - widthOfShadowBrush, 0f),
        end = Offset(translateAnimation, angleOfAxisY)
    )
    
    Box(
        modifier = modifier.background(brush)
    )
}

/**
 * Skeleton card for list loading states
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
    ) {
        ShimmerEffect(
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * Skeleton text line
 */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 16.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
    ) {
        ShimmerEffect(
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * Skeleton circle (for avatars/icons)
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
    ) {
        ShimmerEffect(
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * Pre-built skeleton for a typical list item
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonCircle()
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            SkeletonText(width = 180.dp)
            SkeletonText(width = 120.dp, height = 12.dp)
        }
    }
}

