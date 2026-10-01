package com.bblvn.composebaseproject.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ambient "drop shadow" / glow, drawn behind the composable.
 *
 * On a near-black background a normal `.shadow()` is invisible, and combining
 * `.shadow()` with a translucent background produces render artifacts — so the
 * whole app uses this instead (see ANDROID_APP_DESIGN.md and R3 item 10).
 *
 * Named `dropShadow` rather than `shadow` on purpose; it is deliberately NOT a
 * clash with any future `androidx.compose.ui.draw.dropShadow` because it lives
 * in this package and is imported explicitly.
 *
 * On API < 28 `BlurMaskFilter` is not supported by the hardware canvas, so the
 * glow degrades to a soft, un-blurred halo at reduced alpha rather than
 * disappearing or turning into a hard block.
 */
fun Modifier.dropShadow(
    shape: Shape = RoundedCornerShape(0.dp),
    color: Color = Color.Black.copy(alpha = 0.6f),
    blur: Dp = 20.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    spread: Dp = 0.dp,
): Modifier = this.drawBehind {
    if (color.alpha == 0f || size.minDimension <= 0f) return@drawBehind

    val blurPx = blur.toPx()
    val spreadPx = spread.toPx()
    val outlineSize = Size(size.width + spreadPx * 2, size.height + spreadPx * 2)
    val outline = shape.createOutline(outlineSize, layoutDirection, this)

    val path = Path()
    when (outline) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    if (outline is Outline.Rectangle && outline.rect == Rect.Zero) return@drawBehind

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && blurPx > 0f
        frameworkPaint.color = if (supportsBlur) {
            color.toArgb()
        } else {
            color.copy(alpha = color.alpha * 0.35f).toArgb()
        }
        if (supportsBlur) {
            frameworkPaint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(offsetX.toPx() - spreadPx, offsetY.toPx() - spreadPx)
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), frameworkPaint)
        canvas.restore()
        frameworkPaint.maskFilter = null
    }
}

/** glow_brand — violet halo under the primary CTA / active card. */
fun Modifier.glowBrand(shape: Shape = AppShapes.button, enabled: Boolean = true): Modifier =
    if (!enabled) this else dropShadow(
        shape = shape,
        color = AppColors.accentViolet.copy(alpha = 0.45f),
        blur = 28.dp,
        offsetY = 8.dp,
    )

/** glow_gold — premium halo. */
fun Modifier.glowGold(shape: Shape = AppShapes.button, enabled: Boolean = true): Modifier =
    if (!enabled) this else dropShadow(
        shape = shape,
        color = AppColors.goldDeep.copy(alpha = 0.40f),
        blur = 24.dp,
        offsetY = 6.dp,
    )

/** glow_soft — neutral lift for handles / floating chips over imagery. */
fun Modifier.glowSoft(shape: Shape = AppShapes.chip, enabled: Boolean = true): Modifier =
    if (!enabled) this else dropShadow(
        shape = shape,
        color = Color.Black.copy(alpha = 0.60f),
        blur = 20.dp,
        offsetY = 10.dp,
    )
