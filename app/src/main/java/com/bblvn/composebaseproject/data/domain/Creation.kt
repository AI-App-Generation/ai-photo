package com.bblvn.composebaseproject.data.domain

/**
 * One row of local history. Mirrors the `creations` Room table
 * ([com.bblvn.composebaseproject.data.database.entity.CreationEntity]).
 *
 * Invariant: [resultImagePath] is always a LOCAL file path. Newport result URLs expire in ~1 day
 * (Risks #3), so [resultRemoteUrl] is kept for debugging only and must never be rendered.
 */
data class Creation(
    val id: String,
    val type: CreationType,
    /** Template id, or [ToolType.name]. */
    val sourceId: String,
    /** Display name: template name, or the tool's localized name captured at creation time. */
    val sourceName: String,
    /** Local source file paths, in Seedream `image[]` order. */
    val sourceImagePaths: List<String>,
    val resultImagePath: String?,
    val resultRemoteUrl: String?,
    val status: CreationStatus,
    val errorMessage: String?,
    /** Newport task id — kept so an interrupted PROCESSING row can be resumed on next launch. */
    val taskId: String?,
    val savedToGallery: Boolean,
    val createdAt: Long,
    /** Verbatim prompt used, so "Regenerate" can re-submit without re-reading the catalog. */
    val prompt: String,
    /** Seedream `size` used for this run ("1K" or "1152x2048"). */
    val size: String,
) {
    /** True when the original photos still exist and "Regenerate" can be offered (R6.2). */
    val canRegenerate: Boolean
        get() = prompt.isNotBlank() && sourceImagePaths.isNotEmpty()
}

enum class CreationType { TEMPLATE, TOOL }

enum class CreationStatus {
    PROCESSING,
    SUCCESS,
    FAILED;

    companion object {
        fun fromName(value: String?): CreationStatus =
            entries.firstOrNull { it.name == value } ?: FAILED
    }
}

/** Filter chips on the Creations screen. */
enum class CreationFilter { ALL, TEMPLATE, TOOL, SAVED }
