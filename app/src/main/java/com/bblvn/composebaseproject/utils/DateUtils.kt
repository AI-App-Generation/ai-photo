package com.bblvn.composebaseproject.utils

import android.text.format.DateUtils as AndroidDateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Relative timestamps for history cards ("2 giờ trước").
 *
 * Deliberately built on [android.text.format.DateUtils], which is already translated into every
 * locale the app ships — writing our own plural rules across 11 languages would be pure risk.
 */
object DateUtils {

    fun timeAgo(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val safeNow = maxOf(nowMs, timestampMs)
        return AndroidDateUtils.getRelativeTimeSpanString(
            timestampMs,
            safeNow,
            AndroidDateUtils.MINUTE_IN_MILLIS,
            AndroidDateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }

    /** Stable, sortable name for an exported gallery file. */
    fun exportFileName(prefix: String = "AI_Photo", timestampMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMs))
        return "${prefix}_$stamp.jpg"
    }

    fun formatDate(timestampMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMs))

    /** "12,4 MB" style size label for the Profile storage row. */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
