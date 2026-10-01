package com.bblvn.composebaseproject.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppType

/**
 * "Section title  ............  Xem tất cả ›" row.
 * Used by every Home category row and the Quick Tools strip.
 */
@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    titleStyle: TextStyle = AppType.titleM,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = AppColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
        )
        if (actionText != null && onAction != null) {
            TextActionButton(
                text = actionText,
                color = AppColors.textSecondary,
                trailingIcon = R.drawable.ic_chevron_right,
                height = 32.dp,
                horizontalPadding = 8.dp,
                onClick = onAction,
            )
        }
    }
}
