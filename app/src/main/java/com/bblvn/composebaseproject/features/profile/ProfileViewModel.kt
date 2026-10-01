package com.bblvn.composebaseproject.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.data.database.repo.CreationRepo
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.data.domain.CreationType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ProfileUiState(
    val isPremium: Boolean = false,
    val highQuality: Boolean = false,
    val autoSave: Boolean = false,
    val createdCount: Int = 0,
    val savedCount: Int = 0,
    val templatesUsed: Int = 0,
    val storageBytes: Long = 0L,
) {
    val storageLabel: String
        get() = when {
            storageBytes >= 1_000_000_000L ->
                String.format(Locale.US, "%.1f GB", storageBytes / 1_000_000_000.0)
            storageBytes >= 1_000_000L ->
                String.format(Locale.US, "%.0f MB", storageBytes / 1_000_000.0)
            else -> String.format(Locale.US, "%.0f KB", storageBytes / 1000.0)
        }
}

class ProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                SettingManager.isPurchased,
                SettingManager.highQuality,
                SettingManager.autoSave,
                CreationRepo.creations,
            ) { premium, quality, autoSave, creations ->
                ProfileUiState(
                    isPremium = premium,
                    highQuality = quality,
                    autoSave = autoSave,
                    createdCount = creations.count { it.status == CreationStatus.SUCCESS },
                    savedCount = creations.count { it.savedToGallery },
                    // Distinct templates, not runs — "used 3 templates" is the interesting number,
                    // and re-running one template ten times should not read as ten.
                    templatesUsed = creations
                        .filter { it.type == CreationType.TEMPLATE }
                        .map { it.sourceId }
                        .distinct()
                        .size,
                )
            }.collect { computed ->
                _state.value = computed.copy(storageBytes = _state.value.storageBytes)
            }
        }
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            val bytes = FileManager.storageUsedBytes()
            _state.value = _state.value.copy(storageBytes = bytes)
        }
    }

    fun onHighQualityChanged(enabled: Boolean) {
        if (!_state.value.isPremium) return
        SettingManager.setHighQuality(enabled)
    }

    fun onAutoSaveChanged(enabled: Boolean) = SettingManager.setAutoSave(enabled)

    /** Clears only cacheDir — generated results and source photos are untouched. */
    fun onClearCache() {
        viewModelScope.launch {
            val freed = FileManager.clearCache()
            refreshStorage()
            _events.send(
                UiEvent.ShowToast(
                    UiText.Res(R.string.profile_cache_cleared, listOf(freed / 1_000_000))
                )
            )
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            CreationRepo.deleteAll()
            refreshStorage()
            _events.send(UiEvent.ShowToast(UiText.Res(R.string.profile_history_cleared)))
        }
    }
}
