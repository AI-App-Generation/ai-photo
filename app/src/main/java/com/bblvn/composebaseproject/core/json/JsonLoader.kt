package com.bblvn.composebaseproject.core.json

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The one kotlinx-serialization configuration used app-wide.
 *
 * `ignoreUnknownKeys = true` is mandatory: the catalog JSON carries legacy/dead fields
 * (`style_id`, `template_style_id`, `is_filter`, `example_effect`) and Newport responses carry
 * more fields than we model. `coerceInputValues` keeps a `null` in a non-null field from throwing.
 */
object JsonLoader {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Parse, returning null instead of throwing. Always call from a background dispatcher. */
    inline fun <reified T> parseOrNull(text: String): T? = try {
        json.decodeFromString<T>(text)
    } catch (t: Throwable) {
        null
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)
}
