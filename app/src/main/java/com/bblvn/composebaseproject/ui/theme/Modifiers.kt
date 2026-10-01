package com.bblvn.composebaseproject.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * R3.1 — `clipToWidthFraction` is NOT a built-in modifier. This is the real
 * implementation used by CompareSlider: draw the content but clip it to the
 * left [fraction] of the box's width.
 */
fun Modifier.clipToWidthFraction(fraction: Float): Modifier = clipToWidthFraction { fraction }

/**
 * Lambda form for animated fractions: the value is read in the draw phase, so a moving divider
 * only redraws instead of recomposing everything around it.
 */
fun Modifier.clipToWidthFraction(fraction: () -> Float): Modifier = this.drawWithContent {
    val right = size.width * fraction().coerceIn(0f, 1f)
    if (right <= 0f) return@drawWithContent
    clipRect(left = 0f, top = 0f, right = right, bottom = size.height) {
        this@drawWithContent.drawContent()
    }
}

/**
 * R3.2 — dashed border. Compose has no dashed-border API, so draw the rounded
 * rect stroke ourselves with a dash path effect.
 */
fun Modifier.dashedBorder(
    color: Color = AppColors.strokeSoft,
    width: Dp = 1.dp,
    cornerRadius: Dp = 18.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp,
): Modifier = this.drawBehind {
    val strokePx = width.toPx()
    val radius = CornerRadius(cornerRadius.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(strokePx / 2f, strokePx / 2f),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = radius,
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f
            ),
        ),
    )
}

/**
 * Mandatory scrim under text placed on top of an image (R9). Apply to a
 * `Box.matchParentSize()` layered above the image and below the text.
 */
fun Modifier.imageScrim(
    startFraction: Float = 0.45f,
    endAlpha: Float = 0.85f,
): Modifier = this.background(AppGradients.scrimBottom(startFraction, endAlpha))

/** Scrim that dissolves an image into [AppColors.bgRoot] (hero bottoms). */
fun Modifier.rootScrim(startFraction: Float = 0.55f): Modifier =
    this.background(AppGradients.scrimToRoot(startFraction))

/** Convenience so screens never write a raw Brush for the two brand gradients. */
fun Modifier.brandGradient(): Modifier = this.background(AppGradients.brand)

fun Modifier.goldGradient(): Modifier = this.background(AppGradients.gold)

internal fun Brush.asModifier(): Modifier = Modifier.background(this)
