package com.bblvn.composebaseproject.features.home

import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.StateViewModel
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogRepo
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogState
import com.bblvn.composebaseproject.corefeatures.premium.PremiumManager
import com.bblvn.composebaseproject.data.domain.TemplateCategory
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Home — Discover.
 *
 * Owns no data of its own: it projects [CatalogRepo] + [PremiumManager] into [HomeUiState].
 * The repo already drops disabled and video templates and preserves the catalog's category
 * order, so nothing here re-filters or re-sorts (Risks #9, #10, #11).
 */
class HomeViewModel : StateViewModel<HomeUiState>(HomeUiState()) {

    init {
        viewModelScope.launch {
            combine(CatalogRepo.state, PremiumManager.isPremium) { catalog, premium ->
                catalog to premium
            }.collect { (catalog, premium) -> reduce(catalog, premium) }
        }
    }

    private fun reduce(catalog: CatalogState, premium: Boolean) = when (catalog) {
        is CatalogState.Loading -> update {
            it.copy(isLoading = true, error = null, isPremium = premium)
        }

        is CatalogState.Ready -> update {
            it.copy(
                isLoading = false,
                error = null,
                categories = catalog.categories,
                slides = buildSlides(catalog, catalog.categories),
                isPremium = premium,
            )
        }

        is CatalogState.Error -> update {
            it.copy(
                isLoading = false,
                error = UiText.Res(R.string.home_error_message),
                isPremium = premium,
            )
        }
    }

    /** Resolve each slide's category name once, so the banner never searches during composition. */
    private fun buildSlides(
        catalog: CatalogState.Ready,
        categories: List<TemplateCategory>,
    ): List<HomeSlide> {
        val nameOf = HashMap<String, String>()
        categories.forEach { category ->
            category.templates.forEach { nameOf[it.id] = category.categoryName }
        }
        return catalog.slideTemplates.map { HomeSlide(it, nameOf[it.id].orEmpty()) }
    }

    fun onRetry() {
        update { it.copy(isLoading = true, error = null) }
        CatalogRepo.refreshAsync()
    }

    /** Free users are sent to the paywall; premium users already have everything Home can offer. */
    fun onAccountChipClick() {
        if (!currentState.isPremium) emit(UiEvent.OpenPaywall)
    }
}
