package com.bblvn.composebaseproject.data.api

import android.util.Log
import com.bblvn.composebaseproject.core.json.JsonLoader
import com.bblvn.composebaseproject.data.domain.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Every network call the app makes. All suspend, all on [Dispatchers.IO], all returning
 * [Result] — callers never see an exception escape.
 */
object ApiRepo {

    private const val TAG = "ApiRepo"

    /** OSS answers 203 when the object was stored but its callback to Newport failed. */
    private const val HTTP_CALLBACK_FAILED = 203


    // ---------------------------------------------------------------- upload (3 steps)

    /** Step 1: ask Newport for a pre-signed OSS policy. */
    suspend fun getUploadPolicy(): Result<UploadPolicy> = io {
        val body = ApiClient.encode(UploadPolicyRequest())
        call<UploadPolicy>(ApiClient.jsonPost(ApiClient.PATH_GET_POLICY, body))
    }

    /**
     * Step 2: push the bytes straight to Aliyun OSS.
     *
     * Two rules that are easy to get wrong and produce cryptic OSS errors (Risks #2):
     *  - NO `Authorization` header (handled by [ApiClient]'s host check).
     *  - `file` MUST be the LAST part of the multipart body.
     */
    suspend fun uploadToOss(policy: UploadPolicy, file: File): Result<String> = io {
        require(policy.host.isNotBlank()) { "Upload policy has no host" }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("success_action_status", "200")
            .addFormDataPart("policy", policy.policy)
            .addFormDataPart("OSSAccessKeyId", policy.ossAccessKeyId)
            .addFormDataPart("signature", policy.signature)
            .addFormDataPart("key", policy.key)
            .addFormDataPart("callback", policy.callback)
            // ---- `file` LAST. Do not move this line. ----
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder().url(policy.host).post(multipart).build()
        ApiClient.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            Log.i(TAG, "oss_put http=${response.code} body=${text.take(200)}")

            if (!response.isSuccessful) {
                throw ApiException(response.code, "OSS upload failed: $text")
            }

            // HTTP 203 means OSS stored the object but the callback to Newport FAILED. OkHttp's
            // isSuccessful covers 200..299, so treating 2xx as success here would report a good
            // upload and then blow up at upload_finish, because Newport never learned the file
            // exists. Fail loudly at the real point of failure instead.
            if (response.code == HTTP_CALLBACK_FAILED) {
                throw ApiException(
                    response.code,
                    "OSS stored the file but the Newport callback failed: $text"
                )
            }

            // The policy always carries a callback, and Aliyun's docs are explicit that a
            // configured callback's JSON response REPLACES the normal PostObject body. So a blank
            // body here does not mean "success_action_status short-circuited the callback" (the
            // assumption this code used to make) — it means the callback never ran, therefore
            // Newport has no record of the file, therefore upload_finish will return no url.
            // Fail here, where the information still exists, instead of three steps later.
            if (policy.callback.isNotBlank() && text.isBlank()) {
                throw ApiException(
                    response.code,
                    "OSS returned an empty body (http ${response.code}); the Newport callback " +
                        "did not run, so the file was never registered"
                )
            }

            if (text.isNotBlank()) {
                val envelope = JsonLoader.parseOrNull<NewportEnvelope<OssUploadData>>(text)
                    ?: throw ApiException(
                        response.code,
                        "Unrecognised OSS response (http ${response.code}): ${text.take(160)}"
                    )
                if (!envelope.isOk) throw ApiException(envelope.code, envelope.message)
                if (envelope.data?.verifyStatus != true) {
                    throw ApiException(
                        envelope.code,
                        "OSS callback returned verifyStatus=${envelope.data?.verifyStatus}"
                    )
                }
                // Newport's docs say the finish step must use the reqId returned by THIS step,
                // not the one from get_policy. They have matched in every trace so far, but the
                // policy's copy is not the documented source, so prefer this one.
                envelope.data.reqId.ifBlank { policy.reqId }
            } else {
                policy.reqId
            }
        }
    }

    /**
     * Step 3: exchange the reqId for the public http URL Seedream can fetch.
     *
     * The raw response is logged verbatim in debug builds. Newport answers `code: 0, "ok"` even
     * when it has no record of the upload — it just omits `url` — so the only way to tell a real
     * failure from a parsing mistake on our side is to see the exact bytes it sent.
     */
    suspend fun finishUpload(reqId: String): Result<String> = io {
        val body = ApiClient.encode(FinishUploadRequest(reqId))
        Log.i(TAG, "upload_finish -> $body")
        val raw = rawPost(ApiClient.jsonPost(ApiClient.PATH_UPLOAD_FINISH, body))
        Log.i(TAG, "upload_finish <- ${raw.take(400)}")
        val envelope = JsonLoader.json.decodeFromString<NewportEnvelope<FinishUploadData>>(raw)
        if (!envelope.isOk) throw ApiException(envelope.code, envelope.message)
        val data = envelope.data
            ?: throw ApiException(envelope.code, "No data in upload_finish response")
        data.url.also {
            // Newport answers code 0 / "ok" and simply omits `url` when it cannot find the upload,
            // so a blank url is the only signal that the OSS step did not really register.
            if (it.isBlank()) {
                throw ApiException(
                    ApiException.CODE_TRANSPORT,
                    "Newport returned no url for reqId=$reqId — upload not registered"
                )
            }
        }
    }

    /**
     * The 3 upload steps as one call. Returns the public http URL to put in Seedream's `image[]`
     * — base64/data URIs are rejected with code 10192 (Risks #1).
     */
    suspend fun uploadImage(file: File): Result<String> = io {
        if (!file.exists() || file.length() == 0L) {
            throw IOException("Source file missing or empty: ${file.path}")
        }
        val policy = step("get_policy") { getUploadPolicy().getOrThrow() }
        Log.i(TAG, "upload: policy ok host=${policy.host} key=${policy.key} size=${file.length()}")

        step("oss_put") { uploadToOss(policy, file).getOrThrow() }

        // Prefer the object's own public URL over policy_upload_finish. That endpoint is not
        // reliable for every caller — it returns "ok" with no url on device while answering the
        // identical request correctly from a desktop — and the uploaded object is readable at
        // host/key anyway, verified by a HEAD before we spend a paid generate call on it.
        val direct = policy.objectUrl
        if (direct.isNotBlank() && step("verify_object") { isReachable(direct) }) {
            Log.i(TAG, "upload: using direct object url $direct")
            return@io direct
        }

        Log.w(TAG, "upload: direct url not reachable, falling back to upload_finish")
        step("upload_finish") { finishUpload(policy.reqId).getOrThrow() }
    }

    /** HEAD the url to confirm Seedream will be able to fetch it. */
    private fun isReachable(url: String): Boolean = runCatching {
        val request = Request.Builder().url(url).head().build()
        ApiClient.client.newCall(request).execute().use { it.isSuccessful }
    }.getOrElse {
        Log.w(TAG, "HEAD $url failed: ${it.message}")
        false
    }

    /** Executes a request and returns the body as text, without parsing it. */
    private fun rawPost(request: Request): String =
        ApiClient.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && text.isBlank()) {
                throw ApiException(response.code, "HTTP ${response.code}")
            }
            text
        }

    private inline fun <T> step(name: String, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        Log.w(TAG, "upload step '$name' failed: ${t.message}", t)
        throw UploadStepException(name, t)
    }

    /**
     * OSS notifies Newport through an asynchronous callback, so the public URL can still be empty
     * for a moment right after the PUT returns. Retrying briefly turns that race into a short wait
     * instead of a hard failure the user sees as "could not upload".
     */

    // ---------------------------------------------------------------- generate

    suspend fun submitSeedream(
        prompt: String,
        imageUrls: List<String>,
        size: String,
    ): Result<String> = io {
        require(imageUrls.isNotEmpty()) { "Seedream needs at least one image url" }
        require(imageUrls.all { it.startsWith("http") }) { "image[] must be http urls" }
        val body = ApiClient.encode(
            SeedreamRequest(
                model = ApiClient.MODEL,
                prompt = prompt,
                seed = -1,
                size = size,
                image = imageUrls,
            )
        )
        Log.i(
            TAG,
            "seedream submit: model='${ApiClient.MODEL}' size='$size' images=${imageUrls.size}"
        )
        val data = call<SeedreamSubmitData>(ApiClient.jsonPost(ApiClient.PATH_SEEDREAM, body))
        data.taskId.also {
            if (it.isBlank()) throw ApiException(ApiException.CODE_TRANSPORT, "Empty taskId")
        }
    }

    suspend fun getAsyncResult(taskId: String): Result<AsyncResultData> = io {
        val body = ApiClient.encode(AsyncResultRequest(taskId))
        call<AsyncResultData>(ApiClient.jsonPost(ApiClient.PATH_ASYNC_RESULT, body))
    }

    // ---------------------------------------------------------------- catalog & downloads

    /** Plain GET of the CDN catalog. Returns the raw JSON text so it can be cached verbatim. */
    suspend fun fetchCatalogJson(): Result<String> = io {
        val request = Request.Builder().url(ApiClient.CATALOG_URL).get().build()
        ApiClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "Catalog fetch failed")
            }
            response.body?.string().orEmpty().also {
                if (it.isBlank()) throw IOException("Empty catalog body")
            }
        }
    }

    /** Fetch + parse the catalog. Parsing a ~MB of JSON stays off the main thread (Risks #14). */
    suspend fun fetchCatalog(): Result<Catalog> = io {
        val text = fetchCatalogJson().getOrThrow()
        JsonLoader.json.decodeFromString<Catalog>(text)
    }

    /**
     * Stream a remote image to [target]. Used to pull the Seedream result into local storage
     * immediately — the remote URL expires in ~1 day (Risks #3).
     */
    suspend fun downloadTo(url: String, target: File): Result<File> = io {
        val request = Request.Builder().url(url).get().build()
        ApiClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "Download failed for $url")
            }
            val stream = response.body?.byteStream() ?: throw IOException("Empty download body")
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> stream.copyTo(out, 8 * 1024) }
            if (target.length() == 0L) throw IOException("Downloaded 0 bytes")
            target
        }
    }

    // ---------------------------------------------------------------- internals

    private suspend inline fun <T> io(crossinline block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.onFailure { Log.w(TAG, "call failed: ${it.message}", it) }
        }

    /** Executes a Newport JSON call and unwraps the envelope, honouring code ∈ {0, 200}. */
    private inline fun <reified T> call(request: Request): T {
        ApiClient.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && text.isBlank()) {
                throw ApiException(response.code, "HTTP ${response.code}")
            }
            val envelope = JsonLoader.json.decodeFromString<NewportEnvelope<T>>(text)
            if (!envelope.isOk) throw ApiException(envelope.code, envelope.message)
            return envelope.data
                ?: throw ApiException(envelope.code, "Missing data in response")
        }
    }
}

/** Wraps a failure with the upload step that produced it, so logs and reports name the step. */
class UploadStepException(val step: String, cause: Throwable) :
    IOException("upload step '$step' failed: ${cause.message}", cause)
