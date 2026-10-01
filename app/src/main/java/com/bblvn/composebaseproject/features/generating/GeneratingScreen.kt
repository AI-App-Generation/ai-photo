package com.bblvn.composebaseproject.features.generating

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.components.ConfirmPopup
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.RemoteImage
import com.bblvn.composebaseproject.ui.components.TextActionButton
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import kotlinx.coroutines.delay
import java.io.File

/** Preview frame edge, spec section 9. */
private val PREVIEW_SIZE = 220.dp

/** One full turn of the gradient border every 3s (spec section 9). */
private const val BORDER_PERIOD_MS = 3_000

/** Tips rotate every 4s. */
private const val TIP_PERIOD_MS = 4_000L

private val TIP_RES = listOf(
    R.string.generating_tip_1,
    R.string.generating_tip_2,
    R.string.generating_tip_3,
    R.string.generating_tip_4,
)

/**
 * Generating / Loading — spec section 9, `[Complex UI]`.
 *
 * The screen the user watches for 30–60s while a paid API call runs, so three rules outrank
 * everything visual here:
 *  1. It **observes** [com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine];
 *     the pipeline is Application-scoped and keeps running when this screen goes away (Risks #20).
 *  2. Back never cancels silently — it opens a `Popup` confirmation (Risks #21).
 *  3. Every animation stops when the lifecycle leaves RESUMED (Risks #30).
 */
@Composable
fun GeneratingScreen(
    onCancelled: () -> Unit,
    onSuccess: (String) -> Unit,
    onPickAnother: () -> Unit,
    modifier: Modifier = Modifier,
    vm: GeneratingViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val animationsActive = rememberIsResumed()

    // Attach-or-start, exactly once per screen entry. The ViewModel also guards internally, so a
    // recomposition or a configuration change can never submit a second job.
    LaunchedEffect(Unit) { vm.onEnter() }

    LaunchedEffect(state.successCreationId) {
        state.successCreationId?.let(onSuccess)
    }

    var confirmVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (state.isFailed) {
            vm.onDismissFailure()
            onCancelled()
        } else {
            confirmVisible = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.bgRoot),
    ) {
        AuroraBackdrop(active = animationsActive)

        if (state.isFailed) {
            ErrorOverlay(
                state = state,
                onRetry = vm::onRetry,
                onPickAnother = {
                    vm.onDismissFailure()
                    onPickAnother()
                },
            )
        } else {
            GeneratingContent(state = state, animationsActive = animationsActive)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextActionButton(
                    text = stringResource(R.string.generating_cancel),
                    color = AppColors.textTertiary,
                    horizontalPadding = 20.dp,
                    onClick = { confirmVisible = true },
                )
            }
        }
    }

    ConfirmPopup(
        visible = confirmVisible,
        title = stringResource(R.string.generating_cancel_title),
        message = stringResource(R.string.generating_cancel_message),
        confirmText = stringResource(R.string.generating_cancel_confirm),
        cancelText = stringResource(R.string.generating_cancel_keep),
        destructive = true,
        // A mis-tap outside must not throw away a paid run.
        dismissOnOutsideTouch = false,
        onConfirm = {
            confirmVisible = false
            vm.onCancelConfirmed()
            onCancelled()
        },
        onDismiss = { confirmVisible = false },
    )
}

@Composable
private fun GeneratingContent(state: GeneratingUiState, animationsActive: Boolean) {
    // The bar is smoothed between the engine's coarse 1.5s poll ticks; it is an estimate, never
    // server truth (spec section 9).
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 1_600, easing = LinearEasing),
        label = "generating_progress",
    )
    val borderPhase by rememberAnimationPhase(BORDER_PERIOD_MS, animationsActive)
    val tipIndex = rememberRotatingTipIndex(animationsActive)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PreviewFrame(
            sourcePhotoPath = state.sourcePhotoPath,
            animationsActive = animationsActive,
            borderAngle = { borderPhase * 360f },
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(state.stage.titleRes),
            style = AppType.titleL,
            color = AppColors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(TIP_RES[tipIndex]),
            style = AppType.body,
            color = AppColors.textSecondary,
            textAlign = TextAlign.Center,
            minLines = 2,
        )

        Spacer(Modifier.height(24.dp))

        EstimatedProgressBar(fraction = progress)

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.generating_percent, state.percent),
            style = AppType.caption,
            color = AppColors.textTertiary,
        )
    }
}

@Composable
private fun PreviewFrame(
    sourcePhotoPath: String?,
    animationsActive: Boolean,
    borderAngle: () -> Float,
) {
    Box(modifier = Modifier.size(PREVIEW_SIZE)) {
        if (sourcePhotoPath != null) {
            RemoteImage(
                modifier = Modifier.fillMaxSize(),
                model = remember(sourcePhotoPath) { File(sourcePhotoPath) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                shape = AppShapes.card,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AppShapes.card)
                    .background(AppColors.bgSurface),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(AppShapes.card)
                .background(AppColors.bgScrim.copy(alpha = 0.35f)),
        )

        ShimmerSweep(modifier = Modifier.matchParentSize(), active = animationsActive)

        Box(
            modifier = Modifier
                .matchParentSize()
                .rotatingGradientBorder(angleProvider = borderAngle, width = 2.dp),
        )

        ParticleField(modifier = Modifier.matchParentSize(), active = animationsActive)
    }
}

@Composable
private fun ErrorOverlay(
    state: GeneratingUiState,
    onRetry: () -> Unit,
    onPickAnother: () -> Unit,
) {
    val failure = state.failure ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert_circle),
            contentDescription = null,
            tint = AppColors.stateDanger,
            modifier = Modifier.size(52.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(failure.titleRes()),
            style = AppType.titleM,
            color = AppColors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(failure.hintRes()),
            style = AppType.body,
            color = AppColors.textSecondary,
            textAlign = TextAlign.Center,
        )

        // Debug builds only: the raw reason, so a failure can be diagnosed from the device without
        // attaching a debugger. Release users never see this.
        state.failureDetail?.let { detail ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = detail,
                style = AppType.caption,
                color = AppColors.stateWarning,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            modifier = Modifier.fillMaxWidth(0.72f),
            text = stringResource(R.string.generating_retry),
            icon = R.drawable.ic_refresh,
            enabled = state.canRetry,
            fillMaxWidth = false,
            height = 52.dp,
            onClick = onRetry,
        )

        Spacer(Modifier.height(8.dp))

        TextActionButton(
            text = stringResource(R.string.generating_pick_another),
            color = AppColors.textSecondary,
            horizontalPadding = 20.dp,
            onClick = onPickAnother,
        )
    }
}

/** Tip line rotates every 4s, and pauses with the rest of the screen. */
@Composable
private fun rememberRotatingTipIndex(active: Boolean): Int {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(TIP_PERIOD_MS)
            index = (index + 1) % TIP_RES.size
        }
    }
    return index
}

/**
 * True only while the screen is RESUMED. Every animation on this screen reads it, so a
 * backgrounded app stops burning CPU on particles and gradients (Risks #30).
 */
@Composable
private fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}
