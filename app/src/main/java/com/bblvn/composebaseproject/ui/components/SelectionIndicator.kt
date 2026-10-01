package com.bblvn.composebaseproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients

/**
 * Round check mark used by every single/multi-choice list (Language, Region, Survey).
 * Unselected is an empty ring; selected pops into a brand-gradient disc with a check.
 *
 * [onImage] gives the empty ring a dark fill so it stays visible on top of a photo.
 */
@Composable
fun SelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    onImage: Boolean = false,
) {
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "selection_check",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (onImage) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
            .border(
                1.5.dp,
                if (onImage) Color.White.copy(alpha = 0.7f) else AppColors.strokeStrong,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                    alpha = checkScale.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(AppGradients.brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = AppColors.textPrimary,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
