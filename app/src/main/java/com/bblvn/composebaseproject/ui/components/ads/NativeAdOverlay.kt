package com.bblvn.composebaseproject.ui.components.ads

import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bblvn.composebaseproject.ads.AdsLoader
import com.facebook.shimmer.ShimmerFrameLayout
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun NativeAdOverlay(
    @LayoutRes layoutResId: Int = R.layout.frame_ad_paid,
    closeDelaySeconds: Int? = null,
    onClose: () -> Unit = {},
    /**
     * Dùng cho layout native full (frame_ad_full): phủ kín màn hình bằng nền đặc, chặn chạm xuyên
     * xuống, chừa vùng status bar. Để false thì overlay trong suốt như trước.
     */
    fullScreen: Boolean = false,
    onAdHostReady: (FrameLayout, ShimmerFrameLayout) -> Unit
) {
    val minReloadGapMs = 1_000L
    var remainingTime by remember { mutableStateOf(closeDelaySeconds) }
    var isVisible by remember { mutableStateOf(true) }
    var hasLoaded by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf<FrameLayout?>(null) }
    var lastReloadAt by remember { mutableStateOf(0L) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive by AdsLoader.isActive.collectAsState()
    val onAdHostReadyState by rememberUpdatedState(onAdHostReady)

    fun bindFreshHost(force: Boolean = false) {
        if (!isVisible) return

        val currentRoot = root ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastReloadAt < minReloadGapMs) return

        lastReloadAt = now
        currentRoot.removeAllViews()

        val frame = LayoutInflater.from(currentRoot.context)
            .inflate(layoutResId, currentRoot, true)

        val container = frame.findViewById<FrameLayout>(R.id.frAds)
        val shimmer = frame.findViewById<ShimmerFrameLayout>(R.id.shimmerNative)

        if (container == null || shimmer == null) {
            isVisible = false
            return
        }

        currentRoot.visibility = View.VISIBLE
        onAdHostReadyState(container, shimmer)
    }

    LaunchedEffect(closeDelaySeconds) {
        if (closeDelaySeconds != null && closeDelaySeconds > 0) {
            remainingTime = closeDelaySeconds
            while (remainingTime!! > 0) {
                delay(1000)
                remainingTime = remainingTime!! - 1
            }
        }
    }

    DisposableEffect(lifecycleOwner, hasLoaded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (hasLoaded) bindFreshHost()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isActive && isVisible) {
        Box(
            modifier = if (fullScreen) {
                // Phủ kín bằng nền đặc và nuốt mọi cú chạm, để màn phía sau không lộ ra cũng không
                // bấm xuyên được.
                Modifier
                    .fillMaxSize()
                    .background(AppColors.bgRoot)
                    .pointerInput(Unit) {}
            } else {
                Modifier
            }
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // Full screen: layout tự chừa 56dp cho nút đóng, nên phải đẩy xuống dưới
                    // status bar để nó không đè lên nội dung quảng cáo.
                    .then(if (fullScreen) Modifier.statusBarsPadding() else Modifier)
                    .navigationBarsPadding(),
                factory = { context ->
                    FrameLayout(context)
                },
                update = { viewRoot ->
                    if (!hasLoaded) {
                        root = viewRoot
                        hasLoaded = true
                        bindFreshHost(force = true)
                    }
                }
            )

            if (closeDelaySeconds != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AppColors.bgElevated)
                        .clickable(
                            enabled = (remainingTime ?: 0) <= 0
                        ) {
                            if ((remainingTime ?: 0) <= 0) {
                                onClose()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if ((remainingTime ?: 0) > 0) {
                        Text(
                            "$remainingTime",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
