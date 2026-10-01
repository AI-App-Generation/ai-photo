package com.bblvn.composebaseproject.features.result

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.ActionItem
import com.bblvn.composebaseproject.ui.components.CompareSlider
import com.bblvn.composebaseproject.ui.components.CompareSliderMode
import com.bblvn.composebaseproject.ui.components.RemoteImage
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType
import java.io.File

/** The reveal: 900ms, ease-out, matching [CompareSlider]'s own wipe curve. */
internal const val REVEAL_MILLIS = 900
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** Scrim behind the floating top controls so they stay readable over a bright result. */
private val CONTROL_SCRIM = Color.Black.copy(alpha = 0.35f)

/**
 * The image stage.
 *
 * Two modes, and they differ only in the entrance:
 *  - just generated (`playReveal`) — the compare handle wipes 0f -> 1f over 900ms while the whole
 *    stage fades in and scales 0.96 -> 1.0, so the result is "revealed" out of the source photo;
 *  - replay — everything is already at its final value; the handle simply sits at 1f.
 *
 * Risks #23: source and result routinely have different aspect ratios, so [ContentScale.Fit] is
 * kept (the CompareSlider default) — both layers letterbox inside the same frame and the divider
 * lines up across them. Never Crop here.
 */
@Composable
internal fun ResultStage(
    modifier: Modifier = Modifier,
    resultPath: String,
    sourcePath: String?,
    playReveal: Boolean,
) {
    val reveal = remember(playReveal) { Animatable(if (playReveal) 0f else 1f) }
    LaunchedEffect(playReveal) {
        if (playReveal) reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = EaseOutCubic))
    }
    val progress = reveal.value

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            val scale = 0.96f + 0.04f * progress
            scaleX = scale
            scaleY = scale
        },
    ) {
        val result = remember(resultPath) { File(resultPath) }
        if (sourcePath == null) {
            // The source photo is gone (cache cleared / pruned on an old creation). Show the
            // result on its own rather than a slider with a broken "before" layer.
            RemoteImage(
                modifier = Modifier.fillMaxSize(),
                model = result,
                contentScale = ContentScale.Fit,
                showShimmer = false,
            )
        } else {
            val source = remember(sourcePath) { File(sourcePath) }
            CompareSlider(
                modifier = Modifier.fillMaxSize(),
                before = source,
                after = result,
                initialFraction = 1f,
                mode = CompareSliderMode.Draggable,
                contentScale = ContentScale.Fit,
                revealOnFirstShow = playReveal,
                revealDurationMillis = REVEAL_MILLIS,
            )
        }
    }
}

/** Floating top controls: back · template name · overflow. */
@Composable
internal fun ResultTopBar(
    modifier: Modifier = Modifier,
    title: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = R.drawable.ic_arrow_left,
            contentDescription = stringResource(R.string.back),
            background = CONTROL_SCRIM,
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        if (title.isNotBlank()) {
            AppChip(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = title,
                size = AppChipSize.Small,
                style = AppChipStyle.Overlay,
            )
        }
        Spacer(Modifier.weight(1f))
        AppIconButton(
            icon = R.drawable.ic_more_horizontal,
            contentDescription = stringResource(R.string.result_more),
            background = CONTROL_SCRIM,
            onClick = onMore,
        )
    }
}

/**
 * The 4-action row. R1: every item takes `weight(1f)` inside `spacedBy(8.dp)` — one width
 * strategy for the whole row, never a mix.
 */
@Composable
internal fun ResultActionRow(
    modifier: Modifier = Modifier,
    canRegenerate: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onTryOther: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionItem(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_download,
            label = stringResource(R.string.result_action_save),
            enabled = enabled,
            onClick = onSave,
        )
        ActionItem(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_share,
            label = stringResource(R.string.result_action_share),
            enabled = enabled,
            onClick = onShare,
        )
        ActionItem(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_refresh,
            label = stringResource(R.string.result_action_regenerate),
            // R6.2 — disabled, not hidden, when the sources are gone: the row keeps its rhythm.
            enabled = enabled && canRegenerate,
            onClick = onRegenerate,
        )
        ActionItem(
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_grid,
            label = stringResource(R.string.result_action_try_other),
            onClick = onTryOther,
        )
    }
}

/** Free users only: the single route from this screen to the paywall. */
@Composable
internal fun WatermarkNotice(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.chip)
            .background(AppColors.goldDeep.copy(alpha = 0.14f))
            .border(1.dp, AppColors.goldDeep.copy(alpha = 0.35f), AppShapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_crown),
            contentDescription = null,
            tint = AppColors.goldLight,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.result_watermark_notice),
            style = AppType.caption,
            color = AppColors.goldLight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = AppColors.goldLight,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 140dp transparent -> bg_root fade so the action bar is readable over any image. */
@Composable
internal fun BottomFade(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(AppGradients.bottomCtaFade),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B, widthDp = 380)
@Composable
private fun ResultBottomBarPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            ResultActionRow(
                canRegenerate = false,
                enabled = true,
                onSave = {},
                onShare = {},
                onRegenerate = {},
                onTryOther = {},
            )
            Spacer(Modifier.height(12.dp))
            WatermarkNotice(onClick = {})
        }
    }
}
