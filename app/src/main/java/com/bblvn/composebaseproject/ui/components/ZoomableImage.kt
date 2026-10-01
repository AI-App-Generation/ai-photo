package com.bblvn.composebaseproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.ui.theme.AppColors

/**
 * The pinch/pan state of a [ZoomableImage], reported to the caller so that
 * Adjust Photo can export the bitmap using exactly the matrix the user sees.
 */
data class ImageTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val flippedHorizontally: Boolean = false,
) {
    val isIdentity: Boolean
        get() = scale == 1f && offsetX == 0f && offsetY == 0f &&
            rotationDegrees == 0f && !flippedHorizontally
}

/**
 * Pinch-to-zoom (1x..4x) image with BOUNDED pan and double-tap reset.
 *
 * Shared by Adjust Photo and Try-On Alignment — the two screens differ only in
 * what they draw in [overlayContent] (crop frame vs body silhouette).
 *
 * The pan is clamped so the picture can never be dragged away from under the
 * frame: at scale `s` the image may move at most `(s - 1) * size / 2` in each
 * direction, which is 0 at scale 1.
 *
 * [rotationDegrees] and [flippedHorizontally] are driven from the outside (the
 * Rotate / Flip action chips) so the caller keeps a single source of truth for
 * the export matrix.
 */
@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    model: Any?,
    contentScale: ContentScale = ContentScale.Fit,
    minScale: Float = 1f,
    maxScale: Float = 4f,
    rotationDegrees: Float = 0f,
    flippedHorizontally: Boolean = false,
    /** Set to a new value to snap the gesture state back to identity. */
    resetKey: Any? = null,
    doubleTapToReset: Boolean = true,
    onTransformChange: ((ImageTransform) -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(minScale) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val callback = rememberUpdatedState(onTransformChange)

    LaunchedEffect(resetKey) {
        scale = minScale
        offsetX = 0f
        offsetY = 0f
    }

    LaunchedEffect(scale, offsetX, offsetY, rotationDegrees, flippedHorizontally) {
        callback.value?.invoke(
            ImageTransform(scale, offsetX, offsetY, rotationDegrees, flippedHorizontally)
        )
    }

    BoxWithConstraints(
        modifier = modifier.background(AppColors.bgRoot),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        fun clamp() {
            val maxX = ((scale - 1f) * widthPx / 2f).coerceAtLeast(0f)
            val maxY = ((scale - 1f) * heightPx / 2f).coerceAtLeast(0f)
            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(minScale, maxScale, widthPx, heightPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        offsetX += pan.x
                        offsetY += pan.y
                        clamp()
                    }
                }
                .pointerInput(doubleTapToReset, minScale, maxScale) {
                    if (!doubleTapToReset) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > minScale + 0.01f) {
                                scale = minScale
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = (minScale * 2f).coerceAtMost(maxScale)
                                clamp()
                            }
                        }
                    )
                }
        ) {
            RemoteImage(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (flippedHorizontally) -scale else scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                        rotationZ = rotationDegrees
                    },
                model = model,
                contentScale = contentScale,
                showShimmer = true,
                placeholderSeed = model,
            )
            overlayContent()
        }
    }
}

/**
 * The 2x2 rule-of-thirds grid + bright frame used by Adjust Photo's crop
 * overlay. Kept here so Try-On can reuse the same stroke weights.
 */
@Composable
fun CropFrameOverlay(
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    frameColor: Color = Color.White.copy(alpha = 0.9f),
    gridColor: Color = Color.White.copy(alpha = 0.25f),
) {
    Box(
        modifier = modifier
            .border(width = 1.5.dp, color = frameColor)
            .drawBehind {
                if (!showGrid) return@drawBehind
                val stroke = 1.dp.toPx()
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    val y = size.height * i / 3f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), stroke)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke)
                }
            }
    )
}
