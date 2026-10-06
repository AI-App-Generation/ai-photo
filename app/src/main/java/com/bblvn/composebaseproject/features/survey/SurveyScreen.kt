package com.bblvn.composebaseproject.features.survey

import com.bblvn.composebaseproject.core.Analytics
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.LogScreenView
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ui.components.SelectionIndicator
import com.bblvn.composebaseproject.ui.components.TopHalo
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.bblvn.composebaseproject.ui.theme.imageScrim
import kotlinx.coroutines.delay

/**
 * One interest the user can tick. Artwork is the same demo art Tools uses, so the survey
 * previews what the app actually does.
 */
private enum class SurveyOption(
    @StringRes val label: Int,
    @DrawableRes val image: Int,
    @DrawableRes val icon: Int,
) {
    PORTRAITS(R.string.survey_opt_portraits, R.drawable.onboarding_01, R.drawable.ic_sparkles),
    ENHANCE(R.string.survey_opt_enhance, R.drawable.tool_demo_enhance_after, R.drawable.ic_wand),
    REMOVE_BG(R.string.survey_opt_remove_bg, R.drawable.tool_demo_remove_bg_after, R.drawable.ic_scissors),
    COLORIZE(R.string.survey_opt_colorize, R.drawable.tool_demo_colorize_after, R.drawable.ic_palette),
    FACE_SWAP(R.string.survey_opt_face_swap, R.drawable.tool_demo_face_swap_after, R.drawable.ic_face_swap),
    TRY_ON(R.string.survey_opt_try_on, R.drawable.tool_demo_try_on_after, R.drawable.ic_shirt),
}

/**
 * "What do you want to create?" — a multi-select interest survey between Language and
 * Onboarding. Purely a UX step: the answers stay on the device and are not sent anywhere.
 * At least one option must be ticked to continue.
 */
@Composable
fun SurveyScreen(onNext: () -> Unit = {}) {
    LogScreenView("survey_view")

    // Enum names so the selection survives process death via rememberSaveable.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot)
    ) {
        TopHalo()

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 20.dp)) {
                    Text(
                        text = stringResource(R.string.survey_title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.survey_subtitle),
                        style = AppType.body,
                        color = AppColors.textSecondary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SurveyOption.entries.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEachIndexed { columnIndex, option ->
                                val isSelected = option.name in selected
                                SurveyCard(
                                    modifier = Modifier.weight(1f),
                                    option = option,
                                    selected = isSelected,
                                    appearIndex = rowIndex * 2 + columnIndex,
                                    onClick = {
                                        selected = if (isSelected) {
                                            selected - option.name
                                        } else {
                                            selected + option.name
                                        }
                                        // Chỉ nạp đè một lần, ở lần chạm đầu tiên.
                                        if (!hasReloadedAd && selected.isNotEmpty()) {
                                            hasReloadedAd = true
                                            reloadNativeAd(AdsPlacementHolder.NATIVE_ONB1_CLICK)
                                        }
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Soft edge where the cards scroll under the CTA, instead of a hard cut.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .wrapContentHeight(Alignment.Bottom, unbounded = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(AppGradients.bottomCtaFade)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = selected.size,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "survey_counter",
                ) { count ->
                    Text(
                        text = if (count == 0) {
                            stringResource(R.string.survey_hint)
                        } else {
                            stringResource(R.string.survey_selected_count, count)
                        },
                        style = AppType.caption,
                        color = if (count == 0) AppColors.textTertiary else AppColors.accentCyan,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.survey_continue),
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        // Bắn ngay khi bấm, TRƯỚC khi chen quảng cáo — đặt sau thì lần nào quảng
                        // cáo nuốt luồng là mất số liệu khảo sát. Khảo sát chọn nhiều nên gửi kèm
                        // `count`: chuỗi nối không tách được trong Firebase.
                        Analytics.log(
                            "survey_continue",
                            "interests" to selected.joinToString(",") { it.lowercase() },
                            "count" to selected.size,
                        )
                        onNext()
                    },
                )
            }

            // native_onb1 hiện sẵn; lần chạm đầu tiên nạp đè sang native_onb1_click.
            ComposeNativeAdFrame(
                modifier = Modifier.fillMaxWidth(),
                adPlacement = AdsPlacementHolder.NATIVE_ONB1,
                onReloadReady = { reloadNativeAd = it },
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SurveyCard(
    modifier: Modifier = Modifier,
    option: SurveyOption,
    selected: Boolean,
    appearIndex: Int,
    onClick: () -> Unit,
) {
    // Staggered entrance, played once when the screen opens.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(appearIndex * 70L)
        appear.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }

    val imageZoom by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "survey_zoom",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) AppColors.accentViolet.copy(alpha = 0.22f) else Color.Transparent,
        animationSpec = tween(200),
        label = "survey_tint",
    )

    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .graphicsLayer {
                // ModulateAlpha: no offscreen buffer, so the glow/shadow outside the bounds isn't clipped while fading.
                alpha = appear.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
                translationY = (1f - appear.value) * 24.dp.toPx()
            }
            .glowBrand(AppShapes.card, enabled = selected)
            .clip(AppShapes.card)
            .background(AppColors.bgSurface)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(option.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = imageZoom
                    scaleY = imageZoom
                },
        )
        Box(modifier = Modifier.fillMaxSize().imageScrim(startFraction = 0.35f))
        Box(modifier = Modifier.fillMaxSize().background(tint))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (selected) {
                        Modifier.border(2.dp, AppGradients.brand, AppShapes.card)
                    } else {
                        Modifier.border(1.dp, AppColors.strokeSoft, AppShapes.card)
                    }
                )
        )

        SelectionIndicator(
            selected = selected,
            onImage = true,
            size = 26.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(AppShapes.chip)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, AppColors.strokeSoft, AppShapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(option.icon),
                    contentDescription = null,
                    tint = AppColors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(option.label),
                style = AppType.label,
                color = AppColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
