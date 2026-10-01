package com.bblvn.composebaseproject.features.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.data.domain.SlotRole
import com.bblvn.composebaseproject.data.domain.ToolOption
import com.bblvn.composebaseproject.data.domain.ToolType

/**
 * The UI half of a [ToolType]: names, artwork and icons.
 *
 * `ToolType` deliberately carries no display strings — they are translatable, and a prompt must
 * never be affected by the user's language. This file is the single mapping from the logic enum to
 * resources, shared by Tools Hub and Tool Detail so the two screens cannot disagree.
 */
object ToolPresentation {

    @StringRes
    fun name(tool: ToolType): Int = when (tool) {
        ToolType.ENHANCE -> R.string.tool_enhance_name
        ToolType.REMOVE_BG -> R.string.tool_remove_bg_name
        ToolType.COLORIZE -> R.string.tool_colorize_name
        ToolType.FACE_SWAP -> R.string.tool_face_swap_name
        ToolType.VIRTUAL_TRY_ON -> R.string.tool_try_on_name
    }

    @StringRes
    fun subtitle(tool: ToolType): Int = when (tool) {
        ToolType.ENHANCE -> R.string.tool_enhance_subtitle
        ToolType.REMOVE_BG -> R.string.tool_remove_bg_subtitle
        ToolType.COLORIZE -> R.string.tool_colorize_subtitle
        ToolType.FACE_SWAP -> R.string.tool_face_swap_subtitle
        ToolType.VIRTUAL_TRY_ON -> R.string.tool_try_on_subtitle
    }

    @StringRes
    fun description(tool: ToolType): Int = when (tool) {
        ToolType.ENHANCE -> R.string.tool_enhance_description
        ToolType.REMOVE_BG -> R.string.tool_remove_bg_description
        ToolType.COLORIZE -> R.string.tool_colorize_description
        ToolType.FACE_SWAP -> R.string.tool_face_swap_description
        ToolType.VIRTUAL_TRY_ON -> R.string.tool_try_on_description
    }

    @DrawableRes
    fun icon(tool: ToolType): Int = when (tool) {
        ToolType.ENHANCE -> R.drawable.ic_wand
        ToolType.REMOVE_BG -> R.drawable.ic_scissors
        ToolType.COLORIZE -> R.drawable.ic_palette
        ToolType.FACE_SWAP -> R.drawable.ic_face_swap
        ToolType.VIRTUAL_TRY_ON -> R.drawable.ic_shirt
    }

    /** The "after" demo doubles as the card cover — one asset, so card and example always match. */
    @DrawableRes
    fun cover(tool: ToolType): Int = afterImage(tool)

    @DrawableRes
    fun beforeImage(tool: ToolType): Int = when (tool) {
        ToolType.ENHANCE -> R.drawable.tool_demo_enhance_before
        ToolType.REMOVE_BG -> R.drawable.tool_demo_remove_bg_before
        ToolType.COLORIZE -> R.drawable.tool_demo_colorize_before
        ToolType.FACE_SWAP -> R.drawable.tool_demo_face_swap_before
        ToolType.VIRTUAL_TRY_ON -> R.drawable.tool_demo_try_on_before
    }

    @DrawableRes
    fun afterImage(tool: ToolType): Int = when (tool) {
        ToolType.ENHANCE -> R.drawable.tool_demo_enhance_after
        ToolType.REMOVE_BG -> R.drawable.tool_demo_remove_bg_after
        ToolType.COLORIZE -> R.drawable.tool_demo_colorize_after
        ToolType.FACE_SWAP -> R.drawable.tool_demo_face_swap_after
        ToolType.VIRTUAL_TRY_ON -> R.drawable.tool_demo_try_on_after
    }

    /** Slot captions. Order matches `ToolType.slots`, which is a contract with the prompt. */
    @StringRes
    fun slotLabel(role: SlotRole): Int = when (role) {
        SlotRole.SINGLE -> R.string.tool_slot_photo
        SlotRole.FACE_SOURCE -> R.string.tool_slot_face
        SlotRole.SCENE -> R.string.tool_slot_scene
        SlotRole.PERSON -> R.string.tool_slot_person
        SlotRole.GARMENT -> R.string.tool_slot_garment
    }

    @StringRes
    fun optionLabel(option: ToolOption): Int = when (option) {
        ToolOption.ENHANCE_NATURAL -> R.string.tool_option_natural
        ToolOption.ENHANCE_STRONG -> R.string.tool_option_strong
        ToolOption.BG_TRANSPARENT -> R.string.tool_option_transparent
        ToolOption.BG_WHITE -> R.string.tool_option_white
        ToolOption.BG_BLACK -> R.string.tool_option_black
        ToolOption.COLOR_NATURAL -> R.string.tool_option_natural
        ToolOption.COLOR_WARM -> R.string.tool_option_warm
        ToolOption.COLOR_CINEMATIC -> R.string.tool_option_cinematic
    }

    /** Heading above the option chips; null for tools that have no options. */
    @StringRes
    fun optionTitle(tool: ToolType): Int? = when (tool) {
        ToolType.ENHANCE -> R.string.tool_option_title_strength
        ToolType.REMOVE_BG -> R.string.tool_option_title_background
        ToolType.COLORIZE -> R.string.tool_option_title_tone
        else -> null
    }

    /** Tools Hub shows Enhance as one wide featured card and the rest as a 2-column grid. */
    val featured: ToolType = ToolType.ENHANCE

    val grid: List<ToolType> = ToolType.entries.filter { it != featured }
}
