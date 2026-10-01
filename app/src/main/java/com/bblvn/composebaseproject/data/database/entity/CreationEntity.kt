package com.bblvn.composebaseproject.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.data.domain.CreationType

/**
 * The single `creations` table.
 *
 * [sourceImagePaths] is stored as a newline-separated string rather than with a TypeConverter —
 * paths never contain a newline, and it keeps the schema readable with no converter class.
 */
@Entity(tableName = "creations")
data class CreationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val sourceId: String,
    val sourceName: String,
    val sourceImagePaths: String,
    val resultImagePath: String? = null,
    val resultRemoteUrl: String? = null,
    val status: String,
    val errorMessage: String? = null,
    val taskId: String? = null,
    @ColumnInfo(defaultValue = "0") val savedToGallery: Int = 0,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "") val prompt: String = "",
    @ColumnInfo(defaultValue = "1K") val size: String = "1K",
) {
    fun toDomain(): Creation = Creation(
        id = id,
        type = if (type == CreationType.TOOL.name) CreationType.TOOL else CreationType.TEMPLATE,
        sourceId = sourceId,
        sourceName = sourceName,
        sourceImagePaths = sourceImagePaths.split(SEPARATOR).filter { it.isNotBlank() },
        resultImagePath = resultImagePath,
        resultRemoteUrl = resultRemoteUrl,
        status = CreationStatus.fromName(status),
        errorMessage = errorMessage,
        taskId = taskId,
        savedToGallery = savedToGallery == 1,
        createdAt = createdAt,
        prompt = prompt,
        size = size,
    )

    companion object {
        private const val SEPARATOR = "\n"

        fun fromDomain(creation: Creation): CreationEntity = CreationEntity(
            id = creation.id,
            type = creation.type.name,
            sourceId = creation.sourceId,
            sourceName = creation.sourceName,
            sourceImagePaths = creation.sourceImagePaths.joinToString(SEPARATOR),
            resultImagePath = creation.resultImagePath,
            resultRemoteUrl = creation.resultRemoteUrl,
            status = creation.status.name,
            errorMessage = creation.errorMessage,
            taskId = creation.taskId,
            savedToGallery = if (creation.savedToGallery) 1 else 0,
            createdAt = creation.createdAt,
            prompt = creation.prompt,
            size = creation.size,
        )
    }
}
