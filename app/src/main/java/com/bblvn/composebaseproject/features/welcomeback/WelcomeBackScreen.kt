package com.bblvn.composebaseproject.features.welcomeback

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.LogScreenView
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.ui.theme.rootScrim

/**
 * Overlay shown when the user returns from background (see BaseActivity). Reuses the Splash
 * artwork and logo treatment so coming back feels like the same front door.
 */
@Composable
fun WelcomeBackScreen(
    onDismiss: () -> Unit
) {
    LogScreenView("Welcome Back")

    val activity = LocalActivity.current as? Activity

    LaunchedEffect(activity) {
        activity?.let {
            AdsPolicy.showAppOpenResumeAd(
                activity = it,
                onContinue = {},
            )
        }
    }

    // It covers the whole app, so Back just dismisses it rather than reaching the screen below.
    BackHandler(onBack = onDismiss)

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "welcome_logo_scale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(400),
        label = "welcome_logo_alpha",
    )
    val textAppear by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "welcome_text",
    )
    val ctaAppear by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "welcome_cta",
    )

    val ambient = rememberInfiniteTransition(label = "welcome_ambient")
    val bgScale by ambient.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "welcome_bg",
    )
    val haloPulse by ambient.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "welcome_halo",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot)
            // Swallow taps so nothing underneath the overlay can be hit.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
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
        Box(Modifier.fillMaxSize().rootScrim(startFraction = 0.3f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(180.dp),
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
                    // Raster foreground: R.mipmap.ic_launcher is an adaptive-icon XML on API 26+.
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer {
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
                        text = stringResource(R.string.welcome_back_title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.welcome_back_body),
                        style = AppType.body,
                        color = AppColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(28.dp))

                PrimaryButton(
                    text = stringResource(R.string.welcome_back_continue),
                    icon = R.drawable.ic_sparkles,
                    onClick = onDismiss,
                    modifier = Modifier.graphicsLayer {
                        // ModulateAlpha: no offscreen buffer, so the glow/shadow outside the bounds isn't clipped while fading.
                        alpha = ctaAppear
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        translationY = (1f - ctaAppear) * 12.dp.toPx()
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            ComposeNativeAdFrame(
                adPlacement = AdsPlacementHolder.NATIVE,
                reloadOnResume = false,
            )
        }
    }
}
