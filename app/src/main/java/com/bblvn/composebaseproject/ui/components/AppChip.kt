package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowGold

/**
 * R1 — the app has EXACTLY three chip heights. Every pill-shaped element
 * (tool pill, filter chip, option chip, account chip, meta chip, badge,
 * action chip) must route through [AppChip] with one of these sizes.
 */
enum class AppChipSize(
    val height: Dp,
    val horizontalPadding: Dp,
    val iconSize: Dp,
    val gap: Dp,
) {
    /** 28dp — badge, meta, chip drawn on top of an image. */
    Small(28.dp, 10.dp, 12.dp, 6.dp),

    /** 36dp — filter, option, account, tool pill. */
    Medium(36.dp, 14.dp, 16.dp, 8.dp),

    /** 44dp — action chip with icon (Adjust Photo: Rotate / Flip / Reset). */
    Large(44.dp, 18.dp, 18.dp, 8.dp),
    ;

    val textStyle: TextStyle
        get() = if (this == Small) AppType.caption else AppType.label
}

/** Chip surfaces — all drawn from theme tokens, no ad-hoc colors. */
enum class AppChipStyle {
    /** bg_elevated + stroke_soft. The default / unselected look. */
    Neutral,

    /** brand_gradient. Selected filter / option chips. */
    Brand,

    /** gold_gradient. Premium badges and the premium account chip. */
    Gold,

    /** bg_elevated + solid gold_light stroke, gold icon and label. The "go PRO" account chip for free users. */
    GoldOutline,

    /** black 45% + stroke_soft. Chips sitting on top of an image. */
    Overlay,

    /** state_success tinted. "Đã sẵn sàng". */
    Success,

    /** state_warning tinted. "Chưa khớp khung". */
    Warning,

    /** state_danger tinted. */
    Danger,
}

/**
 * One chip to rule them all.
 *
 * Width strategy (R1): a chip inside a scrolling LazyRow keeps its natural
 * width (the default). A chip inside a fixed Row where every sibling shares the
 * space must be given `Modifier.weight(1f)` by the caller AND `fillWidth = true`
 * so its content stays centred.
 */
@Composable
fun AppChip(
    modifier: Modifier = Modifier,
    text: String? = null,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    size: AppChipSize = AppChipSize.Medium,
    style: AppChipStyle = AppChipStyle.Neutral,
    selected: Boolean = false,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    glow: Boolean = false,
    maxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    val resolved = if (selected && style == AppChipStyle.Neutral) AppChipStyle.Brand else style
    val content = chipContentColor(resolved, enabled)

    var base = modifier
        .height(size.height)
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)

    if (glow && resolved == AppChipStyle.Gold) base = base.glowGold(AppShapes.chip)

    base = base.clip(AppShapes.chip)

    base = when (resolved) {
        AppChipStyle.Brand -> base.background(AppGradients.brand)
        AppChipStyle.Gold -> base.background(AppGradients.gold)
        AppChipStyle.Neutral -> base
            .background(AppColors.bgElevated)
            .border(1.dp, AppColors.strokeSoft, AppShapes.chip)

        AppChipStyle.GoldOutline -> base
            .background(AppColors.bgElevated)
            .border(1.5.dp, AppColors.goldLight, AppShapes.chip)

        AppChipStyle.Overlay -> base
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, AppColors.strokeSoft, AppShapes.chip)

        AppChipStyle.Success -> base.background(AppColors.stateSuccess.copy(alpha = 0.18f))
        AppChipStyle.Warning -> base.background(AppColors.stateWarning.copy(alpha = 0.18f))
        AppChipStyle.Danger -> base.background(AppColors.stateDanger.copy(alpha = 0.18f))
    }

    if (onClick != null) base = base.clickable(enabled = enabled, onClick = onClick)

    Row(
        modifier = base.padding(horizontal = size.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fillWidth) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size.iconSize),
            )
            if (text != null) Spacer(Modifier.width(size.gap))
        }
        if (text != null) {
            Text(
                text = text,
                style = size.textStyle,
                color = content,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null) {
            if (text != null || icon != null) Spacer(Modifier.width(size.gap))
            Icon(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size.iconSize),
            )
        }
    }
}

private fun chipContentColor(style: AppChipStyle, enabled: Boolean): Color {
    val color = when (style) {
        AppChipStyle.Brand -> AppColors.textPrimary
        AppChipStyle.Gold -> AppColors.textOnAccent
        AppChipStyle.Neutral -> AppColors.textSecondary
        AppChipStyle.GoldOutline -> AppColors.goldLight
        AppChipStyle.Overlay -> AppColors.textPrimary
        AppChipStyle.Success -> AppColors.stateSuccess
        AppChipStyle.Warning -> AppColors.stateWarning
        AppChipStyle.Danger -> AppColors.stateDanger
    }
    return if (enabled) color else AppColors.textTertiary
}

/**
 * "PRO" badge — gold gradient + crown. Only two sizes are ever needed
 * (Small on cards, Medium next to a title).
 */
@Composable
fun PremiumBadge(
    modifier: Modifier = Modifier,
    size: AppChipSize = AppChipSize.Small,
    text: String = "PRO",
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
) = AppChip(
    modifier = modifier,
    text = text,
    icon = R.drawable.ic_crown,
    size = size,
    style = AppChipStyle.Gold,
    glow = glow,
    onClick = onClick,
)

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AppChipPreview() {
    AppTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip(text = "Tất cả", size = AppChipSize.Small)
                PremiumBadge()
                AppChip(text = "2 ảnh", size = AppChipSize.Small, style = AppChipStyle.Overlay)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip(text = "Template", selected = true)
                AppChip(text = "Công cụ", icon = R.drawable.ic_wand)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppChip(
                    modifier = Modifier.weight(1f),
                    text = "Xoay",
                    icon = R.drawable.ic_rotate,
                    size = AppChipSize.Large,
                    fillWidth = true,
                )
                AppChip(
                    modifier = Modifier.weight(1f),
                    text = "Đặt lại",
                    icon = R.drawable.ic_reset,
                    size = AppChipSize.Large,
                    fillWidth = true,
                )
            }
        }
    }
}
