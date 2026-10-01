package com.bblvn.composebaseproject.features.tryonalign

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.DisableWelcomeOverlayTemporarily
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppTopBar
import com.bblvn.composebaseproject.ui.components.ErrorState
import com.bblvn.composebaseproject.ui.components.ImageTransform
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.ZoomableImage
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppType
import java.io.File

/**
 * Mandatory alignment step for Virtual Try-On.
 *
 * There is deliberately no skip path: the garment is fitted to the body in the photo, so a subject
 * that does not fill the guide produces a visibly wrong result and wastes a paid call.
 */
@Composable
fun TryOnAlignScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personPath = remember { GenerationDraft.current.slots.getOrNull(0) }
    var transform by remember { mutableStateOf(ImageTransform()) }

    DisableWelcomeOverlayTemporarily()

    // "Aligned enough" is a deliberately loose heuristic: the user must have engaged with the
    // frame (zoomed or panned) rather than hit an exact pose, which no on-device check can judge
    // reliably. It nudges without blocking a photo that is already framed correctly.
    val aligned = transform.scale > 1.05f || transform.offsetX != 0f || transform.offsetY != 0f

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        if (personPath == null) {
            ErrorState(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.tryon_missing_title),
                message = stringResource(R.string.tryon_missing_message),
                actionText = stringResource(R.string.tryon_go_back),
                onAction = onBack,
            )
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.tryon_title),
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                ZoomableImage(
                    modifier = Modifier.fillMaxSize(),
                    model = File(personPath),
                    onTransformChange = { transform = it },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                    Image(
                        painter = painterResource(R.drawable.silhouette_body_guide),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        alpha = 0.55f,
                    )
                }

                androidx.compose.material3.Text(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp),
                    text = stringResource(R.string.tryon_hint),
                    style = AppType.caption,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                AppChip(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    text = stringResource(
                        if (aligned) R.string.tryon_status_ready else R.string.tryon_status_not_ready
                    ),
                    icon = if (aligned) R.drawable.ic_check else R.drawable.ic_alert_triangle,
                    size = AppChipSize.Small,
                    style = if (aligned) AppChipStyle.Success else AppChipStyle.Warning,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                PrimaryButton(
                    text = stringResource(
                        if (aligned) R.string.tryon_continue else R.string.tryon_align_first
                    ),
                    enabled = aligned,
                    onClick = onDone,
                )
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}
