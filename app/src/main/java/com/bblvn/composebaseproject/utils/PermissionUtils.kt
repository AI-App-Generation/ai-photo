package com.bblvn.composebaseproject.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime permission helpers. Permissions are requested at the moment of use only: photo access
 * when the Photo Picker opens, camera when the shutter button is tapped.
 */
object PermissionUtils {

    /** Read-photos permission for this API level. */
    val photoPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    const val CAMERA_PERMISSION: String = Manifest.permission.CAMERA

    /**
     * Write permission needed to save into the public Pictures directory on API 24–28.
     *
     * `null` from API 29 (Q) onward, where [FileManager.exportToGallery] goes through MediaStore
     * `RELATIVE_PATH` and needs no permission at all. Callers must request this (when non-null)
     * BEFORE saving, otherwise the legacy branch fails with EACCES and the user sees nothing
     * happen (C6).
     */
    val galleryWritePermission: String?
        get() = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            null
        }

    /** True when saving to the gallery can proceed right now. */
    fun canWriteGallery(context: Context): Boolean =
        galleryWritePermission?.let { isGranted(context, it) } ?: true

    /**
     * Android 14+ "Select photos" partial grant. The picker must show the granted subset plus a
     * "select more photos" affordance rather than an empty state (Risks #26).
     */
    val partialPhotoPermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        } else {
            null
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun photoAccess(context: Context): PhotoAccess = when {
        isGranted(context, photoPermission) -> PhotoAccess.FULL
        partialPhotoPermission?.let { isGranted(context, it) } == true -> PhotoAccess.PARTIAL
        else -> PhotoAccess.DENIED
    }

    fun hasCamera(context: Context): Boolean = isGranted(context, CAMERA_PERMISSION)

    /**
     * Permissions to pass to the launcher for photo access. On API 34+ we ask for both the full
     * and the user-selected variant so the system can offer partial access.
     */
    fun photoRequestPermissions(): Array<String> =
        listOfNotNull(photoPermission, partialPhotoPermission).toTypedArray()

    /**
     * True when the OS will no longer show a system dialog — the user must go to app settings.
     * Only meaningful right AFTER a denial (the rationale flag is false both before the first ask
     * and after a permanent denial), so callers track "have we asked once" themselves.
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean =
        !isGranted(activity, permission) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

enum class PhotoAccess { FULL, PARTIAL, DENIED }
