package com.bblvn.composebaseproject.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.clipToWidthFraction
import com.bblvn.composebaseproject.ui.theme.glowSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How the handle moves. */
enum class CompareSliderMode {
    /** User drags (or taps) to move the divider. Template Detail, Tool Detail, Result. */
    Draggable,

    /** The divider wipes before -> after on a loop and ignores touch. Home banner. */
    AutoPlay,
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * Before/after comparison.
 *
 * Reused on 4 screens (Template Detail hero, Tool Detail example, Result, and
 * the Home banner in [CompareSliderMode.AutoPlay]).
 *
 * R3.1 — there is no `clipToWidthFraction` modifier in Compose; the "before"
 * layer is clipped with `drawWithContent { clipRect(right = width * f) { ... } }`
 * (see [clipToWidthFraction]).
 *
 * Risks item 23 — before and after images frequently have DIFFERENT aspect
 * ratios (a 1:1 source against a 3:4 result). Pass
 * `contentScale = ContentScale.Fit`: both layers then letterbox inside the same
 * frame, centred, so the divider lines up across the two pictures instead of
 * one being cropped against the other.
 */
@Composable
fun CompareSlider(
    modifier: Modifier = Modifier,
    before: Any?,
    after: Any?,
    initialFraction: Float = 0.5f,
    mode: CompareSliderMode = CompareSliderMode.Draggable,
    // Fit by default: the highest-stakes use (Result) compares a source photo against a result
    // of a different aspect ratio, and Crop would silently misalign them (Risks #23). Pass Crop
    // explicitly for decorative uses where both images share a ratio.
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = RectangleShape,
    showHandle: Boolean = mode == CompareSliderMode.Draggable,
    autoPlayDurationMillis: Int = 2400,
    autoPlayHoldMillis: Long = 1000L,
    /** Template Detail: nudge the handle 0.5 -> 0.75 -> 0.5 once so the user sees it is draggable. */
    hintOnFirstShow: Boolean = false,
    /** Result: wipe 0f -> 1f over 900ms (easeOutCubic) to reveal the finished image. */
    revealOnFirstShow: Boolean = false,
    revealDurationMillis: Int = 900,
    onFractionChange: ((Float) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val anim = remember {
        Animatable(if (revealOnFirstShow) 0f else initialFraction.coerceIn(0f, 1f))
    }
    val scope = rememberCoroutineScope()
    val onChange = rememberUpdatedState(onFractionChange)

    // `anim.value` is never read during composition: the divider moves every frame (the Home
    // banner loops forever), and a composition read would recompose both images and the overlay
    // each time. It is read in the draw / layout phase below instead.
    LaunchedEffect(anim) {
        snapshotFlow { anim.value }.collect { onChange.value?.invoke(it) }
    }

    LaunchedEffect(mode, revealOnFirstShow, hintOnFirstShow) {
        when {
            mode == CompareSliderMode.AutoPlay -> while (true) {
                anim.snapTo(0f)
                delay(500)
                anim.animateTo(
                    1f,
                    tween(autoPlayDurationMillis, easing = FastOutSlowInEasing),
                )
                delay(autoPlayHoldMillis)
            }

            revealOnFirstShow -> anim.animateTo(
                1f,
                tween(revealDurationMillis, easing = EaseOutCubic),
            )

            hintOnFirstShow -> {
                delay(500)
                anim.animateTo(0.75f, tween(500, easing = FastOutSlowInEasing))
                anim.animateTo(initialFraction.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing))
            }
        }
    }

    BoxWithConstraints(modifier = modifier.clip(shape)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val interactive = mode == CompareSliderMode.Draggable

        var gestures: Modifier = Modifier
        if (interactive) {
            gestures = gestures
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            anim.snapTo((anim.value + dragAmount / widthPx).coerceIn(0f, 1f))
                        }
                    }
                }
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        scope.launch {
                            anim.animateTo(
                                (offset.x / widthPx).coerceIn(0f, 1f),
                                tween(220, easing = FastOutSlowInEasing),
                            )
                        }
                    }
                }
        }

        Box(modifier = Modifier.fillMaxSize().then(gestures)) {
            // Base layer = the "after" image, always fully drawn.
            RemoteImage(
                modifier = Modifier.matchParentSize(),
                model = after,
                contentScale = contentScale,
                placeholderSeed = after,
            )

            // Top layer = the "before" image, clipped to the left `fraction`.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clipToWidthFraction { anim.value }
            ) {
                RemoteImage(
                    modifier = Modifier.fillMaxSize(),
                    model = before,
                    contentScale = contentScale,
                    placeholderSeed = before,
                )
            }

            if (showHandle) {
                CompareHandle(
                    fraction = { anim.value },
                    widthPx = widthPx,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }

            overlay()
        }
    }
}

@Composable
private fun BoxScope.CompareHandle(
    fraction: () -> Float,
    widthPx: Float,
    modifier: Modifier = Modifier,
    knobSize: Dp = 40.dp,
) {
    val trackWidth = 48.dp
    Box(
        modifier = modifier
            .width(trackWidth)
            .fillMaxHeight()
            .offset {
                val half = trackWidth.toPx() / 2f
                IntOffset((fraction() * widthPx - half).roundToInt(), 0)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.9f))
        )
        Box(
            modifier = Modifier
                .size(knobSize)
                .glowSoft(CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left_right),
                contentDescription = null,
                tint = AppColors.bgRoot,
                modifier = Modifier.size(20.dp).padding(0.dp),
            )
        }
    }
}
