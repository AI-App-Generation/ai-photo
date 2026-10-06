package com.bblvn.composebaseproject.features.generating

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.BuildConfig
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.StateViewModel
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine
import com.bblvn.composebaseproject.data.domain.FailureReason
import com.bblvn.composebaseproject.data.domain.GenerationRequest
import com.bblvn.composebaseproject.data.domain.GenerationState
import kotlinx.coroutines.launch

/**
 * The four pipeline stages the user is told about. They are a *narration* of
 * [GenerationState], not a 1:1 mapping — polling is by far the longest phase, so it is split into
 * "AI is drawing" and "almost done" on elapsed time.
 */
enum class GeneratingStage(@param:StringRes val titleRes: Int) {
    UPLOADING(R.string.generating_stage_uploading),
    ANALYSING(R.string.generating_stage_analysing),
    DRAWING(R.string.generating_stage_drawing),
    FINISHING(R.string.generating_stage_finishing),
}

/** Localized title + hint for every [FailureReason]; the engine carries no user-facing text. */
@StringRes
fun FailureReason.titleRes(): Int = when (this) {
    FailureReason.NO_NETWORK -> R.string.generating_error_network_title
    FailureReason.UPLOAD_FAILED -> R.string.generating_error_upload_title
    FailureReason.SUBMIT_FAILED -> R.string.generating_error_submit_title
    FailureReason.TIMEOUT -> R.string.generating_error_timeout_title
    FailureReason.SERVICE_BUSY -> R.string.generating_error_busy_title
    FailureReason.NO_FACE_DETECTED -> R.string.generating_error_no_face_title
    FailureReason.STORAGE_FULL -> R.string.generating_error_storage_title
    FailureReason.INTERRUPTED -> R.string.generating_error_interrupted_title
    FailureReason.INSUFFICIENT_CREDITS -> R.string.generating_error_credits_title
    FailureReason.UNKNOWN -> R.string.generating_error_unknown_title
}

@StringRes
fun FailureReason.hintRes(): Int = when (this) {
    FailureReason.NO_NETWORK -> R.string.generating_error_network_hint
    FailureReason.UPLOAD_FAILED -> R.string.generating_error_upload_hint
    FailureReason.SUBMIT_FAILED -> R.string.generating_error_submit_hint
    FailureReason.TIMEOUT -> R.string.generating_error_timeout_hint
    FailureReason.SERVICE_BUSY -> R.string.generating_error_busy_hint
    FailureReason.NO_FACE_DETECTED -> R.string.generating_error_no_face_hint
    FailureReason.STORAGE_FULL -> R.string.generating_error_storage_hint
    FailureReason.INTERRUPTED -> R.string.generating_error_interrupted_hint
    FailureReason.INSUFFICIENT_CREDITS -> R.string.generating_error_credits_hint
    FailureReason.UNKNOWN -> R.string.generating_error_unknown_hint
}

/**
 * Everything the screen draws. [progress] is an **estimate** (see [GeneratingViewModel]); nothing
 * here is real server progress.
 */
data class GeneratingUiState(
    val stage: GeneratingStage = GeneratingStage.UPLOADING,
    val progress: Float = 0f,
    val sourcePhotoPath: String? = null,
    val failure: FailureReason? = null,
    /**
     * Raw engine detail (which upload step broke, the HTTP code, …). Shown on screen in debug
     * builds only — release users get the localized title + hint and nothing technical.
     */
    val failureDetail: String? = null,
    val canRetry: Boolean = true,
    /** Set exactly once when the engine reports Success; the screen then navigates to Result. */
    val successCreationId: String? = null,
) {
    val isFailed: Boolean get() = failure != null

    val percent: Int get() = (progress * 100f).toInt().coerceIn(0, 100)
}

/**
 * Observes [GenerationEngine] — it never re-drives it.
 *
 * The pipeline lives on the Application scope, so this ViewModel is a *reader*: it starts a run
 * only when there is none in flight ([onEnter]), and otherwise attaches to whatever is already
 * running (Risks #20). Cancellation and retry are forwarded to the engine, never reimplemented.
 */
class GeneratingViewModel : StateViewModel<GeneratingUiState>(GeneratingUiState()) {

    /** Guards against a second submit when [onEnter] re-runs (recomposition, config change). */
    private var entered = false

    /**
     * Called once per screen entry. Attaches to a running job, or starts one from the draft.
     * Collection only begins *after* this decision, so a stale terminal state from a previous run
     * can never be mistaken for this run's result.
     */
    fun onEnter() {
        if (entered) return
        entered = true

        val engineState = GenerationEngine.state.value
        if (!engineState.isRunning) {
            val request = buildRequest()
            when {
                request != null -> {
                    // Clear the previous terminal state first, otherwise Success/Failed from the
                    // last run would leak into this one.
                    GenerationEngine.reset()
                    GenerationEngine.start(request)
                }
                // No draft, but the engine finished while the user was away: just show the result.
                engineState is GenerationState.Success || engineState is GenerationState.Failed -> Unit
                // Nothing to attach to and nothing to start — the user arrived with an empty draft.
                else -> {
                    update { it.copy(failure = FailureReason.UNKNOWN, canRetry = false) }
                    return
                }
            }
        }

        update { it.copy(sourcePhotoPath = resolveSourcePhoto()) }
        viewModelScope.launch {
            GenerationEngine.state.collect(::render)
        }
    }

    /** "Thử lại" — same sources, fresh run. */
    fun onRetry() {
        val request = GenerationEngine.currentRequest ?: buildRequest() ?: return
        update {
            it.copy(
                failure = null,
                progress = 0f,
                stage = GeneratingStage.UPLOADING,
                sourcePhotoPath = it.sourcePhotoPath ?: request.imagePaths.firstOrNull(),
            )
        }
        GenerationEngine.reset()
        GenerationEngine.start(request)
    }

    /** Confirmed cancel: the engine kills the job and deletes its half-written row. */
    fun onCancelConfirmed() {
        GenerationEngine.cancel()
    }

    /** Leaving after a failure — drop the terminal state so re-entering starts clean. */
    fun onDismissFailure() {
        GenerationEngine.reset()
    }

    // ------------------------------------------------------------------ internals

    private fun buildRequest(): GenerationRequest? =
        GenerationDraft.current
            .takeIf { it.isComplete }
            ?.let { GenerationDraft.toRequest(size = GenerationEngine.resolveSize()) }

    /** Engine first: the draft may already have been cleared once the run took ownership. */
    private fun resolveSourcePhoto(): String? =
        GenerationEngine.currentRequest?.imagePaths?.firstOrNull()
            ?: GenerationDraft.current.effectivePaths.firstOrNull()

    private fun render(state: GenerationState) = when (state) {
        is GenerationState.Idle -> Unit // cancelled; the screen is already on its way out

        is GenerationState.Uploading -> update {
            it.copy(
                stage = GeneratingStage.UPLOADING,
                progress = uploadProgress(state.index, state.total),
                failure = null,
            )
        }

        is GenerationState.Submitting -> update {
            it.copy(stage = GeneratingStage.ANALYSING, progress = SUBMIT_PROGRESS, failure = null)
        }

        is GenerationState.Polling -> update {
            it.copy(
                stage = if (state.elapsedMs >= LATE_STAGE_MS) {
                    GeneratingStage.FINISHING
                } else {
                    GeneratingStage.DRAWING
                },
                progress = pollProgress(state.elapsedMs),
                failure = null,
            )
        }

        is GenerationState.Downloading -> update {
            it.copy(stage = GeneratingStage.FINISHING, progress = DOWNLOAD_PROGRESS, failure = null)
        }

        is GenerationState.Success -> update {
            it.copy(
                stage = GeneratingStage.FINISHING,
                progress = 1f,
                failure = null,
                successCreationId = state.creationId,
            )
        }

        is GenerationState.Failed -> update {
            it.copy(
                failure = state.reason,
                failureDetail = state.detail?.takeIf { _ -> BuildConfig.DEBUG },
                // Hết credit thì thử lại cũng vẫn hết; chỉ còn cách quay lại.
                canRetry = state.reason != FailureReason.INSUFFICIENT_CREDITS,
            )
        }
    }

    private companion object {
        /** Uploading owns the first slice of the bar. */
        const val UPLOAD_CEILING = 0.12f
        const val SUBMIT_PROGRESS = 0.15f
        const val POLL_FLOOR = 0.18f

        /** The honest fiction: 0 -> 90% over ~40s of polling, then the real result jumps it to 100%. */
        const val POLL_CEILING = 0.90f
        const val POLL_SPAN_MS = 40_000f
        const val DOWNLOAD_PROGRESS = 0.95f

        /** Polling past this point narrates "almost done" instead of "AI is drawing". */
        const val LATE_STAGE_MS = 28_000L

        fun uploadProgress(index: Int, total: Int): Float {
            if (total <= 0) return UPLOAD_CEILING
            return UPLOAD_CEILING * ((index + 1).toFloat() / total)
        }

        fun pollProgress(elapsedMs: Long): Float {
            val ratio = (elapsedMs / POLL_SPAN_MS).coerceIn(0f, 1f)
            return POLL_FLOOR + (POLL_CEILING - POLL_FLOOR) * ratio
        }
    }
}
