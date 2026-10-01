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
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiAction
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.ConfirmPopup
import com.bblvn.composebaseproject.ui.components.ErrorState
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

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {

        val resultPath = state.resultPath
        if (resultPath != null) {
            ResultStage(
                modifier = Modifier.fillMaxSize(),
                resultPath = resultPath,
                sourcePath = state.sourcePath,
                playReveal = state.playReveal,
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
                    onSave = { vm.save() },
                    onShare = vm::share,
                    onRegenerate = { confirmRegenerate = true },
                    onTryOther = onTryOther,
                )
                if (state.showWatermarkNotice) {
                    Spacer(Modifier.height(12.dp))
                    WatermarkNotice(onClick = vm::onWatermarkNoticeClick)
                }
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
                if (vm.regenerate()) onRegenerate()
            },
            onDismiss = { confirmRegenerate = false },
        )

        AppToast(
            visible = toastText != null,
            text = toastText.orEmpty(),
            bottomPaddingPx = with(density) { 120.dp.roundToPx() },
            onDismiss = { toastText = null },
        )
    }
}
