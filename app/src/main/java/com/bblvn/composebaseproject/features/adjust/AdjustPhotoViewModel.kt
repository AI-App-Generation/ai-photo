package com.bblvn.composebaseproject.features.adjust

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class AdjustUiState(
    val sourcePath: String? = null,
    val slotIndex: Int = 0,
    val totalSlots: Int = 1,
    val rotationDegrees: Float = 0f,
    val flipped: Boolean = false,
    /** Bumped to snap the crop frame (and rotate/flip) back to the full picture. */
    val resetToken: Int = 0,
    val isExporting: Boolean = false,
) {
    val showsStepCounter: Boolean get() = totalSlots > 1

    /**
     * True when the picture is neither rotated nor flipped. Together with a crop frame that still
     * covers the whole picture, the export is skipped entirely, so "do nothing and continue" is
     * byte-for-byte lossless rather than a re-encode that softens the image — the behaviour the
     * spec calls for and the one most likely to silently regress.
     */
    val isUntouched: Boolean
        get() = rotationDegrees == 0f && !flipped
}

class AdjustPhotoViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdjustUiState())
    val state: StateFlow<AdjustUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun update(block: (AdjustUiState) -> AdjustUiState) {
        _state.value = block(_state.value)
    }

    fun bind(slotIndex: Int) {
        val draft = GenerationDraft.current
        update {
            it.copy(
                slotIndex = slotIndex,
                totalSlots = draft.requiredSlots,
                sourcePath = draft.slots.getOrNull(slotIndex),
            )
        }
    }

    fun onRotate() = update {
        it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
    }

    fun onFlip() = update { it.copy(flipped = !it.flipped) }

    fun onReset() = update {
        it.copy(
            rotationDegrees = 0f,
            flipped = false,
            resetToken = it.resetToken + 1,
        )
    }

    /** Continue without adjusting: drop any previous crop so a second pass can't reuse it. */
    fun onSkip(onDone: () -> Unit) {
        GenerationDraft.clearAdjusted(_state.value.slotIndex)
        onDone()
    }

    /**
     * Export the visible crop and hand the path to the draft.
     *
     * [viewWidth]/[viewHeight] are the pixel size of the canvas the picture is centred in,
     * [imageRect] the rectangle the (rotated) picture occupies there and [cropRect] the user's
     * frame in that same space — all measured by the screen, so the maths below works on exactly
     * what the user saw.
     */
    fun onConfirm(
        viewWidth: Float,
        viewHeight: Float,
        imageRect: RectF,
        cropRect: RectF,
        onDone: () -> Unit,
    ) {
        val current = _state.value
        val path = current.sourcePath
        if (current.isExporting) return
        if (path == null) {
            viewModelScope.launch { _events.send(UiEvent.ShowToast(UiText.Res(R.string.adjust_error_missing))) }
            return
        }
        val cropIsFull = abs(cropRect.left - imageRect.left) < FULL_TOLERANCE_PX &&
            abs(cropRect.top - imageRect.top) < FULL_TOLERANCE_PX &&
            abs(cropRect.right - imageRect.right) < FULL_TOLERANCE_PX &&
            abs(cropRect.bottom - imageRect.bottom) < FULL_TOLERANCE_PX
        if ((current.isUntouched && cropIsFull) || viewWidth <= 0f || viewHeight <= 0f) {
            GenerationDraft.clearAdjusted(current.slotIndex)
            onDone()
            return
        }

        update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { exportCrop(File(path), current, viewWidth, viewHeight, cropRect) }
            }
            update { it.copy(isExporting = false) }
            result
                .onSuccess { file ->
                    GenerationDraft.setAdjusted(current.slotIndex, file.absolutePath)
                    onDone()
                }
                .onFailure {
                    _events.send(UiEvent.ShowToast(UiText.Res(R.string.adjust_error_export)))
                }
        }
    }

    private suspend fun exportCrop(
        source: File,
        state: AdjustUiState,
        viewWidth: Float,
        viewHeight: Float,
        cropRect: RectF,
    ): File {
        val bitmap = ImageUtils.decodeSampled(source, ImageUtils.MAX_UPLOAD_EDGE)
            ?: throw IllegalStateException("Cannot decode ${source.path}")

        val quarterTurn = isQuarterTurn(state.rotationDegrees)
        val shownWidth = if (quarterTurn) bitmap.height else bitmap.width
        val shownHeight = if (quarterTurn) bitmap.width else bitmap.height
        val fit = min(viewWidth / shownWidth, viewHeight / shownHeight)

        val matrix = displayMatrix(
            bitmapWidth = bitmap.width.toFloat(),
            bitmapHeight = bitmap.height.toFloat(),
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            rotationDegrees = state.rotationDegrees,
            flipped = state.flipped,
        )

        // Keep the crop's own aspect ratio and the picture's own resolution: one source pixel per
        // output pixel (view px / fit), capped so a huge photo does not produce a huge bitmap.
        val aspect = cropRect.width() / cropRect.height()
        val longEdge = min(
            ImageUtils.MAX_UPLOAD_EDGE.toFloat(),
            maxOf(cropRect.width(), cropRect.height()) / fit,
        )
        val outWidth: Int
        val outHeight: Int
        if (aspect >= 1f) {
            outWidth = longEdge.roundToInt().coerceAtLeast(1)
            outHeight = (longEdge / aspect).roundToInt().coerceAtLeast(1)
        } else {
            outHeight = longEdge.roundToInt().coerceAtLeast(1)
            outWidth = (longEdge * aspect).roundToInt().coerceAtLeast(1)
        }

        val cropped = ImageUtils.cropWithMatrix(bitmap, matrix, cropRect, outWidth, outHeight)
        val target = FileManager.adjustedSiblingOf(source.absolutePath)
        FileManager.writeJpeg(cropped, target, quality = 92).getOrThrow()
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
        return target
    }

    companion object {

        /** The crop frame counts as "still the whole picture" within this many pixels. */
        private const val FULL_TOLERANCE_PX = 1.5f

        /** 90° / 270° swap the picture's width and height on screen. */
        fun isQuarterTurn(rotationDegrees: Float): Boolean =
            (rotationDegrees / 90f).roundToInt() % 2 != 0

        /**
         * Maps source-bitmap pixels to the screen exactly as the Adjust screen draws them: the
         * picture is Fit into the canvas AFTER rotation (so a quarter turn swaps its box), flipped
         * and rotated about the canvas centre — the same order a `graphicsLayer` applies (scale,
         * then rotation), which is what the on-screen picture uses.
         */
        fun displayMatrix(
            bitmapWidth: Float,
            bitmapHeight: Float,
            viewWidth: Float,
            viewHeight: Float,
            rotationDegrees: Float,
            flipped: Boolean,
        ): Matrix {
            val quarterTurn = isQuarterTurn(rotationDegrees)
            val shownWidth = if (quarterTurn) bitmapHeight else bitmapWidth
            val shownHeight = if (quarterTurn) bitmapWidth else bitmapHeight
            val fit = min(viewWidth / shownWidth, viewHeight / shownHeight)

            return Matrix().apply {
                setTranslate(-bitmapWidth / 2f, -bitmapHeight / 2f)
                postScale(if (flipped) -fit else fit, fit)
                postRotate(rotationDegrees)
                postTranslate(viewWidth / 2f, viewHeight / 2f)
            }
        }

        /** Cheap bounds-only decode so the screen can size the crop frame before loading pixels. */
        fun readBounds(path: String): Pair<Int, Int>? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            return options.outWidth to options.outHeight
        }
    }
}
