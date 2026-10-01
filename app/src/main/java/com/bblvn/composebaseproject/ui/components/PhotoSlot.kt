package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.dashedBorder

/**
 * A slot the user fills with one photo. Used on Template Detail (two slots) and
 * Tool Detail (one or two slots).
 *
 * R3.2 — Compose has no dashed-border API, so the empty state's border is drawn
 * with `drawBehind` + `PathEffect.dashPathEffect` (see [dashedBorder]).
 *
 * R3.9 — when this sits in a `Row { weight(1f) }`, the caller passes the weight
 * in `modifier` and the aspect ratio is applied here, i.e. AFTER the weight,
 * which is the order that measures correctly.
 */
@Composable
fun PhotoSlot(
    modifier: Modifier = Modifier,
    label: String,
    imageModel: Any? = null,
    aspectRatio: Float = 3f / 4f,
    @DrawableRes emptyIcon: Int = R.drawable.ic_image_plus,
    emptyIconSize: androidx.compose.ui.unit.Dp = 26.dp,
    errorText: String? = null,
    enabled: Boolean = true,
    onPick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(AppShapes.thumb)
                .background(AppColors.bgSurface)
                .then(
                    if (imageModel == null) {
                        Modifier.dashedBorder(
                            color = if (errorText != null) {
                                AppColors.stateDanger.copy(alpha = 0.6f)
                            } else {
                                AppColors.strokeSoft
                            },
                            cornerRadius = 18.dp,
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = enabled, onClick = onPick),
        ) {
            if (imageModel == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(emptyIcon),
                        contentDescription = null,
                        tint = AppColors.textTertiary,
                        modifier = Modifier.size(emptyIconSize),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = label,
                        style = AppType.caption,
                        color = AppColors.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            } else {
                RemoteImage(
                    modifier = Modifier.fillMaxSize(),
                    model = imageModel,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    placeholderSeed = label,
                )
                if (onClear != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Xoá ảnh",
                            tint = AppColors.textPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                // Filled slots keep their label visible as a small overlay chip.
                AppChip(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    text = label,
                    size = AppChipSize.Small,
                    style = AppChipStyle.Overlay,
                )
            }
        }

        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_alert_circle),
                    contentDescription = null,
                    tint = AppColors.stateDanger,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = errorText,
                    style = AppType.caption,
                    color = AppColors.stateDanger,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun PhotoSlotPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoSlot(modifier = Modifier.weight(1f), label = "Khuôn mặt", onPick = {})
            PhotoSlot(
                modifier = Modifier.weight(1f),
                label = "Ảnh nền",
                errorText = "Cần đúng 1 khuôn mặt",
                onPick = {},
            )
        }
    }
}
