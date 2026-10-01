package com.bblvn.composebaseproject.features.photopicker

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.DisableWelcomeOverlayTemporarily
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.EmptyState
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.RemoteImage
import com.bblvn.composebaseproject.ui.components.SecondaryButton
import com.bblvn.composebaseproject.ui.components.ShimmerBox
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.utils.IntentUtils
import com.bblvn.composebaseproject.utils.PermissionUtils
import com.bblvn.composebaseproject.utils.PhotoAccess

/**
 * Single-select gallery picker. Tapping a photo fills [slotIndex] and returns immediately — there
 * is no multi-select and no confirm bar, because slot order is decided by the caller (spec R5).
 */
@Composable
fun PhotoPickerScreen(
    slotIndex: Int,
    onClose: () -> Unit,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PhotoPickerViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

    // The user is mid-task here; a Welcome Back ad overlay on resume would break the flow.
    DisableWelcomeOverlayTemporarily()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        val activity = context as? Activity
        // shouldShowRequestPermissionRationale is only meaningful right after a denial, which is
        // exactly where we are, so this correctly separates "declined once" from "don't ask again".
        val permanentlyDenied = activity != null &&
            PermissionUtils.isPermanentlyDenied(activity, PermissionUtils.photoPermission)
        vm.onPermissionResult(granted, canAskAgain = !permanentlyDenied)
    }

    val cameraTarget = remember { mutableStateOf<java.io.File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = cameraTarget.value
        if (result.resultCode == Activity.RESULT_OK && target != null && target.length() > 0L) {
            vm.onCameraCaptured(slotIndex, target.absolutePath)
        } else {
            // A cancelled capture leaves a zero-byte file behind; don't keep it.
            target?.delete()
        }
        cameraTarget.value = null
    }

    LaunchedEffect(slotIndex) {
        vm.onSlotChanged(slotIndex)
        vm.refreshAccess()
    }

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.NavigateBack -> onPicked()
            is UiEvent.ShowToast -> toast = event.text.resolve(context)
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        Column(modifier = Modifier.fillMaxSize()) {
            PickerTopBar(
                slotLabel = slotLabelFor(slotIndex),
                albumName = state.selectedAlbum?.name,
                albumPickerEnabled = state.access != PhotoAccess.DENIED && state.albums.size > 1,
                onClose = onClose,
                onAlbumClick = { vm.onAlbumSheetToggle(true) },
                onCameraClick = {
                    val prepared = IntentUtils.captureImage(context)
                    if (prepared == null) {
                        vm.onCameraFailed()
                    } else {
                        cameraTarget.value = prepared.second
                        cameraLauncher.launch(prepared.first)
                    }
                },
            )

            when {
                state.access == PhotoAccess.DENIED -> PermissionState(
                    permanentlyDenied = state.permanentlyDenied,
                    onGrant = { permissionLauncher.launch(PermissionUtils.photoRequestPermissions()) },
                    onOpenSettings = { IntentUtils.openAppSettings(context) },
                )

                state.isLoading && state.photos.isEmpty() -> LoadingGrid()

                state.isEmpty -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = stringResource(R.string.picker_empty_title),
                    message = stringResource(R.string.picker_empty_message),
                    icon = R.drawable.ic_image_off,
                    actionText = stringResource(R.string.picker_take_photo),
                    onAction = {
                        val prepared = IntentUtils.captureImage(context)
                        if (prepared == null) {
                            vm.onCameraFailed()
                        } else {
                            cameraTarget.value = prepared.second
                            cameraLauncher.launch(prepared.first)
                        }
                    },
                )

                else -> PhotoGrid(
                    state = state,
                    onPick = { vm.onPhotoPicked(slotIndex, it) },
                )
            }
        }

        if (state.access == PhotoAccess.PARTIAL) {
            PartialAccessBanner(
                modifier = Modifier.align(Alignment.BottomCenter),
                onSelectMore = { permissionLauncher.launch(PermissionUtils.photoRequestPermissions()) },
            )
        }

        AlbumPopup(
            visible = state.albumSheetOpen,
            albums = state.albums,
            selectedId = state.selectedAlbumId,
            onSelect = vm::onAlbumSelected,
            onDismiss = { vm.onAlbumSheetToggle(false) },
        )

        AppToast(
            visible = toast != null,
            text = toast.orEmpty(),
            icon = R.drawable.ic_alert_circle,
            iconTint = AppColors.stateDanger,
            onDismiss = { toast = null },
        )
    }
}

@Composable
private fun PickerTopBar(
    slotLabel: Int?,
    albumName: String?,
    albumPickerEnabled: Boolean,
    onClose: () -> Unit,
    onAlbumClick: () -> Unit,
    onCameraClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.picker_close),
            onClick = onClose,
        )
        Spacer(Modifier.width(4.dp))
        if (albumPickerEnabled) {
            AppChip(
                text = albumName ?: stringResource(R.string.picker_album_all),
                trailingIcon = R.drawable.ic_chevron_down,
                size = AppChipSize.Medium,
                onClick = onAlbumClick,
            )
        } else {
            Text(
                text = albumName ?: stringResource(R.string.picker_title),
                style = AppType.titleM,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        // Which slot is being filled, so the user knows what this photo is for in 2-image flows.
        if (slotLabel != null) {
            AppChip(text = stringResource(slotLabel), size = AppChipSize.Small)
            Spacer(Modifier.width(4.dp))
        }
        AppIconButton(
            icon = R.drawable.ic_camera,
            contentDescription = stringResource(R.string.picker_take_photo),
            onClick = onCameraClick,
        )
    }
}

@Composable
private fun PhotoGrid(
    state: PhotoPickerUiState,
    onPick: (GalleryPhoto) -> Unit,
) {
    val photos = state.visiblePhotos
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !state.isCopying) { onPick(photo) },
            ) {
                RemoteImage(
                    modifier = Modifier.fillMaxSize(),
                    model = photo.uri,
                    contentDescription = null,
                    showShimmer = true,
                )
            }
        }
        item(span = { GridItemSpan(3) }) {
            Spacer(Modifier.height(240.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(30) {
            ShimmerBox(
                modifier = Modifier.aspectRatio(1f),
                shape = RoundedCornerShape(4.dp),
            )
        }
    }
}

@Composable
private fun PermissionState(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_image_lock),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AppColors.textTertiary),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.picker_permission_title),
            style = AppType.titleM,
            color = AppColors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.picker_permission_message),
            style = AppType.body,
            color = AppColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            modifier = Modifier.width(220.dp),
            text = stringResource(R.string.picker_permission_grant),
            fillMaxWidth = false,
            height = 48.dp,
            onClick = onGrant,
        )
        if (permanentlyDenied) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = stringResource(R.string.picker_permission_settings),
                onClick = onOpenSettings,
            )
        }
    }
}

/**
 * Android 14+ can grant access to a chosen subset of photos. Showing an empty-looking grid in that
 * case reads as a bug, so the banner explains it and offers a way to add more (Risks #26).
 */
@Composable
private fun PartialAccessBanner(
    modifier: Modifier = Modifier,
    onSelectMore: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .clip(AppShapes.card)
            .background(AppColors.bgElevated)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.picker_partial_access),
            style = AppType.caption,
            color = AppColors.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        AppChip(
            text = stringResource(R.string.picker_select_more),
            size = AppChipSize.Small,
            onClick = onSelectMore,
        )
    }
}

@Composable
private fun AlbumPopup(
    visible: Boolean,
    albums: List<GalleryAlbum>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 12.dp, end = 12.dp)
                    .clip(AppShapes.card)
                    .background(AppColors.bgSurface)
                    .heightIn(max = 420.dp),
            ) {
                LazyColumn {
                    items(albums, key = { it.id }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(album.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RemoteImage(
                                modifier = Modifier.size(44.dp).clip(AppShapes.thumb),
                                model = album.coverUri,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = AppType.label,
                                    color = if (album.id == selectedId) {
                                        AppColors.accentViolet
                                    } else {
                                        AppColors.textPrimary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = album.count.toString(),
                                    style = AppType.caption,
                                    color = AppColors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Slot 1 of a two-image flow gets a hint chip; single-image flows need none. */
private fun slotLabelFor(slotIndex: Int): Int? =
    if (slotIndex > 0) R.string.picker_slot_second else null
