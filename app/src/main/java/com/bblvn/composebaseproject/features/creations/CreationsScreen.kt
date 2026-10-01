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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationFilter
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.EmptyState
import com.bblvn.composebaseproject.ui.components.RemoteImage
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
    modifier: Modifier = Modifier,
    vm: CreationsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

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
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = stringResource(R.string.creations_empty_title),
                    message = stringResource(R.string.creations_empty_message),
                    illustration = R.drawable.empty_creations,
                    actionText = stringResource(R.string.creations_empty_action),
                    onAction = onExplore,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visible, key = { it.id }) { creation ->
                        CreationCard(
                            creation = creation,
                            onClick = {
                                if (creation.status == CreationStatus.SUCCESS) {
                                    onOpenCreation(creation.id)
                                }
                            },
                            onRetry = { vm.onRetry(creation) },
                        )
                    }
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(240.dp)) }
                }
            }
        }

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
