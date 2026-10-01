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
     * "Before" half of the Template Detail hero compare slider.
     *
     * The catalog has no dedicated before/after pair, so the policy is fixed here once rather
     * than re-invented at each call site: the second example image when the catalog supplies two,
     * otherwise the thumbnail. Both lists can be empty (Risks #15), in which case this is null and
     * the caller falls back to GradientPlaceholder.
     */
    val compareBeforeUrl: String?
        get() = exampleImgs.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: thumbUrl.takeIf { it.isNotBlank() }

    /** "After" half of the hero compare slider — the showcase result. See [compareBeforeUrl]. */
    val compareAfterUrl: String?
        get() = exampleImgs.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: displayImageUrl.takeIf { it.isNotBlank() }
}
