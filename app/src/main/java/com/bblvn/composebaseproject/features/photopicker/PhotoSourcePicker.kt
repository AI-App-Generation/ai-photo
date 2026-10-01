package com.bblvn.composebaseproject.features.photopicker

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.utils.IntentUtils
import com.bblvn.composebaseproject.utils.PermissionUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Replacement for the custom [PhotoPickerScreen]: a small "Gallery / Camera" sheet where Gallery
 * opens the system Photo Picker (`PickVisualMedia`). The system picker needs no storage
 * permission, so the app no longer declares READ_MEDIA_IMAGES.
 *
 * The contract with the rest of the flow is unchanged: the picked photo is copied into `sources/`,
 * written to [GenerationDraft] at the requested slot, then [onPicked] runs — exactly what
 * [PhotoPickerViewModel.onPhotoPicked] / [PhotoPickerViewModel.onCameraCaptured] did.
 *
 * To go back to the custom screen, see the `PHOTO_PICKER` notes in `MainContent.kt`.
 */
@Stable
class PhotoSourcePickerState internal constructor() {
    internal var slotIndex by mutableStateOf<Int?>(null)

    /** Open the source sheet for [slot]. Slot order is the caller's contract (spec R5). */
    fun open(slot: Int) {
        slotIndex = slot
    }
}

@Composable
fun rememberPhotoSourcePicker(): PhotoSourcePickerState = remember { PhotoSourcePickerState() }

@Composable
fun PhotoSourceSheet(
    state: PhotoSourcePickerState,
    onPicked: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    var toast by remember { mutableStateOf<String?>(null) }
    var isCopying by remember { mutableStateOf(false) }
    // Slot being filled while the external activity is open (the sheet is already closed).
    // Saveable: the activity can be recreated while the picker/camera is in front.
    var pendingSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var cameraTargetPath by rememberSaveable { mutableStateOf<String?>(null) }
    val welcomeGuard = rememberWelcomeBackGuard()

    fun finishPick(slot: Int, path: String) {
        GenerationDraft.setSlot(slot, path)
        currentOnPicked()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot == null || isCopying) return@rememberLauncherForActivityResult
        isCopying = true
        scope.launch {
            // Copy, never keep the uri: the picker's read grant does not outlive this task and
            // generation can run minutes later (same reason as PhotoPickerViewModel).
            val result = FileManager.copyUriToSources(uri)
            isCopying = false
            result
                .onSuccess { finishPick(slot, it.absolutePath) }
                .onFailure { toast = context.getString(R.string.picker_error_copy_failed) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val slot = pendingSlot
        val target = cameraTargetPath?.let(::File)
        pendingSlot = null
        cameraTargetPath = null
        if (result.resultCode == android.app.Activity.RESULT_OK &&
            slot != null && target != null && target.length() > 0L
        ) {
            finishPick(slot, target.absolutePath)
        } else {
            // A cancelled capture leaves a zero-byte file behind; don't keep it.
            target?.delete()
        }
    }

    fun launchCamera(slot: Int) {
        val prepared = IntentUtils.captureImage(context)
        if (prepared == null) {
            toast = context.getString(R.string.picker_error_camera)
            return
        }
        pendingSlot = slot
        cameraTargetPath = prepared.second.absolutePath
        welcomeGuard.arm()
        runCatching { cameraLauncher.launch(prepared.first) }.onFailure {
            Log.w(TAG, "Camera launch failed", it)
            prepared.second.delete()
            cameraTargetPath = null
            pendingSlot = null
            welcomeGuard.disarm()
            toast = context.getString(R.string.picker_error_camera)
        }
    }

    // CAMERA is declared in the manifest, so ACTION_IMAGE_CAPTURE throws unless it is granted.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val slot = pendingSlot
        pendingSlot = null
        if (granted && slot != null) launchCamera(slot)
    }

    val openSlot = state.slotIndex
    if (openSlot != null) {
        SourceSheet(
            onGallery = {
                state.slotIndex = null
                GenerationDraft.setActiveSlot(openSlot)
                pendingSlot = openSlot
                welcomeGuard.arm()
                runCatching {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }.onFailure {
                    Log.w(TAG, "Photo picker launch failed", it)
                    pendingSlot = null
                    welcomeGuard.disarm()
                    toast = context.getString(R.string.picker_error_copy_failed)
                }
            },
            onCamera = {
                state.slotIndex = null
                GenerationDraft.setActiveSlot(openSlot)
                if (PermissionUtils.hasCamera(context)) {
                    launchCamera(openSlot)
                } else {
                    pendingSlot = openSlot
                    cameraPermissionLauncher.launch(PermissionUtils.CAMERA_PERMISSION)
                }
            },
            onDismiss = { state.slotIndex = null },
        )
    }

    AppToast(
        visible = toast != null,
        text = toast.orEmpty(),
        icon = R.drawable.ic_alert_circle,
        iconTint = AppColors.stateDanger,
        onDismiss = { toast = null },
    )
}

/**
 * Bottom sheet drawn as an in-tree overlay rather than a [androidx.compose.ui.window.Popup]: a
 * Popup window is limited to the area between the system bars, so its scrim can't cover the
 * status/navigation bars in this edge-to-edge app. The caller must place [PhotoSourceSheet] after
 * the screen content in a full-size Box so it draws on top.
 */
@Composable
private fun SourceSheet(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgScrim.copy(alpha = 0.55f))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.sheet)
                .background(AppColors.bgSurface)
                .border(1.dp, AppColors.strokeSoft, AppShapes.sheet)
                // Swallow clicks so tapping the sheet does not dismiss it.
                .clickable(interactionSource = interaction, indication = null, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(AppColors.strokeSoft),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                text = stringResource(R.string.picker_title),
                style = AppType.titleM,
                color = AppColors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            SourceRow(
                icon = R.drawable.ic_image,
                label = stringResource(R.string.picker_source_gallery),
                onClick = onGallery,
            )
            SourceRow(
                icon = R.drawable.ic_camera,
                label = stringResource(R.string.picker_take_photo),
                onClick = onCamera,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SourceRow(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppColors.bgElevated)
                .border(1.dp, AppColors.strokeSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AppColors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text = label, style = AppType.label, color = AppColors.textPrimary)
    }
}

/**
 * Leaving for the system picker or camera backgrounds the app, which would trigger the Welcome
 * Back ad on return. The old picker screen used `DisableWelcomeOverlayTemporarily()` for its whole
 * lifetime; here only the one round trip is suppressed so the Detail screens keep normal ads.
 *
 * `BaseActivity.onResume` posts its check; the lifecycle ON_RESUME observer runs after it and posts
 * the re-enable, so the re-enable lands after the check.
 */
private class WelcomeBackGuard {
    var armed = false
        private set

    fun arm() {
        armed = true
        AdsPolicy.allowShowWelcomeBack = false
    }

    fun disarm() {
        if (!armed) return
        armed = false
        AdsPolicy.allowShowWelcomeBack = true
    }
}

@Composable
private fun rememberWelcomeBackGuard(): WelcomeBackGuard {
    val guard = remember { WelcomeBackGuard() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val handler = Handler(Looper.getMainLooper())
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && guard.armed) {
                handler.post { guard.disarm() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            guard.disarm()
        }
    }
    return guard
}

private const val TAG = "PhotoSourcePicker"
