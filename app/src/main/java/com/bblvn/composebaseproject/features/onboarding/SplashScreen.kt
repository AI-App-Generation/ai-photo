package com.bblvn.composebaseproject.features.onboarding

import com.bblvn.composebaseproject.core.AppCapability
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.LogScreenView
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ui.components.ads.NativeAdOverlay
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.dropShadow
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.ui.theme.rootScrim
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val LOADING_PROGRESS_ANIMATION_MS = 1200

@Composable
fun SplashScreen(onNext: () -> Unit = {}, onPremiumReady: () -> Unit = onNext) {
    LogScreenView("splash_view")

    val activity = LocalActivity.current as Activity

    val ready by AdsLoader.finished.collectAsState()
    val isPremium by AppCapability.isPremium.collectAsState()
    var progressComplete by remember { mutableStateOf(false) }
    var showNativeFull by remember { mutableStateOf(false) }

    // Rời Splash đúng MỘT lần.
    //
    // Cần chốt này vì có hai đường ra chạy song song: nhánh premium ở effect bên dưới, và callback
    // của quảng cáo mở màn. Nếu quyền trả phí về ĐÚNG LÚC quảng cáo đang hiển thị thì effect chạy
    // lại, đẩy user vào Main, rồi lát sau quảng cáo đóng lại gọi onNext và ném họ ngược về màn chọn
    // ngôn ngữ. Ai tới trước thì thắng, người sau bị chặn.
    val hasLeft = remember { AtomicBoolean(false) }
    val leaveOnce: (() -> Unit) -> Unit = { go -> if (hasLeft.compareAndSet(false, true)) go() }
    // Inter đóng, native full đóng và native full lỗi đều dẫn tới onNext.
    val leave: () -> Unit = { leaveOnce(onNext) }

    // Theo dõi cả isPremium chứ không chỉ ready: quyền trả phí đến từ Google Play nên có thể về muộn
    // hơn AdsLoader một nhịp; khi nó lật, effect chạy lại và user vẫn kịp đi đường tắt.
    LaunchedEffect(ready, isPremium) {
        if (!ready) return@LaunchedEffect

        progressComplete = true

        // Cả chặng Language / Survey / Onboarding đều là phễu quảng cáo. Bắt người vừa trả tiền để
        // không xem quảng cáo đi hết phễu đó là chuyện vô nghĩa nhất có thể làm với họ.
        if (isPremium) {
            leaveOnce(onPremiumReady)
            return@LaunchedEffect
        }

        delay(LOADING_PROGRESS_ANIMATION_MS + 1500L)

        // inter_splash; từ lần mở thứ 2 AdsPolicy tự đổi sang inter_splash_2nd.
        AdsPolicy.showInterAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.INTER_SPLASH,
            force = true,
            onContinue = {
                // Inter xong thì chen native full; user trả phí / ads tắt thì đi thẳng.
                if (AdsLoader.isAdsEnabled()) showNativeFull = true else leave()
            }
        )
    }

    // One-shot entrance: the logo springs in, the wordmark and loader follow.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "splash_logo_scale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500),
        label = "splash_logo_alpha",
    )
    val textAppear by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(600, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "splash_text",
    )
    val loaderAppear by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(600, delayMillis = 450, easing = FastOutSlowInEasing),
        label = "splash_loader",
    )

    // Ambient loops: slow Ken-Burns on the artwork, a breathing halo and a gentle float.
    val ambient = rememberInfiniteTransition(label = "splash_ambient")
    val bgScale by ambient.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "splash_bg",
    )
    val haloPulse by ambient.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splash_halo",
    )
    val logoFloat by ambient.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splash_float",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot)
    ) {
        Image(
            painter = painterResource(R.drawable.bg_splash_aurora),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = bgScale
                    scaleY = bgScale
                },
        )
        // Lets the loader and the ad notice sit on near-solid colour at the bottom.
        Box(Modifier.fillMaxSize().rootScrim(startFraction = 0.4f))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .statusBarsPadding()
                .offset(y = (-48).dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = haloPulse
                            scaleY = haloPulse
                            alpha = logoAlpha
                        }
                        .background(AppGradients.halo())
                )
                // The raster adaptive foreground: `R.mipmap.ic_launcher` resolves to an
                // <adaptive-icon> XML on API 26+, which painterResource cannot draw.
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(116.dp)
                        .graphicsLayer {
                            translationY = logoFloat.dp.toPx()
                            scaleX = logoScale
                            scaleY = logoScale
                            // ModulateAlpha: no offscreen buffer, so the glow/shadow outside the bounds isn't clipped while fading.
                            alpha = logoAlpha
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .glowBrand(AppShapes.card)
                        .clip(AppShapes.card)
                        .border(1.dp, AppColors.strokeStrong, AppShapes.card),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textAppear
                    translationY = (1f - textAppear) * 16.dp.toPx()
                },
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppType.display,
                    color = AppColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = AppType.body,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SplashLoader(
                isDone = progressComplete,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = loaderAppear
                        translationY = (1f - loaderAppear) * 12.dp.toPx()
                    },
            )

            if (ready) {
                Spacer(Modifier.height(12.dp))
                ComposeNativeAdFrame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    layoutResId = R.layout.frame_ad_small,
                    templateAdLayoutRes = R.layout.template_ad_small,
                    adPlacement = AdsPlacementHolder.NATIVE_SPLASH,
                    reloadOnResume = false,
                )
            }
        }

        if (showNativeFull) {
            NativeAdOverlay(
                layoutResId = R.layout.frame_ad_full,
                closeDelaySeconds = 0,
                fullScreen = true,
                onClose = leave,
            ) { container, shimmer ->
                AdsPolicy.bindNativeAd(
                    activity = activity,
                    adPlacement = AdsPlacementHolder.NATIVE_FULL_SPLASH,
                    container = container,
                    shimmer = shimmer,
                    templateAdLayoutRes = R.layout.template_ad_full,
                    // Không load được thì sang Language luôn, không bắt user chờ.
                    onAdFailed = leave,
                )
            }
        }
    }
}

/** Status line + progress bar + the "may contain ads" notice under it. */
@Composable
private fun SplashLoader(
    isDone: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val target = remember { (60..85).random() / 100f }

    LaunchedEffect(isDone) {
        if (isDone) {
            progress.animateTo(1f, tween(LOADING_PROGRESS_ANIMATION_MS, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(target, tween(LOADING_PROGRESS_ANIMATION_MS, easing = FastOutSlowInEasing))
            // Keep creeping while ads/remote config are still loading so the bar never looks stuck.
            progress.animateTo(0.94f, tween(12_000, easing = LinearOutSlowInEasing))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.splash_loading),
                style = AppType.label,
                color = AppColors.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = "${(progress.value * 100).roundToInt()}%",
                style = AppType.label,
                color = AppColors.textPrimary,
            )
        }
        Spacer(Modifier.height(10.dp))
        GlowProgressBar(progress = progress.value)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = AppColors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.splash_ad_notice),
                style = AppType.caption,
                color = AppColors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 8dp brand-gradient bar with a glow under the fill, a light band sweeping across it and a
 * bright head that rides the leading edge.
 */
@Composable
private fun GlowProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val loops = rememberInfiniteTransition(label = "progress_loops")
    val sheenT by loops.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "progress_sheen",
    )
    val headPulse by loops.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "progress_head",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val barWidth = maxWidth

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(AppShapes.chip)
                .background(AppColors.bgElevated.copy(alpha = 0.7f))
                .border(1.dp, AppColors.strokeSoft, AppShapes.chip)
        )

        if (fraction > 0f) {
            // Fill
            Box(
                modifier = Modifier
                    .width(barWidth * fraction)
                    .height(8.dp)
                    .dropShadow(
                        shape = AppShapes.chip,
                        color = AppColors.accentMagenta.copy(alpha = 0.55f),
                        blur = 12.dp,
                    )
                    .clip(AppShapes.chip)
                    .background(AppGradients.brandHorizontal)
                    .drawWithContent {
                        drawContent()
                        val band = 64.dp.toPx()
                        val x = -band + (size.width + band) * sheenT
                        drawRect(
                            brush = AppGradients.sheen(startX = x, endX = x + band),
                            topLeft = Offset(x, 0f),
                            size = Size(band, size.height),
                        )
                    }
            )

            // Head
            Box(
                modifier = Modifier
                    .offset(x = (barWidth * fraction - 8.dp).coerceAtLeast(0.dp))
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = headPulse
                        scaleY = headPulse
                    }
                    .dropShadow(
                        shape = CircleShape,
                        color = AppColors.accentMagenta.copy(alpha = 0.8f),
                        blur = 10.dp,
                    )
                    .clip(CircleShape)
                    .background(AppColors.textPrimary)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(AppColors.accentMagenta)
            )
        }
    }
}
