package com.bblvn.composebaseproject.features.nav

import android.net.Uri

/**
 * Every navigation route in the app, in one place.
 *
 * Two rules this file exists to enforce:
 *
 *  1. **Nothing heavy travels in a route.** No file paths, no bitmaps, no template objects — only
 *     ids and small indices. Picked photos and the active template/tool live in
 *     `GenerationDraft`; a route argument that carried a path would break as soon as the process
 *     was recreated (R5/R6.5).
 *  2. **Route strings are built here, never hand-concatenated at a call site.** A typo in a
 *     hand-written route fails at runtime, not at compile time.
 */
object Routes {

    // --- app flow (outside the tab shell)
    const val SPLASH = "splash"
    const val LANGUAGE = "language"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

    // --- bottom navigation tabs
    const val TAB_HOME = "home"
    const val TAB_TOOLS = "tools"
    const val TAB_CREATIONS = "creations"
    const val TAB_PROFILE = "profile"

    // --- full-screen destinations
    const val CATEGORY_ALL = "categoryAll/{categoryId}"
    const val TEMPLATE_DETAIL = "templateDetail/{templateId}"
    const val TOOL_DETAIL = "toolDetail/{toolType}"
    const val PHOTO_PICKER = "photoPicker/{slotIndex}"
    const val ADJUST = "adjust/{slotIndex}"
    const val TRY_ON_ALIGN = "tryOnAlign"
    const val GENERATING = "generating"
    const val RESULT = "result?creationId={creationId}&replay={replay}"
    const val PAYWALL = "paywall"
    const val LANGUAGE_SETTINGS = "settings/language"

    // --- argument keys
    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val ARG_TOOL_TYPE = "toolType"
    const val ARG_SLOT_INDEX = "slotIndex"
    const val ARG_CREATION_ID = "creationId"
    const val ARG_REPLAY = "replay"

    fun categoryAll(categoryId: Int): String = "categoryAll/$categoryId"

    /** Template ids look like `tpl-1787138912003-9fa37bx`; encoded anyway in case that changes. */
    fun templateDetail(templateId: String): String =
        "templateDetail/${Uri.encode(templateId)}"

    fun toolDetail(toolTypeName: String): String = "toolDetail/$toolTypeName"

    /** [slotIndex] is 0 for single-image flows, 0 or 1 for the two-image ones. */
    fun photoPicker(slotIndex: Int): String = "photoPicker/$slotIndex"

    fun adjust(slotIndex: Int): String = "adjust/$slotIndex"

    /**
     * [replay] true means "viewing an old creation from Creations" — the reveal animation is
     * skipped and the screen reads from the DB rather than from the engine's live state.
     */
    fun result(creationId: String? = null, replay: Boolean = false): String =
        "result?creationId=${creationId.orEmpty()}&replay=$replay"
}
