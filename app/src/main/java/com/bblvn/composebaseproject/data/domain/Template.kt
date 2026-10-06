package com.bblvn.composebaseproject.data.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One catalog template.
 *
 * This is BOTH the wire shape of a `components[]` entry in the catalog JSON and the domain model
 * used everywhere in the app — there is no separate DTO, because the two are a 1:1 mapping and an
 * extra layer would only duplicate 15 fields (see skills/APP_STRUCTURE.md: no layers without a
 * real problem). JSON field names differ from the Kotlin names, so every field carries
 * [SerialName]; the catalog also contains legacy/dead fields, so the parser MUST run with
 * `ignoreUnknownKeys = true` (see [com.bblvn.composebaseproject.core.json.JsonLoader]).
 */
@Serializable
data class Template(
    @SerialName("id") val id: String = "",
    @SerialName("template_name") val name: String = "",
    /**
     * Full Seedream prompt (~6.4k chars). Sent VERBATIM — never trimmed, summarised or rewritten,
     * and never shown to the user.
     */
    @SerialName("prompt") val prompt: String = "",
    @SerialName("template_webp_url") val thumbUrl: String = "",
    /** Non-empty => video template. Those are filtered out entirely in this build (Risks #11). */
    @SerialName("template_video_url") val videoUrl: String = "",
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("allows_two_images") val allowsTwoImages: Boolean = false,
    @SerialName("example_imgs") val exampleImgs: List<String> = emptyList(),
    @SerialName("demo_imgs") val demoImgs: List<String> = emptyList(),
    @SerialName("is_show_on_slide") val isShowOnSlide: Boolean = false,
    @SerialName("slide_order") val slideOrder: Int = 0,
    /** Internal coin price (100 image / 250 video). Unused in this build; kept for a later economy. */
    @SerialName("price") val price: Int = 0,
) {
    val isVideo: Boolean get() = videoUrl.isNotBlank()

    /** Number of source photos the user must supply. */
    val requiredImageCount: Int get() = if (allowsTwoImages) 2 else 1

    /**
     * Best available image for a card/banner. Catalog thumbnails point at
     * `storage.bunnycdn.com/bblprivate/...` which may 403 publicly (Risks #13), so callers should
     * treat this as a first choice and fall back to [fallbackImageUrl] then to a gradient
     * placeholder.
     */
    val displayImageUrl: String
        get() = thumbUrl.ifBlank { exampleImgs.firstOrNull().orEmpty() }

    /** Second choice when [displayImageUrl] fails to load. May be blank (Risks #15). */
    val fallbackImageUrl: String
        get() = exampleImgs.firstOrNull { it != displayImageUrl }
            ?: demoImgs.firstOrNull()
            ?: ""

    /**
     * "Before" half of the Template Detail hero compare slider: the example INPUT photo.
     *
     * In the catalog `example_imgs` are the sample photos a user would feed in (files named
     * `<Name>_example_0`, plus `_example_1` for two-photo templates), while `template_webp_url` is
     * the finished showcase result. So before = the first example photo. It is null when the
     * catalog supplies no example (183 of the usable templates), in which case the hero shows the
     * result on its own instead of a slider.
     */
    val compareBeforeUrl: String?
        get() = exampleImgs.firstOrNull()?.takeIf { it.isNotBlank() }

    /** "After" half of the hero compare slider: the showcase RESULT (`template_webp_url`). See [compareBeforeUrl]. */
    val compareAfterUrl: String?
        get() = thumbUrl.takeIf { it.isNotBlank() }
            ?: displayImageUrl.takeIf { it.isNotBlank() }
}
