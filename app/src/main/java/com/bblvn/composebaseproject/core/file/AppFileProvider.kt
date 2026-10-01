package com.bblvn.composebaseproject.core.file

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Thin wrapper over [androidx.core.content.FileProvider], whose authority and served paths are
 * declared in the manifest and `res/xml/file_paths.xml`.
 *
 * This used to be a hand-written ContentProvider. It is not any more: the platform class already
 * handles path-traversal defence, `ParcelFileDescriptor` modes and subdirectories correctly, and
 * the only reason to avoid it (adding an xml resource) was never a real constraint.
 */
object AppFileProvider {

    private const val TAG = "AppFileProvider"

    private fun authority(context: Context): String = "${context.packageName}.files"

    /**
     * Content uri for [file], or null when it sits outside the declared paths — callers treat
     * null as "cannot share this file" rather than crashing.
     */
    fun uriFor(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, authority(context), file)
    }.onFailure { Log.w(TAG, "No configured path serves ${file.path}", it) }.getOrNull()
}
