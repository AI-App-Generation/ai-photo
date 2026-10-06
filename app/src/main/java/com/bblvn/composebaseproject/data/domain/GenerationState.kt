package com.bblvn.composebaseproject.data.domain

/**
 * Progress of the single in-flight generation, published by `GenerationEngine.state`.
 *
 * The Generating screen derives its 4 stage titles and its *estimated* progress bar from this —
 * Newport returns no real percentage (see UI spec section 9).
 */
sealed interface GenerationState {

    data object Idle : GenerationState

    /** Uploading source photo [index] (0-based) of [total]. */
    data class Uploading(val index: Int, val total: Int) : GenerationState

    data object Submitting : GenerationState

    /** Polling `getAsyncResult`. [elapsedMs] drives the estimated progress bar. */
    data class Polling(val elapsedMs: Long) : GenerationState

    /** Downloading the result to local storage before it expires (Risks #3). */
    data object Downloading : GenerationState

    data class Success(val creationId: String, val resultPath: String) : GenerationState

    /**
     * [reason] is a user-safe key; never a raw API message. Newport key errors (13005/13006/13007)
     * collapse to [FailureReason.SERVICE_BUSY] and only the detail is logged.
     */
    data class Failed(
        val creationId: String?,
        val reason: FailureReason,
        val detail: String? = null,
    ) : GenerationState

    val isRunning: Boolean
        get() = this is Uploading || this is Submitting || this is Polling || this is Downloading
}

/**
 * User-facing failure buckets. The UI maps each to a localized title + hint; the engine never
 * passes raw server text to the screen.
 */
enum class FailureReason {
    NO_NETWORK,
    UPLOAD_FAILED,
    SUBMIT_FAILED,
    TIMEOUT,
    SERVICE_BUSY,
    NO_FACE_DETECTED,
    STORAGE_FULL,
    INTERRUPTED,

    /** Không đủ credit cho lượt tạo ảnh (xem AppCapability). Không có gì để thử lại ngay. */
    INSUFFICIENT_CREDITS,
    UNKNOWN,
}
