package com.bblvn.composebaseproject.features.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.data.domain.TemplateCategory
import com.bblvn.composebaseproject.data.domain.ToolType
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.CompareSlider
import com.bblvn.composebaseproject.ui.components.CompareSliderMode
import com.bblvn.composebaseproject.ui.components.PremiumBadge
import com.bblvn.composebaseproject.ui.components.SectionHeader
import com.bblvn.composebaseproject.ui.components.ShimmerBox
import com.bblvn.composebaseproject.ui.components.TemplateCard
import com.bblvn.composebaseproject.ui.components.TemplateCardSize
import com.bblvn.composebaseproject.ui.components.TemplateCardSkeleton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.imageScrim
import kotlinx.coroutines.delay

private const val BANNER_AUTO_SCROLL_MS = 4_000L
private val BANNER_HEIGHT = 220.dp

/**
 * Auto-playing before/after banner.
 *
 * Two independent motions, deliberately kept separate:
 *  - the pager advances one page every 4s, paused while the user has a finger on it;
 *  - each card wipes before -> after on a loop, which is [CompareSliderMode.AutoPlay] inside the
 *    shared [CompareSlider] — not a hand-rolled animation (R2).
 */
@Composable
fun BannerCarousel(
    slides: List<HomeSlide>,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { slides.size })
    // Touch pauses the timer. Tracked with a raw pointer flag rather than the pager's own
    // interaction source: `isScrollInProgress` is also true while OUR animateScrollToPage runs,
    // which would cancel the driving coroutine halfway and strand the pager between pages.
    var paused by remember { mutableStateOf(false) }

    // Keyed on `paused` on purpose: releasing the banner restarts a full 4s dwell rather than
    // flicking to the next page the instant the finger lifts.
    LaunchedEffect(pagerState, paused, slides.size) {
        if (paused || slides.size <= 1) return@LaunchedEffect
        while (true) {
            delay(BANNER_AUTO_SCROLL_MS)
            val next = (pagerState.currentPage + 1) % slides.size
            pagerState.animateScrollToPage(next)
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT)
                .padding(horizontal = 16.dp)
                // Initial pass: the press is seen before the pager consumes it as a drag.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        paused = true
                        try {
                            waitForUpOrCancellation(PointerEventPass.Initial)
                        } finally {
                            paused = false
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 12.dp,
                key = { index -> slides[index].template.id },
            ) { page ->
                BannerCard(
                    slide = slides[page],
                    onClick = { onTemplateClick(slides[page].template.id) },
                )
            }

            PagerDots(
                count = slides.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun BannerCard(
    slide: HomeSlide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = slide.template
    CompareSlider(
        modifier = modifier
            .fillMaxSize()
            .clip(AppShapes.card)
            .pointerInput(template.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null) onClick()
                }
            },
        before = template.compareBeforeUrl,
        after = template.compareAfterUrl,
        mode = CompareSliderMode.AutoPlay,
        // Both halves come from the same catalog entry and share a ratio, so Crop is safe here
        // and fills the card edge to edge (the Fit default is for mismatched pairs on Result).
        contentScale = ContentScale.Crop,
        shape = AppShapes.card,
    ) {
        // Mandatory scrim before any text lands on the image (R9).
        Box(Modifier.fillMaxSize().imageScrim(startFraction = 0.40f, endAlpha = 0.85f))

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppChip(
                text = stringResource(R.string.home_banner_before_after),
                size = AppChipSize.Small,
                style = AppChipStyle.Overlay,
            )
            Spacer(Modifier.weight(1f))
            if (template.isPremium) PremiumBadge(size = AppChipSize.Small)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = template.name,
                style = AppType.titleL,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (slide.categoryName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = slide.categoryName,
                    style = AppType.caption,
                    color = AppColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 16.dp else 5.dp,
                animationSpec = tween(220),
                label = "dot_width",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(5.dp)
                    .clip(AppShapes.chip)
                    .background(
                        if (active) AppColors.textPrimary
                        else AppColors.textPrimary.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

/** The 5 quick tools as a scrolling strip of pills. All natural width — never `weight` (R1). */
@Composable
fun QuickToolsStrip(
    tools: List<ToolType>,
    onToolClick: (String) -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 24.dp)) {
        SectionHeader(
            title = stringResource(R.string.home_quick_tools),
            actionText = stringResource(R.string.home_see_all_short),
            onAction = onOpenTools,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = tools, key = { it.name }) { tool ->
                AppChip(
                    text = stringResource(tool.labelRes),
                    icon = tool.iconRes,
                    size = AppChipSize.Medium,
                    style = AppChipStyle.Neutral,
                    onClick = { onToolClick(tool.name) },
                )
            }
        }
    }
}

/**
 * One category row.
 *
 * R3.6 — the row's [LazyListState] is remembered against [TemplateCategory.categoryId]; without
 * that key the states get reused as rows recycle and a row inherits its neighbour's horizontal
 * scroll offset.
 */
@Composable
fun CategoryRow(
    category: TemplateCategory,
    onTemplateClick: (String) -> Unit,
    onCategoryAll: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState: LazyListState = rememberSaveable(
        category.categoryId,
        saver = LazyListState.Saver,
    ) { LazyListState() }

    val templates: List<Template> = remember(category) {
        category.templates.take(HOME_ROW_TEMPLATE_LIMIT)
    }

    Column(modifier = modifier.padding(top = 24.dp)) {
        SectionHeader(
            title = category.categoryName,
            actionText = stringResource(R.string.home_see_all),
            onAction = { onCategoryAll(category.categoryId) },
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = rowState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = templates, key = { it.id }) { template ->
                TemplateCard(
                    imageModel = template.displayImageUrl,
                    name = template.name,
                    size = TemplateCardSize.Row,
                    isPremium = template.isPremium,
                    topStartBadge = if (template.allowsTwoImages) {
                        stringResource(R.string.home_badge_two_photos)
                    } else {
                        null
                    },
                    seed = template.id,
                    onClick = { onTemplateClick(template.id) },
                )
            }
        }
    }
}

/** First-load skeleton: 1 banner + 3 category rows, matching the real layout's metrics. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(BANNER_HEIGHT),
            shape = AppShapes.card,
        )
        repeat(3) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(140.dp)
                        .height(20.dp),
                    shape = AppShapes.chip,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(3) { TemplateCardSkeleton(size = TemplateCardSize.Row) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tool display mapping

/**
 * `ToolType` deliberately carries no display strings (they are translatable and belong to the UI
 * layer), so Home maps them here for its own strip.
 */
@get:StringRes
private val ToolType.labelRes: Int
    get() = when (this) {
        ToolType.ENHANCE -> R.string.home_tool_enhance
        ToolType.REMOVE_BG -> R.string.home_tool_remove_bg
        ToolType.COLORIZE -> R.string.home_tool_colorize
        ToolType.FACE_SWAP -> R.string.home_tool_face_swap
        ToolType.VIRTUAL_TRY_ON -> R.string.home_tool_try_on
    }

@get:DrawableRes
private val ToolType.iconRes: Int
    get() = when (this) {
        ToolType.ENHANCE -> R.drawable.ic_sparkles
        ToolType.REMOVE_BG -> R.drawable.ic_scissors
        ToolType.COLORIZE -> R.drawable.ic_palette
        ToolType.FACE_SWAP -> R.drawable.ic_face_swap
        ToolType.VIRTUAL_TRY_ON -> R.drawable.ic_shirt
    }
