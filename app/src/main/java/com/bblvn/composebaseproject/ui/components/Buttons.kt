package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
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
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.ui.theme.glowGold

/**
 * The three looks a primary CTA can have. The variant only changes the
 * background and content color — never the size (R1: the CTA is a fixed 56dp
 * so switching between "Dùng ảnh này" / "Mở khoá với PRO" / "Chọn đủ 2 ảnh"
 * cannot make the button jump).
 */
enum class ButtonVariant {
    /** brand_gradient + glow_brand. The normal action. */
    Brand,

    /** gold_gradient + glow_gold. Anything that leads to the paywall. */
    Gold,

    /** bg_elevated, no glow, tertiary text. The disabled / "not ready" state. */
    Neutral,
}

/**
 * Primary call to action. Used on 9 screens.
 *
 * Height is fixed (default 56dp) and the variant is resolved from [enabled]:
 * a disabled button always renders as [ButtonVariant.Neutral], so callers only
 * pass the enabled-state variant.
 */
@Composable
fun PrimaryButton(
    modifier: Modifier = Modifier,
    text: String,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Brand,
    fillMaxWidth: Boolean = true,
    height: Dp = 56.dp,
    shape: Shape = AppShapes.button,
    onClick: () -> Unit,
) {
    val effective = if (enabled) variant else ButtonVariant.Neutral
    val contentColor = when (effective) {
        ButtonVariant.Brand -> AppColors.textPrimary
        ButtonVariant.Gold -> AppColors.textOnAccent
        ButtonVariant.Neutral -> AppColors.textTertiary
    }

    var base = modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .height(height)

    base = when (effective) {
        ButtonVariant.Brand -> base.glowBrand(shape)
        ButtonVariant.Gold -> base.glowGold(shape)
        ButtonVariant.Neutral -> base
    }

    base = base.clip(shape)

    base = when (effective) {
        ButtonVariant.Brand -> base.background(AppGradients.brand)
        ButtonVariant.Gold -> base.background(AppGradients.gold)
        ButtonVariant.Neutral -> base
            .background(AppColors.bgElevated)
            .border(1.dp, AppColors.strokeSoft, shape)
    }

    Box(
        modifier = base.clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AppType.titleM,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Quieter action: bg_elevated + 1dp stroke_soft, 48dp tall. */
@Composable
fun SecondaryButton(
    modifier: Modifier = Modifier,
    text: String,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false,
    height: Dp = 48.dp,
    contentColor: Color = AppColors.textPrimary,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(AppShapes.button)
            .background(AppColors.bgElevated)
            .border(1.dp, AppColors.strokeSoft, AppShapes.button)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (enabled) contentColor else AppColors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AppType.label,
                color = if (enabled) contentColor else AppColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * Borderless text action ("Bỏ qua", "Xem tất cả", "Huỷ").
 * Clipped to the pill shape so the ripple looks right.
 */
@Composable
fun TextActionButton(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = AppColors.textSecondary,
    style: androidx.compose.ui.text.TextStyle = AppType.label,
    @DrawableRes trailingIcon: Int? = null,
    height: Dp = 44.dp,
    horizontalPadding: Dp = 12.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(AppShapes.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = style, color = color, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Icon-only button. Clipped to a pill so the ripple is circular, with an
 * optional translucent scrim background for use on top of an image.
 */
@Composable
fun AppIconButton(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    contentDescription: String? = null,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = AppColors.textPrimary,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AppShapes.chip)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else AppColors.textTertiary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun ButtonsPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Dùng ảnh này", icon = R.drawable.ic_sparkles) {}
            PrimaryButton(
                text = "Mở khoá với PRO",
                icon = R.drawable.ic_lock,
                variant = ButtonVariant.Gold,
            ) {}
            PrimaryButton(text = "Chọn đủ 2 ảnh", enabled = false) {}
            SecondaryButton(text = "Thử lại", icon = R.drawable.ic_refresh) {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(icon = R.drawable.ic_arrow_left) {}
                TextActionButton(text = "Xem tất cả", trailingIcon = R.drawable.ic_chevron_right) {}
            }
        }
    }
}
