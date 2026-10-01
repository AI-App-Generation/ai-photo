package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType

/** What sits on the right-hand side of a [SettingRow]. */
sealed interface SettingTrailing {
    /** A read-only value, e.g. "Tiếng Việt" or "128 MB". */
    data class Value(val text: String) : SettingTrailing

    /** A self-built switch — Material's Switch is not used anywhere. */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) :
        SettingTrailing

    /** A chevron meaning "opens another screen". */
    data object Chevron : SettingTrailing

    /** Nothing on the right (destructive actions). */
    data object None : SettingTrailing
}

/**
 * One row inside a Profile setting group. 56dp tall, icon + title + trailing,
 * everything vertically centred with a 14dp gap (R9: icon rows are never
 * top-aligned).
 */
@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    title: String,
    trailing: SettingTrailing = SettingTrailing.Chevron,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val contentColor = when {
        !enabled -> AppColors.textTertiary
        danger -> AppColors.stateDanger
        else -> AppColors.textPrimary
    }
    val iconColor = if (danger) AppColors.stateDanger else AppColors.textSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = AppType.body,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        when (trailing) {
            is SettingTrailing.Value -> Text(
                text = trailing.text,
                style = AppType.caption,
                color = AppColors.textTertiary,
                maxLines = 1,
            )

            is SettingTrailing.Toggle -> ToggleSwitch(
                checked = trailing.checked,
                onCheckedChange = trailing.onCheckedChange,
                enabled = enabled,
            )

            SettingTrailing.Chevron -> Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppColors.textTertiary,
                modifier = Modifier.size(18.dp),
            )

            SettingTrailing.None -> Unit
        }
    }
}

/** 1dp hairline between rows, inset so it starts past the icon. */
@Composable
fun SettingDivider(modifier: Modifier = Modifier, startInset: androidx.compose.ui.unit.Dp = 50.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(AppColors.strokeSoft)
    )
}

/**
 * A titled group of [SettingRow]s inside one `shape_card` surface, with the
 * inset dividers automatically placed between (never after) rows.
 */
@Composable
fun SettingGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = AppType.caption,
                color = AppColors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(AppColors.bgSurface)
                .border(1.dp, AppColors.strokeSoft, AppShapes.card),
            content = content,
        )
    }
}

/**
 * Self-built switch. 48x28 track, 22dp thumb, 150ms slide.
 * Material's `Switch` is intentionally not used.
 */
@Composable
fun ToggleSwitch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.bgElevated
            checked -> AppColors.accentViolet
            else -> AppColors.bgElevated
        },
        animationSpec = tween(150),
        label = "toggle_track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(150),
        label = "toggle_thumb",
    )

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(AppShapes.chip)
            .background(trackColor)
            .border(
                1.dp,
                if (checked) Color.Transparent else AppColors.strokeSoft,
                AppShapes.chip,
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) AppColors.textPrimary else AppColors.textTertiary
                )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun SettingRowPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingGroup(title = "Ứng dụng") {
                SettingRow(
                    icon = R.drawable.ic_globe,
                    title = "Ngôn ngữ",
                    trailing = SettingTrailing.Value("Tiếng Việt"),
                    onClick = {},
                )
                SettingDivider()
                SettingRow(
                    icon = R.drawable.ic_save,
                    title = "Tự động lưu vào thư viện",
                    trailing = SettingTrailing.Toggle(true) {},
                )
                SettingDivider()
                SettingRow(
                    icon = R.drawable.ic_trash,
                    title = "Xoá toàn bộ lịch sử",
                    trailing = SettingTrailing.None,
                    danger = true,
                    onClick = {},
                )
            }
        }
    }
}
