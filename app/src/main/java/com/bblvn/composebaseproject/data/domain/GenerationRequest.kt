package com.bblvn.composebaseproject.data.domain

/**
 * Normalised input for [com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine].
 *
 * Both branches of the app (template and tool) collapse into this one shape — the engine never
 * knows which screen it came from.
 */
data class GenerationRequest(
    /** Verbatim template prompt, or the fixed tool prompt + option suffix. */
    val prompt: String,
    /** Local file paths, in Seedream `image[]` order. Order is a contract (Risks #17). */
    val imagePaths: List<String>,
    /** Seedream `size`: "1K" by default, "1152x2048" for premium high quality (R6.8). */
    val size: String = SIZE_1K,
    val type: CreationType,
    /** Template id or [ToolType.name]. */
    val sourceId: String,
    /** Display name stored in history. */
    val sourceLabel: String,
    /** Reuse an existing row instead of inserting one (used when resuming/retrying). */
    val existingCreationId: String? = null,
) {
    companion object {
        const val SIZE_1K = "1K"

        /** Max 9:16 that still bills at the 1K rate (<= 2,360,000 px). Never use "2K" (R6.8). */
        const val SIZE_HIGH = "1152x2048"
    }
}
