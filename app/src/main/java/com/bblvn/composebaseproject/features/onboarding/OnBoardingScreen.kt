package com.bblvn.composebaseproject.features.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bblvn.composebaseproject.LogScreenView
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ui.components.ads.NativeAdOverlay
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.ui.theme.rootScrim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pages that trigger a native-full overlay when the user taps "Next"
private val PAGES_WITH_NATIVE_FULL = setOf(0, 1)

private val TEXT_FADE_HEIGHT = 120.dp

/** Artwork + copy for one onboarding page. */
private data class OnboardingContent(
    @DrawableRes val image: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingContent(R.drawable.onboarding_01, R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingContent(R.drawable.onboarding_02, R.string.onboarding_2_title, R.string.onboarding_2_body),
    // Last page is the "Welcome to <app>" page; its title is rendered specially.
    OnboardingContent(R.drawable.onboarding_03, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingScreen(onNext: () -> Unit = {}) {
    val pageCount = ONBOARDING_PAGES.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current ?: return
    var showNativeFull by remember { mutableStateOf(false) }
    val isAdActive by AdsLoader.isActive.collectAsState()

    LogScreenView("Onboarding${pagerState.settledPage + 1}")

    LaunchedEffect(showNativeFull, pagerState.settledPage) {
        val blockWelcome = showNativeFull || pagerState.settledPage in setOf(0, 1)
        AdsPolicy.allowShowWelcomeBack = !blockWelcome
    }
    DisposableEffect(Unit) {
        onDispose { AdsPolicy.allowShowWelcomeBack = true }
    }

    fun navigateToNextReal() {
        showNativeFull = false
        scope.launch {
            delay(200)
            val nextPage = pagerState.currentPage + 1
            if (nextPage < pagerState.pageCount) {
                pagerState.animateScrollToPage(nextPage)
            } else {
                onNext()
            }
        }
    }

    fun navigateToNext() {
        if (isAdActive && pagerState.currentPage in PAGES_WITH_NATIVE_FULL) {
            showNativeFull = true
        } else {
            navigateToNextReal()
        }
    }

    BackHandler {
        if (showNativeFull) {
            showNativeFull = false
        } else {
            scope.launch {
                pagerState.animateScrollToPage(
                    (pagerState.currentPage - 1).coerceIn(0, pagerState.pageCount - 1)
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isInViewport = pagerState.settledPage == page
            val isLast = page == pageCount - 1
            OnboardingPage(
                content = ONBOARDING_PAGES[page],
                pageIndex = page,
                pageCount = pageCount,
                isWelcome = isLast,
                adPlacement = if (isLast) null else AdsPlacementHolder.NATIVE,
                isInViewport = isInViewport,
                onNext = { if (isLast) navigateToNextReal() else navigateToNext() },
            )
        }

        if (showNativeFull) {
            val placement = when (pagerState.currentPage) {
                0 -> AdsPlacementHolder.NATIVE
                else -> AdsPlacementHolder.NATIVE
            }
            NativeAdOverlay(
                layoutResId = R.layout.frame_ad_full,
                closeDelaySeconds = 0,
                onClose = { navigateToNextReal() }
            ) { container, shimmer ->
                AdsPolicy.bindNativeAd(
                    activity = activity as Activity,
                    adPlacement = placement,
                    container = container,
                    shimmer = shimmer,
                    templateAdLayoutRes = R.layout.template_ad_full,
                    onAdFailed = { navigateToNextReal() }
                )
            }
        }
    }
}

/**
 * One onboarding page: full-bleed artwork that dissolves into the page background, then the
 * page dots, title, body and the CTA, with the native ad (if any) at the very bottom.
 *
 * @param adPlacement  Native-ad placement rendered at the very bottom, or
 *                     `null` to hide the ad slot on this page.
 * @param isInViewport Pass `pagerState.settledPage == page` so the native ad
 *                     only binds while this page is fully settled in view.
 * @param onNext       Called when the user taps the CTA.
 */
@Composable
private fun OnboardingPage(
    content: OnboardingContent,
    pageIndex: Int,
    pageCount: Int,
    isWelcome: Boolean,
    adPlacement: String? = null,
    isInViewport: Boolean = true,
    onNext: () -> Unit = {}
) {
    // Artwork settles from a slight zoom, and the copy rises in, each time the page lands.
    val imageScale by animateFloatAsState(
        targetValue = if (isInViewport) 1f else 1.12f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "onboarding_image",
    )
    val textAppear by animateFloatAsState(
        targetValue = if (isInViewport) 1f else 0f,
        animationSpec = tween(500, delayMillis = if (isInViewport) 120 else 0, easing = FastOutSlowInEasing),
        label = "onboarding_text",
    )

    // Clip: the zoomed artwork would otherwise spill into the neighbouring pager page.
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(content.image)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = imageScale
                    scaleY = imageScale
                }
        )
        Box(Modifier.fillMaxSize().rootScrim(startFraction = 0.5f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Solid backing that rises with the copy, so it stays legible over busy
                    // artwork whether or not the native ad below pushes it up.
                    .drawBehind { drawRect(AppGradients.fadeToRoot(TEXT_FADE_HEIGHT.toPx())) }
                    .padding(top = TEXT_FADE_HEIGHT - 24.dp)
                    .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                    .graphicsLayer {
                        // ModulateAlpha: no offscreen buffer, so the glow/shadow outside the bounds isn't clipped while fading.
                        alpha = textAppear
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        translationY = (1f - textAppear) * 20.dp.toPx()
                    },
                horizontalAlignment = Alignment.Start,
            ) {
                if (isWelcome) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .glowBrand(AppShapes.thumb)
                            .clip(AppShapes.thumb)
                            .border(1.dp, AppColors.strokeStrong, AppShapes.thumb),
                    )
                    Spacer(Modifier.height(20.dp))
                }

                PageIndicator(count = pageCount, current = pageIndex)
                Spacer(Modifier.height(16.dp))

                if (isWelcome) {
                    WelcomeTitle(title = content.title)
                } else {
                    Text(
                        text = stringResource(content.title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(content.body),
                    style = AppType.body,
                    color = AppColors.textSecondary,
                )
                Spacer(Modifier.height(28.dp))

                PrimaryButton(
                    text = stringResource(if (isWelcome) R.string.onboarding_get_started else R.string.next),
                    icon = if (isWelcome) R.drawable.ic_sparkles else null,
                    onClick = onNext,
                )
            }

            if (adPlacement != null && isInViewport) {
                ComposeNativeAdFrame(
                    adPlacement = adPlacement,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * "Welcome to <app>" with the app name painted in the brand gradient. The name is located
 * inside the formatted string, so each translation keeps whatever word order it needs.
 */
@Composable
private fun WelcomeTitle(@StringRes title: Int) {
    val appName = stringResource(R.string.app_name)
    val full = stringResource(title, appName)
    val start = full.indexOf(appName)
    val text = buildAnnotatedString {
        if (start < 0) {
            append(full)
        } else {
            append(full.substring(0, start))
            withStyle(SpanStyle(brush = AppGradients.brandHorizontal)) { append(appName) }
            append(full.substring(start + appName.length))
        }
    }
    Text(text = text, style = AppType.display, color = AppColors.textPrimary)
}

/** Dots, with the active one stretched into a brand-gradient pill. */
@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 24.dp else 8.dp,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "indicator_width",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(AppShapes.chip)
                    .then(
                        if (active) {
                            Modifier.background(AppGradients.brandHorizontal)
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.2f))
                        }
                    )
            )
        }
    }
}
