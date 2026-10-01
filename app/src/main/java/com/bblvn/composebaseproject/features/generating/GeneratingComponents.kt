package com.bblvn.composebaseproject.features.generating

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Screen-local animation pieces for Generating. They live here rather than in `ui/components`
 * because nothing else in the app is allowed to spend this much CPU (spec Theme: "Generating and
 * Result are the only two screens where expensive animation is permitted").
 *
 * Every one of them takes an `active` flag and draws a *static* frame when it is false, so the
 * whole screen goes quiet as soon as the lifecycle leaves RESUMED (Risks #30).
 */

/** 12 is the budget — enough to feel alive, cheap enough for a weak device (Risks #30). */
private const val PARTICLE_COUNT = 12

/**
 * A 0f..1f phase that advances once every [durationMillis] and freezes when [active] is false.
 *
 * Implemented with [withFrameNanos] rather than `rememberInfiniteTransition` on purpose: an
 * infinite transition keeps its animation running for as long as it is composed, while this
 * coroutine is simply cancelled when the screen is paused, and resumes from the phase it stopped
 * at instead of snapping back to 0.
 */
@Composable
fun rememberAnimationPhase(durationMillis: Int, active: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, durationMillis) {
        if (!active || durationMillis <= 0) return@LaunchedEffect
        val periodNanos = durationMillis * 1_000_000f
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val delta = (now - last).coerceAtLeast(0L)
                last = now
                phase.floatValue = (phase.floatValue + delta / periodNanos) % 1f
            }
        }
    }
    return phase
}

/**
 * The aurora plate behind everything. Slowly drifts and breathes over a 12s loop so the backdrop
 * never looks like a static JPEG, at the cost of one `graphicsLayer` per frame.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, active: Boolean) {
    val phase by rememberAnimationPhase(durationMillis = 12_000, active = active)
    val angle = phase * 2f * PI.toFloat()
    Image(
        painter = painterResource(R.drawable.bg_generating_aurora),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.35f,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val breathe = 1.08f + 0.04f * sin(angle)
                scaleX = breathe
                scaleY = breathe
                translationX = sin(angle) * 24.dp.toPx()
                translationY = sin(angle * 0.5f) * 16.dp.toPx()
            },
    )
}

/**
 * Diagonal light band sweeping top -> bottom across the preview, 1800ms per pass.
 * Draws nothing at all when paused (a frozen highlight band would read as a rendering bug).
 */
@Composable
fun ShimmerSweep(
    modifier: Modifier = Modifier,
    active: Boolean,
    shape: Shape = AppShapes.card,
) {
    val phase by rememberAnimationPhase(durationMillis = 1_800, active = active)
    Canvas(modifier = modifier.clip(shape)) {
        if (!active) return@Canvas
        val bandHeight = size.height * 0.45f
        val travel = size.height + bandHeight * 2f
        val top = -bandHeight + phase * travel
        val brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.22f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = bandHeight,
        )
        rotate(degrees = -18f) {
            translate(top = top) {
                drawRect(
                    brush = brush,
                    topLeft = Offset(-size.width * 0.5f, 0f),
                    size = Size(size.width * 2f, bandHeight),
                )
            }
        }
    }
}

/**
 * 12 small motes drifting upward, alpha breathing as they go. One [Canvas], no per-particle
 * composable, no allocation inside the draw loop.
 */
@Composable
fun ParticleField(modifier: Modifier = Modifier, active: Boolean) {
    val phase by rememberAnimationPhase(durationMillis = 6_000, active = active)
    val particles = remember {
        val random = Random(seed = 4_2)
        List(PARTICLE_COUNT) {
            Particle(
                x = random.nextFloat(),
                radiusDp = 1.5f + random.nextFloat() * 2.5f,
                speed = 0.6f + random.nextFloat() * 0.8f,
                offset = random.nextFloat(),
                drift = (random.nextFloat() - 0.5f) * 0.12f,
                color = if (random.nextBoolean()) AppColors.accentCyan else AppColors.accentViolet,
            )
        }
    }
    Canvas(modifier = modifier) {
        particles.forEach { particle ->
            val travel = (phase * particle.speed + particle.offset) % 1f
            val y = size.height * (1f - travel)
            val x = size.width * (particle.x + particle.drift * sin(travel * 2f * PI.toFloat()))
            val alpha = 0.25f + 0.55f * sin(travel * PI.toFloat())
            drawCircle(
                color = particle.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = particle.radiusDp.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}

private data class Particle(
    val x: Float,
    val radiusDp: Float,
    val speed: Float,
    val offset: Float,
    val drift: Float,
    val color: Color,
)

/**
 * R3.3 — a gradient border that rotates 360° every 3s.
 *
 * The rotation is applied to the *brush fill*, clipped to the ring: rotating the Box itself would
 * rotate the photo inside it. The ring path and the sweep brush are built once per size in
 * [drawWithCache]; only the angle changes per frame.
 */
fun Modifier.rotatingGradientBorder(
    angleProvider: () -> Float,
    shape: Shape = AppShapes.card,
    width: Dp = 2.dp,
): Modifier = this.drawWithCache {
    val strokePx = width.toPx()
    val outer = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
    }
    val innerSize = Size(
        width = (size.width - strokePx * 2f).coerceAtLeast(0f),
        height = (size.height - strokePx * 2f).coerceAtLeast(0f),
    )
    val inner = Path().apply {
        addOutline(shape.createOutline(innerSize, layoutDirection, this@drawWithCache))
        translate(Offset(strokePx, strokePx))
    }
    val ring = Path().apply { op(outer, inner, PathOperation.Difference) }

    val diagonal = hypot(size.width, size.height)
    val fillTopLeft = Offset(
        x = (size.width - diagonal) / 2f,
        y = (size.height - diagonal) / 2f,
    )
    // Sweep centre is expressed in the fill rect's own coordinates, which puts it exactly on the
    // box centre — so rotating the fill spins the gradient in place.
    val brush = Brush.sweepGradient(
        colors = listOf(
            AppColors.accentViolet,
            AppColors.accentMagenta,
            AppColors.accentCyan,
            AppColors.accentViolet,
        ),
        center = Offset(diagonal / 2f, diagonal / 2f),
    )

    onDrawWithContent {
        drawContent()
        if (innerSize.width <= 0f || innerSize.height <= 0f) return@onDrawWithContent
        clipPath(ring) {
            rotate(degrees = angleProvider()) {
                drawRect(
                    brush = brush,
                    topLeft = fillTopLeft,
                    size = Size(diagonal, diagonal),
                )
            }
        }
    }
}

/**
 * Estimated progress bar. [fraction] is coerced away from 0f because `fillMaxWidth(0f)` throws /
 * collapses the child (R3.4).
 */
@Composable
fun EstimatedProgressBar(modifier: Modifier = Modifier, fraction: Float) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(AppShapes.chip)
            .background(AppColors.bgElevated),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                .clip(AppShapes.chip)
                .background(AppGradients.brand),
        )
    }
}
