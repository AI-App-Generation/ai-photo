package com.bblvn.composebaseproject.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.bblvn.composebaseproject.core.file.AppFileProvider
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.premium.PremiumManager
import java.io.File

/** Outgoing intents: share, open links, jump to app settings, take a photo. */
object IntentUtils {

    private const val TAG = "IntentUtils"

    /**
     * Share a generated image. The uri comes from [AppFileProvider] with a read grant — never a
     * `file://` uri, which throws FileUriExposedException on API 24+.
     */
    suspend fun shareImage(
        context: Context,
        file: File,
        chooserTitle: String = "Share",
    ): Boolean {
        // Staging happens here, not at the call site, so no screen can accidentally share the
        // clean original to a free user. The staged copy is swept later by
        // FileManager.clearStaleExports() — never deleted right after the chooser opens, which
        // would race the receiving app (R6.7).
        val staged = FileManager.prepareForShare(
            source = file,
            watermark = PremiumManager.shouldWatermarkExports(),
        )
        val uri = AppFileProvider.uriFor(context, staged) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, Intent.createChooser(intent, chooserTitle))
    }

    fun openUrl(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** Used by the permission empty-state after a permanent denial. */
    fun openAppSettings(context: Context): Boolean = start(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    )

    /**
     * System camera capture writing straight into `sources/`. Returns the intent plus the target
     * file so the caller can register the result. Null when no camera app exists.
     */
    fun captureImage(context: Context): Pair<Intent, File>? {
        val target = com.bblvn.composebaseproject.core.file.FileManager.newSourceFile()
        val uri = AppFileProvider.uriFor(context, target) ?: return null
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) return null
        return intent to target
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $intent", e)
        false
    }
}
