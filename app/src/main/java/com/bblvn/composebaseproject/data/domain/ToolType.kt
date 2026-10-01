package com.bblvn.composebaseproject.data.domain

/**
 * The 5 quick photo tools. A tool is the "template-less" branch of the same generation pipeline:
 * the only differences from a [Template] are where the prompt comes from
 * ([com.bblvn.composebaseproject.corefeatures.generation.ToolPromptProvider]) and how many photos
 * it takes.
 *
 * NOTE: no display strings live here. Names, subtitles and slot labels are translatable and belong
 * in string resources owned by the UI layer; map `ToolType` -> `@StringRes` in the feature package.
 */
enum class ToolType(
    val requiredImageCount: Int,
    val isPremium: Boolean,
    /** Ordered slot roles. Index == position in the Seedream `image[]` array (Risks #17). */
    val slots: List<SlotRole>,
    val options: List<ToolOption>,
    /** Face Swap only: slot 0 must contain exactly one clear face (Risks #18, R6.6). */
    val validatesFaceOnSlot0: Boolean = false,
    /** Virtual Try-On only: the alignment screen is mandatory, no skip path (Risks #19). */
    val requiresAlignment: Boolean = false,
    /** Enhance / Remove BG / Colorize go straight to Generating, skipping the Adjust screen. */
    val skipsAdjust: Boolean = true,
) {
    ENHANCE(
        requiredImageCount = 1,
        isPremium = false,
        slots = listOf(SlotRole.SINGLE),
        options = listOf(ToolOption.ENHANCE_NATURAL, ToolOption.ENHANCE_STRONG),
    ),
    REMOVE_BG(
        requiredImageCount = 1,
        isPremium = false,
        slots = listOf(SlotRole.SINGLE),
        options = listOf(
            ToolOption.BG_TRANSPARENT,
            ToolOption.BG_WHITE,
            ToolOption.BG_BLACK,
        ),
    ),
    COLORIZE(
        requiredImageCount = 1,
        isPremium = false,
        slots = listOf(SlotRole.SINGLE),
        options = listOf(
            ToolOption.COLOR_NATURAL,
            ToolOption.COLOR_WARM,
            ToolOption.COLOR_CINEMATIC,
        ),
    ),
    FACE_SWAP(
        requiredImageCount = 2,
        isPremium = true,
        slots = listOf(SlotRole.FACE_SOURCE, SlotRole.SCENE),
        options = emptyList(),
        validatesFaceOnSlot0 = true,
    ),
    VIRTUAL_TRY_ON(
        requiredImageCount = 2,
        isPremium = true,
        slots = listOf(SlotRole.PERSON, SlotRole.GARMENT),
        options = emptyList(),
        requiresAlignment = true,
        skipsAdjust = false,
    );

    /** Chip selected by default when the tool has options; null when it has none. */
    val defaultOption: ToolOption? get() = options.firstOrNull()

    companion object {
        /** Safe parse for nav arguments / DB rows. */
        fun fromName(value: String?): ToolType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

}

/** What a photo slot means. Order is a contract with the prompt — never reorder (Risks #17). */
enum class SlotRole { SINGLE, FACE_SOURCE, SCENE, PERSON, GARMENT }

/** Option chips shown on Tool Detail. Each maps to a prompt suffix in `ToolPromptProvider`. */
enum class ToolOption {
    ENHANCE_NATURAL,
    ENHANCE_STRONG,
    BG_TRANSPARENT,
    BG_WHITE,
    BG_BLACK,
    COLOR_NATURAL,
    COLOR_WARM,
    COLOR_CINEMATIC;

    companion object {
        fun fromName(value: String?): ToolOption? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
