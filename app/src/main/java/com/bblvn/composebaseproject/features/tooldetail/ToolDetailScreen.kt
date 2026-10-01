package com.bblvn.composebaseproject.features.tooldetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.data.domain.ToolType
import com.bblvn.composebaseproject.features.tools.ToolPresentation
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.AppTopBar
import com.bblvn.composebaseproject.ui.components.BottomCtaBar
import com.bblvn.composebaseproject.ui.components.ButtonVariant
import com.bblvn.composebaseproject.ui.components.CompareSlider
import com.bblvn.composebaseproject.ui.components.PhotoSlot
import com.bblvn.composebaseproject.ui.components.PremiumBadge
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import java.io.File

/**
 * One layout serving all five tools; they differ only in slots and options.
 *
 * Scrollable because a 2-slot tool with an option row is taller than a small phone screen.
 */
@Composable
fun ToolDetailScreen(
    tool: ToolType,
    onBack: () -> Unit,
    onPickPhoto: (Int) -> Unit,
    onStart: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ToolDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tool) { vm.bind(tool) }

    // The picker pops straight back here, so re-read the draft whenever this screen is shown again.
    androidx.compose.runtime.LaunchedEffect(tool, Unit) { vm.syncFromDraft(tool) }

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.OpenPaywall -> onOpenPaywall()
            is UiEvent.ShowToast -> toast = event.text.resolve(context)
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 56.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(1f)
                        .clip(AppShapes.card),
                ) {
                    CompareSlider(
                        modifier = Modifier.fillMaxSize(),
                        // Drawable ids, NOT painterResource(): CompareSlider hands `before`/
                        // `after` straight to Coil, which rejects a Painter at runtime.
                        before = ToolPresentation.beforeImage(tool),
                        after = ToolPresentation.afterImage(tool),
                        initialFraction = 0.5f,
                        // Both demo images are the same 1:1 render, so Crop is safe here and fills
                        // the card edge to edge.
                        contentScale = ContentScale.Crop,
                    )
                    AppChip(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        text = stringResource(R.string.tool_example),
                        size = AppChipSize.Small,
                        style = AppChipStyle.Overlay,
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(ToolPresentation.name(tool)),
                            style = AppType.titleL,
                            color = AppColors.textPrimary,
                        )
                        // align = Top on the Row, so a 2-line tool name does not drag the badge
                        // down to the vertical centre (R1).
                        if (tool.isPremium) PremiumBadge()
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(ToolPresentation.description(tool)),
                        style = AppType.body,
                        color = AppColors.textSecondary,
                    )
                }
            }

            item {
                ToolSlots(
                    tool = tool,
                    slots = state.slots,
                    errorText = state.slotError?.resolve(context),
                    enabled = !state.isValidatingFace,
                    onPick = onPickPhoto,
                    onClear = vm::onClearSlot,
                )
            }

            val optionTitle = ToolPresentation.optionTitle(tool)
            if (optionTitle != null && tool.options.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                        Text(
                            text = stringResource(optionTitle),
                            style = AppType.label,
                            color = AppColors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        // Natural widths with a fixed gap: the option count varies per tool, so
                        // stretching them would make a 2-option row look unlike a 3-option one.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tool.options.forEach { option ->
                                AppChip(
                                    text = stringResource(ToolPresentation.optionLabel(option)),
                                    size = AppChipSize.Medium,
                                    style = if (state.option == option) {
                                        AppChipStyle.Brand
                                    } else {
                                        AppChipStyle.Neutral
                                    },
                                    selected = state.option == option,
                                    onClick = { vm.onOptionSelected(option) },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(240.dp)) }
        }

        AppTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = stringResource(ToolPresentation.name(tool)),
            onBack = onBack,
        )

        BottomCtaBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            PrimaryButton(
                text = ctaLabel(state),
                icon = if (state.locked) R.drawable.ic_lock else R.drawable.ic_sparkles,
                enabled = state.canStart || state.locked,
                variant = if (state.locked) ButtonVariant.Gold else ButtonVariant.Brand,
                onClick = { vm.onStart(onStart) },
            )
        }

        AppToast(
            visible = toast != null,
            text = toast.orEmpty(),
            icon = R.drawable.ic_alert_circle,
            iconTint = AppColors.stateWarning,
            onDismiss = { toast = null },
        )
    }
}

@Composable
private fun ctaLabel(state: ToolDetailUiState): String = when {
    state.locked -> stringResource(R.string.tool_cta_unlock)
    state.isBusy -> stringResource(R.string.tool_cta_busy)
    state.isValidatingFace -> stringResource(R.string.tool_cta_checking)
    !state.hasAllPhotos -> stringResource(R.string.tool_cta_pick_photo)
    else -> stringResource(R.string.tool_cta_start)
}

@Composable
private fun ToolSlots(
    tool: ToolType,
    slots: List<String?>,
    errorText: String?,
    enabled: Boolean,
    onPick: (Int) -> Unit,
    onClear: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (tool.requiredImageCount == 1) {
            PhotoSlot(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(ToolPresentation.slotLabel(tool.slots[0])),
                imageModel = slots.getOrNull(0)?.let { File(it) },
                errorText = errorText,
                enabled = enabled,
                onPick = { onPick(0) },
                onClear = if (slots.getOrNull(0) != null) {
                    { onClear(0) }
                } else {
                    null
                },
            )
        } else {
            // Slot order is a contract with the prompt, so the labels must stay put: index 0 is
            // always the face/person, index 1 the scene/garment (Risks #17).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tool.slots.forEachIndexed { index, role ->
                    PhotoSlot(
                        modifier = Modifier.weight(1f),
                        label = stringResource(ToolPresentation.slotLabel(role)),
                        imageModel = slots.getOrNull(index)?.let { File(it) },
                        errorText = if (index == 0) errorText else null,
                        enabled = enabled,
                        onPick = { onPick(index) },
                        onClear = if (slots.getOrNull(index) != null) {
                            { onClear(index) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
