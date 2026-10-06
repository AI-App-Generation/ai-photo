package com.bblvn.composebaseproject.features.categoryall

import com.bblvn.composebaseproject.core.Analytics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogRepo
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogState
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.ui.components.AppTopBar
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.components.TemplateCard
import com.bblvn.composebaseproject.ui.components.TemplateCardSize
import com.bblvn.composebaseproject.ui.components.TemplateCardSkeleton
import com.bblvn.composebaseproject.ui.components.ads.rememberNativeAdSlots
import com.bblvn.composebaseproject.ui.components.ads.NativeAdSlot
import com.bblvn.composebaseproject.ui.theme.AppColors

/** Chiều cao phần nội dung của AppTopBar (mặc định `height = 56.dp`), chưa tính status bar. */
private val TOP_BAR_HEIGHT = 56.dp

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

    // AppTopBar cao đúng status bar + 56dp (nó tự áp statusBarsPadding), nên phần đệm đầu lưới phải bằng
    // cả hai; chỉ chừa 56dp thì status bar che mất một phần hàng đầu.
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TOP_BAR_HEIGHT

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        when {
            catalog is CatalogState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topBarHeight, start = 16.dp, end = 16.dp),
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

            else -> {
                // native_template_list: cứ 4 template (2 hàng) chèn 1 ô ad cỡ medium trải hết hai cột.
                // Slots mở ở mức màn hình để cuộn ra/vào không làm nạp lại ad.
                val adSlots = rememberNativeAdSlots(AdsPlacementHolder.NATIVE_TEMPLATE_LIST)
                val entries = remember(category, adSlots != null) {
                    buildGridEntries(category.templates, withAds = adSlots != null)
                }
                val gridState = rememberLazyGridState()

                // Yêu cầu ad dần theo vị trí cuộn: chỉ các ô sắp tới (nhìn trước vài hàng) mới đi nạp.
                LaunchedEffect(adSlots, entries, gridState) {
                    if (adSlots == null) return@LaunchedEffect
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { lastVisible ->
                            val horizon = lastVisible + AD_LOOKAHEAD_ITEMS
                            val slotsNeeded = entries.take(horizon + 1).count { it is GridEntry.Ad }
                            adSlots.ensure(slotsNeeded)
                        }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topBarHeight, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                items(
                    items = entries,
                    key = { it.key },
                    span = { GridItemSpan(if (it is GridEntry.Ad) 2 else 1) },
                ) { entry ->
                    if (entry is GridEntry.Ad) {
                        NativeAdSlot(slots = adSlots, slot = entry.slot)
                        return@items
                    }
                    val template = (entry as GridEntry.Tpl).template
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
                        onClick = {
                            Analytics.logTemplateClick(template, "see_all")
                            onTemplateClick(template.id)
                        },
                    )
                }
                item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(240.dp)) }
                }
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

/** Số item nhìn trước vị trí cuộn hiện tại để yêu cầu ad cho các ô sắp tới. */
private const val AD_LOOKAHEAD_ITEMS = 8

/** Cứ bấy nhiêu template thì chèn một ô ad (4 template = 2 hàng của lưới 2 cột). */
private const val TEMPLATES_PER_AD = 4

/** Một phần tử của lưới: template hoặc ô ad (đánh số ô theo thứ tự xuất hiện). */
private sealed interface GridEntry {
    val key: String

    /** [position] vào khoá vì catalog có template trùng id; thiếu nó thì lưới crash vì trùng khoá. */
    data class Tpl(val template: Template, val position: Int) : GridEntry {
        override val key: String get() = "t_${position}_${template.id}"
    }

    data class Ad(val slot: Int) : GridEntry {
        override val key: String get() = "ad_$slot"
    }
}

/**
 * Xen ô ad sau mỗi [TEMPLATES_PER_AD] template. Không chèn sau template cuối cùng: ad ở cuối lưới
 * chỉ là một khoảng trống thừa trước đoạn đệm đáy.
 */
private fun buildGridEntries(templates: List<Template>, withAds: Boolean): List<GridEntry> =
    buildList {
        templates.forEachIndexed { index, template ->
            add(GridEntry.Tpl(template, index))
            val count = index + 1
            if (withAds && count % TEMPLATES_PER_AD == 0 && count < templates.size) {
                add(GridEntry.Ad(slot = count / TEMPLATES_PER_AD - 1))
            }
        }
    }
