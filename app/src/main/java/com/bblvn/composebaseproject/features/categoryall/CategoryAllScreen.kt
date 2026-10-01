package com.bblvn.composebaseproject.features.categoryall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogRepo
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogState
import com.bblvn.composebaseproject.ui.components.AppTopBar
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.components.TemplateCard
import com.bblvn.composebaseproject.ui.components.TemplateCardSize
import com.bblvn.composebaseproject.ui.components.TemplateCardSkeleton
import com.bblvn.composebaseproject.ui.theme.AppColors

/**
 * Every template in one category, as a 2-column grid.
 *
 * Reads the already-loaded catalog rather than re-fetching: the user arrived here from Home, so
 * the data is in memory and a second network round-trip would only add a spinner.
 */
@Composable
fun CategoryAllScreen(
    categoryId: Int,
    onBack: () -> Unit,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog by CatalogRepo.state.collectAsStateWithLifecycle()
    val category = (catalog as? CatalogState.Ready)
        ?.categories
        ?.firstOrNull { it.categoryId == categoryId }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        when {
            catalog is CatalogState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 56.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(6) { TemplateCardSkeleton(size = TemplateCardSize.Grid) }
                }
            }

            category == null -> ErrorState(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.category_missing_title),
                message = stringResource(R.string.category_missing_message),
                actionText = stringResource(R.string.category_go_back),
                onAction = onBack,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 56.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(category.templates, key = { it.id }) { template ->
                    TemplateCard(
                        imageModel = template.displayImageUrl,
                        name = template.name,
                        size = TemplateCardSize.Grid,
                        isPremium = template.isPremium,
                        topStartBadge = if (template.allowsTwoImages) {
                            stringResource(R.string.category_two_photos)
                        } else {
                            null
                        },
                        seed = template.id,
                        onClick = { onTemplateClick(template.id) },
                    )
                }
                item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(240.dp)) }
            }
        }

        AppTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = category?.categoryName,
            onBack = onBack,
            solid = true,
        )
    }
}
