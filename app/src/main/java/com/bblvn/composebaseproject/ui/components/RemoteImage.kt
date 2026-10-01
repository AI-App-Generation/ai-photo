package com.bblvn.composebaseproject.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import kotlinx.coroutines.delay

/** How long a load must take before the shimmer and the fade-in are worth starting. */
private const val SHIMMER_DELAY_MS = 120L

/**
 * The single entry point for every network image in the app.
 *
 * Three states, exactly as the Theme conventions require:
 *  1. [ShimmerBox] skeleton while loading,
 *  2. 200ms fade-in once the bitmap is ready,
 *  3. [GradientPlaceholder] (brand-toned, seeded) on error — never a grey box.
 *
 * Risks item 29: the Coil request is sized to the composable's own drawn size,
 * so a 4000px template thumbnail is not decoded at full resolution into a
 * 150dp card.
 */
@Composable
fun RemoteImage(
    modifier: Modifier = Modifier,
    model: Any?,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
    alignment: Alignment = Alignment.Center,
    placeholderSeed: Any? = model,
    showShimmer: Boolean = true,
    showPlaceholderIcon: Boolean = true,
    fadeInMillis: Int = 200,
) {
    // Coil throws IllegalArgumentException on a Painter ("Unsupported type: Painter"), and the
    // `model: Any?` signature makes that easy to hit by accident. Render it directly instead of
    // crashing — callers that already hold a Painter simply get drawn.
    if (model is Painter) {
        Image(
            painter = model,
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = contentScale,
            alignment = alignment,
        )
        return
    }

    Box(modifier = modifier.clip(shape)) {
        val context = LocalContext.current
        // No explicit size: Coil then resolves the request against the size the painter is drawn
        // at, which is this composable's own size. A plain Box is enough — BoxWithConstraints
        // subcomposes, and that is paid again for every card that scrolls into a lazy list.
        val request = remember(context, model) {
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(false) // the fade is driven below so it is exactly 200ms
                .build()
        }
        val painter = rememberAsyncImagePainter(model = request, contentScale = contentScale)
        val state = painter.state

        // An image already in Coil's memory cache still passes through Empty/Loading for a frame
        // or two. Starting a shimmer (an infinite animation) and a fade for that is pure churn
        // while scrolling, so both only kick in once the load has genuinely taken a moment.
        var slowLoad by remember(model) { mutableStateOf(false) }
        LaunchedEffect(model) {
            delay(SHIMMER_DELAY_MS)
            slowLoad = true
        }

        val targetAlpha = if (state is AsyncImagePainter.State.Success) 1f else 0f
        val imageAlpha = animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = if (slowLoad) tween(durationMillis = fadeInMillis) else snap(),
            label = "remote_image_fade",
        )

        when (state) {
            is AsyncImagePainter.State.Loading,
            is AsyncImagePainter.State.Empty,
            -> {
                if (showShimmer && slowLoad) {
                    ShimmerBox(modifier = Modifier.fillMaxSize(), shape = RectangleShape)
                } else if (showShimmer) {
                    Box(Modifier.fillMaxSize().background(AppColors.bgSkeleton))
                } else {
                    GradientPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        seed = placeholderSeed,
                        showIcon = false,
                    )
                }
            }

            is AsyncImagePainter.State.Error -> GradientPlaceholder(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                seed = placeholderSeed,
                showIcon = showPlaceholderIcon,
            )

            is AsyncImagePainter.State.Success -> Unit
        }

        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                // Read in the layer block so the 200ms fade redraws without recomposing.
                .graphicsLayer { alpha = imageAlpha.value },
            alignment = alignment,
            contentScale = contentScale,
        )
    }
}

/**
 * Card-shaped convenience wrapper — same thing, pre-clipped to `shape_thumb`.
 */
@Composable
fun RemoteThumb(
    modifier: Modifier = Modifier,
    model: Any?,
    contentDescription: String? = null,
    seed: Any? = model,
) = RemoteImage(
    modifier = modifier,
    model = model,
    contentDescription = contentDescription,
    shape = AppShapes.thumb,
    placeholderSeed = seed,
)

/** Internal helper so other components can render an empty frame consistently. */
@Composable
internal fun ImageFrame(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.card,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(modifier = modifier.clip(shape), content = content)
}
