package com.bblvn.composebaseproject.features.templatedetail

import com.bblvn.composebaseproject.core.AppCapability
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.core.StateViewModel
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogRepo
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogState
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine
import com.bblvn.composebaseproject.corefeatures.premium.PremiumManager
import com.bblvn.composebaseproject.data.domain.Template
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What the CTA is allowed to do right now. Exactly one of these is true at a time, and the
 * button keeps its 56dp height across all four (R1) — only the label, colour and action change.
 */
enum class TemplateCtaState {
    /** Free template (or premium user) with every photo it needs. -> onStart() */
    Ready,

    /** One-image template with nothing picked yet. -> onPickPhoto(0) */
    PickPhoto,

    /** Premium template, free user. -> onOpenPaywall() */
    Locked,

    /** Two-image template still missing a slot. Disabled. */
    NeedsPhotos,

    /** Another generation is already running (R6.3). Disabled. */
    Busy,
}

/**
 * State of the Template Detail screen. Every field has a default, loading/error are plain
 * properties, and derived values are `val`s here instead of being recomputed in composables
 * (core/UiState.kt convention).
 */
data class TemplateDetailUiState(
    val templateId: String = "",
    val template: Template? = null,
    val categoryName: String = "",
    val related: List<Template> = emptyList(),
    /** Mirrors [GenerationDraft.Draft.slots]; index == Seedream `image[]` index (Risks #17). */
    val slots: List<String?> = listOf(null, null),
    val isPremiumUser: Boolean = false,
    /** Lượt mở khoá một lần còn lại (xem AppCapability); có lượt cho template này thì hết bị khoá. */
    val unlockPasses: List<String> = emptyList(),
    val isGeneratingElsewhere: Boolean = false,
    val isLoading: Boolean = true,
    val descriptionExpanded: Boolean = false,
    val error: UiText? = null,
) {
    /** 1 for a normal template, 2 when `allows_two_images` (R5). */
    val requiredSlots: Int get() = template?.requiredImageCount ?: 1

    /** Slots are shown ONLY for two-image templates — a one-image template has no slot UI (R5). */
    val showsSlots: Boolean get() = template?.allowsTwoImages == true

    val hasAllPhotos: Boolean
        get() = (0 until requiredSlots).all { !slots.getOrNull(it).isNullOrBlank() }

    val isLocked: Boolean
        get() = template != null && template.isPremium && !isPremiumUser &&
            AppCapability.templateUnlockKey(template.id) !in unlockPasses

    val ctaState: TemplateCtaState
        get() = when {
            isGeneratingElsewhere -> TemplateCtaState.Busy
            isLocked -> TemplateCtaState.Locked
            hasAllPhotos -> TemplateCtaState.Ready
            showsSlots -> TemplateCtaState.NeedsPhotos
            else -> TemplateCtaState.PickPhoto
        }
}

/**
 * Resolves the template from [CatalogRepo], keeps the draft in sync with the slots and watches
 * the three flags the CTA depends on (entitlement, running generation, picked photos).
 */
class TemplateDetailViewModel : StateViewModel<TemplateDetailUiState>(TemplateDetailUiState()) {

    private var started = false

    fun bind(templateId: String) {
        if (started) return
        started = true
        update { it.copy(templateId = templateId) }

        viewModelScope.launch {
            CatalogRepo.state.collectLatest { catalog -> onCatalog(catalog, templateId) }
        }
        viewModelScope.launch {
            PremiumManager.isPremium.collectLatest { premium ->
                update { it.copy(isPremiumUser = premium) }
            }
        }
        viewModelScope.launch {
            AppCapability.unlockPasses.collectLatest { passes ->
                update { it.copy(unlockPasses = passes) }
            }
        }
        viewModelScope.launch {
            GenerationDraft.draft.collectLatest { draft ->
                update { it.copy(slots = draft.slots) }
            }
        }
        viewModelScope.launch {
            // `isGenerating` is a lateinit built in GenerationEngine.init(); a preview or a unit
            // test can reach this screen before the app scope exists, and a crash there would be
            // a worse outcome than simply assuming nothing is running.
            val flow: StateFlow<Boolean> = runCatching { GenerationEngine.isGenerating }
                .getOrElse { MutableStateFlow(false) }
            flow.collectLatest { running -> update { it.copy(isGeneratingElsewhere = running) } }
        }
    }

    private fun onCatalog(catalog: CatalogState, templateId: String) {
        when (catalog) {
            is CatalogState.Loading -> update { it.copy(isLoading = true, error = null) }

            is CatalogState.Error -> update {
                it.copy(
                    isLoading = false,
                    error = if (it.template == null) UiText.Raw(catalog.message) else null,
                )
            }

            is CatalogState.Ready -> {
                val template = CatalogRepo.templateById(templateId)
                if (template == null) {
                    update { it.copy(isLoading = false, template = null, error = null) }
                    return
                }
                // Only (re)start the draft when this is a different template, so coming back from
                // the Photo Picker does not wipe the slot the user just filled.
                if (GenerationDraft.current.template?.id != template.id) {
                    GenerationDraft.startTemplate(template)
                }
                update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        template = template,
                        categoryName = CatalogRepo.categoryOf(templateId)?.categoryName.orEmpty(),
                        related = CatalogRepo.relatedTemplates(templateId),
                        slots = GenerationDraft.current.slots,
                    )
                }
            }
        }
    }

    /** Called before navigating to the single-select picker so the result lands in this slot. */
    fun onPickSlot(index: Int) = GenerationDraft.setActiveSlot(index)

    fun onClearSlot(index: Int) = GenerationDraft.clearSlot(index)

    fun onToggleDescription() = update { it.copy(descriptionExpanded = !it.descriptionExpanded) }

    fun onRetry() = CatalogRepo.refreshAsync()
}
