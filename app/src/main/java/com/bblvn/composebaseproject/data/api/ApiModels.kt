package com.bblvn.composebaseproject.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Newport request/response shapes. See instructions/api_specs/AI-Photo-API.md.
 *
 * Everything is optional with a default because the API returns different subsets per endpoint and
 * the parser runs with `ignoreUnknownKeys = true`.
 */

@Serializable
data class NewportEnvelope<T>(
    val code: Int = -1,
    val message: String = "",
    val data: T? = null,
) {
    /** Success is `0` OR `200` — never check `== 0` only (Risks #5). */
    val isOk: Boolean get() = code == 0 || code == 200
}

// ---------------------------------------------------------------- upload

@Serializable
data class UploadPolicyRequest(val scene: String = "Dream-CN")

@Serializable
data class UploadPolicy(
    val policy: String = "",
    val signature: String = "",
    @SerialName("OSSAccessKeyId") val ossAccessKeyId: String = "",
    val key: String = "",
    val host: String = "",
    val callback: String = "",
    val expire: String = "",
    val reqId: String = "",
) {
    /**
     * Public URL of the object this policy uploads to.
     *
     * `policy_upload_finish` is supposed to hand back this URL, but it answers `code: 0, "ok"`
     * with no `url` for some callers while returning it correctly for others — same key, same
     * reqId, same moment. The object itself is publicly readable at `host/key`, so building the
     * URL here removes that unreliable round-trip from the critical path entirely.
     */
    val objectUrl: String
        get() = if (host.isBlank() || key.isBlank()) "" else "${host.trimEnd('/')}/$key"
}

@Serializable
data class OssUploadData(
    val verifyStatus: Boolean = false,
    val reqId: String = "",
)

@Serializable
data class FinishUploadRequest(val reqId: String)

@Serializable
data class FinishUploadData(
    val url: String = "",
    val expireAt: String = "",
    val reqId: String = "",
)

// ---------------------------------------------------------------- generate

@Serializable
data class SeedreamRequest(
    val model: String,
    val prompt: String,
    val seed: Int = -1,
    val size: String,
    val image: List<String>,
)

@Serializable
data class SeedreamSubmitData(val taskId: String = "")

@Serializable
data class AsyncResultRequest(val taskId: String)

@Serializable
data class AsyncTask(
    val taskId: String = "",
    /** `< 0`, `4`, or `3` with no images means failure. */
    val status: Int = 0,
    val reason: String = "",
    val taskType: String = "",
)

@Serializable
data class AsyncImage(
    val imageUrl: String = "",
    val imageType: String = "",
)

@Serializable
data class AsyncResultData(
    val task: AsyncTask = AsyncTask(),
    val images: List<AsyncImage> = emptyList(),
) {
    val imageUrl: String? get() = images.firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() }

    val isFailed: Boolean
        get() = task.status < 0 || task.status == 4 || (task.status == 3 && imageUrl == null)
}

/**
 * A typed API failure. [code] is the Newport code when the call reached the server.
 * Key problems (13005 blank / 13006 invalid / 13007 wrong prefix) are configuration bugs, not
 * user errors — surface a generic "service busy" message and log the detail (spec section 4).
 */
class ApiException(
    val code: Int,
    override val message: String,
) : Exception(message) {

    val isKeyProblem: Boolean get() = code in 13005..13007

    companion object {
        const val CODE_NON_HTTP_IMAGE = 10192
        const val CODE_TRANSPORT = -1
    }
}
