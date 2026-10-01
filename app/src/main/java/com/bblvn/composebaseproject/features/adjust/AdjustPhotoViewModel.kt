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
import com.bblvn.composebaseproject.ui.components.ImageTransform
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
import kotlin.math.min
import kotlin.math.roundToInt

data class AdjustUiState(
    val sourcePath: String? = null,
    val slotIndex: Int = 0,
    val totalSlots: Int = 1,
    val rotationDegrees: Float = 0f,
    val flipped: Boolean = false,
    /** Bumped to snap [ImageTransform] back to identity. */
    val resetToken: Int = 0,
    val transform: ImageTransform = ImageTransform(),
    val isExporting: Boolean = false,
) {
    val showsStepCounter: Boolean get() = totalSlots > 1

    /**
     * True when the user has not moved anything. The export is skipped entirely in this case, so
     * "do nothing and continue" is byte-for-byte lossless rather than a re-encode that softens the
     * image — the behaviour the spec calls for and the one most likely to silently regress.
     */
    val isUntouched: Boolean
        get() = transform.isIdentity && rotationDegrees == 0f && !flipped
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

    fun onTransformChange(transform: ImageTransform) = update { it.copy(transform = transform) }

    fun onRotate() = update {
        it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
    }

    fun onFlip() = update { it.copy(flipped = !it.flipped) }

    fun onReset() = update {
        it.copy(
            rotationDegrees = 0f,
            flipped = false,
            resetToken = it.resetToken + 1,
            transform = ImageTransform(),
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
     * [viewWidth]/[viewHeight] are the pixel size of the ZoomableImage box and [cropRect] the crop
     * frame in that same coordinate space — both measured by the screen, so the maths below works
     * on exactly what the user saw.
     */
    fun onConfirm(
        viewWidth: Float,
        viewHeight: Float,
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
        if (current.isUntouched || viewWidth <= 0f || viewHeight <= 0f) {
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

        val matrix = previewMatrix(
            bitmapWidth = bitmap.width.toFloat(),
            bitmapHeight = bitmap.height.toFloat(),
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            transform = state.transform.copy(
                rotationDegrees = state.rotationDegrees,
                flippedHorizontally = state.flipped,
            ),
        )

        // Keep the crop's own aspect ratio; cap the long edge so a zoomed-in crop of a big photo
        // does not produce a needlessly huge bitmap.
        val aspect = cropRect.width() / cropRect.height()
        val longEdge = min(ImageUtils.MAX_UPLOAD_EDGE.toFloat(), maxOf(cropRect.width(), cropRect.height()) * 2f)
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

        /**
         * Rebuilds, in source-bitmap space, the exact transform `ZoomableImage` applied on screen.
         *
         * `ZoomableImage` draws the image with `ContentScale.Fit` filling the box, then applies a
         * `graphicsLayer` whose scale and rotation act about the BOX CENTRE and whose translation
         * is applied last. Reproducing that order matters: doing the pan before the scale, or
         * rotating about the image centre instead of the box centre, both look almost right for a
         * centred subject and are visibly wrong once the user pans.
         *
         * Worked example — a 3000x4000 photo in a 1080x1440 box at scale 2.0 panned 100px left:
         * s0 = min(1080/3000, 1440/4000) = 0.36, so the fitted image is exactly 1080x1440 at
         * (0, 0). Scaling by 2 about the centre (540, 720) maps the image's left edge to
         * 540 - 540*2 = -540, and the -100 pan shifts it to -640. A crop frame at x = 0 therefore
         * starts 640px into the scaled image, i.e. 640 / (0.36 * 2) = 889px into the source.
         */
        fun previewMatrix(
            bitmapWidth: Float,
            bitmapHeight: Float,
            viewWidth: Float,
            viewHeight: Float,
            transform: ImageTransform,
        ): Matrix {
            val fit = min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
            val fittedLeft = (viewWidth - bitmapWidth * fit) / 2f
            val fittedTop = (viewHeight - bitmapHeight * fit) / 2f
            val centreX = viewWidth / 2f
            val centreY = viewHeight / 2f

            return Matrix().apply {
                setScale(fit, fit)
                postTranslate(fittedLeft, fittedTop)
                // graphicsLayer order: scale, then rotation, then translation — about the centre.
                postTranslate(-centreX, -centreY)
                postScale(
                    if (transform.flippedHorizontally) -transform.scale else transform.scale,
                    transform.scale,
                )
                postRotate(transform.rotationDegrees)
                postTranslate(centreX, centreY)
                postTranslate(transform.offsetX, transform.offsetY)
            }
        }

        /**
         * The crop frame: exactly the rectangle the un-zoomed image occupies.
         *
         * Deliberately NOT a fixed 3:4 box. A fixed ratio would crop a 4:3 photo the moment the
         * screen opened, so a user who touched nothing would still get a different image than they
         * picked — which the spec forbids.
         */
        fun defaultCropRect(
            bitmapWidth: Float,
            bitmapHeight: Float,
            viewWidth: Float,
            viewHeight: Float,
        ): RectF {
            val fit = min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
            val width = bitmapWidth * fit
            val height = bitmapHeight * fit
            val left = (viewWidth - width) / 2f
            val top = (viewHeight - height) / 2f
            return RectF(left, top, left + width, top + height)
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
