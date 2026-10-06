package com.bblvn.composebaseproject

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.consent.ConsentCallback
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.features.onboarding.SplashScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Button
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.features.language.LanguageScreen
import com.bblvn.composebaseproject.features.onboarding.OnboardingScreen
import com.bblvn.composebaseproject.features.nav.MainContent
import com.bblvn.composebaseproject.features.survey.SurveyScreen

private const val ENTER_TIME = 400
private const val EXIT_TIME = 300

/*
Đây là bộ hiệu ứng do tôi soạn, bạn nên lựa chọn và tái sử dụng chúng một cách phù hợp khi kết hợp với NavHost hoặc AnimatedContent
Đối với các app có NavBar, transition cơ bản giữa các màn cùng level trên thanh đó thì dùng fade với 150ms
*/

val ZoomTransition = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME)) + scaleIn(initialScale = 1.3f, animationSpec = tween(ENTER_TIME)),
    fadeOut(animationSpec = tween(EXIT_TIME)) + scaleOut(targetScale = 1.3f, animationSpec = tween(EXIT_TIME)),
)

val SlideHorizontalTransition = Pair(
    slideInHorizontally(initialOffsetX = { it }),
    slideOutHorizontally(targetOffsetX = { it }),
)

// slideIn* must carry the same explicit tween as the fade: without it Compose falls back to an
// unbounded spring, so the enter transition (and the NavBackStackEntry becoming RESUMED) can
// outlast the 200ms fade — a tap landing in that gap on the freshly-drawn screen is silently
// dropped by the RESUMED guard in MainContent.goOnce(), which looked like "the first tap does
// nothing, the second one works".
val FadeSlideHorizontal = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(ENTER_TIME / 2),
    ),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)),
)

val FadeSlideUp = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInVertically(
        initialOffsetY = { it / 4 },
        animationSpec = tween(ENTER_TIME / 2),
    ),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)),
)

// Mirrored pop transitions for the two pairs above: the screen underneath just fades back in,
// while the screen being popped fades out and slides back the way it came.
val FadeSlideHorizontalPop = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)) + slideOutHorizontally(
        targetOffsetX = { it / 3 },
        animationSpec = tween(EXIT_TIME / 2),
    ),
)

val FadeSlideUpPop = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)) + slideOutVertically(
        targetOffsetY = { it / 4 },
        animationSpec = tween(EXIT_TIME / 2),
    ),
)

val FlowTransition = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(ENTER_TIME)
    ),
    slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(ENTER_TIME * 2)
    ),
)

// Bắt buộc dùng trong màn Home thật sự
@Composable
fun CheckForConsent() {
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        if (SettingManager.checkConsentDone.value) return@LaunchedEffect

        AdsManager.getInstance().runConsentFlow(activity, object : ConsentCallback {
            override fun onConsentComplete(canPersonalized: Boolean) {
                AdsManager.getInstance().logCustomEvent("consent_success", mutableMapOf<String, Any?>("result" to canPersonalized))
                AdsManager.getInstance().reloadConsent()
                SettingManager.setCheckConsentDone()
            }

            override fun onConsentError(message: String?) {
                AdsManager.getInstance().logCustomEvent("consent_error", mutableMapOf<String, Any?>("message" to (message ?: "")))
                AdsManager.getInstance().reloadConsent()
                SettingManager.setCheckConsentDone()
            }
        })
    }
}

@Composable
fun LogScreenView(screenName: String) {
    val context = LocalContext.current
    LaunchedEffect(screenName) {
        if (BuildConfig.DEBUG) return@LaunchedEffect
        FirebaseAnalytics.getInstance(context).logEvent(screenName, null)
    }
}

// Dùng ở màn nào được tôi yêu cầu vô hiệu hóa show Welcome back screen
@Composable
fun DisableWelcomeOverlayTemporarily() {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AdsPolicy.allowShowWelcomeBack = false
            }
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                AdsPolicy.allowShowWelcomeBack = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AdsPolicy.allowShowWelcomeBack = true
        }
    }
}



// Đây là flow chuẩn và cố định của App, bạn bắt đầu cần xây dựng từ MainContent() và thay thế vào flow bên dưới
private enum class AppScreen { Splash, Language, Survey, Onboarding, Main }

@Composable
fun App() {
    var screenOrdinal by rememberSaveable { mutableStateOf(AppScreen.Splash.ordinal) }
    val screen = AppScreen.entries[screenOrdinal]
    fun goTo(s: AppScreen) { screenOrdinal = s.ordinal }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { FlowTransition.first togetherWith FlowTransition.second },
        label = "app_flow"
    ) { target ->
        when (target) {
            AppScreen.Splash -> SplashScreen(
                onNext = { goTo(AppScreen.Language) },
                // Đã trả phí: vào thẳng app, bỏ qua cả phễu Language / Survey / Onboarding.
                onPremiumReady = { goTo(AppScreen.Main) },
            )
            AppScreen.Language -> LanguageScreen(
                onNext = { goTo(AppScreen.Survey) }
            )
            AppScreen.Survey -> SurveyScreen(
                onNext = { goTo(AppScreen.Onboarding) }
            )
            AppScreen.Onboarding -> OnboardingScreen(
                onNext = {
                    SettingManager.setOnBoardingFlowDone()
                    goTo(AppScreen.Main)
                }
            )
            AppScreen.Main -> {
                // Từ đây Welcome Back mới được phép hiện khi user quay lại app.
                LaunchedEffect(Unit) {
                    AdsPolicy.hasReachedHome = true
                    AdsPolicy.preloadHomeInters()
                }
                MainContent()
            }
        }
    }
}
