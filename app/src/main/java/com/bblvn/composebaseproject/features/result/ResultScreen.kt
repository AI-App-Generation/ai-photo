package com.bblvn.composebaseproject.features.result

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.AppCapability
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiAction
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.ConfirmPopup
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.components.RewardPrompt
import com.bblvn.composebaseproject.ui.components.RewardKind
import com.bblvn.composebaseproject.ui.components.MenuEntry
import com.bblvn.composebaseproject.ui.components.MenuPopup
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.utils.IntentUtils
import com.bblvn.composebaseproject.utils.PermissionUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * 10. Result — the payoff screen [Complex UI].
 *
 * Two modes behind one composable:
 *  - **live** (`replay == false`): the image has just been generated, so it is revealed — the
 *    compare handle wipes 0f -> 1f over 900ms (easeOutCubic) while the stage fades in and scales
 *    0.96 -> 1.0. Afterwards the handle is the user's to drag.
 *  - **replay** (`replay == true`, opened from Creations): no reveal, the result is simply there.
 *
 * Not scrollable by design (R4): a full-bleed image with a fixed floating action bar.
 */
@Composable
fun ResultScreen(
    creationId: String?,
    replay: Boolean,
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    onTryOther: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ResultViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val activity = LocalActivity.current
    val adsActive by AdsLoader.isActive.collectAsState()

    // Chiều cao ô native_result ở đáy, để vùng ảnh co lại đúng bằng nó và ảnh không bị ad che.
    var adHeight by remember { mutableStateOf(0.dp) }
    // Chặn bấm Save lần hai trong lúc inter còn đang nạp/hiện.
    var saveAdBusy by remember { mutableStateOf(false) }
    // Dialog "xem quảng cáo thưởng hoặc mở paywall": Save của user free, hoặc Regenerate khi hết credit.
    var rewardKind by remember { mutableStateOf<RewardKind?>(null) }

    // Lưu CÓ watermark: chen inter_save rồi mới lưu; đóng, lỗi hay bị bỏ qua đều gọi lưu. force = true
    // vì inter này không chịu giãn cách 30s của inter_tab/open_*.
    fun saveWithWatermark() {
        val host = activity
        if (host == null) {
            vm.save()
        } else if (!saveAdBusy) {
            saveAdBusy = true
            AdsPolicy.showInterAd(
                activity = host,
                adPlacement = AdsPlacementHolder.INTER_SAVE,
                force = true,
                onContinue = {
                    saveAdBusy = false
                    vm.save()
                },
            )
        }
    }

    var toastText by remember { mutableStateOf<String?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }

    LaunchedEffect(creationId, replay) { vm.bind(creationId, replay) }

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.ShowToast -> toastText = event.text.resolve(context)
            is UiEvent.ShowError -> toastText = event.text.resolve(context)
            is UiEvent.NavigateBack -> onBack()
            is UiEvent.OpenPaywall -> onOpenPaywall()
            is UiEvent.RunAction -> when (val action = event.action) {
                // IntentUtils.shareImage is suspend and applies the free-user watermark rule
                // itself via FileManager.prepareForShare — the screen never stages or deletes.
                is UiAction.ShareImage -> scope.launch {
                    val ok = IntentUtils.shareImage(
                        context = context,
                        file = File(action.filePath),
                        chooserTitle = context.getString(R.string.result_share_chooser),
                    )
                    if (!ok) vm.onShareFailed()
                }

                else -> Unit
            }

            else -> Unit
        }
    }

    // API 24-28 only: saving writes the public Pictures dir and needs WRITE_EXTERNAL_STORAGE.
    // FileManager reports the missing permission as MissingGalleryPermissionException; the
    // ViewModel bumps a token, we ask, and the save is retried exactly once (C6).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.save(afterPermissionGrant = true) else vm.onGalleryPermissionDenied()
    }
    LaunchedEffect(state.galleryPermissionToken) {
        if (state.galleryPermissionToken == 0) return@LaunchedEffect
        val permission = PermissionUtils.galleryWritePermission
        if (permission == null) vm.save(afterPermissionGrant = true)
        else permissionLauncher.launch(permission)
    }

    var viewerOf by remember { mutableStateOf<String?>(null) }
    var viewerIsResult by remember { mutableStateOf(false) }
    val isPremiumNow by com.bblvn.composebaseproject.core.AppCapability.isPremium.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {

        val resultPath = state.resultPath
        if (resultPath != null) {
            ResultStage(
                modifier = Modifier.fillMaxSize().padding(bottom = if (adsActive) adHeight else 0.dp),
                resultPath = resultPath,
                sourcePath = state.sourcePath,
                playReveal = state.playReveal,
                onOpenImage = { isSource -> viewerOf = if (isSource) state.sourcePath else resultPath; viewerIsResult = !isSource },
            )
        } else if (state.isMissing) {
            // An old creation whose result file was pruned, or a row already deleted.
            ErrorState(
                title = stringResource(R.string.result_missing_title),
                message = stringResource(R.string.result_missing_message),
                icon = R.drawable.ic_image_off,
                actionText = stringResource(R.string.result_action_try_other),
                onAction = onTryOther,
            )
        }

        ResultTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = state.title,
            onBack = onBack,
            onMore = { menuVisible = true },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            BottomFade()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.bgRoot)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                ResultActionRow(
                    canRegenerate = state.canRegenerate,
                    enabled = resultPath != null && !state.isBusy,
                    // User free: luôn hỏi "xem quảng cáo để lưu không watermark / mở paywall / lưu có
                    // watermark". Premium lưu thẳng (không có watermark, cũng không có quảng cáo).
                    onSave = {
                        if (state.isPremium) saveWithWatermark() else rewardKind = RewardKind.HqSave
                    },
                    onShare = vm::share,
                    onRegenerate = { confirmRegenerate = true },
                    onTryOther = onTryOther,
                )
                if (state.showWatermarkNotice) {
                    Spacer(Modifier.height(12.dp))
                    WatermarkNotice(onClick = vm::onWatermarkNoticeClick)
                }
                // native_result: dưới 4 nút. Dùng cỡ vừa (144dp) để ảnh kết quả vẫn là nhân vật chính.
                ComposeNativeAdFrame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .onSizeChanged { size ->
                            // Cộng thêm 12dp padding phía trên ô để ảnh chừa đủ chỗ.
                            adHeight = with(density) { size.height.toDp() } + 12.dp
                        },
                    layoutResId = R.layout.frame_ad_medium,
                    templateAdLayoutRes = R.layout.template_ad_medium,
                    adPlacement = AdsPlacementHolder.NATIVE_RESULT,
                )
            }
        }

        // Anchor the menu just below the overflow button (status bar + 8dp padding + 40dp button).
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val menuOffset = with(density) {
            IntOffset((-4).dp.roundToPx(), (statusBarTop + 52.dp).roundToPx())
        }
        MenuPopup(
            visible = menuVisible,
            alignment = Alignment.TopEnd,
            offset = menuOffset,
            onDismiss = { menuVisible = false },
            items = buildList {
                if (state.sourcePath != null) {
                    add(
                        MenuEntry(
                            text = stringResource(R.string.result_menu_save_source),
                            icon = R.drawable.ic_image,
                            onClick = vm::saveSource,
                        )
                    )
                }
                add(
                    MenuEntry(
                        text = stringResource(R.string.result_menu_report),
                        icon = R.drawable.ic_alert_triangle,
                        onClick = vm::reportBadImage,
                    )
                )
                add(
                    MenuEntry(
                        text = stringResource(R.string.result_menu_delete),
                        icon = R.drawable.ic_trash,
                        danger = true,
                        onClick = { confirmDelete = true },
                    )
                )
            },
        )

        ConfirmPopup(
            visible = confirmDelete,
            title = stringResource(R.string.result_delete_title),
            message = stringResource(R.string.result_delete_message),
            confirmText = stringResource(R.string.result_delete_confirm),
            cancelText = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.deleteCreation()
            },
            onDismiss = { confirmDelete = false },
        )

        // Risks #24 — regenerating uses seed = -1, so the new image WILL differ from this one.
        // Saying so up front stops users reading the difference as a bug.
        ConfirmPopup(
            visible = confirmRegenerate,
            title = stringResource(R.string.result_regenerate_title),
            message = stringResource(R.string.result_regenerate_message),
            confirmText = stringResource(R.string.result_regenerate_confirm),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                confirmRegenerate = false
                // Hết credit: hỏi xem quảng cáo thưởng (hoặc mở paywall) thay vì chỉ báo lỗi.
                if (!AppCapability.canAffordGeneration()) {
                    rewardKind = RewardKind.Credits
                } else if (vm.regenerate()) {
                    onRegenerate()
                }
            },
            onDismiss = { confirmRegenerate = false },
        )

        RewardPrompt(
            kind = rewardKind,
            onGranted = { kind ->
                when (kind) {
                    // Xem hết quảng cáo: lưu ngay, không watermark (chỉ cho lần này).
                    RewardKind.HqSave -> vm.save(noWatermark = true)
                    // Có credit rồi thì Regenerate luôn.
                    RewardKind.Credits -> {
                        AppCapability.grantRewardedCredits()
                        if (vm.regenerate()) onRegenerate()
                    }
                    else -> Unit
                }
            },
            onOpenPaywall = {
                rewardKind = null
                onOpenPaywall()
            },
            onDismiss = { rewardKind = null },
            // Chỉ Save mới có lựa chọn thứ ba: chấp nhận watermark.
            onSkip = if (rewardKind == RewardKind.HqSave) {
                {
                    rewardKind = null
                    saveWithWatermark()
                }
            } else {
                null
            },
            skipText = R.string.reward_save_with_watermark,
        )

        viewerOf?.let { path ->
            ResultImageViewer(
                path = path,
                showWatermark = viewerIsResult && !isPremiumNow,
                onClose = { viewerOf = null },
            )
        }

        AppToast(
            visible = toastText != null,
            text = toastText.orEmpty(),
            bottomPaddingPx = with(density) { 120.dp.roundToPx() },
            onDismiss = { toastText = null },
        )
    }
}
