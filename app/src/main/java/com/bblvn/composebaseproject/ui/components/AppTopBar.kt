package com.bblvn.composebaseproject.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppType

/**
 * Fixed 56dp top bar. It always lives OUTSIDE the scrolling container and
 * applies `statusBarsPadding()` to itself, per the Theme conventions.
 *
 * [scrollFraction] (0f..1f) fades the bar in as the content scrolls under it:
 * transparent at the top of the list, `bg_root` at 92% with a hairline bottom
 * border once the user has scrolled ~24dp. Screens without a scroll reaction
 * can leave it at 0f, or pass `solid = true`.
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleStyle: TextStyle = AppType.titleM,
    @DrawableRes navigationIcon: Int? = R.drawable.ic_arrow_left,
    onBack: (() -> Unit)? = null,
    centerTitle: Boolean = false,
    scrollFraction: Float = 0f,
    solid: Boolean = false,
    height: Dp = 56.dp,
    horizontalPadding: Dp = 8.dp,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val fade = if (solid) 1f else scrollFraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.bgRoot.copy(alpha = 0.92f * fade))
            .drawBehind {
                if (fade <= 0f) return@drawBehind
                val stroke = 1.dp.toPx()
                drawLine(
                    color = AppColors.strokeSoft.copy(alpha = AppColors.strokeSoft.alpha * fade),
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke,
                )
            }
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null && navigationIcon != null) {
                AppIconButton(
                    icon = navigationIcon,
                    contentDescription = "Quay lại",
                    onClick = onBack,
                )
                Spacer(Modifier.width(4.dp))
            }
            leading()

            if (centerTitle) Spacer(Modifier.weight(1f))

            if (title != null) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = AppColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Non-centered: the title absorbs every remaining pixel up to the trailing
                    // slot. Do NOT pair this with a second weight(1f) spacer below — two equal
                    // weights split the bar 50/50 and ellipsize long names (Vietnamese category
                    // names especially) while half the bar sits empty.
                    modifier = if (centerTitle) Modifier else Modifier.weight(1f),
                )
            }

            if (centerTitle || title == null) Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = trailing,
            )
        }
    }
}
