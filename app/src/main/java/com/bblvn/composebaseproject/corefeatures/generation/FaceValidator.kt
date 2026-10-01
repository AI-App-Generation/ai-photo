package com.bblvn.composebaseproject.corefeatures.generation

import android.graphics.Bitmap
import android.util.Log
import com.bblvn.composebaseproject.utils.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * On-device face check used before a Face Swap is submitted.
 *
 * This runs locally and costs nothing; a bad photo that reaches Newport costs credits and 40
 * seconds of the user's time before failing (Risks #18). Only the FACE slot (index 0) is checked —
 * the scene photo may legitimately contain several people (R6.6).
 */
object FaceValidator {

    private const val TAG = "FaceValidator"

    /** A face smaller than this fraction of the image width is too small to swap convincingly. */
    private const val MIN_FACE_WIDTH_RATIO = 0.12f

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_WIDTH_RATIO)
                .build()
        )
    }

    suspend fun validate(path: String): FaceCheck = validate(File(path))

    suspend fun validate(file: File): FaceCheck {
        // Downsample first: ML Kit does not need a 12MP bitmap and full decode risks OOM.
        val bitmap = ImageUtils.decodeSampled(file, maxEdge = 1024)
            ?: return FaceCheck.Unreadable
        return try {
            validate(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun validate(bitmap: Bitmap): FaceCheck = withContext(Dispatchers.Default) {
        val faces = detectFaces(bitmap) ?: return@withContext FaceCheck.Unreadable
        when {
            faces.isEmpty() -> FaceCheck.NoFace
            faces.size > 1 -> FaceCheck.MultipleFaces(faces.size)
            else -> {
                val face = faces.first()
                val widthRatio = face.boundingBox.width().toFloat() / bitmap.width
                if (widthRatio < MIN_FACE_WIDTH_RATIO) FaceCheck.FaceTooSmall else FaceCheck.Ok
            }
        }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face>? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { faces ->
                        if (continuation.isActive) continuation.resume(faces)
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "Face detection failed", error)
                        if (continuation.isActive) continuation.resume(null)
                    }
            }.onFailure {
                Log.w(TAG, "Face detection could not start", it)
                if (continuation.isActive) continuation.resume(null)
            }
        }
}

/** Result of the face check. The UI maps each case to a localized hint under the photo slot. */
sealed interface FaceCheck {

    data object Ok : FaceCheck

    data object NoFace : FaceCheck

    data class MultipleFaces(val count: Int) : FaceCheck

    data object FaceTooSmall : FaceCheck

    /** Decode or detector failure. Treat as a soft warning, not a hard block. */
    data object Unreadable : FaceCheck

    val isAcceptable: Boolean get() = this is Ok || this is Unreadable
}
