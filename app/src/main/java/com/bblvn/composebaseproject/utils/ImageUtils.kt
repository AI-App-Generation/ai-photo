package com.bblvn.composebaseproject.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import com.bblvn.composebaseproject.core.file.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bitmap work. Every entry point downsamples BEFORE decoding — a 12MP or RAW photo decoded at
 * full size is the most reliable way to OOM this app (Risks #8).
 */
object ImageUtils {

    /** Longest edge we ever upload. Bigger costs bandwidth without improving Seedream output. */
    const val MAX_UPLOAD_EDGE = 2048

    const val UPLOAD_QUALITY = 85

    /**
     * Decode a file at roughly [maxEdge] on its longest side, honouring the EXIF rotation that
     * phone cameras write instead of rotating pixels.
     */
    suspend fun decodeSampled(file: File, maxEdge: Int = MAX_UPLOAD_EDGE): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                    ?: return@runCatching null
                applyExifRotation(file, decoded)
            }.getOrNull()
        }

    suspend fun decodeSampled(
        context: Context,
        uri: Uri,
        maxEdge: Int = MAX_UPLOAD_EDGE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    /** Largest power of two whose downscale still leaves the image >= [maxEdge]. */
    fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Produce the JPEG that actually gets uploaded: downsampled, capped at [MAX_UPLOAD_EDGE],
     * quality 85. Returns the original file untouched if it is already small enough.
     */
    suspend fun prepareForUpload(source: File, target: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeSampled(source, MAX_UPLOAD_EDGE)
                    ?: error("Cannot decode ${source.name}")
                val scaled = scaleToMaxEdge(bitmap, MAX_UPLOAD_EDGE)
                val written = FileManager.writeJpeg(scaled, target, UPLOAD_QUALITY).getOrThrow()
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                written
            }
        }

    fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flipHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Render [source] through the transform the Adjust screen built, cropping to [cropRect]
     * expressed in the same coordinate space as [matrix]'s output.
     *
     * Kept here (not in the Adjust feature) because Try-On Align exports with the exact same code.
     */
    fun cropWithMatrix(
        source: Bitmap,
        matrix: Matrix,
        cropRect: RectF,
        outputWidth: Int,
        outputHeight: Int,
    ): Bitmap {
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(-cropRect.left, -cropRect.top)
        canvas.scale(
            outputWidth / cropRect.width(),
            outputHeight / cropRect.height(),
            cropRect.left,
            cropRect.top,
        )
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
        return output
    }

    /**
     * Draws the app logo, faded, into the bottom-right corner.
     *
     * Applied ONLY to the exported/shared copy for free users — never to the file in `results/`,
     * so a later PRO upgrade can re-export the same creation clean (R6.7).
     */
    fun applyWatermark(source: Bitmap, text: String = WATERMARK_TEXT): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = WATERMARK_ALPHA
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = output.width * WATERMARK_TEXT_RATIO
            letterSpacing = 0.06f
            setShadowLayer(output.width * 0.005f, 0f, 0f, android.graphics.Color.argb(90, 0, 0, 0))
        }
        val margin = output.width * WATERMARK_MARGIN_RATIO
        canvas.drawText(text, output.width - margin - paint.measureText(text), output.height - margin, paint)
        return output
    }

    const val WATERMARK_TEXT = "Vezora"

    /** Text size / margin as a fraction of the image width — shared by the export and the viewer so the mark looks identical in both. */
    const val WATERMARK_TEXT_RATIO = 0.05f
    const val WATERMARK_MARGIN_RATIO = 0.03f

    /** 40% opacity — keep in sync with the viewer overlay in ResultScreen. */
    const val WATERMARK_ALPHA = 102

    /**
     * Force a flat background colour behind a subject. Used by the Remove Background tool when
     * the user picked White/Black: Seedream returns an opaque JPEG, so "transparent" is delivered
     * by the model painting a flat backdrop and this pass only normalises the other two options.
     */
    fun withBackgroundColor(source: Bitmap, color: Int): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(color)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        })
        return output
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val rotated = rotate(bitmap, degrees)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
