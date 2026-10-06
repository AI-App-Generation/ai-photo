package com.bblvn.composebaseproject.ui.components.ads

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.NativeAdPool
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import kotlinx.coroutines.delay

/**
 * Mở một session carousel gắn với vòng đời của composable gọi nó, và trả lại khi composable đó rời
 * composition hoặc [reloadKey] đổi.
 *
 * **Reload khi chuyển tab:** gọi hàm này ở mức MÀN HÌNH (bên ngoài `LazyColumn`). Đổi tab làm màn
 * rời composition → session cũ được trả (ad đã gắn bị huỷ, ad chưa gắn quay về pool), tab mới mở
 * session mới và nhận ad mới.
 *
 * Không gọi trong `item { }` của lazy list: cuộn item ra khỏi màn cũng làm nó rời composition và
 * kéo theo nạp lại ad mỗi lần cuộn.
 *
 * Trả về null khi ads tắt / premium / placement bị tắt / không có ad unit id.
 */
@Composable
fun rememberAdCarouselSession(
    placement: String = AdsPlacementHolder.NATIVE_SWIPE_HOME,
    adCount: Int = NativeAdPool.TARGET_COUNT,
    reloadKey: Any? = null,
): NativeAdPool.Session? {
    val isActive by AdsLoader.isActive.collectAsState()
    val enabled = isActive && NativeAdPool.isEnabled(placement)

    val session = remember(placement, adCount, reloadKey, enabled) {
        if (enabled) NativeAdPool.Session(placement, adCount) else null
    }
    DisposableEffect(session) {
        session?.start()
        onDispose { session?.release() }
    }
    return session
}

/** Dạng gọn cho nơi không nằm trong lazy list: tự mở session rồi vẽ. Xem [rememberAdCarouselSession]. */
@Composable
fun AdCarousel(
    modifier: Modifier = Modifier,
    placement: String = AdsPlacementHolder.NATIVE_SWIPE_HOME,
    adCount: Int = NativeAdPool.TARGET_COUNT,
    autoSwipeMs: Long = 3_000L,
    reloadKey: Any? = null,
) {
    AdCarousel(
        session = rememberAdCarouselSession(placement, adCount, reloadKey),
        modifier = modifier,
        autoSwipeMs = autoSwipeMs,
    )
}

/**
 * Carousel native ad: nhiều ad (mặc định 3) trong một [HorizontalPager], vuốt được và tự chuyển
 * trang sau [autoSwipeMs]. Dùng khung/template cỡ vừa (`frame_ad_medium` / `template_ad_medium`).
 *
 * Ad lấy từ [NativeAdPool], không đi qua `AdsPolicy.bindNativeAd` của module-ads.
 *
 * Không có ad nào (session null, hết fill, premium) thì không vẽ gì cả, không để lại khoảng trống.
 *
 * @param session lấy từ [rememberAdCarouselSession] ở mức màn hình.
 */
@Composable
fun AdCarousel(
    session: NativeAdPool.Session?,
    modifier: Modifier = Modifier,
    autoSwipeMs: Long = 5_000L,
) {
    val isActive by AdsLoader.isActive.collectAsState()
    // Dừng sớm: ads tắt giữa chừng (vd. vừa mua premium) thì thôi vẽ và thôi auto-swipe ngay, không
    // chờ session được trả ở rememberAdCarouselSession.
    if (session == null || !isActive) return

    val state by session.state.collectAsState()
    val pageCount = state.ready.size + state.pending
    // Hết fill hoàn toàn: ẩn hẳn.
    if (pageCount == 0) return

    val pagerState = rememberPagerState(pageCount = { pageCount })

    AutoSwipe(
        pagerState = pagerState,
        // Chỉ xoay giữa các ad ĐÃ có; trang skeleton chỉ tới được bằng tay.
        rotatingPages = state.ready.size,
        intervalMs = autoSwipeMs,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp,
            // Giữ mọi trang đang được vẽ: nếu để Pager tháo trang ra khỏi composition thì vuốt
            // ngược lại sẽ gắn cùng một NativeAd vào view mới (đăng ký lần hai).
            beyondViewportPageCount = pageCount,
            key = { index -> state.ready.getOrNull(index)?.id ?: -(index + 1) },
        ) { index ->
            AdPage(loaded = state.ready.getOrNull(index))
        }

        if (pageCount > 1) {
            Spacer(Modifier.height(10.dp))
            PageDots(count = pageCount, current = pagerState.currentPage)
        }
    }
}

/**
 * Tự chuyển trang. Đếm lại [intervalMs] từ đầu mỗi khi trang đổi (kể cả do user vuốt), nên tay
 * vừa vuốt xong sẽ không bị giật sang trang khác ngay. Dừng khi user đang kéo hoặc màn không ở
 * trạng thái RESUMED.
 */
@Composable
private fun AutoSwipe(
    pagerState: PagerState,
    rotatingPages: Int,
    intervalMs: Long,
) {
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(rotatingPages, isDragged, isResumed, pagerState.settledPage) {
        if (rotatingPages < 2 || isDragged || !isResumed) return@LaunchedEffect
        delay(intervalMs)
        val next = (pagerState.currentPage + 1) % rotatingPages
        pagerState.animateScrollToPage(next)
    }
}

/** Một trang/ô: skeleton khi [loaded] còn null, quảng cáo thật khi đã có. Dùng chung với [NativeAdSlot]. */
@Composable
internal fun AdPage(
    loaded: NativeAdPool.Loaded?,
    modifier: Modifier = Modifier,
    size: AdSlotSize = AdSlotSize.Medium,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            // View đã gắn ad được giữ lại trong [NativeAdPool.Loaded] để khi item bị dispose rồi
            // vẽ lại (cuộn ra/vào màn) ta dùng lại đúng view đó, không đăng ký NativeAd lần hai.
            val cached = loaded?.pageView
            if (cached != null) {
                (cached.parent as? ViewGroup)?.removeView(cached)
                cached
            } else {
                LayoutInflater.from(context).inflate(size.frameRes, null)
            }
        },
        update = { root ->
            if (loaded != null && root.tag !== loaded.ad) {
                root.tag = loaded.ad
                // Lưu cả view khung lẫn NativeAdView: pool cần NativeAdView để destroy đúng thứ tự
                // (view trước, ad sau) khi carousel rời màn.
                loaded.adView = bindAd(root, loaded.ad, size)
                loaded.pageView = root
            }
        },
    )
}

/** Thay skeleton bằng template cỡ [size] đã điền dữ liệu của [ad]. Trả về NativeAdView vừa dựng. */
private fun bindAd(root: View, ad: NativeAd, size: AdSlotSize): NativeAdView {
    val container = root.findViewById<FrameLayout>(R.id.frAds)
    val shimmer = root.findViewById<ShimmerFrameLayout>(R.id.shimmerNative)

    shimmer.stopShimmer()
    shimmer.visibility = View.GONE

    container.removeAllViews()
    val adView = LayoutInflater.from(root.context)
        .inflate(size.templateRes, container, false) as NativeAdView

    val headline = adView.findViewById<TextView>(R.id.ad_headline)
    val body = adView.findViewById<TextView>(R.id.ad_body)
    val cta = adView.findViewById<Button>(R.id.ad_call_to_action)
    // Cỡ small không có media: registerNativeAd nhận null cho mediaView.
    val media: MediaView? = adView.findViewById(R.id.ad_media)

    headline.text = ad.headline
    adView.headlineView = headline

    // Body / CTA có thể vắng: ẩn bằng INVISIBLE để chiều cao cố định của template không đổi.
    body.text = ad.body
    body.visibility = if (ad.body.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
    adView.bodyView = body

    cta.text = ad.callToAction
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
    adView.callToActionView = cta

    // Gắn ad vào view sau cùng, khi mọi asset view đã được khai báo. Ad chưa hiện được chuyển từ
    // tab cũ sang đã từng gắn vào một view khác (đã destroy), nên chỗ này có thể bị SDK từ chối.
    runCatching { adView.registerNativeAd(ad, media) }
        .onFailure { Log.w("AdCarousel", "registerNativeAd failed: ${it.message}") }

    container.addView(adView)
    container.visibility = View.VISIBLE
    return adView
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                animationSpec = tween(250),
                label = "carousel_dot_width",
            )
            Spacer(
                Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(AppShapes.chip)
                    .then(
                        if (active) {
                            Modifier.background(AppGradients.brandHorizontal)
                        } else {
                            Modifier.background(AppColors.strokeStrong)
                        }
                    )
            )
        }
    }
}
