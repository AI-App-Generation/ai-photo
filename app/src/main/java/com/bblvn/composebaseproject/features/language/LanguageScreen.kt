package com.bblvn.composebaseproject.features.language

import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bblvn.composebaseproject.FadeSlideHorizontal
import com.bblvn.composebaseproject.LogScreenView
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.ui.components.AppChip
import com.bblvn.composebaseproject.ui.components.AppChipSize
import com.bblvn.composebaseproject.ui.components.AppChipStyle
import com.bblvn.composebaseproject.ui.components.AppIconButton
import com.bblvn.composebaseproject.ui.components.SelectionIndicator
import com.bblvn.composebaseproject.ui.components.TopHalo
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import kotlinx.coroutines.delay

enum class LanguageStep {
    Language,
    Region,
}

/**
 * Language (then optional region) picker, shared by the entry flow and Settings.
 *
 * Picking a language applies it immediately, so the screen itself re-renders in that language as
 * a live preview.
 *
 * @param onBack  Settings mode when non-null: shows a back arrow, pre-selects the current app
 *                language, and backing out restores the language the screen was opened with.
 * @param showAds Native ad slot at the bottom. The entry flow shows it; Settings does not.
 */
@Composable
fun LanguageScreen(
    selectRegion: Boolean = true,
    showAds: Boolean = true,
    onBack: (() -> Unit)? = null,
    onNext: () -> Unit = {},
) {
    // _1 cho lần mở app đầu tiên, _2 cho mọi lần sau — cùng quy ước với inter_splash / _2.
    val isFirstTime by SettingManager.isFirstTime.collectAsState()
    // Chỉ log khi màn nằm trong luồng khởi động (onBack == null). Mở từ Settings để đổi ngôn ngữ
    // không phải là "lần 2 vào app" nên không được tính vào language_2_view.
    if (onBack == null) {
        LogScreenView(if (isFirstTime) "language_1_view" else "language_2_view")
    }

    val isSettings = onBack != null

    var currentStep by rememberSaveable {
        mutableStateOf(LanguageStep.Language)
    }

    // Locale in force when the screen opened — what Settings falls back to on back.
    val originalLocale = rememberSaveable { SettingManager.appLocale.value }

    var selectedLanguageLocaleId by rememberSaveable {
        mutableStateOf(if (isSettings) findLanguage(originalLocale)?.localeId else null)
    }

    var selectedRegionIndex by rememberSaveable {
        mutableIntStateOf(-1)
    }

    val selectedLanguage = remember(selectedLanguageLocaleId) {
        supportedLanguages.find { it.localeId == selectedLanguageLocaleId }
    }

    fun leaveSettings() {
        if (SettingManager.appLocale.value != originalLocale) {
            SettingManager.setAppLocale(originalLocale)
        }
        onBack?.invoke()
    }

    BackHandler(currentStep == LanguageStep.Region,) {
        selectedRegionIndex = -1
        currentStep = LanguageStep.Language
    }

    BackHandler(isSettings && currentStep == LanguageStep.Language) {
        leaveSettings()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot)
    ) {
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                when {
                    initialState == LanguageStep.Language && targetState == LanguageStep.Region ->
                        FadeSlideHorizontal.first togetherWith ExitTransition.KeepUntilTransitionsFinished
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "LanguageStepAnimation",
        ) { step ->
          // Opaque per-step container: Language is kept on screen (KeepUntilTransitionsFinished)
          // while Region fades in on top, so a transparent step would show both lists at once.
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .background(AppColors.bgRoot)
          ) {
            TopHalo()

            when (step) {
                LanguageStep.Language -> {
                    LanguageSelectionContent(
                        selectedLocaleId = selectedLanguageLocaleId,
                        showAds = showAds,
                        onBack = if (isSettings) ::leaveSettings else null,
                        onLanguageSelected = { localeId ->
                            selectedLanguageLocaleId = localeId
                            SettingManager.setAppLocale(localeId)
                        },
                        onNext = {
                            val language = selectedLanguage

                            if (
                                selectRegion &&
                                language != null &&
                                language.regions.isNotEmpty()
                            ) {
                                currentStep = LanguageStep.Region
                            } else {
                                onNext()
                            }
                        },
                    )
                }

                LanguageStep.Region -> {
                    val language = selectedLanguage
                        ?: return@AnimatedContent

                    RegionSelectionContent(
                        regions = language.regions,
                        selectedRegionIndex = selectedRegionIndex,
                        showAds = showAds,
                        onBack = {
                            selectedRegionIndex = -1
                            currentStep = LanguageStep.Language
                        },
                        onRegionSelected = {
                            selectedRegionIndex = it
                        },
                        onNext = {
                            val region =
                                language.regions.getOrNull(selectedRegionIndex)

                            SettingManager.setAppLocale(
                                region?.localeId ?: language.localeId
                            )

                            onNext()
                        },
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun LanguageSelectionContent(
    selectedLocaleId: String?,
    showAds: Boolean,
    onBack: (() -> Unit)?,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit,
) {
    var showNextButton by remember { mutableStateOf(false) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }

    LaunchedEffect(selectedLocaleId) {
        if (selectedLocaleId != null) {
            showNextButton = false
            delay(500)
            showNextButton = true
        } else {
            showNextButton = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        PickerHeader(
            title = stringResource(R.string.language_title),
            subtitle = stringResource(R.string.language_subtitle),
            confirmLabel = stringResource(if (onBack != null) R.string.done else R.string.next),
            showConfirm = showNextButton,
            onBack = onBack,
            onConfirm = onNext,
        )

        // native_language_small: ô nhỏ ngay dưới title, ngoài ô lớn lfo1 ở đáy màn.
        if (showAds) {
            ComposeNativeAdFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                layoutResId = R.layout.frame_ad_small,
                templateAdLayoutRes = R.layout.template_ad_small,
                adPlacement = AdsPlacementHolder.NATIVE_LANGUAGE_SMALL,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(supportedLanguages, key = { it.localeId }) { language ->
                val nativeName = remember(language.localeId) { nativeLanguageName(language.localeId) }
                PickerItemCard(
                    flagRes = language.resId,
                    title = nativeName,
                    // English name as a hint, unless it just repeats the native one.
                    subtitle = language.text.takeIf { !it.equals(nativeName, ignoreCase = true) },
                    isSelected = selectedLocaleId == language.localeId,
                    onClick = {
                        onLanguageSelected(language.localeId)
                        // Lần chọn đầu tiên nạp đè sang ô lfo2 (AdsPolicy tự đổi sang _2nd từ
                        // lần mở app thứ 2).
                        if (!hasReloadedAd) {
                            hasReloadedAd = true
                            reloadNativeAd(AdsPlacementHolder.NATIVE_LFO2)
                        }
                    },
                )
            }
        }
        if (showAds) {
            ComposeNativeAdFrame(
                modifier = Modifier.fillMaxWidth(),
                adPlacement = AdsPlacementHolder.NATIVE_LFO1,
                onReloadReady = { reloadNativeAd = it },
            )
        }
    }
}

@Composable
private fun RegionSelectionContent(
    regions: List<Region>,
    selectedRegionIndex: Int,
    showAds: Boolean,
    onBack: () -> Unit,
    onRegionSelected: (Int) -> Unit,
    onNext: () -> Unit,
) {
    var showNextButton by remember { mutableStateOf(false) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }

    LaunchedEffect(selectedRegionIndex) {
        if (selectedRegionIndex >= 0) {
            showNextButton = false
            delay(500)
            showNextButton = true
        } else {
            showNextButton = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        PickerHeader(
            title = stringResource(R.string.select_region),
            subtitle = stringResource(R.string.region_subtitle),
            confirmLabel = stringResource(R.string.next),
            showConfirm = showNextButton,
            onBack = onBack,
            onConfirm = onNext,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(regions, key = { index, region -> index }) { index, region ->
                PickerItemCard(
                    flagRes = region.resId,
                    title = region.text,
                    subtitle = null,
                    isSelected = selectedRegionIndex == index,
                    onClick = {
                        onRegionSelected(index)
                        // Lần chọn đầu tiên nạp đè sang ô rg2.
                        if (!hasReloadedAd) {
                            hasReloadedAd = true
                            reloadNativeAd(AdsPlacementHolder.NATIVE_RG2)
                        }
                    },
                )
            }
        }
        if (showAds) {
            ComposeNativeAdFrame(
                modifier = Modifier.fillMaxWidth(),
                adPlacement = AdsPlacementHolder.NATIVE_RG1,
                reloadOnResume = false,
                onReloadReady = { reloadNativeAd = it },
            )
        }
    }
}

/**
 * Title block with an optional back arrow and a compact brand pill that confirms the choice.
 * The pill only appears once something is selected (after the existing 500ms delay).
 */
@Composable
private fun PickerHeader(
    title: String,
    subtitle: String,
    confirmLabel: String,
    showConfirm: Boolean,
    onBack: (() -> Unit)?,
    onConfirm: () -> Unit,
) {
    // Plain alpha on an always-laid-out pill: the title never reflows, and there is no glow —
    // a blurred shadow under a fading/scaling layer smears and gets clipped mid-animation.
    val confirmAlpha by animateFloatAsState(
        targetValue = if (showConfirm) 1f else 0f,
        animationSpec = tween(200),
        label = "picker_confirm",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            AppIconButton(
                icon = R.drawable.ic_arrow_left,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppType.titleL,
                color = AppColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = AppType.label,
                color = AppColors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        AppChip(
            modifier = Modifier.graphicsLayer { alpha = confirmAlpha },
            text = confirmLabel,
            trailingIcon = R.drawable.ic_chevron_right,
            size = AppChipSize.Medium,
            style = AppChipStyle.Brand,
            onClick = if (showConfirm) onConfirm else null,
        )
    }
}

@Composable
private fun PickerItemCard(
    flagRes: Int,
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) AppColors.accentViolet.copy(alpha = 0.14f) else AppColors.bgSurface,
        animationSpec = tween(200),
        label = "picker_item_bg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(AppShapes.thumb)
            .background(background)
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, AppGradients.brand, AppShapes.thumb)
                } else {
                    Modifier.border(1.dp, AppColors.strokeSoft, AppShapes.thumb)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(flagRes),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.dp, AppColors.strokeSoft, CircleShape),
            contentScale = ContentScale.Crop,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = title,
                style = AppType.body,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppType.caption,
                    color = AppColors.textTertiary,
                    maxLines = 1,
                )
            }
        }

        SelectionIndicator(selected = isSelected)
    }
}
