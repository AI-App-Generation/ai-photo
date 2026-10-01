package com.bblvn.composebaseproject.features.adjust

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.bblvn.composebaseproject.ui.components.CropFrameOverlay
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.ZoomableImage
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppType
import java.io.File

/**
 * The optional crop/zoom step between picking a photo and generating.
 *
 * Not scrollable by design: it is a gesture canvas, and a scroll container would compete with the
 * pan gesture for the same drag events.
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
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

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

        Column(modifier = Modifier.fillMaxSize()) {
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

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { viewSize = it },
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()

                // One source of truth: the same rect is drawn as the frame and used as the export
                // crop, so the picture cannot drift from what the user framed.
                val cropRect = remember(bounds, widthPx, heightPx) {
                    AdjustPhotoViewModel.defaultCropRect(
                        bitmapWidth = bounds.first.toFloat(),
                        bitmapHeight = bounds.second.toFloat(),
                        viewWidth = widthPx,
                        viewHeight = heightPx,
                    )
                }

                ZoomableImage(
                    modifier = Modifier.fillMaxSize(),
                    model = File(sourcePath),
                    rotationDegrees = state.rotationDegrees,
                    flippedHorizontally = state.flipped,
                    resetKey = state.resetToken,
                    onTransformChange = vm::onTransformChange,
                ) {
                    val density = LocalDensity.current
                    CropFrameOverlay(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            // Offset in raw pixels so the drawn frame lands exactly on the rect
                            // the export crops to; going through dp would round it out of sync.
                            .offset {
                                IntOffset(cropRect.left.toInt(), cropRect.top.toInt())
                            }
                            .size(
                                width = with(density) { cropRect.width().toDp() },
                                height = with(density) { cropRect.height().toDp() },
                            ),
                        // The grid is guidance while framing; it fades once the user settles.
                        showGrid = !state.transform.isIdentity,
                    )
                }

                AdjustBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enabled = !state.isExporting,
                    onRotate = vm::onRotate,
                    onFlip = vm::onFlip,
                    onReset = vm::onReset,
                    onConfirm = {
                        vm.onConfirm(
                            viewWidth = widthPx,
                            viewHeight = heightPx,
                            cropRect = RectF(cropRect),
                            onDone = onDone,
                        )
                    },
                )
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
