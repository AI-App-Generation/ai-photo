package com.bblvn.composebaseproject.features.home

import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.data.domain.TemplateCategory
import com.bblvn.composebaseproject.data.domain.ToolType

/**
 * One banner page: the slide template plus the category it came from, resolved once in the
 * ViewModel so the composable never walks the catalog while recomposing.
 */
data class HomeSlide(
    val template: Template,
    val categoryName: String,
)

/**
 * Home — Discover screen state (see `core/UiState.kt` for the convention).
 *
 * Loading and error are plain properties, not a sealed hierarchy: the catalog is served
 * stale-while-revalidate, so Home routinely shows cached content *and* a refresh in flight at the
 * same time. The skeleton / error views are therefore only shown when there is nothing to show.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    /** Catalog array order is preserved by `CatalogRepo`; never re-sorted here (Risks #10). */
    val categories: List<TemplateCategory> = emptyList(),
    val slides: List<HomeSlide> = emptyList(),
    val tools: List<ToolType> = ToolType.entries.toList(),
    val isPremium: Boolean = false,
) {
    val hasContent: Boolean get() = categories.isNotEmpty()

    /** Full-screen shimmer skeleton: first load, nothing cached yet. */
    val showSkeleton: Boolean get() = isLoading && !hasContent

    /** Full-screen retry: the fetch failed and there is no cache to fall back on. */
    val showError: Boolean get() = error != null && !hasContent
}

/** Max templates rendered per category row on Home; the rest live behind "Xem tất cả". */
const val HOME_ROW_TEMPLATE_LIMIT = 12
