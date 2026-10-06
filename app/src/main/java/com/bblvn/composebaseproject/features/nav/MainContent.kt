package com.bblvn.composebaseproject.features.nav

import com.bblvn.composebaseproject.core.Analytics
import com.bblvn.composebaseproject.features.paywall.CreditStoreScreen
import com.bblvn.composebaseproject.ui.components.LocalOpenCreditStore
import androidx.compose.runtime.CompositionLocalProvider
import com.bblvn.composebaseproject.core.AppCapability
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bblvn.composebaseproject.FadeSlideHorizontal
import com.bblvn.composebaseproject.FadeSlideHorizontalPop
import com.bblvn.composebaseproject.FadeSlideUp
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.InterAdOutcome
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.FadeSlideUpPop
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.data.domain.ToolType
import com.bblvn.composebaseproject.features.adjust.AdjustPhotoScreen
import com.bblvn.composebaseproject.features.categoryall.CategoryAllScreen
import com.bblvn.composebaseproject.features.creations.CreationsScreen
import com.bblvn.composebaseproject.features.generating.GeneratingScreen
import com.bblvn.composebaseproject.features.language.LanguageScreen
import com.bblvn.composebaseproject.features.home.AiPhotoHomeScreen
import com.bblvn.composebaseproject.features.paywall.PaywallScreen
// Custom picker screen, disabled (see PHOTO_PICKER note below):
// import com.bblvn.composebaseproject.features.photopicker.PhotoPickerScreen
import com.bblvn.composebaseproject.features.photopicker.PhotoSourceSheet
import com.bblvn.composebaseproject.features.photopicker.rememberPhotoSourcePicker
import com.bblvn.composebaseproject.features.profile.ProfileScreen
import com.bblvn.composebaseproject.features.result.ResultScreen
import com.bblvn.composebaseproject.features.templatedetail.TemplateDetailScreen
import com.bblvn.composebaseproject.features.tooldetail.ToolDetailScreen
import com.bblvn.composebaseproject.features.tools.ToolsHubScreen
import com.bblvn.composebaseproject.features.tryonalign.TryOnAlignScreen
import com.bblvn.composebaseproject.ui.components.AiPhotoNavItems
import com.bblvn.composebaseproject.ui.components.BottomNavBar
import com.bblvn.composebaseproject.ui.components.ads.HomeCollapsingAd
import com.bblvn.composebaseproject.R
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import com.bblvn.composebaseproject.ui.components.ads.NativeAdOverlay
import com.bblvn.composebaseproject.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The tab shell plus every full-screen destination.
 *
 * Transitions come from the project's prebuilt set in `App.kt` (C7) rather than new ones:
 * `FadeSlideHorizontal` for drill-downs, `FadeSlideUp` for modal-feeling screens, and a plain
 * 150ms fade between the four tabs, which is what the base project prescribes for nav-bar apps.
 */
@Composable
fun MainContent() {
    val nav = rememberNavController()
    val activity = LocalActivity.current

    // inter_open_template / inter_open_tool: chen inter trước khi mở màn chi tiết, dùng chung giãn
    // cách 30s với inter_tab (cùng mode 1, không force). Lỗi hoặc bị chặn thì mở luôn.
    fun openTemplate(templateId: String) {
        val host = activity
        if (host == null) {
            nav.goOnce(Routes.templateDetail(templateId))
            return
        }
        AdsPolicy.showInterAd(
            activity = host,
            adPlacement = AdsPlacementHolder.INTER_OPEN_TEMPLATE,
            onContinue = { nav.goOnce(Routes.templateDetail(templateId)) },
        )
    }

    fun openTool(toolName: String) {
        // Mỗi tool một event không param (tool_enhance_click, tool_remove_bg_click…). Bắn trước khi
        // chen inter để quảng cáo có nuốt luồng cũng không mất lượt bấm.
        Analytics.log("tool_${Analytics.snakeCase(toolName)}_click")
        val host = activity
        if (host == null) {
            nav.goOnce(Routes.toolDetail(toolName))
            return
        }
        AdsPolicy.showInterAd(
            activity = host,
            adPlacement = AdsPlacementHolder.INTER_OPEN_TOOL,
            onContinue = { nav.goOnce(Routes.toolDetail(toolName)) },
        )
    }

    CompositionLocalProvider(LocalOpenCreditStore provides { nav.goOnce(Routes.CREDIT_STORE) }) {
    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        // Matches the base project: only routes that explicitly set a transition should animate.
        // Without this, a destination with no override (only Routes.MAIN here) falls back to
        // Navigation-Compose's own internal default transition instead of just staying put.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        // The theme's windowBackground is white (Theme.AppCompat.Light); without this, the gap a
        // sliding transition briefly leaves uncovered shows that white through as a bright streak
        // instead of the app's own dark background.
        modifier = Modifier.fillMaxSize().background(AppColors.bgRoot),
    ) {
        composable(Routes.MAIN) {
            TabShell(nav, onOpenTemplate = ::openTemplate, onOpenTool = ::openTool)
        }

        composable(
            route = Routes.CATEGORY_ALL,
            arguments = listOf(navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.IntType }),
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second },
            popEnterTransition = { FadeSlideHorizontalPop.first },
            popExitTransition = { FadeSlideHorizontalPop.second },
        ) { entry ->
            CategoryAllScreen(
                categoryId = entry.arguments?.getInt(Routes.ARG_CATEGORY_ID) ?: 0,
                onBack = nav::goBackOnce,
                onTemplateClick = ::openTemplate,
            )
        }

        composable(
            route = Routes.TEMPLATE_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_TEMPLATE_ID) { type = NavType.StringType }),
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second },
            popEnterTransition = { FadeSlideHorizontalPop.first },
            popExitTransition = { FadeSlideHorizontalPop.second },
        ) { entry ->
            // System Photo Picker (replaces the custom PHOTO_PICKER screen, see note there).
            val photoSource = rememberPhotoSourcePicker()
            // Sheet goes last so its scrim draws over the whole screen, system bars included.
            Box(Modifier.fillMaxSize()) {
                TemplateDetailScreen(
                    templateId = entry.arguments?.getString(Routes.ARG_TEMPLATE_ID).orEmpty(),
                    onBack = nav::goBackOnce,
                    // Custom picker screen (restore together with the PHOTO_PICKER route below):
                    // onPickPhoto = { slot -> nav.goOnce(Routes.photoPicker(slot)) },
                    onPickPhoto = photoSource::open,
                    onStart = { nav.goOnce(Routes.adjust(0)) },
                    onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
                    onTemplateClick = ::openTemplate,
                )
                PhotoSourceSheet(state = photoSource, onPicked = { nav.continueAfterPick() })
            }
        }

        composable(
            route = Routes.TOOL_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_TOOL_TYPE) { type = NavType.StringType }),
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second },
            popEnterTransition = { FadeSlideHorizontalPop.first },
            popExitTransition = { FadeSlideHorizontalPop.second },
        ) { entry ->
            val tool = ToolType.fromName(entry.arguments?.getString(Routes.ARG_TOOL_TYPE))
                ?: ToolType.ENHANCE
            // System Photo Picker (replaces the custom PHOTO_PICKER screen, see note there).
            val photoSource = rememberPhotoSourcePicker()
            // Sheet goes last so its scrim draws over the whole screen, system bars included.
            Box(Modifier.fillMaxSize()) {
                ToolDetailScreen(
                    tool = tool,
                    onBack = nav::goBackOnce,
                    // Custom picker screen (restore together with the PHOTO_PICKER route below):
                    // onPickPhoto = { slot -> nav.goOnce(Routes.photoPicker(slot)) },
                    onPickPhoto = photoSource::open,
                    onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
                    // Try-On must align first; the other four tools skip every intermediate step.
                    onStart = {
                        if (tool.requiresAlignment) {
                            nav.goOnce(Routes.TRY_ON_ALIGN)
                        } else {
                            nav.goOnce(Routes.GENERATING)
                        }
                    },
                )
                PhotoSourceSheet(state = photoSource, onPicked = { nav.continueAfterPick() })
            }
        }

        // ---------------------------------------------------------------------------------
        // Custom photo picker screen — DISABLED in favour of the system Photo Picker
        // (features/photopicker/PhotoSourcePicker.kt), which needs no READ_MEDIA_IMAGES.
        // To restore: uncomment this block, switch both `onPickPhoto` lines in TEMPLATE_DETAIL
        // and TOOL_DETAIL back to `nav.goOnce(Routes.photoPicker(slot))`, and re-enable the
        // photo permissions in AndroidManifest.xml. PhotoPickerScreen/ViewModel are untouched.
        // ---------------------------------------------------------------------------------
//      composable(
//          route = Routes.PHOTO_PICKER,
//          arguments = listOf(navArgument(Routes.ARG_SLOT_INDEX) { type = NavType.IntType }),
//          enterTransition = { FadeSlideUp.first },
//          exitTransition = { FadeSlideUp.second },
//          popEnterTransition = { FadeSlideUpPop.first },
//          popExitTransition = { FadeSlideUpPop.second },
//      ) { entry ->
//          PhotoPickerScreen(
//              slotIndex = entry.arguments?.getInt(Routes.ARG_SLOT_INDEX) ?: 0,
//              onClose = nav::goBackOnce,
//              // Spec R5: a 1-image flow has nothing left to pick, so the picker is the last
//              // step before moving on — continue straight to Adjust/Align/Generating instead of
//              // bouncing back to Detail. A 2-image flow still returns to Detail every time (even
//              // after the second slot) so the user explicitly presses the CTA to proceed.
//              onPicked = {
//                  val draft = GenerationDraft.current
//                  when {
//                      draft.requiredSlots > 1 -> nav.goBackOnce()

//                      draft.tool?.requiresAlignment == true ->
//                          nav.navigate(Routes.TRY_ON_ALIGN) {
//                              popUpTo(Routes.PHOTO_PICKER) { inclusive = true }
//                          }

//                      draft.tool != null ->
//                          nav.navigate(Routes.GENERATING) {
//                              popUpTo(Routes.PHOTO_PICKER) { inclusive = true }
//                          }

//                      else -> // template
//                          nav.navigate(Routes.adjust(0)) {
//                              popUpTo(Routes.PHOTO_PICKER) { inclusive = true }
//                          }
//                  }
//              },
//          )
//      }

        composable(
            route = Routes.ADJUST,
            arguments = listOf(navArgument(Routes.ARG_SLOT_INDEX) { type = NavType.IntType }),
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second },
            popEnterTransition = { FadeSlideHorizontalPop.first },
            popExitTransition = { FadeSlideHorizontalPop.second },
        ) { entry ->
            val slot = entry.arguments?.getInt(Routes.ARG_SLOT_INDEX) ?: 0
            AdjustPhotoScreen(
                slotIndex = slot,
                onBack = nav::goBackOnce,
                onDone = {
                    // Two-image templates repeat this screen for slot 1 before generating.
                    val needsSecond = GenerationDraft.current.requiredSlots > slot + 1
                    if (needsSecond) {
                        nav.goOnce(Routes.adjust(slot + 1))
                    } else {
                        nav.goOnce(Routes.GENERATING)
                    }
                },
            )
        }

        composable(
            route = Routes.TRY_ON_ALIGN,
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second },
            popEnterTransition = { FadeSlideHorizontalPop.first },
            popExitTransition = { FadeSlideHorizontalPop.second },
        ) {
            TryOnAlignScreen(
                onBack = nav::goBackOnce,
                onDone = { nav.goOnce(Routes.GENERATING) },
            )
        }

        composable(
            route = Routes.GENERATING,
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
            popEnterTransition = { FadeSlideUpPop.first },
            popExitTransition = { FadeSlideUpPop.second },
        ) {
            GeneratingScreen(
                onCancelled = nav::goBackOnce,
                onSuccess = { creationId ->
                    // popUpTo inclusive: Back from Result must never land on Generating (R6).
                    nav.navigate(Routes.result(creationId)) {
                        popUpTo(Routes.GENERATING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onPickAnother = nav::goBackOnce,
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument(Routes.ARG_CREATION_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.ARG_REPLAY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
            popEnterTransition = { FadeSlideUpPop.first },
            popExitTransition = { FadeSlideUpPop.second },
        ) { entry ->
            ResultScreen(
                creationId = entry.arguments?.getString(Routes.ARG_CREATION_ID)?.ifBlank { null },
                replay = entry.arguments?.getBoolean(Routes.ARG_REPLAY) ?: false,
                onBack = nav::goBackOnce,
                onRegenerate = { nav.goOnce(Routes.GENERATING) },
                onTryOther = {
                    // "Thử template khác" returns to the tab shell rather than stacking (R6.1).
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
            )
        }

        composable(
            route = Routes.PAYWALL,
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
            popEnterTransition = { FadeSlideUpPop.first },
            popExitTransition = { FadeSlideUpPop.second },
        ) {
            // native_paywall_close: đóng paywall mà chưa mua thì chen native full rồi mới thoát. Cả nút X
            // lẫn nút Back hệ thống đều đi qua đây. Đã mua / premium / ads tắt thì thoát luôn.
            var showCloseNative by remember { mutableStateOf(false) }
            val paywallHost = activity

            fun leavePaywall() {
                showCloseNative = false
                nav.goBackOnce()
            }

            fun closePaywall() {
                if (paywallHost == null || AppCapability.isPremium.value || !AdsLoader.isAdsEnabled()) {
                    leavePaywall()
                } else {
                    showCloseNative = true
                }
            }

            // Nạp sẵn ngay khi mở paywall để lúc đóng không phải chờ.
            LaunchedEffect(Unit) { AdsPolicy.loadAd(AdsPlacementHolder.NATIVE_PAYWALL_CLOSE) }

            // Mua xong (premium bật ngay trong phiên) thì đóng paywall, không chen native.
            val isPremiumNow by AppCapability.isPremium.collectAsState()
            LaunchedEffect(isPremiumNow) { if (isPremiumNow) leavePaywall() }

            BackHandler { closePaywall() }
            // Đang xem native full mà bấm Back = bỏ qua nó và thoát.
            BackHandler(enabled = showCloseNative) { leavePaywall() }

            Box(Modifier.fillMaxSize()) {
                PaywallScreen(onClose = ::closePaywall)

                if (showCloseNative && paywallHost != null) {
                    NativeAdOverlay(
                        layoutResId = R.layout.frame_ad_full,
                        closeDelaySeconds = 1,
                        fullScreen = true,
                        onClose = ::leavePaywall,
                    ) { container, shimmer ->
                        AdsPolicy.bindNativeAd(
                            activity = paywallHost,
                            adPlacement = AdsPlacementHolder.NATIVE_PAYWALL_CLOSE,
                            container = container,
                            shimmer = shimmer,
                            templateAdLayoutRes = R.layout.template_ad_full,
                            // Không nạp được thì thoát luôn, không bắt user chờ.
                            onAdFailed = ::leavePaywall,
                        )
                    }
                }
            }
        }

        composable(
            route = Routes.CREDIT_STORE,
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
            popEnterTransition = { FadeSlideUpPop.first },
            popExitTransition = { FadeSlideUpPop.second },
        ) {
            CreditStoreScreen(onClose = nav::goBackOnce)
        }

        composable(
            route = Routes.LANGUAGE_SETTINGS,
            enterTransition = { FadeSlideHorizontal.first },
            exitTransition = { FadeSlideHorizontal.second },
            popEnterTransition = { FadeSlideHorizontalPop.first },
            popExitTransition = { FadeSlideHorizontalPop.second },
        ) {
            // Same screen as the entry flow, minus the region step and the ad slot.
            LanguageScreen(
                selectRegion = false,
                showAds = false,
                onBack = nav::goBackOnce,
                onNext = nav::goBackOnce,
            )
        }
    }
    }
}

/**
 * The four bottom-nav tabs.
 *
 * Tab content is kept in a [rememberSaveableStateHolder] so each tab keeps its scroll position
 * when the user comes back to it (R3.5) — without it Home jumps to the top on every switch.
 */
@Composable
private fun TabShell(
    nav: NavHostController,
    onOpenTemplate: (String) -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(Routes.TAB_HOME) }

    // inter_tab → native_full_inter, CHỈ khi đổi tab bằng nút trên tab bar (các nút khác như "see all
    // tools" ở Home đổi tab thẳng). Tab chỉ đổi sau khi cả chuỗi quảng cáo kết thúc.
    val activity = LocalActivity.current
    var pendingTab by remember { mutableStateOf<String?>(null) }
    var showNativeFullInter by remember { mutableStateOf(false) }
    var tabAdFlowBusy by remember { mutableStateOf(false) }

    fun finishTabAdFlow() {
        if (!tabAdFlowBusy) return
        val target = pendingTab
        showNativeFullInter = false
        pendingTab = null
        tabAdFlowBusy = false
        if (target != null) selected = target
    }

    fun onTabBarSelect(tab: String) {
        if (tab == selected || tabAdFlowBusy) return
        val host = activity
        if (host == null) {
            selected = tab
            return
        }
        pendingTab = tab
        tabAdFlowBusy = true
        AdsPolicy.showInterAd(
            activity = host,
            adPlacement = AdsPlacementHolder.INTER_TAB,
            onContinue = {},
            onOutcome = { outcome ->
                // Chỉ chen native full khi inter thật sự đã hiện và đóng; bị chặn / lỗi thì đổi tab luôn.
                if (outcome == InterAdOutcome.CLOSED && AdsLoader.isAdsEnabled()) {
                    showNativeFullInter = true
                } else {
                    finishTabAdFlow()
                }
            },
        )
    }

    // Back khi đang xem native full = bỏ qua nó và đổi tab.
    BackHandler(enabled = showNativeFullInter) { finishTabAdFlow() }

    // Ad đáy (native thu gọn về banner) là sibling nằm dưới nav bar. Chờ một nhịp sau khi vào tab
    // cho màn kịp vẽ rồi mới hiện, đỡ giật layout.
    val isAdsActive by AdsLoader.isActive.collectAsState()
    var adGateReady by remember { mutableStateOf(false) }
    LaunchedEffect(isAdsActive) {
        if (!isAdsActive || adGateReady) return@LaunchedEffect
        delay(500L)
        adGateReady = true
    }
    val showBottomAd = isAdsActive && adGateReady
    val density = LocalDensity.current
    var adHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.bgRoot)) {
        // Nội dung các tab co lại đúng bằng chiều cao ad (đang co giãn theo animation). Nav bar vẫn
        // đè lên đáy nội dung như cũ nên các Spacer cuối danh sách giữ nguyên.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomAd) adHeight else 0.dp),
        ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                // 150ms fade between same-level destinations, per the base project's guidance.
                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
            },
            label = "tab_switch",
        ) { tab ->
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    Routes.TAB_HOME -> AiPhotoHomeScreen(
                        onTemplateClick = onOpenTemplate,
                        onCategoryAll = { nav.goOnce(Routes.categoryAll(it)) },
                        onToolClick = onOpenTool,
                        onOpenTools = { selected = Routes.TAB_TOOLS },
                        onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
                    )

                    Routes.TAB_TOOLS -> ToolsHubScreen(
                        onToolClick = onOpenTool,
                        onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
                    )

                    Routes.TAB_CREATIONS -> CreationsScreen(
                        onOpenCreation = { id ->
                            nav.goOnce(Routes.result(creationId = id, replay = true))
                        },
                        onExplore = { selected = Routes.TAB_HOME },
                        onShareRequested = { file ->
                            scope.launch { FileManager.clearStaleExports() }
                        },
                        onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
                    )

                    else -> ProfileScreen(
                        onOpenPaywall = { nav.goOnce(Routes.PAYWALL) },
                        onOpenLanguage = { nav.goOnce(Routes.LANGUAGE_SETTINGS) },
                    )
                }
            }
        }
        }

        // Nav bar và ad là hai sibling trong một Column: ad cao lên thì nav bar bị đẩy lên, ad co
        // xuống thì nav bar trả về chỗ cũ. Nav bar không biết gì về ad.
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(
                // Khi có ad bên dưới, ad lo padding thanh điều hướng hệ thống; nav bar tự áp
                // navigationBarsPadding() nên phải "tiêu thụ" inset trước để nó không đệm đôi.
                modifier = if (showBottomAd) {
                    Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                } else {
                    Modifier
                },
                items = AiPhotoNavItems,
                selectedId = selected,
                onSelect = ::onTabBarSelect,
            )
            if (showBottomAd) {
                HomeCollapsingAd(
                    tabKey = selected,
                    modifier = Modifier.onSizeChanged { size ->
                        adHeight = with(density) { size.height.toDp() }
                    },
                )
            }
        }

        if (showNativeFullInter && activity != null) {
            NativeAdOverlay(
                layoutResId = R.layout.frame_ad_full,
                closeDelaySeconds = 1,
                fullScreen = true,
                onClose = ::finishTabAdFlow,
            ) { container, shimmer ->
                AdsPolicy.bindNativeAd(
                    activity = activity,
                    adPlacement = AdsPlacementHolder.NATIVE_FULL_INTER,
                    container = container,
                    shimmer = shimmer,
                    templateAdLayoutRes = R.layout.template_ad_full,
                    // Không nạp được thì đổi tab luôn, không bắt user chờ.
                    onAdFailed = ::finishTabAdFlow,
                )
            }
        }
    }
}

// A NavBackStackEntry only reaches RESUMED once its *own* enter transition has fully played out,
// not as soon as it's on top of the stack. Gating navigation on `isResumed()` (the pattern this
// used to follow) therefore drops any tap that lands while the current screen is still animating
// in — which is exactly the "tap import, nothing happens, tap again and it works" bug: the entry
// the user is standing on hasn't finished becoming RESUMED yet when they tap. A short wall-clock
// debounce gives the same double-tap protection without being coupled to transition duration.
private const val NAV_DEBOUNCE_MS = 400L
private var lastNavigationAtMs = 0L

/** Navigate at most once per [NAV_DEBOUNCE_MS] window, regardless of transition/lifecycle state.
 *
 * Deliberately does NOT pass `launchSingleTop = true`: several destinations navigate to their own
 * route with a different argument (e.g. the "Cùng phong cách" strip on Template Detail calling
 * `templateDetail/{otherId}` while already on `templateDetail/{id}`, or Adjust moving from slot 0
 * to slot 1). `launchSingleTop` matches on the destination pattern only, not the resolved
 * arguments, so it would reuse the existing back-stack entry as-is and the screen would silently
 * fail to update — no new entry, no recomposition, no transition.
 */
/**
 * Where to go after the system picker fills a slot on a Detail screen. Same decisions as the old
 * PHOTO_PICKER `onPicked` (spec R5), minus popping the picker route, which no longer exists: a
 * 2-image flow stays on Detail so the user presses the CTA; a 1-image flow moves straight on.
 */
private fun NavHostController.continueAfterPick() {
    val draft = GenerationDraft.current
    when {
        draft.requiredSlots > 1 -> Unit
        draft.tool?.requiresAlignment == true -> goOnce(Routes.TRY_ON_ALIGN)
        draft.tool != null -> goOnce(Routes.GENERATING)
        else -> goOnce(Routes.adjust(0)) // template
    }
}

private fun NavHostController.goOnce(route: String) {
    val now = android.os.SystemClock.elapsedRealtime()
    if (now - lastNavigationAtMs < NAV_DEBOUNCE_MS) return
    lastNavigationAtMs = now
    navigate(route)
}

private fun NavHostController.goBackOnce() {
    val now = android.os.SystemClock.elapsedRealtime()
    if (now - lastNavigationAtMs < NAV_DEBOUNCE_MS) return
    lastNavigationAtMs = now
    popBackStack()
}
