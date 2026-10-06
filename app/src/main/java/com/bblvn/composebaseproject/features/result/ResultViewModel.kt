package com.bblvn.composebaseproject.features.result

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.core.StateViewModel
import com.bblvn.composebaseproject.core.UiAction
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine
import com.bblvn.composebaseproject.corefeatures.premium.PremiumManager
import com.bblvn.composebaseproject.data.database.repo.CreationRepo
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Result — the payoff screen.
 *
 * State the screen renders. Everything is derived from one [Creation] row: even right after a
 * generation finishes, the row is already written (GenerationEngine persists before it reports
 * SUCCESS), so replay and live mode read the exact same source of truth and only differ in
 * whether the reveal animation plays.
 */
data class ResultUiState(
    val isLoading: Boolean = true,
    val creationId: String? = null,
    /** Local result file. Null means the creation is gone or its file was pruned. */
    val resultPath: String? = null,
    /**
     * Local source photo used for the before/after slider, or null when the file no longer
     * exists (cache cleared / pruned on an old creation). The screen then shows the result alone.
     */
    val sourcePath: String? = null,
    val title: String = "",
    val isPremium: Boolean = false,
    /** True when opened from Creations: no reveal animation (R6.2). */
    val replay: Boolean = false,
    val savedToGallery: Boolean = false,
    val isBusy: Boolean = false,
    /** False when the source photos are gone — "Tạo lại" is disabled rather than crashing. */
    val canRegenerate: Boolean = false,
    /**
     * Bumped when a save failed for want of WRITE_EXTERNAL_STORAGE on API 24–28. The screen
     * launches the system permission request and calls [save] again once (C6).
     */
    val galleryPermissionToken: Int = 0,
) {
    /** Both layers present -> the compare slider; otherwise the result alone. */
    val canCompare: Boolean get() = resultPath != null && sourcePath != null

    /** The reveal wipe only plays for an image the user has just waited for. */
    val playReveal: Boolean get() = !replay && resultPath != null

    val showWatermarkNotice: Boolean get() = !isPremium && resultPath != null

    val isMissing: Boolean get() = !isLoading && resultPath == null
}

class ResultViewModel : StateViewModel<ResultUiState>(ResultUiState()) {

    private var bound = false
    private var creation: Creation? = null

    /**
     * Called once from the screen. [creationId] comes from the nav argument; when it is null the
     * screen was opened straight off a finished run, so the engine knows which row that was.
     */
    fun bind(creationId: String?, replay: Boolean) {
        if (bound) return
        bound = true
        val id = creationId ?: GenerationEngine.currentCreationId
        update { it.copy(replay = replay, creationId = id) }
        if (id == null) {
            update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            combine(CreationRepo.creations, PremiumManager.isPremium) { rows, premium ->
                rows.firstOrNull { it.id == id } to premium
            }.collect { (row, premium) -> reduce(id, row, premium) }
        }
    }

    private suspend fun reduce(id: String, row: Creation?, premium: Boolean) {
        creation = row
        if (row == null || row.status != CreationStatus.SUCCESS) {
            // The row was deleted (or never succeeded): fall back to the file on disk so a
            // just-finished run is still shown even if the DB write is a beat behind.
            val fallback = FileManager.resultFile(id).takeIf { existsOnDisk(it) }
            update {
                it.copy(
                    isLoading = false,
                    resultPath = fallback?.absolutePath,
                    sourcePath = null,
                    isPremium = premium,
                    canRegenerate = false,
                )
            }
            return
        }

        val result = row.resultImagePath?.let { File(it) }?.takeIf { existsOnDisk(it) }
        val source = resolveSource(row)
        update {
            it.copy(
                isLoading = false,
                creationId = id,
                resultPath = result?.absolutePath,
                sourcePath = source?.absolutePath,
                title = row.sourceName,
                isPremium = premium,
                savedToGallery = row.savedToGallery,
                // R6.2 — an old creation can have lost its sources; regenerating would then fail
                // at upload, so the action is disabled instead.
                canRegenerate = row.canRegenerate && source != null,
            )
        }
    }

    /**
     * The photo actually fed to the model, in slot order. The adjusted (`_adj`) variant is what
     * was uploaded when the user cropped, so prefer it and fall back to the untouched original.
     */
    private suspend fun resolveSource(row: Creation): File? = withContext(Dispatchers.IO) {
        row.sourceImagePaths.firstNotNullOfOrNull { path ->
            val adjusted = FileManager.adjustedSiblingOf(path)
            when {
                adjusted.exists() && adjusted.length() > 0L -> adjusted
                else -> File(path).takeIf { it.exists() && it.length() > 0L }
            }
        }
    }

    private suspend fun existsOnDisk(file: File): Boolean =
        withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }

    // ---------------------------------------------------------------- actions

    /**
     * "Lưu" — always through [FileManager.exportForUser] so the free-user watermark is applied
     * in exactly one place (R6.7). [afterPermissionGrant] marks the single retry that follows a
     * permission request, so a still-missing permission cannot loop.
     */
    fun save(afterPermissionGrant: Boolean = false, noWatermark: Boolean = false) {
        // Lần thử lại sau khi cấp quyền giữ nguyên lựa chọn ban đầu của lần bấm.
        if (!afterPermissionGrant) skipWatermark = noWatermark
        val path = currentState.resultPath ?: return
        val id = currentState.creationId
        if (currentState.isBusy) return
        update { it.copy(isBusy = true) }
        viewModelScope.launch {
            FileManager.exportForUser(
                source = File(path),
                displayName = DateUtils.exportFileName(),
                // skipWatermark: user xem quảng cáo thưởng để lưu ảnh không watermark (một lần).
                watermark = PremiumManager.shouldWatermarkExports() && !skipWatermark,
            ).onSuccess {
                if (id != null) CreationRepo.markSaved(id)
                update { it.copy(isBusy = false, savedToGallery = true) }
                toast(UiText.Res(R.string.result_toast_saved))
            }.onFailure { error ->
                update { it.copy(isBusy = false) }
                handleExportFailure(error, afterPermissionGrant)
            }
        }
    }

    /**
     * Overflow "Lưu ảnh gốc" — the user's own untouched photo. It is not generated content, so it
     * is exported WITHOUT a watermark (watermarking someone's own camera roll photo would be a
     * bug); it still goes through [FileManager.exportForUser] so the MediaStore/legacy branch and
     * the IOException handling stay in one place.
     */
    /** Lần Save đang chạy có được bỏ watermark nhờ xem quảng cáo thưởng không. */
    private var skipWatermark = false

    fun saveSource() {
        val path = currentState.sourcePath ?: return
        if (currentState.isBusy) return
        update { it.copy(isBusy = true) }
        viewModelScope.launch {
            FileManager.exportForUser(
                source = File(path),
                displayName = DateUtils.exportFileName(prefix = "AI_Photo_Source"),
                watermark = false,
            ).onSuccess {
                update { it.copy(isBusy = false) }
                toast(UiText.Res(R.string.result_toast_source_saved))
            }.onFailure { error ->
                update { it.copy(isBusy = false) }
                handleExportFailure(error, afterPermissionGrant = true)
            }
        }
    }

    private fun handleExportFailure(error: Throwable, afterPermissionGrant: Boolean) {
        when {
            // API 24–28 only: ask for WRITE_EXTERNAL_STORAGE, then retry exactly once. Without
            // this, "Lưu" silently does nothing on older devices.
            error is FileManager.MissingGalleryPermissionException && !afterPermissionGrant ->
                update { it.copy(galleryPermissionToken = it.galleryPermissionToken + 1) }

            error is FileManager.MissingGalleryPermissionException ->
                toast(UiText.Res(R.string.result_toast_permission_denied))

            // A full device is a real case (Risks #25) — say so instead of failing silently.
            error is IOException -> toast(UiText.Res(R.string.result_toast_storage_full))

            else -> {
                Log.w(TAG, "export failed", error)
                toast(UiText.Res(R.string.result_toast_save_failed))
            }
        }
    }

    /** Share goes out as a [UiAction]; IntentUtils applies the same watermark rule internally. */
    fun share() {
        val path = currentState.resultPath ?: return
        emit(UiEvent.RunAction(UiAction.ShareImage(path)))
    }

    fun onShareFailed() = toast(UiText.Res(R.string.result_toast_share_failed))

    fun onGalleryPermissionDenied() = toast(UiText.Res(R.string.result_toast_permission_denied))

    /**
     * "Tạo lại" — the engine re-submits with `seed = -1`, so the new image WILL differ. The screen
     * confirms that first (Risks #24) and only calls this on confirm.
     *
     * Returns true when a run actually started, which is the screen's cue to navigate to
     * Generating. Navigation stays in the screen (convention 4), so this is a plain return value
     * rather than a new [UiEvent] subtype.
     */
    fun regenerate(): Boolean {
        val row = creation
        if (row == null || !currentState.canRegenerate) {
            toast(UiText.Res(R.string.result_toast_regenerate_unavailable))
            return false
        }
        if (!AppCapability.canAffordGeneration()) {
            toast(UiText.Res(R.string.credits_not_enough))
            return false
        }
        if (GenerationEngine.regenerate(row)) return true
        // Exactly one run at a time (R6.3).
        toast(UiText.Res(R.string.result_toast_busy))
        return false
    }

    /** No reporting backend yet — acknowledge the user and log the id for the next phase. */
    fun reportBadImage() {
        Log.i(TAG, "bad image reported: creationId=${currentState.creationId}")
        toast(UiText.Res(R.string.result_toast_reported))
    }

    /** Deletes the row, its result and its sources; the screen leaves right after. */
    fun deleteCreation() {
        val id = currentState.creationId ?: return
        GenerationEngine.deleteCreation(id)
        emit(UiEvent.NavigateBack)
    }

    fun onWatermarkNoticeClick() = emit(UiEvent.OpenPaywall)

    private companion object {
        const val TAG = "ResultScreen"
    }
}
