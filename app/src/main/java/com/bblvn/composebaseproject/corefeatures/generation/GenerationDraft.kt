package com.bblvn.composebaseproject.corefeatures.generation

import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.data.domain.CreationType
import com.bblvn.composebaseproject.data.domain.GenerationRequest
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.data.domain.ToolOption
import com.bblvn.composebaseproject.data.domain.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The scratchpad that carries the in-progress selection between screens.
 *
 * Navigation passes a `slotIndex` and NOTHING else (R5) — image paths never travel in a nav
 * argument, because they are long, need escaping, and would survive process death in a backstack
 * while the files behind them might not.
 *
 * Lifecycle: filled on Template/Tool Detail -> Photo Picker -> Adjust/Align -> handed to
 * [GenerationEngine]; [clear] MUST be called when the user leaves the flow (back to Home/Tools)
 * so a stale photo can't leak into the next run (R6.5).
 */
object GenerationDraft {

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    val current: Draft get() = _draft.value

    data class Draft(
        val template: Template? = null,
        val tool: ToolType? = null,
        val option: ToolOption? = null,
        /** Original picked photos per slot. Index == Seedream `image[]` index (Risks #17). */
        val slots: List<String?> = listOf(null, null),
        /** Cropped/aligned variants per slot; falls back to the original when the user skipped. */
        val adjustedSlots: List<String?> = listOf(null, null),
        /** Slot the Photo Picker is currently filling. */
        val activeSlot: Int = 0,
    ) {
        val requiredSlots: Int
            get() = template?.requiredImageCount ?: tool?.requiredImageCount ?: 1

        /** Paths actually sent, in order: adjusted when present, otherwise the original. */
        val effectivePaths: List<String>
            get() = (0 until requiredSlots).mapNotNull { index ->
                adjustedSlots.getOrNull(index) ?: slots.getOrNull(index)
            }

        val isComplete: Boolean get() = effectivePaths.size == requiredSlots

        val sourceLabel: String get() = template?.name ?: tool?.name.orEmpty()
    }

    fun startTemplate(template: Template) {
        _draft.value = Draft(template = template)
    }

    fun startTool(tool: ToolType) {
        _draft.value = Draft(tool = tool, option = tool.defaultOption)
    }

    fun setOption(option: ToolOption) {
        _draft.value = _draft.value.copy(option = option)
    }

    /** Called before navigating to the picker, so the result knows where to land. */
    fun setActiveSlot(index: Int) {
        _draft.value = _draft.value.copy(activeSlot = index)
    }

    /** Photo Picker result. Deletes whatever this slot held so orphans don't pile up. */
    fun setSlot(index: Int, path: String) {
        val draft = _draft.value
        draft.slots.getOrNull(index)?.takeIf { it != path }?.let(FileManager::delete)
        draft.adjustedSlots.getOrNull(index)?.let(FileManager::delete)
        _draft.value = draft.copy(
            slots = draft.slots.replaceAt(index, path),
            adjustedSlots = draft.adjustedSlots.replaceAt(index, null),
        )
    }

    /** Adjust / Try-On Align result. */
    fun setAdjusted(index: Int, path: String) {
        val draft = _draft.value
        draft.adjustedSlots.getOrNull(index)?.takeIf { it != path }?.let(FileManager::delete)
        _draft.value = draft.copy(adjustedSlots = draft.adjustedSlots.replaceAt(index, path))
    }

    /**
     * Drop only the adjusted variant for [index], keeping the original photo.
     *
     * Used when the user taps "Bỏ qua" on the Adjust screen: without this, a crop written on an
     * earlier pass would silently be reused even though the user just asked to skip adjusting.
     */
    fun clearAdjusted(index: Int) {
        val draft = _draft.value
        draft.adjustedSlots.getOrNull(index)?.let(FileManager::delete)
        _draft.value = draft.copy(adjustedSlots = draft.adjustedSlots.replaceAt(index, null))
    }

    fun clearSlot(index: Int) {
        val draft = _draft.value
        draft.slots.getOrNull(index)?.let(FileManager::delete)
        draft.adjustedSlots.getOrNull(index)?.let(FileManager::delete)
        _draft.value = draft.copy(
            slots = draft.slots.replaceAt(index, null),
            adjustedSlots = draft.adjustedSlots.replaceAt(index, null),
        )
    }

    /**
     * Drop the draft. [deleteFiles] is false when the paths were just handed to the engine — the
     * Creation row now owns those files.
     */
    fun clear(deleteFiles: Boolean = true) {
        if (deleteFiles) {
            val draft = _draft.value
            (draft.slots + draft.adjustedSlots).filterNotNull().forEach(FileManager::delete)
        }
        _draft.value = Draft()
    }

    /**
     * Build the engine request from the current draft. Null when the draft is incomplete — the
     * CTA should have been disabled in that case (Risks #16).
     */
    fun toRequest(
        size: String = GenerationRequest.SIZE_1K,
        /** Localized tool name for history; falls back to the enum name. */
        toolDisplayName: String? = null,
    ): GenerationRequest? {
        val draft = _draft.value
        if (!draft.isComplete) return null

        val template = draft.template
        val tool = draft.tool
        return when {
            template != null -> GenerationRequest(
                // Verbatim. Never trimmed or rewritten — length is what makes Seedream behave.
                prompt = template.prompt,
                imagePaths = draft.effectivePaths,
                size = size,
                type = CreationType.TEMPLATE,
                sourceId = template.id,
                sourceLabel = template.name,
            )

            tool != null -> GenerationRequest(
                prompt = ToolPromptProvider.promptFor(tool, draft.option),
                imagePaths = draft.effectivePaths,
                size = size,
                type = CreationType.TOOL,
                sourceId = tool.name,
                sourceLabel = toolDisplayName ?: tool.name,
            )

            else -> null
        }
    }

    private fun <T> List<T?>.replaceAt(index: Int, value: T?): List<T?> {
        val padded = if (size > index) toMutableList() else toMutableList().apply {
            while (size <= index) add(null)
        }
        padded[index] = value
        return padded
    }
}
