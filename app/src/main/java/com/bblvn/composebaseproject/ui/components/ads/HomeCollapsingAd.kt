package com.bblvn.composebaseproject.ui.components.ads

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdModeManager
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ui.theme.AppColors
import kotlinx.coroutines.delay

private const val COLLAPSE_AFTER_MS = 15_000L

/**
 * Ad đáy dùng chung cho cả 4 tab chính: native lớn (`native_collapse_home`, MỘT placement cho mọi
 * tab) thu về banner (`banner_all`).
 *
 *  - Mỗi lần [tabKey] đổi (đổi tab), native được dựng lại và nạp ad mới, timer thu gọn chạy lại.
 *  - Native thu về banner khi: user bấm X, hết [collapseAfterMs], hoặc native không nạp được.
 *  - Đã thu gọn thì banner ở lại tới lần đổi tab kế tiếp; native mới chỉ được nạp lúc đó, để nó
 *    không tranh request với banner.
 *
 * Composable này KHÔNG thuộc nav bar: nó là sibling nằm dưới nav bar, và khi cao lên/co xuống thì
 * đẩy nav bar lên/trả về chỗ cũ (xem `TabShell`). Padding thanh điều hướng hệ thống do chính nó
 * lo, vì nó là phần tử dưới cùng.
 */
@Composable
fun HomeCollapsingAd(
    tabKey: String,
    modifier: Modifier = Modifier,
    collapseAfterMs: Long = COLLAPSE_AFTER_MS,
) {
    // Placement native bị tắt trên Remote Config (mode 0) thì bỏ qua native, vào thẳng banner.
    // Cần vì bindNativeAd khi bị tắt chỉ ẩn ô chứ không báo lỗi, nên sẽ kẹt ở trạng thái mở rộng.
    val nativeEnabled = AdModeManager.isEnabled(AdsPlacementHolder.NATIVE_COLLAPSE_HOME)
    var expanded by remember(tabKey) { mutableStateOf(nativeEnabled) }

    LaunchedEffect(tabKey, expanded, collapseAfterMs) {
        if (!expanded) return@LaunchedEffect
        delay(collapseAfterMs)
        expanded = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.bgRoot)
            .animateContentSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (expanded) {
            key(tabKey) {
                CollapsibleNative(onCollapse = { expanded = false })
            }
        } else {
            key("banner_$tabKey") {
                AdBanner(placement = AdsPlacementHolder.BANNER_ALL)
            }
        }
    }
}

/** Native lớn kèm nút X ở góc phải; lỗi nạp hoặc bấm X đều gọi [onCollapse]. */
@Composable
private fun CollapsibleNative(onCollapse: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        ComposeNativeAdFrame(
            modifier = Modifier.fillMaxWidth(),
            adPlacement = AdsPlacementHolder.NATIVE_COLLAPSE_HOME,
            onAdFailed = onCollapse,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(AppColors.bgElevated.copy(alpha = 0.85f))
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.close),
                tint = AppColors.textPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
