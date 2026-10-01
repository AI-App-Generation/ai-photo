package com.bblvn.composebaseproject.core.file

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.utils.ImageUtils
import com.bblvn.composebaseproject.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Owns every file the app writes.
 *
 * Layout under `filesDir`:
 * ```
 *   sources/<uuid>.jpg        photo copied out of the system picker (immutable, URI-independent)
 *   sources/<uuid>_adj.jpg    the cropped/adjusted variant; the original is kept for Regenerate
 *   results/<creationId>.jpg  the downloaded generation result
 *   cache/catalog.json        catalog snapshot (in cacheDir)
 * ```
 * Nothing here ever touches the user's gallery except [exportToGallery].
 */
object FileManager {

    private const val TAG = "FileManager"
    private const val DIR_SOURCES = "sources"
    private const val DIR_RESULTS = "results"
    private const val GALLERY_FOLDER = "AI Photo"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        sourcesDir()
        resultsDir()
    }

    fun sourcesDir(): File = File(appContext.filesDir, DIR_SOURCES).apply { mkdirs() }

    fun resultsDir(): File = File(appContext.filesDir, DIR_RESULTS).apply { mkdirs() }

    fun cacheFile(name: String): File = File(appContext.cacheDir, name)

    fun newSourceFile(): File = File(sourcesDir(), "${UUID.randomUUID()}.jpg")

    /** The adjusted sibling of a source file: `abc.jpg` -> `abc_adj.jpg`. */
    fun adjustedSiblingOf(sourcePath: String): File {
        val original = File(sourcePath)
        val base = original.nameWithoutExtension.removeSuffix("_adj")
        return File(sourcesDir(), "${base}_adj.jpg")
    }

    fun resultFile(creationId: String): File = File(resultsDir(), "$creationId.jpg")

    // ---------------------------------------------------------------- copy / write

    /**
     * Copy a `content://` picture into app storage immediately, so we never depend on a URI grant
     * that can be revoked later (UI spec section 6).
     */
    suspend fun copyUriToSources(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = newSourceFile()
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open $uri" }
                FileOutputStream(target).use { output -> input.copyTo(output, DEFAULT_BUFFER) }
            }
            if (target.length() == 0L) throw IOException("Empty file copied from $uri")
            target
        }
    }

    /** Write a bitmap as JPEG. Recycling is the caller's business. */
    suspend fun writeJpeg(bitmap: Bitmap, target: File, quality: Int = 85): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                target
            }
        }

    suspend fun writeText(target: File, text: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            target.parentFile?.mkdirs()
            target.writeText(text)
            target
        }
    }

    suspend fun readText(file: File): String? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Deletes a creation's result plus its source photos (and their `_adj` variants). */
    fun deleteCreationFiles(resultPath: String?, sourcePaths: List<String>) {
        delete(resultPath)
        sourcePaths.forEach { source ->
            delete(source)
            delete(adjustedSiblingOf(source).absolutePath)
        }
    }

    // ---------------------------------------------------------------- gallery export

    /**
     * Copy [source] into `Pictures/AI Photo/`.
     *
     * API 29+ uses MediaStore `RELATIVE_PATH` (no storage permission needed); 24–28 falls back to
     * writing the public Pictures directory and inserting the row by `DATA` (C6).
     *
     * Any [IOException] — most commonly a full device (Risks #25) — comes back as a failed
     * [Result]; the caller shows a toast rather than crashing.
     */
    suspend fun exportToGallery(source: File, displayName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!source.exists()) throw IOException("Result file missing: ${source.path}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportViaMediaStore(source, displayName)
                } else {
                    if (!PermissionUtils.canWriteGallery(appContext)) {
                        throw MissingGalleryPermissionException()
                    }
                    exportLegacy(source, displayName)
                }
            }.onFailure { Log.w(TAG, "exportToGallery failed", it) }
        }

    /**
     * Thrown when saving on API 24–28 without WRITE_EXTERNAL_STORAGE. Callers catch this to
     * trigger a permission request instead of showing a generic failure toast.
     */
    class MissingGalleryPermissionException :
        IOException("WRITE_EXTERNAL_STORAGE required to save on API ${Build.VERSION.SDK_INT}")

    // ------------------------------------------------------- the single user-facing export path

    /**
     * THE entry point for every image leaving the app — "Lưu", "Chia sẻ", and the engine's
     * auto-save all go through here. Do not call [exportToGallery] or [AppFileProvider.uriFor]
     * directly from a screen.
     *
     * When [watermark] is true the bytes are composited with the app logo first and the result is
     * written to a short-lived cache file, so the clean original in `results/` is never modified.
     * That is what makes a later PRO upgrade able to re-export the same creation without the mark
     * (R6.7). The caller passes `PremiumManager.shouldWatermarkExports()` — the decision lives in
     * one place rather than being re-derived on every screen (Risks #33).
     */
    suspend fun exportForUser(
        source: File,
        displayName: String,
        watermark: Boolean,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val staged = if (watermark) stageWatermarked(source) else null
        try {
            exportToGallery(staged ?: source, displayName)
        } finally {
            staged?.delete()
        }
    }

    /**
     * Prepare a file suitable for sharing. Same watermark rule as [exportForUser].
     *
     * The returned file may be a cache copy. Callers do NOT delete it — staged copies are swept
     * by [clearStaleExports] at startup instead (see that function for why).
     */
    suspend fun prepareForShare(source: File, watermark: Boolean): File =
        withContext(Dispatchers.IO) {
            if (watermark) stageWatermarked(source) ?: source else source
        }

    /**
     * Deletes staged export copies left over from earlier sessions.
     *
     * Deliberately age-based and called at startup rather than right after the share intent: a
     * content uri handed to another app may be read seconds later, so deleting the staged file as
     * soon as `startActivity` returns races the receiver and produces an empty share.
     */
    suspend fun clearStaleExports(maxAgeMs: Long = STALE_EXPORT_MS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        shareCacheDir().listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
        Unit
    }

    /**
     * Returns a watermarked copy in the cache dir, or null when compositing fails — in which case
     * callers fall back to the clean file rather than blocking the export entirely.
     */
    private suspend fun stageWatermarked(source: File): File? = runCatching {
        val bitmap = ImageUtils.decodeSampled(source, MAX_EXPORT_EDGE)
            ?: return@runCatching null
        val marked = ImageUtils.applyWatermark(appContext, bitmap, R.drawable.watermark_ai_photo)
        val target = File(shareCacheDir().apply { mkdirs() }, "export_${System.nanoTime()}.jpg")
        FileOutputStream(target).use { out ->
            marked.compress(Bitmap.CompressFormat.JPEG, EXPORT_QUALITY, out)
        }
        if (marked !== bitmap) marked.recycle()
        bitmap.recycle()
        target
    }.onFailure { Log.w(TAG, "watermark staging failed, exporting clean", it) }.getOrNull()

    private fun shareCacheDir(): File = File(appContext.cacheDir, "export")

    private fun exportViaMediaStore(source: File, displayName: String): Uri {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore refused the insert")
        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "Cannot open $uri for writing" }
                source.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(source: File, displayName: String): Uri {
        val pictures =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val folder = File(pictures, GALLERY_FOLDER).apply { mkdirs() }
        val target = File(folder, displayName)
        source.inputStream().use { input ->
            FileOutputStream(target).use { out -> input.copyTo(out, DEFAULT_BUFFER) }
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, target.absolutePath)
        }
        return appContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(target)
    }

    // ---------------------------------------------------------------- housekeeping

    /** Bytes used by `sources/` + `results/` + cacheDir — shown on the Profile screen. */
    suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        sizeOf(sourcesDir()) + sizeOf(resultsDir()) + sizeOf(appContext.cacheDir)
    }

    /** Clears cacheDir (Coil's disk cache included). Never touches sources/ or results/. */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = sizeOf(appContext.cacheDir)
        appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    /** Removes source/result files not referenced by any row in the DB. */
    suspend fun pruneOrphans(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        listOf(sourcesDir(), resultsDir()).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.absolutePath !in referencedPaths) file.delete()
            }
        }
    }

    private fun sizeOf(dir: File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private const val DEFAULT_BUFFER = 8 * 1024

    /** Exports stay well under the 4096px most galleries downscale to anyway. */
    private const val MAX_EXPORT_EDGE = 2048
    private const val EXPORT_QUALITY = 92

    /** Staged export copies older than this are swept at startup. */
    private const val STALE_EXPORT_MS = 60 * 60 * 1000L
}
