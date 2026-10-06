package com.bblvn.composebaseproject.features.uninstall

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.bblvn.composebaseproject.BuildConfig
import com.bblvn.composebaseproject.LogScreenView
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPlacementHolder
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.data.database.repo.CreationRepo
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.SelectionIndicator
import com.bblvn.composebaseproject.ui.components.TextActionButton
import com.bblvn.composebaseproject.ui.components.ads.AdBanner
import com.bblvn.composebaseproject.ui.components.ads.ComposeNativeAdFrame
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowBrand
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/** Thời gian để banner kịp hiện trước khi chen inter, khi user còn xem quảng cáo. */
private const val SPLASH_WITH_ADS_MS = 2_000L

/** User đã trả phí không có quảng cáo để chờ: chỉ cần một nhịp ngắn cho khỏi giật. */
private const val SPLASH_NO_ADS_MS = 600L

/**
 * Bước 0 — cổng chờ khi user bấm shortcut "Uninstall".
 *
 * Banner `banner_splash_uninstall` nằm ở đáy; chờ [AdsLoader] sẵn sàng rồi chen
 * `inter_splash_uninstall`. Không load được inter thì vẫn đi tiếp, không chặn đường.
 */
@Composable
fun UninstallSplashScreen(
    onKeepApp: () -> Unit,
    onNext: () -> Unit,
) {
    LogScreenView("UninstallSplash")

    val activity = LocalActivity.current
    val ready by AdsLoader.finished.collectAsState()

    // Back = đổi ý.
    BackHandler(onBack = onKeepApp)

    // Rời Splash đúng MỘT lần: callback của inter có thể về sau khi user đã bấm Back.
    val hasLeft = remember { AtomicBoolean(false) }
    val leave: () -> Unit = { if (hasLeft.compareAndSet(false, true)) onNext() }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect

        if (activity == null || !AdsLoader.isAdsEnabled()) {
            delay(SPLASH_NO_ADS_MS)
            leave()
            return@LaunchedEffect
        }

        delay(SPLASH_WITH_ADS_MS)
        AdsPolicy.showInterAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.INTER_SPLASH_UNINSTALL,
            force = true,
            // Activity này không có overlay chờ inter của BaseActivity nên không bật cờ chờ.
            showWaitingOverlay = false,
            onContinue = leave,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .glowBrand(AppShapes.card)
                    .clip(AppShapes.card)
                    .border(1.dp, AppColors.strokeStrong, AppShapes.card),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = AppType.display,
                color = AppColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = AppColors.accentViolet,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.uninstall_splash_loading),
                style = AppType.label,
                color = AppColors.textSecondary,
            )
        }

        // Giữ chỗ cao 96dp dù banner đã về hay chưa để bố cục không nhảy.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(96.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (ready) AdBanner(placement = AdsPlacementHolder.BANNER_SPLASH_UNINSTALL)
        }
    }
}

/**
 * Bước 1 — native `native_uninstall`.
 *
 * Giữ giọng điệu nhẹ nhàng, chưa doạ: chỉ nhắc rằng các tác phẩm đang nằm trong app.
 */
@Composable
fun UninstallNoticeScreen(
    onKeepApp: () -> Unit,
    onContinue: () -> Unit,
) {
    LogScreenView("UninstallNotice")

    // Bấm Back ở đây = đổi ý, không phải thoát ra màn hình chờ.
    BackHandler(onBack = onKeepApp)

    val creations by CreationRepo.creations.collectAsState()
    val count = creations.size

    UninstallScaffold(adPlacement = AdsPlacementHolder.NATIVE_UNINSTALL) {
        Spacer(Modifier.weight(1f))
        HeroIcon(R.drawable.ic_sparkles)
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.uninstall_notice_title),
            style = AppType.display,
            color = AppColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (count == 0) {
                stringResource(R.string.uninstall_notice_desc_empty)
            } else {
                stringResource(R.string.uninstall_notice_desc, count)
            },
            style = AppType.body,
            color = AppColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            text = stringResource(R.string.uninstall_keep_app),
            onClick = onKeepApp,
        )
        Spacer(Modifier.height(6.dp))
        // Lối rời đi để dạng chữ, không dùng nút đặc — hành động phụ không nên tranh sự chú ý
        // với nút giữ app.
        TextActionButton(
            text = stringResource(R.string.uninstall_continue),
            color = AppColors.textTertiary,
            onClick = onContinue,
        )
        Spacer(Modifier.weight(1f))
    }
}

/** Lý do gỡ. [key] là giá trị gửi đi cho phân tích, không dịch. */
private enum class UninstallReason(val key: String, @StringRes val label: Int) {
    QUALITY("quality", R.string.uninstall_reason_quality),
    SLOW("slow", R.string.uninstall_reason_slow),
    ADS("ads", R.string.uninstall_reason_ads),
    PRICE("price", R.string.uninstall_reason_price),
    BETTER_APP("better_app", R.string.uninstall_reason_better),
    OTHER("other", R.string.uninstall_reason_other),
}

/**
 * Bước 2 — native `native_survey_uninstall`.
 *
 * Hỏi lý do gỡ. Không bắt buộc chọn: chặn nút gỡ sau một câu hỏi chỉ khiến user bực. Lý do (nếu
 * có) được gửi lên Firebase ngay trước khi bàn giao cho hộp thoại gỡ của hệ thống.
 */
@Composable
fun UninstallSurveyScreen(
    onKeepApp: () -> Unit,
    onUninstall: () -> Unit,
) {
    LogScreenView("UninstallSurvey")

    BackHandler(onBack = onKeepApp)

    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    fun confirm() {
        val reason = selected
        if (reason != null && !BuildConfig.DEBUG) {
            FirebaseAnalytics.getInstance(context)
                .logEvent("uninstall_reason", bundleOf("reason" to reason))
        }
        onUninstall()
    }

    UninstallScaffold(adPlacement = AdsPlacementHolder.NATIVE_SURVEY_UNINSTALL) {
        // Vùng cuộn: danh sách lý do + tiêu đề. Nút nằm ngoài vùng này nên luôn thấy được dù ad
        // chiếm chỗ.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.uninstall_survey_title),
                style = AppType.display,
                color = AppColors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.uninstall_survey_subtitle),
                style = AppType.body,
                color = AppColors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UninstallReason.entries.forEach { reason ->
                    ReasonRow(
                        text = stringResource(reason.label),
                        selected = selected == reason.key,
                        onClick = { selected = reason.key },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = stringResource(R.string.uninstall_keep_app),
            onClick = onKeepApp,
        )
        TextActionButton(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = stringResource(R.string.uninstall_confirm),
            color = AppColors.stateDanger,
            onClick = ::confirm,
        )
    }
}

/**
 * Khung chung của các bước có ad native ở đáy: cùng lề, cùng chỗ đặt ad, chỉ khác nội dung — để
 * các màn nối nhau mà không bị nhảy layout.
 */
@Composable
private fun UninstallScaffold(
    adPlacement: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bgRoot),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )

            ComposeNativeAdFrame(
                modifier = Modifier.fillMaxWidth(),
                adPlacement = adPlacement,
                reloadOnResume = false,
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun HeroIcon(icon: Int) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .glowBrand(CircleShape)
            .clip(CircleShape)
            .background(AppColors.accentViolet.copy(alpha = 0.15f))
            .border(1.dp, AppColors.strokeStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppColors.textPrimary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ReasonRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) AppColors.accentViolet.copy(alpha = 0.14f) else AppColors.bgSurface,
        animationSpec = tween(200),
        label = "reason_bg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(AppShapes.thumb)
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, AppGradients.brand, AppShapes.thumb)
                } else {
                    Modifier.border(1.dp, AppColors.strokeSoft, AppShapes.thumb)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = AppType.body,
            color = AppColors.textPrimary,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        SelectionIndicator(selected = selected)
    }
}
