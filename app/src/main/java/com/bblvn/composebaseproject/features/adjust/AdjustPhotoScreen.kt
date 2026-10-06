package com.bblvn.composebaseproject.features.adjust

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.bblvn.composebaseproject.ui.components.RemoteImage
import com.bblvn.composebaseproject.ui.theme.AppGradients
import kotlin.math.min
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.DisableWelcomeOverlayTemporarily
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppType
import java.io.File

/**
 * The optional crop step between picking a photo and generating.
 *
 * The picture is centred in the WHOLE screen (not in the strip between the bars); the top bar and
 * the action bar float over it and fade out while a crop handle is being dragged, so nothing hides
 * the edge the user is placing. The crop frame is free-form: drag a corner to resize, drag inside
 * to move.
 *
 * Not scrollable by design: it is a gesture canvas, and a scroll container would compete with the
 * drag gesture for the same events.
 */
@Composable
fun AdjustPhotoScreen(
    slotIndex: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AdjustPhotoViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

    DisableWelcomeOverlayTemporarily()

    LaunchedEffect(slotIndex) { vm.bind(slotIndex) }

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.ShowToast -> toast = event.text.resolve(context)
            else -> Unit
        }
    }

    val sourcePath = state.sourcePath
    val bounds = remember(sourcePath) {
        sourcePath?.let { AdjustPhotoViewModel.readBounds(it) }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        if (sourcePath == null || bounds == null) {
            ErrorState(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.adjust_error_missing_title),
                message = stringResource(R.string.adjust_error_missing),
                actionText = stringResource(R.string.adjust_go_back),
                onAction = onBack,
            )
            return@Box
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            // The picture as it is shown: a quarter turn swaps its width and height, and it is
            // Fit into the canvas and centred. This rect is the largest possible crop.
            val quarterTurn = AdjustPhotoViewModel.isQuarterTurn(state.rotationDegrees)
            val shownWidth = (if (quarterTurn) bounds.second else bounds.first).toFloat()
            val shownHeight = (if (quarterTurn) bounds.first else bounds.second).toFloat()
            val fit = min(widthPx / shownWidth, heightPx / shownHeight)
            val imageRect = remember(shownWidth, shownHeight, widthPx, heightPx) {
                val w = shownWidth * fit
                val h = shownHeight * fit
                val left = (widthPx - w) / 2f
                val top = (heightPx - h) / 2f
                Rect(left, top, left + w, top + h)
            }

            // Back to the full picture whenever the picture shape changes (rotate) or on Reset.
            var crop by remember(imageRect, state.resetToken) { mutableStateOf(imageRect) }
            var dragging by remember { mutableStateOf(false) }

            // The picture: laid out UNrotated at its fitted size, then rotated / flipped about the
            // canvas centre. AdjustPhotoViewModel.displayMatrix reproduces this order for export.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        width = with(density) { (bounds.first * fit).toDp() },
                        height = with(density) { (bounds.second * fit).toDp() },
                    )
                    .graphicsLayer {
                        rotationZ = state.rotationDegrees
                        scaleX = if (state.flipped) -1f else 1f
                    },
            ) {
                RemoteImage(
                    modifier = Modifier.fillMaxSize(),
                    model = File(sourcePath),
                    contentScale = ContentScale.Fit,
                    placeholderSeed = sourcePath,
                )
            }

            CropOverlay(
                modifier = Modifier.fillMaxSize(),
                imageRect = imageRect,
                crop = crop,
                dragging = dragging,
                onCropChange = { crop = it },
                onDragChange = { dragging = it },
            )

            AnimatedVisibility(
                modifier = Modifier.align(Alignment.TopCenter),
                visible = !dragging,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
                    ),
                ) {
                    AdjustTopBar(
                        stepLabel = if (state.showsStepCounter) {
                            stringResource(
                                R.string.adjust_title_step,
                                state.slotIndex + 1,
                                state.totalSlots,
                            )
                        } else {
                            stringResource(R.string.adjust_title)
                        },
                        onBack = onBack,
                        onSkip = { vm.onSkip(onDone) },
                    )
                }
            }

            AnimatedVisibility(
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = !dragging,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.background(AppGradients.bottomCtaFade)) {
                    AdjustBottomBar(
                        enabled = !state.isExporting,
                        onRotate = vm::onRotate,
                        onFlip = vm::onFlip,
                        onReset = vm::onReset,
                        onConfirm = {
                            vm.onConfirm(
                                viewWidth = widthPx,
                                viewHeight = heightPx,
                                imageRect = RectF(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom),
                                cropRect = RectF(crop.left, crop.top, crop.right, crop.bottom),
                                onDone = onDone,
                            )
                        },
                    )
                }
            }
        }

        AppToast(
            visible = toast != null,
            text = toast.orEmpty(),
            icon = R.drawable.ic_alert_circle,
            iconTint = AppColors.stateDanger,
            onDismiss = { toast = null },
        )
    }
}

private enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight, Move }

/**
 * The free-form crop frame: dim outside, white border, bracket handles on the four corners and a
 * thirds grid while dragging. Corners resize, the inside moves the whole frame; the frame is kept
 * inside [imageRect] and never smaller than [MIN_CROP_DP].
 */
@Composable
private fun CropOverlay(
    modifier: Modifier,
    imageRect: Rect,
    crop: Rect,
    dragging: Boolean,
    onCropChange: (Rect) -> Unit,
    onDragChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val hitRadius = with(density) { HIT_RADIUS_DP.dp.toPx() }
    val minSize = with(density) { MIN_CROP_DP.dp.toPx() }
    val currentCrop by rememberUpdatedState(crop)

    var active by remember { mutableStateOf<CropHandle?>(null) }

    Canvas(
        modifier = modifier.pointerInput(imageRect) {
            detectDragGestures(
                onDragStart = { start ->
                    val c = currentCrop
                    active = when {
                        (start - c.topLeft).getDistance() <= hitRadius -> CropHandle.TopLeft
                        (start - c.topRight).getDistance() <= hitRadius -> CropHandle.TopRight
                        (start - c.bottomLeft).getDistance() <= hitRadius -> CropHandle.BottomLeft
                        (start - c.bottomRight).getDistance() <= hitRadius -> CropHandle.BottomRight
                        c.contains(start) -> CropHandle.Move
                        else -> null
                    }
                    if (active != null) onDragChange(true)
                },
                onDragEnd = { active = null; onDragChange(false) },
                onDragCancel = { active = null; onDragChange(false) },
                onDrag = { change, amount ->
                    val handle = active ?: return@detectDragGestures
                    change.consume()
                    val c = currentCrop
                    val next = when (handle) {
                        CropHandle.Move -> {
                            val dx = amount.x.coerceIn(imageRect.left - c.left, imageRect.right - c.right)
                            val dy = amount.y.coerceIn(imageRect.top - c.top, imageRect.bottom - c.bottom)
                            c.translate(dx, dy)
                        }

                        else -> {
                            val moveLeft = handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft
                            val moveTop = handle == CropHandle.TopLeft || handle == CropHandle.TopRight
                            Rect(
                                left = if (moveLeft) (c.left + amount.x).coerceIn(imageRect.left, c.right - minSize) else c.left,
                                top = if (moveTop) (c.top + amount.y).coerceIn(imageRect.top, c.bottom - minSize) else c.top,
                                right = if (!moveLeft) (c.right + amount.x).coerceIn(c.left + minSize, imageRect.right) else c.right,
                                bottom = if (!moveTop) (c.bottom + amount.y).coerceIn(c.top + minSize, imageRect.bottom) else c.bottom,
                            )
                        }
                    }
                    onCropChange(next)
                },
            )
        },
    ) {
        // Dim everything inside the picture but outside the frame.
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset(imageRect.left, imageRect.top), Size(imageRect.width, crop.top - imageRect.top))
        drawRect(dim, Offset(imageRect.left, crop.bottom), Size(imageRect.width, imageRect.bottom - crop.bottom))
        drawRect(dim, Offset(imageRect.left, crop.top), Size(crop.left - imageRect.left, crop.height))
        drawRect(dim, Offset(crop.right, crop.top), Size(imageRect.right - crop.right, crop.height))

        if (dragging) {
            val grid = Color.White.copy(alpha = 0.35f)
            val line = 1.dp.toPx()
            for (i in 1..2) {
                val x = crop.left + crop.width * i / 3f
                val y = crop.top + crop.height * i / 3f
                drawLine(grid, Offset(x, crop.top), Offset(x, crop.bottom), line)
                drawLine(grid, Offset(crop.left, y), Offset(crop.right, y), line)
            }
        }

        drawRect(
            Color.White.copy(alpha = 0.9f),
            topLeft = Offset(crop.left, crop.top),
            size = Size(crop.width, crop.height),
            style = Stroke(width = 1.dp.toPx()),
        )

        // Corner brackets: the visible handles.
        val arm = 22.dp.toPx()
        val thick = 3.dp.toPx()
        val handleColor = Color.White
        fun bracket(corner: Offset, dirX: Float, dirY: Float) {
            drawLine(handleColor, corner, Offset(corner.x + dirX * arm, corner.y), thick, StrokeCap.Square)
            drawLine(handleColor, corner, Offset(corner.x, corner.y + dirY * arm), thick, StrokeCap.Square)
        }
        bracket(crop.topLeft, 1f, 1f)
        bracket(crop.topRight, -1f, 1f)
        bracket(crop.bottomLeft, 1f, -1f)
        bracket(crop.bottomRight, -1f, -1f)
    }
}

private const val HIT_RADIUS_DP = 40
private const val MIN_CROP_DP = 72

@Composable
private fun AdjustTopBar(
    stepLabel: String,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = R.drawable.ic_arrow_left,
            contentDescription = stringResource(R.string.adjust_go_back),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        Text(text = stepLabel, style = AppType.titleM, color = AppColors.textPrimary)
        Spacer(Modifier.weight(1f))
        AppChip(
            text = stringResource(R.string.adjust_skip),
            size = AppChipSize.Small,
            onClick = onSkip,
        )
    }
}

@Composable
private fun AdjustBottomBar(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // All three share weight(1f): "Đặt lại" is wider than "Lật", and natural widths would
        // leave the row visibly off-rhythm (spec R1).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppChip(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.adjust_rotate),
                icon = R.drawable.ic_rotate,
                size = AppChipSize.Large,
                fillWidth = true,
                enabled = enabled,
                onClick = onRotate,
            )
            AppChip(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.adjust_flip),
                icon = R.drawable.ic_flip,
                size = AppChipSize.Large,
                fillWidth = true,
                enabled = enabled,
                onClick = onFlip,
            )
            AppChip(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.adjust_reset),
                icon = R.drawable.ic_reset,
                size = AppChipSize.Large,
                fillWidth = true,
                enabled = enabled,
                onClick = onReset,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.adjust_continue),
            enabled = enabled,
            onClick = onConfirm,
        )
    }
}
