package com.bblvn.composebaseproject.corefeatures.generation

import android.graphics.BitmapFactory
import android.util.Log
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.premium.PremiumManager
import com.bblvn.composebaseproject.data.api.ApiException
import com.bblvn.composebaseproject.data.api.ApiRepo
import com.bblvn.composebaseproject.data.database.repo.CreationRepo
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.data.domain.FailureReason
import com.bblvn.composebaseproject.data.domain.GenerationRequest
import com.bblvn.composebaseproject.data.domain.GenerationState
import com.bblvn.composebaseproject.utils.DateUtils
import com.bblvn.composebaseproject.utils.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The heart of the app: one pipeline shared by templates and tools.
 *
 *     compress -> upload (x1 or x2) -> submit -> poll -> download -> persist
 *
 * Design rules that the UI depends on:
 *  - Runs on an **Application-scoped** [CoroutineScope], never `viewModelScope`. Leaving the
 *    Generating screen, rotating, or backgrounding the app must not restart or kill the job
 *    (Risks #20).
 *  - **Exactly one** generation at a time. [start] returns false while another is running; the
 *    caller should route the user back to the Generating screen instead (R6.3).
 *  - Cancellable at any point; a cancelled run deletes its own Creation row so history has no
 *    junk in it.
 *  - The result image is downloaded to local storage **before** the row is marked SUCCESS,
 *    because the Newport URL dies in ~1 day (Risks #3).
 */
object GenerationEngine {

    private const val TAG = "GenerationEngine"
    private const val POLL_INTERVAL_MS = 1_500L
    private const val POLL_TIMEOUT_MS = 180_000L

    private lateinit var scope: CoroutineScope
    private var job: Job? = null

    private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val state: StateFlow<GenerationState> = _state.asStateFlow()

    /** App-wide flag every "Generate" CTA reads to disable itself (R6.3). */
    lateinit var isGenerating: StateFlow<Boolean>
        private set

    /** The run currently on screen, so Generating can show the right source photo. */
    @Volatile
    var currentRequest: GenerationRequest? = null
        private set

    @Volatile
    var currentCreationId: String? = null
        private set

    fun init(appScope: CoroutineScope) {
        if (::scope.isInitialized) return
        scope = appScope
        isGenerating = _state
            .map { it.isRunning }
            .stateIn(appScope, SharingStarted.Eagerly, false)
        appScope.launch { resumeInterrupted() }
    }

    // ---------------------------------------------------------------- public API

    /** Starts a run. Returns false when one is already in flight. */
    fun start(request: GenerationRequest): Boolean {
        if (job?.isActive == true) return false
        currentRequest = request
        _state.value = GenerationState.Uploading(0, request.imagePaths.size)
        job = scope.launch { execute(request) }
        return true
    }

    /** "Regenerate": same sources and prompt, new random seed (seed = -1) so output differs. */
    fun regenerate(creation: Creation): Boolean {
        if (!creation.canRegenerate) return false
        return start(
            GenerationRequest(
                prompt = creation.prompt,
                imagePaths = creation.sourceImagePaths,
                size = creation.size,
                type = creation.type,
                sourceId = creation.sourceId,
                sourceLabel = creation.sourceName,
            )
        )
    }

    /**
     * Cancel and delete the row. Called from the "Cancel generation?" confirmation on the
     * Generating screen.
     */
    fun cancel() {
        val creationId = currentCreationId
        job?.cancel()
        job = null
        _state.value = GenerationState.Idle
        currentRequest = null
        currentCreationId = null
        if (creationId != null) {
            scope.launch { CreationRepo.delete(creationId) }
        }
    }

    /** Deleting from history must cancel a running job first (Risks #27). */
    fun deleteCreation(creationId: String) {
        if (currentCreationId == creationId) cancel()
        scope.launch { CreationRepo.delete(creationId) }
    }

    /** Clears a terminal state so the Generating screen can be re-entered cleanly. */
    fun reset() {
        if (_state.value.isRunning) return
        _state.value = GenerationState.Idle
        currentRequest = null
        currentCreationId = null
    }

    /**
     * Seedream `size` for a new run: "1K" normally, the max-9:16-at-1K-rate exact size when the
     * user enabled High quality AND is premium (R6.8). Never "2K".
     */
    fun resolveSize(): String =
        if (SettingManager.highQuality.value && PremiumManager.canUseHighQuality()) {
            GenerationRequest.SIZE_HIGH
        } else {
            GenerationRequest.SIZE_1K
        }

    // ---------------------------------------------------------------- pipeline

    private suspend fun execute(request: GenerationRequest) {
        val creationId = request.existingCreationId ?: UUID.randomUUID().toString()
        currentCreationId = creationId
        try {
            // ---- 1. upload every source photo, in slot order ----
            val urls = mutableListOf<String>()
            request.imagePaths.forEachIndexed { index, path ->
                _state.value = GenerationState.Uploading(index, request.imagePaths.size)
                val prepared = prepareUpload(File(path), creationId, index)
                val url = ApiRepo.uploadImage(prepared).getOrElse { error ->
                    fail(creationId, request, classify(error, FailureReason.UPLOAD_FAILED), error)
                    return
                }
                urls += url
            }

            // ---- 2. submit ----
            _state.value = GenerationState.Submitting
            val taskId = ApiRepo.submitSeedream(request.prompt, urls, request.size)
                .getOrElse { error ->
                    fail(creationId, request, classify(error, FailureReason.SUBMIT_FAILED), error)
                    return
                }

            // Persist NOW, with the taskId, so the run survives a process kill (Risks #7) and the
            // Creations grid shows a "processing" card immediately.
            upsertProcessing(creationId, request, taskId)

            // ---- 3. poll ----
            val imageUrl = poll(taskId)
            if (imageUrl == null) {
                fail(creationId, request, FailureReason.TIMEOUT, null)
                return
            }

            // ---- 4. download before the URL expires, then persist ----
            _state.value = GenerationState.Downloading
            finish(creationId, request, taskId, imageUrl)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            fail(creationId, request, classify(t, FailureReason.UNKNOWN), t)
        }
    }

    /**
     * Downsample + recompress so we never push a 12MP original over the wire (Risks #8).
     *
     * The result is verified to still decode as an image before it goes out. OSS's callback to
     * Newport reads `imageInfo.width/height/format` from the stored object; if the bytes are not a
     * readable image that callback fails, OSS answers 203, and the file is never registered —
     * which surfaces much later as an empty URL from `policy_upload_finish`, pointing at entirely
     * the wrong step. Catching it here names the real problem.
     */
    private suspend fun prepareUpload(source: File, creationId: String, index: Int): File {
        val target = FileManager.cacheFile("upload_${creationId}_$index.jpg")
        val prepared = ImageUtils.prepareForUpload(source, target).getOrDefault(source)
        if (isReadableImage(prepared)) return prepared

        Log.w(TAG, "prepared upload is not a readable image: ${prepared.path}; using the original")
        if (prepared != source && isReadableImage(source)) return source
        throw IOException("Source photo is not a readable image: ${source.path}")
    }

    private fun isReadableImage(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    /** Returns the result url, or null on timeout. Throws on a hard API failure. */
    private suspend fun poll(taskId: String): String? {
        val startedAt = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed > POLL_TIMEOUT_MS) return null // Risks #4: never poll forever
            _state.value = GenerationState.Polling(elapsed)

            val result = ApiRepo.getAsyncResult(taskId)
            val data = result.getOrNull()
            if (data == null) {
                // A single transient network hiccup should not kill a task that is still running,
                // but a real API error (bad key, rejected task) must surface immediately.
                val error = result.exceptionOrNull()
                if (error is ApiException && error.code != ApiException.CODE_TRANSPORT) throw error
                Log.w(TAG, "poll hiccup, retrying", error)
                delay(POLL_INTERVAL_MS)
                continue
            }
            val url = data.imageUrl
            if (url != null) return url
            if (data.isFailed) {
                throw ApiException(
                    ApiException.CODE_TRANSPORT,
                    data.task.reason.ifBlank { "Task failed with status ${data.task.status}" },
                )
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun finish(
        creationId: String,
        request: GenerationRequest,
        taskId: String,
        imageUrl: String,
    ) {
        val target = FileManager.resultFile(creationId)
        val file = ApiRepo.downloadTo(imageUrl, target).getOrElse { error ->
            val reason = when {
                error is ApiException -> classify(error, FailureReason.UNKNOWN)
                error is IOException -> FailureReason.STORAGE_FULL
                else -> FailureReason.UNKNOWN
            }
            fail(creationId, request, reason, error)
            return
        }

        CreationRepo.mutate(creationId) {
            it.copy(
                status = CreationStatus.SUCCESS,
                resultImagePath = file.absolutePath,
                resultRemoteUrl = imageUrl,
                taskId = taskId,
                errorMessage = null,
            )
        }

        if (SettingManager.autoSave.value) {
            // Goes through exportForUser, not exportToGallery, so auto-saved images obey the same
            // free/PRO watermark rule as the Save button (R6.7). A missing gallery permission on
            // API 24-28 just leaves the creation unsaved; the user can still save from Result.
            FileManager.exportForUser(
                source = file,
                displayName = DateUtils.exportFileName(),
                watermark = PremiumManager.shouldWatermarkExports(),
            ).onSuccess { CreationRepo.markSaved(creationId) }
        }

        FileManager.delete(FileManager.cacheFile("upload_${creationId}_0.jpg").absolutePath)
        FileManager.delete(FileManager.cacheFile("upload_${creationId}_1.jpg").absolutePath)

        currentCreationId = creationId
        _state.value = GenerationState.Success(creationId, file.absolutePath)
        job = null
    }

    private suspend fun upsertProcessing(
        creationId: String,
        request: GenerationRequest,
        taskId: String,
    ) {
        val existing = CreationRepo.get(creationId)
        if (existing != null) {
            CreationRepo.update(
                existing.copy(
                    status = CreationStatus.PROCESSING,
                    taskId = taskId,
                    errorMessage = null,
                )
            )
            return
        }
        CreationRepo.insert(
            Creation(
                id = creationId,
                type = request.type,
                sourceId = request.sourceId,
                sourceName = request.sourceLabel,
                sourceImagePaths = request.imagePaths,
                resultImagePath = null,
                resultRemoteUrl = null,
                status = CreationStatus.PROCESSING,
                errorMessage = null,
                taskId = taskId,
                savedToGallery = false,
                createdAt = System.currentTimeMillis(),
                prompt = request.prompt,
                size = request.size,
            )
        )
    }

    private suspend fun fail(
        creationId: String,
        request: GenerationRequest,
        reason: FailureReason,
        error: Throwable?,
    ) {
        Log.w(TAG, "generation failed: $reason", error)
        val rowExists = CreationRepo.get(creationId) != null
        if (rowExists) {
            CreationRepo.mutate(creationId) {
                it.copy(status = CreationStatus.FAILED, errorMessage = reason.name)
            }
        } else {
            // Failed before submit: still record it so the user sees why in history.
            CreationRepo.insert(
                Creation(
                    id = creationId,
                    type = request.type,
                    sourceId = request.sourceId,
                    sourceName = request.sourceLabel,
                    sourceImagePaths = request.imagePaths,
                    resultImagePath = null,
                    resultRemoteUrl = null,
                    status = CreationStatus.FAILED,
                    errorMessage = reason.name,
                    taskId = null,
                    savedToGallery = false,
                    createdAt = System.currentTimeMillis(),
                    prompt = request.prompt,
                    size = request.size,
                )
            )
        }
        _state.value = GenerationState.Failed(creationId, reason, error?.message)
        job = null
    }

    /**
     * Maps a throwable to a user-safe bucket. Newport key errors are configuration bugs — the user
     * only ever sees "service busy" while the real code goes to the log (spec section 4).
     */
    private fun classify(error: Throwable, fallback: FailureReason): FailureReason = when {
        error is ApiException && error.isKeyProblem -> {
            Log.e(TAG, "NEWPORT KEY PROBLEM code=${error.code}: ${error.message}")
            FailureReason.SERVICE_BUSY
        }

        error is ApiException && error.code == ApiException.CODE_NON_HTTP_IMAGE ->
            FailureReason.UPLOAD_FAILED

        error is java.net.UnknownHostException -> FailureReason.NO_NETWORK
        error is java.net.SocketTimeoutException -> FailureReason.NO_NETWORK

        // UploadStepException extends IOException, so it must be handled BEFORE the catch-all
        // below — otherwise a perfectly healthy connection that simply got a bad answer is
        // reported to the user as "no network", which sends them chasing the wrong problem.
        error is com.bblvn.composebaseproject.data.api.UploadStepException -> fallback

        error is IOException -> FailureReason.NO_NETWORK
        else -> fallback
    }

    // ---------------------------------------------------------------- resume after process death

    /**
     * On launch, any row still marked PROCESSING belongs to a run the OS killed. Poll its stored
     * taskId once: if the task finished while we were away we keep the image, otherwise the row is
     * marked FAILED/INTERRUPTED rather than spinning forever (Risks #7).
     */
    private suspend fun resumeInterrupted() {
        val pending = runCatching { CreationRepo.processing() }.getOrDefault(emptyList())
        pending.forEach { creation ->
            val taskId = creation.taskId
            if (taskId.isNullOrBlank()) {
                CreationRepo.markInterrupted(creation.id, FailureReason.INTERRUPTED.name)
                return@forEach
            }
            val data = ApiRepo.getAsyncResult(taskId).getOrNull()
            val url = data?.imageUrl
            if (url.isNullOrBlank()) {
                CreationRepo.markInterrupted(creation.id, FailureReason.INTERRUPTED.name)
                return@forEach
            }
            val target = FileManager.resultFile(creation.id)
            ApiRepo.downloadTo(url, target)
                .onSuccess { file ->
                    CreationRepo.mutate(creation.id) {
                        it.copy(
                            status = CreationStatus.SUCCESS,
                            resultImagePath = file.absolutePath,
                            resultRemoteUrl = url,
                        )
                    }
                }
                .onFailure {
                    CreationRepo.markInterrupted(creation.id, FailureReason.INTERRUPTED.name)
                }
        }
    }
}
