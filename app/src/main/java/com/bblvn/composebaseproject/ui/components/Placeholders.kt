package com.bblvn.composebaseproject.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme

/**
 * Skeleton block: `bg_skeleton` with a white 6% band sweeping across it.
 * Used by Home / Creations skeletons and by [RemoteImage] while loading.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.thumb,
    baseColor: Color = AppColors.bgSkeleton,
    highlightAlpha: Float = 0.06f,
    durationMillis: Int = 1200,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )

    Box(
        modifier
            .clip(shape)
            .background(baseColor)
            // The band's brush is built once per size and only translated per frame: creating a
            // new gradient shader on every frame, for every visible skeleton, is what made a
            // screen full of loading cards stutter.
            .drawWithCache {
                val bandWidth = size.width * 0.45f
                val band = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = highlightAlpha),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(bandWidth, size.height),
                )
                val bandSize = Size(bandWidth, size.height)
                onDrawWithContent {
                    drawContent()
                    if (bandWidth > 0f) {
                        val start = -bandWidth + (size.width + bandWidth * 2f) * progress
                        translate(left = start) { drawRect(brush = band, size = bandSize) }
                    }
                }
            }
    )
}

/**
 * Theme-toned gradient fallback for any image that failed or is missing.
 *
 * Never a flat grey box: the tone pair is picked from the brand palette using a
 * hash of [seed], so a grid full of failures does not look like one uniform
 * block. Pass the template/creation id as the seed.
 */
@Composable
fun GradientPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.thumb,
    seed: Any? = null,
    showIcon: Boolean = true,
    label: String? = null,
) {
    val brush = remember(seed) { gradientPlaceholderBrush(seed) }
    Box(
        modifier
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (showIcon) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.placeholder_icon_outlined),
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.32f)),
                )
            }
        }
    }
}

/** Deterministic brand-toned gradient for a given seed. */
internal fun gradientPlaceholderBrush(seed: Any?): Brush {
    val hash = (seed?.hashCode() ?: 0)
    val index = ((hash % TONE_PAIRS.size) + TONE_PAIRS.size) % TONE_PAIRS.size
    val (top, bottom) = TONE_PAIRS[index]
    return Brush.linearGradient(
        colors = listOf(top, bottom),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}

private val TONE_PAIRS: List<Pair<Color, Color>> = listOf(
    Color(0xFF24193F) to Color(0xFF12101C),
    Color(0xFF3A1733) to Color(0xFF16101A),
    Color(0xFF1A2440) to Color(0xFF101420),
    Color(0xFF2E1B46) to Color(0xFF14101E),
    Color(0xFF13303A) to Color(0xFF0E1620),
    Color(0xFF3A2240) to Color(0xFF130F1A),
)

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun PlaceholdersPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(modifier = Modifier.width(150.dp).height(90.dp))
            GradientPlaceholder(modifier = Modifier.width(150.dp).height(90.dp), seed = "alpha")
            GradientPlaceholder(modifier = Modifier.width(150.dp).height(90.dp), seed = "beta")
        }
    }
}
