package com.bblvn.composebaseproject.ui.components.ads

import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.NativeAdPool

/**
 * Cỡ của một ô ad trong danh sách. Mỗi cỡ là một cặp frame/template khớp nhau từng dp; [heightDp] là
 * chiều cao cả thẻ, dùng để kiểm tra giới hạn theo chiều cao màn hình.
 */
enum class AdSlotSize(
    @LayoutRes val frameRes: Int,
    @LayoutRes val templateRes: Int,
    val heightDp: Float,
) {
    /** Thanh nhỏ: không có media, CTA 84x32 (frame_ad_small / template_ad_small). */
    Small(R.layout.frame_ad_small, R.layout.template_ad_small, 56f),

    /** Thẻ vừa: media vuông 120dp bên trái (frame_ad_medium / template_ad_medium). */
    Medium(R.layout.frame_ad_medium, R.layout.template_ad_medium, 144f),
}

/** Một ô ad không được cao quá tỉ lệ này so với chiều cao màn hình. */
private const val MAX_AD_SCREEN_FRACTION = 0.4f

/**
 * Mở bộ ô ad cho một danh sách cuộn, gắn với vòng đời của composable gọi nó (rời màn thì trả ad về
 * pool). Gọi ở mức MÀN HÌNH, bên ngoài lazy list, để cuộn item ra/vào không làm nạp lại ad.
 *
 * Trả về null (danh sách không chèn ô ad nào) khi: ads tắt / premium, placement bị tắt hoặc chưa có
 * ad unit id, hoặc màn quá thấp khiến thẻ vượt [MAX_AD_SCREEN_FRACTION] chiều cao màn hình.
 */
@Composable
fun rememberNativeAdSlots(
    placement: String,
    size: AdSlotSize = AdSlotSize.Medium,
): NativeAdPool.Slots? {
    val isActive by AdsLoader.isActive.collectAsState()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val fitsScreen = size.heightDp <= screenHeightDp * MAX_AD_SCREEN_FRACTION
    val enabled = isActive && fitsScreen && NativeAdPool.isEnabled(placement)

    val slots = remember(placement, enabled) {
        if (enabled) NativeAdPool.Slots(placement) else null
    }
    DisposableEffect(slots) {
        onDispose { slots?.release() }
    }
    return slots
}

/**
 * Một ô ad trong danh sách, theo chỉ số ô [slot] (0, 1, 2…), cỡ [size] (phải trùng với cỡ đã truyền
 * cho [rememberNativeAdSlots]). Skeleton khi ad còn đang nạp,
 * quảng cáo thật khi đã có, và KHÔNG vẽ gì khi chưa yêu cầu / hết fill / ads tắt. Cần gọi
 * `slots.ensure(n)` từ màn để các ô được yêu cầu.
 */
@Composable
fun NativeAdSlot(
    slots: NativeAdPool.Slots?,
    slot: Int,
    modifier: Modifier = Modifier,
    size: AdSlotSize = AdSlotSize.Medium,
) {
    if (slots == null) return
    val isActive by AdsLoader.isActive.collectAsState()
    if (!isActive) return

    val state by slots.state.collectAsState()
    val loaded = state.ads[slot]
    when {
        loaded != null -> AdPage(loaded = loaded, modifier = modifier, size = size)
        slot in state.pending -> AdPage(loaded = null, modifier = modifier, size = size)
    }
}
