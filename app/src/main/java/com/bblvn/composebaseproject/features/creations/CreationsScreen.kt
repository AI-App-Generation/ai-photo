package com.bblvn.composebaseproject.features.creations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationFilter
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.CreditChip
import com.bblvn.composebaseproject.ui.components.EmptyState
import com.bblvn.composebaseproject.ui.components.RemoteImage
import com.bblvn.composebaseproject.ui.components.RewardPrompt
import com.bblvn.composebaseproject.ui.components.RewardKind
import com.bblvn.composebaseproject.ui.components.ads.AdCarousel
import com.bblvn.composebaseproject.ui.components.ads.AdSlotSize
import com.bblvn.composebaseproject.ui.components.ads.rememberNativeAdSlots
import com.bblvn.composebaseproject.ui.components.ads.NativeAdSlot
import com.bblvn.composebaseproject.ui.components.ads.rememberAdCarouselSession
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.imageScrim
import com.bblvn.composebaseproject.utils.DateUtils
import java.io.File

/**
 * The history tab. Rows come straight from the Room-backed repo, so a run that is still in flight
 * shows up here live rather than appearing only once it finishes.
 */
@Composable
fun CreationsScreen(
    onOpenCreation: (String) -> Unit,
    onExplore: () -> Unit,
    onShareRequested: (File) -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CreationsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }
    // Mở ở mức màn hình (ngoài lưới) để cuộn không làm nạp lại ad; đổi tab thì session được trả.
    val adSession = rememberAdCarouselSession()
    // native_your_photos: ô ad xen trong lưới (cứ 4 ảnh = 2 hàng một ô, cỡ small), một ô ở cuối khi ít ảnh, và
    // một ô dưới EmptyState khi chưa có ảnh. Slots mở ở mức màn hình để cuộn không làm nạp lại.
    val photoSlots = rememberNativeAdSlots(AdsPlacementHolder.NATIVE_YOUR_PHOTOS, AdSlotSize.Small)

    // Thử lại một bản ghi thất bại cần 1 credit: hết thì hỏi xem quảng cáo thưởng (hoặc mở paywall).
    var rewardKind by remember { mutableStateOf<RewardKind?>(null) }
    var pendingRetry by remember { mutableStateOf<Creation?>(null) }

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.ShowToast -> toast = event.text.resolve(context)
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header is fixed, outside the scrolling grid, per the app-wide top-bar rule.
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.creations_title),
                        style = AppType.titleL,
                        color = AppColors.textPrimary,
                    )
                    CreditChip()
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    items(CreationFilter.entries.toList(), key = { it.name }) { filter ->
                        AppChip(
                            text = stringResource(filterLabel(filter)),
                            size = AppChipSize.Medium,
                            style = if (state.filter == filter) {
                                AppChipStyle.Brand
                            } else {
                                AppChipStyle.Neutral
                            },
                            selected = state.filter == filter,
                            onClick = { vm.onFilterSelected(filter) },
                        )
                    }
                }
            }

            if (state.visible.isEmpty()) {
                LaunchedEffect(photoSlots) { photoSlots?.ensure(1) }
                Column(modifier = Modifier.fillMaxSize()) {
                    EmptyState(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        title = stringResource(R.string.creations_empty_title),
                        message = stringResource(R.string.creations_empty_message),
                        illustration = R.drawable.empty_creations,
                        actionText = stringResource(R.string.creations_empty_action),
                        onAction = onExplore,
                    )
                    NativeAdSlot(
                        slots = photoSlots,
                        slot = 0,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        size = AdSlotSize.Small,
                    )
                }
            } else {
                val entries = remember(state.visible, photoSlots != null) {
                    buildPhotoEntries(state.visible, withAds = photoSlots != null)
                }
                val gridState = rememberLazyGridState()

                // Yêu cầu ad dần theo vị trí cuộn. Item đầu của lưới là carousel nên lệch 1.
                LaunchedEffect(photoSlots, entries, gridState) {
                    if (photoSlots == null) return@LaunchedEffect
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { lastVisible ->
                            val horizon = lastVisible - GRID_HEADER_ITEMS + AD_LOOKAHEAD_ITEMS
                            val slotsNeeded = entries.take((horizon + 1).coerceAtLeast(0))
                                .count { it is PhotoEntry.Ad }
                            photoSlots.ensure(maxOf(slotsNeeded, 1))
                        }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // native_swipe_home: ngay dưới hàng chip category, cuộn cùng danh sách.
                    item(key = "ad_carousel", span = { GridItemSpan(2) }) {
                        AdCarousel(session = adSession)
                    }

                    items(
                        items = entries,
                        key = { it.key },
                        span = { GridItemSpan(if (it is PhotoEntry.Ad) 2 else 1) },
                    ) { entry ->
                        if (entry is PhotoEntry.Ad) {
                            NativeAdSlot(slots = photoSlots, slot = entry.slot, size = AdSlotSize.Small)
                            return@items
                        }
                        val creation = (entry as PhotoEntry.Photo).creation
                        CreationCard(
                            creation = creation,
                            onClick = {
                                if (creation.status == CreationStatus.SUCCESS) {
                                    onOpenCreation(creation.id)
                                }
                            },
                            onRetry = {
                                if (AppCapability.canAffordGeneration()) {
                                    vm.onRetry(creation)
                                } else {
                                    pendingRetry = creation
                                    rewardKind = RewardKind.Credits
                                }
                            },
                        )
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(240.dp)) }
                }
            }
        }

        RewardPrompt(
            kind = rewardKind,
            onGranted = {
                AppCapability.grantRewardedCredits()
                // Có credit rồi thì thử lại luôn bản ghi vừa bấm.
                pendingRetry?.let(vm::onRetry)
            },
            onOpenPaywall = {
                rewardKind = null
                pendingRetry = null
                onOpenPaywall()
            },
            onDismiss = {
                rewardKind = null
                pendingRetry = null
            },
        )

        AppToast(
            visible = toast != null,
            text = toast.orEmpty(),
            onDismiss = { toast = null },
        )
    }
}

@Composable
private fun CreationCard(
    creation: Creation,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .clip(AppShapes.card)
            .background(AppColors.bgSurface)
            .clickable(enabled = creation.status == CreationStatus.SUCCESS, onClick = onClick),
    ) {
        RemoteImage(
            modifier = Modifier.fillMaxSize(),
            model = creation.resultImagePath?.let { File(it) },
            contentDescription = null,
            placeholderSeed = creation.id,
        )
        Box(modifier = Modifier.fillMaxSize().imageScrim(startFraction = 0.55f))

        when (creation.status) {
            CreationStatus.PROCESSING -> StatusOverlay {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AppColors.accentViolet,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.creations_status_processing),
                    style = AppType.caption,
                    color = AppColors.textSecondary,
                )
            }

            CreationStatus.FAILED -> StatusOverlay {
                Text(
                    text = stringResource(R.string.creations_status_failed),
                    style = AppType.caption,
                    color = AppColors.stateDanger,
                )
                Spacer(Modifier.height(6.dp))
                AppChip(
                    text = stringResource(R.string.creations_retry),
                    size = AppChipSize.Small,
                    style = AppChipStyle.Brand,
                    onClick = onRetry,
                )
            }

            CreationStatus.SUCCESS -> Unit
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                text = creation.sourceName,
                style = AppType.label,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DateUtils.timeAgo(creation.createdAt),
                style = AppType.caption,
                color = AppColors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatusOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

private fun filterLabel(filter: CreationFilter): Int = when (filter) {
    CreationFilter.ALL -> R.string.creations_filter_all
    CreationFilter.TEMPLATE -> R.string.creations_filter_template
    CreationFilter.TOOL -> R.string.creations_filter_tool
    CreationFilter.SAVED -> R.string.creations_filter_saved
}

/** Số item đứng trước các ảnh trong lưới (hiện là ô carousel ở đầu). */
private const val GRID_HEADER_ITEMS = 1

/** Số item nhìn trước vị trí cuộn hiện tại để yêu cầu ad cho các ô sắp tới. */
private const val AD_LOOKAHEAD_ITEMS = 8

/** Cứ bấy nhiêu ảnh thì chèn một ô ad (4 ảnh = 2 hàng của lưới 2 cột). */
private const val PHOTOS_PER_AD = 4

/** Một phần tử của lưới: ảnh đã tạo hoặc ô ad (đánh số ô theo thứ tự xuất hiện). */
private sealed interface PhotoEntry {
    val key: String

    data class Photo(val creation: Creation) : PhotoEntry {
        override val key: String get() = "c_${creation.id}"
    }

    data class Ad(val slot: Int) : PhotoEntry {
        override val key: String get() = "ad_$slot"
    }
}

/**
 * Xen một ô ad sau mỗi [PHOTOS_PER_AD] ảnh (không chèn sau ảnh cuối). Khi ít ảnh nên chưa chèn được
 * ô nào (tối đa [PHOTOS_PER_AD] ảnh), đặt một ô ở cuối lưới để màn vẫn có ad.
 */
private fun buildPhotoEntries(creations: List<Creation>, withAds: Boolean): List<PhotoEntry> =
    buildList {
        var inserted = 0
        creations.forEachIndexed { index, creation ->
            add(PhotoEntry.Photo(creation))
            val count = index + 1
            if (withAds && count % PHOTOS_PER_AD == 0 && count < creations.size) {
                add(PhotoEntry.Ad(slot = inserted++))
            }
        }
        if (withAds && inserted == 0 && creations.isNotEmpty()) add(PhotoEntry.Ad(slot = 0))
    }
