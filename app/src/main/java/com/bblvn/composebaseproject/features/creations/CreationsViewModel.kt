package com.bblvn.composebaseproject.features.creations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine
import com.bblvn.composebaseproject.data.database.repo.CreationRepo
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class CreationsUiState(
    val all: List<Creation> = emptyList(),
    val filter: CreationFilter = CreationFilter.ALL,
) {
    val visible: List<Creation>
        get() = when (filter) {
            CreationFilter.ALL -> all
            CreationFilter.TEMPLATE -> all.filter {
                it.type == com.bblvn.composebaseproject.data.domain.CreationType.TEMPLATE
            }
            CreationFilter.TOOL -> all.filter {
                it.type == com.bblvn.composebaseproject.data.domain.CreationType.TOOL
            }
            CreationFilter.SAVED -> all.filter { it.savedToGallery }
        }
}

class CreationsViewModel : ViewModel() {

    private val _state = MutableStateFlow(CreationsUiState())
    val state: StateFlow<CreationsUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // The repo is the single source of truth and already sorts newest-first, so a run that is
        // still in flight appears here immediately with its PROCESSING overlay.
        viewModelScope.launch {
            CreationRepo.creations.collect { list ->
                _state.value = _state.value.copy(all = list)
            }
        }
    }

    fun onFilterSelected(filter: CreationFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    /**
     * Retry a failed row.
     *
     * Refused while something else is running: the engine allows only one generation at a time, so
     * silently queuing a second would leave the user staring at a card that never changes.
     */
    fun onRetry(creation: Creation) {
        viewModelScope.launch {
            if (GenerationEngine.isGenerating.value) {
                _events.send(UiEvent.ShowToast(UiText.Res(R.string.creations_busy)))
                return@launch
            }
            if (!AppCapability.canAffordGeneration()) {
                _events.send(UiEvent.ShowToast(UiText.Res(R.string.credits_not_enough)))
                return@launch
            }
            val started = GenerationEngine.regenerate(creation)
            if (!started) {
                _events.send(UiEvent.ShowToast(UiText.Res(R.string.creations_retry_failed)))
            }
        }
    }
}
