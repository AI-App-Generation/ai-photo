package com.bblvn.composebaseproject.data.api

import com.bblvn.composebaseproject.BuildConfig
import com.bblvn.composebaseproject.core.json.JsonLoader
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Low-level HTTP plumbing for Newport plus the plain CDN fetch used by the catalog.
 *
 * The Bearer key lives only in `BuildConfig.NEWPORT_KEY` (debug = test key, release = product
 * key), never inline in feature code.
 */
object ApiClient {

    const val BASE_URL: String = BuildConfig.NEWPORT_BASE_URL
    const val MODEL: String = BuildConfig.SEEDREAM_MODEL
    const val CATALOG_URL: String = BuildConfig.CATALOG_URL

    /**
     * NOTE: no `charset` parameter, and that is deliberate — see [jsonPost].
     */
    val JSON_MEDIA_TYPE = "application/json".toMediaType()

    /**
     * Adds the Newport Bearer header — but ONLY for requests to the Newport host. The OSS upload
     * step must not carry an Authorization header (Risks #2), and the CDN catalog does not need
     * one either, so the rule is enforced here instead of being remembered at each call site.
     */
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val isNewport = request.url.host == "api.newportai.com"
        val out = if (isNewport) {
            request.newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.NEWPORT_KEY}")
                .build()
        } else {
            request
        }
        chain.proceed(out)
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    // HEADERS, not BODY: prompts are ~6.4k chars and policies are base64 blobs.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.HEADERS
                            redactHeader("Authorization")
                        }
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // large multipart uploads
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * JSON POST.
     *
     * The body is built from a ByteArray, not a String, for one specific reason: Newport rejects
     * any request whose `Content-Type` carries a charset parameter, answering
     * `parameter error;model cannot be empty` no matter what the body actually contains.
     *
     *   application/json                  -> accepted
     *   application/json; charset=utf-8   -> rejected, with that misleading message
     *
     * `String.toRequestBody(mediaType)` APPENDS `; charset=utf-8` whenever the media type has
     * none, and OkHttp's BridgeInterceptor then overwrites any Content-Type header set by hand —
     * after the logging interceptor has already run, so the logs show a clean `application/json`
     * while the wire carries the charset. `ByteArray.toRequestBody` appends nothing, so the header
     * is exactly what is written here. The bytes are still UTF-8 either way.
     */
    fun jsonPost(path: String, body: String): Request = Request.Builder()
        .url(if (path.startsWith("http")) path else "$BASE_URL$path")
        .post(body.escapeNonAscii().toByteArray(Charsets.US_ASCII).toRequestBody(JSON_MEDIA_TYPE))
        .build()

    /**
     * Rewrites every non-ASCII character as a JSON unicode escape.
     *
     * Dropping the charset parameter (see [jsonPost]) leaves the server free to decode the body
     * as whatever it likes, which would corrupt accented characters - Vietnamese text especially.
     * Escaping first makes the payload pure ASCII, so every encoding agrees on it and the charset
     * question stops mattering at all. JSON treats an escape and the literal character as
     * identical, so the value the server parses is unchanged.
     */
    private fun String.escapeNonAscii(): String {
        if (all { it.code < 0x80 }) return this
        val out = StringBuilder(length + 16)
        for (ch in this) {
            if (ch.code < 0x80) {
                out.append(ch)
            } else {
                out.append("\\u").append("%04x".format(ch.code))
            }
        }
        return out.toString()
    }

    inline fun <reified T> encode(value: T): String = JsonLoader.encode(value)

    // Endpoints
    const val PATH_GET_POLICY = "/api/file/v1/get_policy"
    const val PATH_UPLOAD_FINISH = "/api/file/v1/policy_upload_finish"
    const val PATH_SEEDREAM = "/api/async/seedream"
    const val PATH_ASYNC_RESULT = "/api/getAsyncResult"
}
