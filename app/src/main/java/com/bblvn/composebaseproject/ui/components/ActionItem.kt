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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType

/**
 * Circular icon above a caption. Used by the Result action row (Lưu / Chia sẻ /
 * Tạo lại / Template khác) and the Creations selection bar (Lưu / Chia sẻ /
 * Xoá).
 *
 * R1 — in both places every sibling gets `Modifier.weight(1f)` from the caller,
 * so the row divides the full width evenly; never mix weight with natural width
 * inside one row.
 */
@Composable
fun ActionItem(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    label: String,
    tint: Color = AppColors.textPrimary,
    labelColor: Color = AppColors.textSecondary,
    circleSize: Dp = 48.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.thumb)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(AppColors.bgElevated)
                .border(1.dp, AppColors.strokeSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (enabled) tint else AppColors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = AppType.caption,
            color = if (enabled) labelColor else AppColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun ActionItemPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppColors.bgRoot)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionItem(Modifier.weight(1f), R.drawable.ic_download, "Lưu") {}
            ActionItem(Modifier.weight(1f), R.drawable.ic_share, "Chia sẻ") {}
            ActionItem(Modifier.weight(1f), R.drawable.ic_refresh, "Tạo lại") {}
            ActionItem(
                Modifier.weight(1f),
                R.drawable.ic_trash,
                "Xoá",
                tint = AppColors.stateDanger,
            ) {}
        }
    }
}
