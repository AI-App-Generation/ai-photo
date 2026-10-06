package com.bblvn.composebaseproject.features.templatedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.BottomCtaBar
import com.bblvn.composebaseproject.ui.components.ButtonVariant
import com.bblvn.composebaseproject.ui.components.EmptyState
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.RewardPrompt
import com.bblvn.composebaseproject.ui.components.RewardKind
import com.bblvn.composebaseproject.ui.components.ShimmerBox
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes

/**
 * Template Detail — Layout spec section 5.
 *
 * Structure: a full-bleed 3:4 compare-slider hero that runs under the status bar, the info block
 * (name + PRO badge + meta chips + an app-composed description), two photo slots for two-image
 * templates only (R5), a "Cùng phong cách" strip, and a floating CTA whose four states all share
 * the same 56dp height (R1 / R6.3).
 *
 * The signature is the contract `features/nav/MainContent.kt` wires against — do not change it.
 */
@Composable
fun TemplateDetailScreen(
    templateId: String,
    onBack: () -> Unit,
    onPickPhoto: (Int) -> Unit,
    onStart: () -> Unit,
    onOpenPaywall: () -> Unit,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: TemplateDetailViewModel = viewModel(),
) {
    LaunchedEffect(templateId) { vm.bind(templateId) }
    val state by vm.state.collectAsState()
    val template = state.template

    // Dialog "xem quảng cáo thưởng hoặc mở paywall": hiện khi template bị khoá PRO, hoặc khi hết
    // credit ở nút bắt đầu (chặn từ sớm, trước khi sang Adjust / chọn ảnh).
    var rewardKind by remember { mutableStateOf<RewardKind?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.bgRoot),
    ) {
        when {
            template != null -> TemplateDetailContent(
                state = state,
                template = template,
                onPickPhoto = { slot ->
                    vm.onPickSlot(slot)
                    onPickPhoto(slot)
                },
                onClearPhoto = vm::onClearSlot,
                onToggleDescription = vm::onToggleDescription,
                onStart = onStart,
                onOpenPaywall = onOpenPaywall,
                onTemplateClick = onTemplateClick,
                onNeedCredits = { rewardKind = RewardKind.Credits },
                onNeedUnlock = { rewardKind = RewardKind.UnlockTemplate },
            )

            state.isLoading -> TemplateDetailSkeleton()

            else -> EmptyState(
                modifier = Modifier.padding(bottom = 48.dp),
                title = stringResource(R.string.template_detail_not_found_title),
                message = stringResource(R.string.template_detail_not_found_message),
                actionText = stringResource(R.string.back),
                onAction = onBack,
            )
        }

        // Floats over the hero; owns its own status-bar inset (the hero has none).
        AppIconButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            icon = R.drawable.ic_arrow_left,
            contentDescription = stringResource(R.string.back),
            background = Color.Black.copy(alpha = 0.35f),
            onClick = onBack,
        )

        RewardPrompt(
            kind = rewardKind,
            onGranted = { kind ->
                when (kind) {
                    // Cộng một lượt mở khoá cho template này; nút khoá tự đổi thành nút bắt đầu.
                    RewardKind.UnlockTemplate ->
                        AppCapability.grantUnlock(AppCapability.templateUnlockKey(templateId))
                    RewardKind.Credits -> AppCapability.grantRewardedCredits()
                    else -> Unit
                }
            },
            onOpenPaywall = {
                rewardKind = null
                onOpenPaywall()
            },
            onDismiss = { rewardKind = null },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TemplateDetailContent(
    state: TemplateDetailUiState,
    template: Template,
    onPickPhoto: (Int) -> Unit,
    onClearPhoto: (Int) -> Unit,
    onToggleDescription: () -> Unit,
    onStart: () -> Unit,
    onOpenPaywall: () -> Unit,
    onTemplateClick: (String) -> Unit,
    onNeedCredits: () -> Unit,
    onNeedUnlock: () -> Unit,
) {
    val description = templateDescription(template, state.categoryName)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "hero") { TemplateHero(template) }

        item(key = "info") {
            TemplateInfo(
                template = template,
                categoryName = state.categoryName,
                description = description,
                expanded = state.descriptionExpanded,
                onToggleExpand = onToggleDescription,
                showProBadge = state.isLocked,
            )
        }

        // native_template_detail: dưới đoạn mô tả, trên khối "Cùng phong cách". Ô tự ẩn khi không có ad.
        item(key = "ad_native") {
            ComposeNativeAdFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                layoutResId = R.layout.frame_ad_inline,
                adPlacement = AdsPlacementHolder.NATIVE_TEMPLATE_DETAIL,
            )
        }

        // R5 — one-image templates have NO slots at all; the CTA opens the picker instead.
        if (state.showsSlots) {
            item(key = "slots") {
                TemplateInputSlots(
                    slots = state.slots,
                    onPick = onPickPhoto,
                    onClear = onClearPhoto,
                )
            }
        }

        if (state.related.isNotEmpty()) {
            item(key = "related") {
                MoreFromCategory(templates = state.related, onTemplateClick = onTemplateClick)
            }
        }

        // Nothing may hide behind the floating CTA.
        item(key = "tail") { Spacer(Modifier.height(240.dp)) }
    }

    TemplateDetailCta(
        modifier = Modifier.align(Alignment.BottomCenter),
        state = state,
        onPickPhoto = onPickPhoto,
        onStart = onStart,
        onOpenPaywall = onOpenPaywall,
        onNeedCredits = onNeedCredits,
        onNeedUnlock = onNeedUnlock,
    )
}

/**
 * The CTA. Four states, ONE fixed 56dp height so the button never resizes when the state flips
 * (R1): brand gradient when the template is usable, gold when it is locked behind PRO, and the
 * neutral disabled look while photos are missing or another generation is still running (R6.3).
 */
@Composable
private fun TemplateDetailCta(
    state: TemplateDetailUiState,
    onPickPhoto: (Int) -> Unit,
    onStart: () -> Unit,
    onOpenPaywall: () -> Unit,
    onNeedCredits: () -> Unit,
    onNeedUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cta = state.ctaState
    val text = stringResource(
        when (cta) {
            TemplateCtaState.Busy -> R.string.template_detail_cta_busy
            TemplateCtaState.Locked -> R.string.template_detail_cta_unlock
            TemplateCtaState.NeedsPhotos -> R.string.template_detail_cta_need_photos
            TemplateCtaState.Ready, TemplateCtaState.PickPhoto -> R.string.template_detail_cta_use
        }
    )
    val icon = when (cta) {
        TemplateCtaState.Locked -> R.drawable.ic_lock
        TemplateCtaState.Ready, TemplateCtaState.PickPhoto -> R.drawable.ic_sparkles
        else -> null
    }

    BottomCtaBar(modifier = modifier) {
        PrimaryButton(
            text = text,
            icon = icon,
            enabled = cta != TemplateCtaState.Busy && cta != TemplateCtaState.NeedsPhotos,
            variant = if (cta == TemplateCtaState.Locked) ButtonVariant.Gold else ButtonVariant.Brand,
            // Giá 1 credit hiện trên mọi nút dẫn tới tạo ảnh; nút khoá PRO / đang bận thì không có giá.
            cost = when (cta) {
                TemplateCtaState.Ready, TemplateCtaState.PickPhoto, TemplateCtaState.NeedsPhotos ->
                    AppCapability.COST_PER_GENERATION
                TemplateCtaState.Locked, TemplateCtaState.Busy -> null
            },
            onClick = {
                when (cta) {
                    // Template PRO: mở dialog "Watch ad" / "Unlock with PRO" thay vì nhảy thẳng sang paywall.
                    TemplateCtaState.Locked -> onNeedUnlock()
                    // One-image template with nothing picked: the single-select picker comes
                    // first, and the CTA then proceeds to the Adjust step (R5). Hết credit thì chặn
                    // luôn ở đây, đừng để user chọn ảnh xong mới biết không tạo được.
                    TemplateCtaState.PickPhoto ->
                        if (AppCapability.canAffordGeneration()) onPickPhoto(0) else onNeedCredits()
                    TemplateCtaState.Ready ->
                        if (AppCapability.canAffordGeneration()) onStart() else onNeedCredits()
                    TemplateCtaState.Busy, TemplateCtaState.NeedsPhotos -> Unit
                }
            },
        )
    }
}

/** Same footprint as the real screen so the transition into it does not jump. */
@Composable
private fun TemplateDetailSkeleton() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                shape = androidx.compose.ui.graphics.RectangleShape,
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            ShimmerBox(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(28.dp),
                shape = AppShapes.chip,
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            ShimmerBox(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(60.dp),
                shape = AppShapes.field,
            )
        }
        item { Spacer(Modifier.height(240.dp)) }
    }
}
