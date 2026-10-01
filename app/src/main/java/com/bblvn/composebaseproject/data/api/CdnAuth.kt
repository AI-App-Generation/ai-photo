package com.bblvn.composebaseproject.data.api

import com.bblvn.composebaseproject.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Auth for Bunny **private storage**, where every catalog image lives.
 *
 * The catalog JSON is public, but each `template_webp_url` / `example_imgs` entry points at
 * `storage.bunnycdn.com/bblprivate/...` — the Storage API, which answers 401 to an unauthenticated
 * GET. The image loader must therefore send the key on those requests or the whole app shows
 * placeholders while the JSON loads perfectly. (That asymmetry — "JSON fine, images blank" — is
 * the signature of this bug.)
 *
 * Two things this is NOT:
 *  - It is not the Newport key. That one is `sk-...` and goes out as `Authorization: Bearer ...`
 *    to api.newportai.com. Mixing them up produces a 401 from whichever system got the wrong key.
 *  - It is not a Bearer token. Bunny wants the raw key, no prefix.
 *
 * The key is only ever attached to [STORAGE_HOST]; it must never leak onto Newport or any other
 * host the app talks to.
 */
object CdnAuth {

    const val STORAGE_HOST = "storage.bunnycdn.com"

    /**
     * Current key. Defaults to the build-time value and can be replaced at runtime once Firebase
     * Remote Config has fetched `apiAccessKey`, so a rotated key does not require an app update.
     */
    @Volatile
    var accessKey: String = BuildConfig.CDN_ACCESS_KEY
        private set

    fun updateAccessKey(key: String) {
        if (key.isNotBlank()) accessKey = key
    }

    /** Resolve a catalog media value: absolute URLs pass through, relative paths get the base. */
    fun resolveUrl(raw: String): String = when {
        raw.isBlank() -> raw
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        else -> BuildConfig.CDN_BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')
    }

    /**
     * Adds the key to private-storage requests only.
     *
     * Bunny accepts `AccessKey`; the reference client sends `Authorization` with the same raw value
     * as well, so both are set here to stay compatible with whichever one the edge checks.
     */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != STORAGE_HOST || accessKey.isBlank()) {
            chain.proceed(request)
        } else {
            chain.proceed(
                request.newBuilder()
                    .header("AccessKey", accessKey)
                    .header("Authorization", accessKey)
                    .build()
            )
        }
    }
}
