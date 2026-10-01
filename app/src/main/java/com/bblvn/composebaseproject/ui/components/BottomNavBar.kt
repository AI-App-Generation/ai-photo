package com.bblvn.composebaseproject.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppTheme
import com.bblvn.composebaseproject.ui.theme.AppType

/** One destination in the bottom bar. */
data class BottomNavItem(
    val id: String,
    @param:DrawableRes val icon: Int,
    val label: String,
)

/** The app's four root tabs, in order. */
val AiPhotoNavItems: List<BottomNavItem> = listOf(
    BottomNavItem("home", R.drawable.ic_nav_home, "Trang chủ"),
    BottomNavItem("tools", R.drawable.ic_nav_tools, "Công cụ"),
    BottomNavItem("creations", R.drawable.ic_nav_creations, "Ảnh của bạn"),
    BottomNavItem("profile", R.drawable.ic_nav_profile, "Hồ sơ"),
)

/**
 * The bottom navigation bar for the four root tabs.
 *
 * R3.7 — `Modifier.blur()` only works on API 31+, and Compose cannot blur what
 * is *behind* a surface anyway, so instead of a fake blur the bar uses a denser
 * `bg_surface` alpha on older devices. It is never fully transparent, so
 * content scrolling underneath can never make the labels unreadable.
 *
 * The bar applies its own `navigationBarsPadding()`.
 */
@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    items: List<BottomNavItem> = AiPhotoNavItems,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val surfaceAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.92f else 0.96f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.navBar)
            .background(AppColors.bgSurface.copy(alpha = surfaceAlpha))
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = AppColors.strokeSoft,
                    start = Offset(0f, stroke / 2f),
                    end = Offset(size.width, stroke / 2f),
                    strokeWidth = stroke,
                )
            }
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavItem(
                    modifier = Modifier.weight(1f),
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    modifier: Modifier = Modifier,
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) AppColors.accentViolet else AppColors.textTertiary
    Column(
        modifier = modifier
            .clip(AppShapes.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = AppType.caption,
            color = tint,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (selected) AppGradients.brand else SolidTransparent)
        )
    }
}

private val SolidTransparent = androidx.compose.ui.graphics.SolidColor(
    androidx.compose.ui.graphics.Color.Transparent
)

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun BottomNavBarPreview() {
    AppTheme {
        Box(Modifier.background(AppColors.bgRoot).padding(top = 24.dp)) {
            BottomNavBar(selectedId = "home", onSelect = {})
        }
    }
}

/** Kept so screens can reserve exactly the bar's height when needed. */
@Composable
fun RowScope.BottomNavSpacer() = Spacer(Modifier.weight(1f))
