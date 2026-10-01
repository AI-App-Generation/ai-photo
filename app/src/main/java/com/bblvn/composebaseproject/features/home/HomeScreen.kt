package com.bblvn.composebaseproject.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.CheckForConsent
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.ui.components.AccountChip
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppTopBar
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppType

private val TOP_BAR_HEIGHT = 56.dp
private val HALO_HEIGHT = 320.dp
private val HALO_RADIUS = 520.dp

/** Distance scrolled before the top bar is fully opaque. */
private val TOP_BAR_FADE_DISTANCE = 24.dp

/**
 * Home — Discover.
 *
 * Fixed violet halo at the top, a scrolling LazyColumn (banner carousel, quick tools, one row per
 * catalog category, 240dp tail spacer) and a fixed top bar overlaying it. No ads here by
 * deliberate decision (C4): the banner carousel already owns the top of the screen.
 */
@Composable
fun AiPhotoHomeScreen(
    onTemplateClick: (String) -> Unit,
    onCategoryAll: (Int) -> Unit,
    onToolClick: (String) -> Unit,
    onOpenTools: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Required by the base project on the real Home screen.
    CheckForConsent()

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.OpenPaywall -> onOpenPaywall()
            else -> Unit
        }
    }

    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fadeDistancePx = remember(density) { with(density) { TOP_BAR_FADE_DISTANCE.toPx() } }
    val scrollFraction by remember(listState, fadeDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / fadeDistancePx).coerceIn(0f, 1f)
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {

        // Fixed ambient halo — a Brush, never an image asset (R8). Does not scroll with content.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HALO_HEIGHT)
                .drawBehind {
                    drawRect(
                        AppGradients.topHalo(
                            radiusPx = HALO_RADIUS.toPx(),
                            centerX = size.width / 2f,
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, AppColors.bgRoot))
                    )
                }
        )

        when {
            state.showSkeleton -> HomeSkeleton(
                modifier = Modifier.padding(top = statusBarTop + TOP_BAR_HEIGHT),
            )

            state.showError -> ErrorState(
                modifier = Modifier.padding(top = statusBarTop + TOP_BAR_HEIGHT),
                title = stringResource(R.string.home_error_title),
                message = state.error?.resolve(context),
                actionText = stringResource(R.string.home_retry),
                onAction = vm::onRetry,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = statusBarTop + TOP_BAR_HEIGHT),
            ) {
                item(key = "banner") {
                    BannerCarousel(
                        slides = state.slides,
                        onTemplateClick = onTemplateClick,
                    )
                }

                item(key = "quick_tools") {
                    QuickToolsStrip(
                        tools = state.tools,
                        onToolClick = onToolClick,
                        onOpenTools = onOpenTools,
                    )
                }

                items(
                    items = state.categories,
                    key = { it.categoryId },
                ) { category ->
                    CategoryRow(
                        category = category,
                        onTemplateClick = onTemplateClick,
                        onCategoryAll = onCategoryAll,
                    )
                }

                item(key = "tail") { Spacer(Modifier.height(240.dp)) }
            }
        }

        AppTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = null,
            navigationIcon = null,
            onBack = null,
            scrollFraction = scrollFraction,
            height = TOP_BAR_HEIGHT,
            horizontalPadding = 16.dp,
            leading = {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.logo_ai_photo_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_app_title),
                    style = AppType.titleM,
                    color = AppColors.textPrimary,
                    maxLines = 1,
                )
            },
            trailing = {
                AccountChip(
                    isPremium = state.isPremium,
                    onClick = vm::onAccountChipClick,
                )
            },
        )
    }
}
