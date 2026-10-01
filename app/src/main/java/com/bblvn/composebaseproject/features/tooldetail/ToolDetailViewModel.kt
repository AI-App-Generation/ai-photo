package com.bblvn.composebaseproject.features.tooldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.corefeatures.generation.FaceCheck
import com.bblvn.composebaseproject.corefeatures.generation.FaceValidator
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine
import com.bblvn.composebaseproject.corefeatures.premium.PremiumManager
import com.bblvn.composebaseproject.data.domain.ToolOption
import com.bblvn.composebaseproject.data.domain.ToolType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

data class ToolDetailUiState(
    val tool: ToolType = ToolType.ENHANCE,
    val slots: List<String?> = listOf(null, null),
    val option: ToolOption? = null,
    val locked: Boolean = false,
    val isBusy: Boolean = false,
    val isValidatingFace: Boolean = false,
    val slotError: UiText? = null,
) {
    val filledCount: Int
        get() = (0 until tool.requiredImageCount).count { slots.getOrNull(it) != null }

    val hasAllPhotos: Boolean get() = filledCount == tool.requiredImageCount

    /** Enabled only when everything is ready: photos in, not locked, nothing else generating. */
    val canStart: Boolean
        get() = hasAllPhotos && !locked && !isBusy && !isValidatingFace && slotError == null
}

class ToolDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(ToolDetailUiState())
    val state: StateFlow<ToolDetailUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun update(block: (ToolDetailUiState) -> ToolDetailUiState) {
        _state.value = block(_state.value)
    }

    init {
        // The system Photo Picker fills the draft while this screen stays on top (no pop-back to
        // trigger syncFromDraft), so follow slot changes directly, like TemplateDetailViewModel.
        viewModelScope.launch {
            GenerationDraft.draft
                .map { it.slots }
                .distinctUntilChanged()
                .collect { slots -> update { it.copy(slots = slots, slotError = null) } }
        }
    }

    /**
     * Bind to [tool], starting a fresh draft the first time.
     *
     * Re-binding on every recomposition would wipe the photos the user just picked, so the draft
     * is only restarted when this really is a different tool than the one in flight.
     */
    fun bind(tool: ToolType) {
        val draft = GenerationDraft.current
        if (draft.tool != tool || draft.template != null) {
            GenerationDraft.startTool(tool)
        }
        syncFromDraft(tool)

        viewModelScope.launch {
            GenerationEngine.isGenerating.collect { busy -> update { it.copy(isBusy = busy) } }
        }
    }

    /** Called when the screen resumes, since the picker fills the draft and pops back here. */
    fun syncFromDraft(tool: ToolType) {
        val draft = GenerationDraft.current
        update {
            it.copy(
                tool = tool,
                slots = draft.slots,
                option = draft.option ?: tool.defaultOption,
                locked = !PremiumManager.canUseTool(tool),
                slotError = null,
            )
        }
    }

    fun onOptionSelected(option: ToolOption) {
        GenerationDraft.setOption(option)
        update { it.copy(option = option) }
    }

    fun onClearSlot(index: Int) {
        GenerationDraft.clearSlot(index)
        update { it.copy(slots = GenerationDraft.current.slots, slotError = null) }
    }

    /**
     * Validate before spending anything.
     *
     * Face Swap is checked on-device first: a photo with no face, or with several, produces a
     * useless result, and finding that out after a paid round-trip is both slow and expensive
     * (Risks #18). Only slot 0 is checked — the scene photo is allowed to contain many people.
     */
    fun onStart(onProceed: () -> Unit) {
        val current = _state.value
        when {
            current.locked -> {
                viewModelScope.launch { _events.send(UiEvent.OpenPaywall) }
                return
            }

            current.isBusy -> {
                viewModelScope.launch {
                    _events.send(UiEvent.ShowToast(UiText.Res(R.string.tool_busy)))
                }
                return
            }

            !current.hasAllPhotos -> return
        }

        if (!current.tool.validatesFaceOnSlot0) {
            onProceed()
            return
        }

        val facePath = current.slots.getOrNull(0) ?: return
        update { it.copy(isValidatingFace = true, slotError = null) }
        viewModelScope.launch {
            val check = FaceValidator.validate(facePath)
            update { it.copy(isValidatingFace = false) }
            when (check) {
                is FaceCheck.Ok -> onProceed()

                // Detection itself failed (unreadable file, detector unavailable). Don't block the
                // user on our own infrastructure problem — let the request through.
                is FaceCheck.Unreadable -> onProceed()

                is FaceCheck.NoFace ->
                    update { it.copy(slotError = UiText.Res(R.string.tool_error_no_face)) }

                is FaceCheck.MultipleFaces ->
                    update { it.copy(slotError = UiText.Res(R.string.tool_error_many_faces)) }

                is FaceCheck.FaceTooSmall ->
                    update { it.copy(slotError = UiText.Res(R.string.tool_error_face_small)) }
            }
        }
    }
}
