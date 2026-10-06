package com.bblvn.composebaseproject.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.configs.RemoteConfigManager
import com.bbl.ads.modules.function.AdCallback
import com.bblvn.composebaseproject.core.SettingManager
import com.facebook.shimmer.ShimmerFrameLayout
import com.bblvn.composebaseproject.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdModeManager {

    private val modeMap = ConcurrentHashMap<String, Int>()

    init {
        initDefaultModes()
    }

    private fun initDefaultModes() {
        AdsPlacementHolder::class.java.fields
            .filter { field ->
                field.type == String::class.java
            }
            .forEach { field ->
                val placement = field.get(null) as? String ?: return@forEach
                modeMap[placement] = 1
            }
    }

    fun syncFromRemoteConfig(context: Context) {
        val remote = RemoteConfigManager.getInstance(context)

        AdsPlacementHolder::class.java.fields
            .filter { field ->
                field.type == String::class.java
            }
            .forEach { field ->
                val placement = field.get(null) as? String ?: return@forEach

                val mode = try {
                    remote.getString(placement, "1")
                        ?.toIntOrNull() ?: 1
                } catch (_: Exception) {
                    1
                }

                modeMap[placement] = mode
            }
    }

    fun getMode(placement: String): Int {
        return modeMap[placement] ?: 1
    }

    fun isEnabled(placement: String): Boolean {
        return getMode(placement) != 0
    }
}

/** Kết cục của một lần gọi [AdsPolicy.showInterAd]. */
enum class InterAdOutcome {
    /** Không hiện (ads tắt, mode 0, đang trong giãn cách, hoặc bỏ qua theo luật mode 2). */
    SKIPPED,

    /** Có thử hiện nhưng nạp/hiện lỗi. */
    FAILED,

    /** Đã hiện và user đã đóng. */
    CLOSED,
}

object AdsPolicy {
    private const val INTERVAL = 30_000L

    @Volatile
    private var lastTimeShowedAt: Long = 0L

    @Volatile
    private var runtimeInterCount = 0

    @Volatile
    var allowShowWelcomeBack = true

    /**
     * Đã vào tới Home trong phiên này chưa. Màn Welcome Back (và ad đính kèm) chỉ chạy từ Home trở
     * đi: thoát app rồi quay lại khi còn ở Splash / Language / Survey / Onboarding thì không hiện.
     * Cố ý không lưu xuống đĩa — mỗi lần mở app lại phải đi qua flow đầu rồi mới tới Home.
     */
    @Volatile
    var hasReachedHome = false

    @Volatile
    var isShowingInterAd = false
        private set

    private val _isWaitingInterAd = MutableStateFlow(false)
    val isWaitingInterAd: StateFlow<Boolean> = _isWaitingInterAd.asStateFlow()

    private fun setShowingInter(showing: Boolean) {
        isShowingInterAd = showing
    }

    private fun setWaitingInterAd(waiting: Boolean) {
        _isWaitingInterAd.value = waiting
    }

    fun canShowInterAd(): Boolean = System.currentTimeMillis() - lastTimeShowedAt >= INTERVAL

    private fun resolvePlacement(placement: String): String {
        if (SettingManager.isFirstTime.value) return placement
        return when (placement) {
            AdsPlacementHolder.INTER_SPLASH -> AdsPlacementHolder.INTER_SPLASH_2ND
            AdsPlacementHolder.NATIVE_LFO1 -> AdsPlacementHolder.NATIVE_LFO1_2ND
            AdsPlacementHolder.NATIVE_LFO2 -> AdsPlacementHolder.NATIVE_LFO2_2ND
            else -> placement
        }
    }

    private inline fun skipIfAdsInactive(onSkip: () -> Unit): Boolean {
        if (!AdsLoader.isAdsEnabled()) {
            onSkip()
            return true
        }
        return false
    }

    private inline fun skipIfDisabled(
        placement: String,
        onDisabled: () -> Unit
    ): Boolean {
        if (!AdModeManager.isEnabled(placement)) {
            onDisabled()
            return true
        }
        return false
    }

    /**
     * Quảng cáo thưởng.
     *
     * Ba kết cục tách bạch, vì hệ quả của chúng trái ngược nhau:
     *  - [onRewardGranted] xem hết, đáng được thưởng.
     *  - [onAdFailed] không hiện nổi (hết fill, remote config tắt) — lỗi của ta, không thưởng gì.
     *  - [onDismissedWithoutReward] user tự đóng giữa chừng — KHÔNG thưởng, nếu không thì mở lên
     *    đóng ngay là có quà và chẳng ai xem hết quảng cáo nữa.
     */
    fun showRewardAd(
        activity: Activity,
        adPlacement: String,
        onRewardGranted: () -> Unit,
        onAdFailed: () -> Unit = {},
        onDismissedWithoutReward: () -> Unit = {},
    ) {
        val placement = resolvePlacement(adPlacement)
        if (!AdModeManager.isEnabled(placement)) {
            onAdFailed()
            return
        }

        setShowingInter(true)
        setWaitingInterAd(true)

        AdsManager.getInstance().loadAndShow(activity, placement, object : AdCallback {
            // SDK bắn onAdClosed cả sau khi đã thưởng và sau khi lỗi, nên phải lọc để chỉ kết cục
            // ĐẦU TIÊN được tính. Callback đều về main thread nên một cờ thường là đủ.
            private var settled = false

            /**
             * Dọn cờ rồi mới lọc: cờ phải sạch kể cả ở lần gọi thừa.
             * Bỏ sót đúng chỗ này là kẹt [isShowingInterAd] và chết cả màn Welcome Back.
             */
            private fun settle(outcome: () -> Unit) {
                setWaitingInterAd(false)
                setShowingInter(false)
                if (settled) return
                settled = true
                outcome()
            }

            override fun onAdLoaded(name: String) = setWaitingInterAd(false)

            override fun onRewardGranted(name: String) = settle(onRewardGranted)

            override fun onAdClosed(name: String) = settle(onDismissedWithoutReward)

            override fun onAdFailed(name: String, error: String) {
                Log.e("AdsLib", "$placement: $error")
                settle(onAdFailed)
            }
        })
    }

    fun showInterAd(
        activity: Activity,
        adPlacement: String,
        onContinue: () -> Unit,
        delayTime: Long = 0,
        force: Boolean = false, // Show ngay lập tức, không áp dụng luật bên dưới
        showWaitingOverlay: Boolean = true,
        /** Gọi ngay trước [onContinue], cho biết inter có thật sự hiện và đóng hay không. */
        onOutcome: ((InterAdOutcome) -> Unit)? = null,
    ) {
        val hasContinued = AtomicBoolean(false)
        val continueOnce = { outcome: InterAdOutcome ->
            if (hasContinued.compareAndSet(false, true)) {
                onOutcome?.invoke(outcome)
                onContinue()
            }
        }

        if (skipIfAdsInactive { continueOnce(InterAdOutcome.SKIPPED) }) return

        val placement = resolvePlacement(adPlacement)

        val mode = AdModeManager.getMode(placement)
        if (mode == 0) {
            continueOnce(InterAdOutcome.SKIPPED)
            return
        }

        if (!force) {
            when (mode) {
                1 -> {
                    if (!canShowInterAd()) {
                        continueOnce(InterAdOutcome.SKIPPED)
                        return
                    }
                }
                2 -> {
                    runtimeInterCount++
                    if (runtimeInterCount % 2 != 1) {
                        continueOnce(InterAdOutcome.SKIPPED)
                        return
                    }
                }
            }
        }

        val useCooldownOnImpression = !force && mode == 1

        setShowingInter(true)
        if (showWaitingOverlay) setWaitingInterAd(true)
        AdsManager.getInstance()
            .loadAndShowWithTimeDelay(
                activity,
                placement,
                25000,
                delayTime,
                object : AdCallback {
                    override fun onAdLoaded(name: String) {
                        if (showWaitingOverlay) setWaitingInterAd(false)
                        Log.i("AdsLib", "$placement loaded")
                        loadNextAd(placement)
                    }

                    override fun onAdFailed(name: String, error: String) {
                        if (showWaitingOverlay) setWaitingInterAd(false)
                        setShowingInter(false)
                        Log.e("AdsLib", "$placement: $error")
                        continueOnce(InterAdOutcome.FAILED)
                        loadNextAd(placement)
                    }

                    override fun onAdClosed(name: String) {
                        if (showWaitingOverlay) setWaitingInterAd(false)
                        setShowingInter(false)
                        Log.i("AdsLib", "$placement closed")
                        continueOnce(InterAdOutcome.CLOSED)
                    }

                    override fun onAdImpression(name: String) {
                        super.onAdImpression(name)
                        Log.i("AdsLib", "[Impression] $placement")
                        if (useCooldownOnImpression) {
                            lastTimeShowedAt = System.currentTimeMillis()
                        }
                    }
                }
            )
    }

    fun showAppOpenResumeAd(
        activity: Activity,
        onContinue: () -> Unit = {},
        delayTime: Long = 0L,
        showWaitingOverlay: Boolean = true,
    ) {
        showInterAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.APP_OPEN_RESUME,
            onContinue = onContinue,
            delayTime = delayTime,
            force = true,
            showWaitingOverlay = showWaitingOverlay,
        )
    }

    fun bindBannerAd(
        activity: Activity,
        adPlacement: String,
        onAdFailed: (() -> Unit)? = null
    ) {
        val placement = resolvePlacement(adPlacement)
        loadNextAd(placement)
        val bannerContainer = activity.findViewById<FrameLayout>(R.id.banner_container)

        if (skipIfAdsInactive {
                bannerContainer?.visibility = View.GONE
                onAdFailed?.invoke()
            }) return

        if (skipIfDisabled(placement) {
                bannerContainer?.visibility = View.GONE
            }) return

        AdsManager.getInstance().show(activity, placement, object : AdCallback {
            override fun onAdFailed(name: String, error: String) {
                bannerContainer?.visibility = View.GONE
                onAdFailed?.invoke()
                Log.e("AdsLib", "$name: $error")
            }

            override fun onAdImpression(name: String) {
                super.onAdImpression(name)
                Log.i("AdsLib", "[Impression] $placement")
            }
        })
    }

    fun bindNativeAd(
        activity: Activity,
        adPlacement: String,
        container: FrameLayout? = null,
        shimmer: ShimmerFrameLayout? = null,
        parentContainer: ViewGroup? = null,
        templateAdLayoutRes: Int = R.layout.template_ad,
        onAdFailed: (() -> Unit)? = null,
        onAdLoaded: (() -> Unit)? = null,
    ) {
        val placement = resolvePlacement(adPlacement)

        val adContainer: FrameLayout? = container ?: activity.findViewById(R.id.frAds)
        val shimmerView: ShimmerFrameLayout? = shimmer ?: activity.findViewById(R.id.shimmerNative)

        if (adContainer == null || shimmerView == null) return

        if (skipIfAdsInactive {
                shimmerView.stopShimmer()
                shimmerView.visibility = View.GONE
                adContainer.visibility = View.GONE
                parentContainer?.visibility = View.GONE
                onAdFailed?.invoke()
            }) return

        if (skipIfDisabled(placement) {
                shimmerView.stopShimmer()
                shimmerView.visibility = View.GONE
                adContainer.visibility = View.GONE
                parentContainer?.visibility = View.GONE
            }) return

        adContainer.visibility = View.VISIBLE
        adContainer.removeAllViews()

        shimmerView.visibility = View.VISIBLE
        shimmerView.startShimmer()

        if (shimmerView.parent != adContainer) {
            (shimmerView.parent as? ViewGroup)?.removeView(shimmerView)
            adContainer.addView(shimmerView)
        }

        AdsManager.getInstance().loadAndPopulateNativeAd(
            activity,
            placement,
            templateAdLayoutRes,
            adContainer,
            shimmerView,
            object : AdCallback {

                override fun onAdLoaded(name: String) {
                    shimmerView.stopShimmer()
                    shimmerView.visibility = View.GONE
                    onAdLoaded?.invoke()
                    Log.i("AdsLib", "$placement loaded")
                    preloadAfterShowNativeAd(placement)
                }

                override fun onAdImpression(name: String) {
                    super.onAdImpression(name)
                    Log.i("AdsLib", "[Impression] $placement")
                }

                override fun onAdFailed(name: String, error: String) {
                    shimmerView.stopShimmer()
                    shimmerView.visibility = View.GONE
                    adContainer.visibility = View.GONE

                    parentContainer?.visibility = View.GONE

                    onAdFailed?.invoke()

                    Log.e("AdsLib", "$placement: $error")
                    preloadAfterShowNativeAd(placement)
                }
            }
        )
    }

    /**
     * Nạp trước các ad của màn Welcome Back ngay lúc app bị đẩy xuống nền, để khi user quay lại
     * chúng đã sẵn sàng. Chỉ nạp khi màn đó có thể hiện (đã tới Home, không bị chặn).
     */
    fun preloadWelcomeBack() {
        if (!AdsLoader.isAdsEnabled() || !hasReachedHome || !allowShowWelcomeBack) return
        loadAd(AdsPlacementHolder.NATIVE_RESUME)
        loadAd(AdsPlacementHolder.INTER_WELCOME_BACK)
    }

    fun loadAd(adPlacement: String) {
        if (!AdsLoader.isAdsEnabled()) return
        val placement = resolvePlacement(adPlacement)
        AdsManager.getInstance().load(placement, object : AdCallback {
            override fun onAdLoaded(name: String) {
                super.onAdLoaded(name)
                Log.i("AdsLib", "$name preloaded")
            }

            override fun onAdFailed(name: String, error: String) {
                super.onAdFailed(name, error)
                Log.e("AdsLib", "$name preload failed: $error")
            }
        })
    }
    /**
     * Nạp trước các inter dùng chung giãn cách 30s (đổi tab, mở template, mở tool) ngay khi tới Home,
     * để lần bấm đầu tiên không phải chờ nạp.
     */
    fun preloadHomeInters() {
        if (!AdsLoader.isAdsEnabled()) return
        loadAd(AdsPlacementHolder.INTER_TAB)
        loadAd(AdsPlacementHolder.INTER_OPEN_TEMPLATE)
        loadAd(AdsPlacementHolder.INTER_OPEN_TOOL)
    }

    fun loadNextAd(adPlacement: String) {
        if (!AdsLoader.isAdsEnabled()) return

        // Sau inter_tab là native_full_inter: nạp sẵn để chuỗi không bị khựng.
        if (adPlacement == AdsPlacementHolder.INTER_TAB) loadAd(AdsPlacementHolder.NATIVE_FULL_INTER)

        if (adPlacement == AdsPlacementHolder.INTER_SPLASH || adPlacement == AdsPlacementHolder.INTER_SPLASH_2ND) {
            loadAd(AdsPlacementHolder.NATIVE_LFO1)
            loadAd(AdsPlacementHolder.NATIVE_LANGUAGE_SMALL)
        }
    }

    /** [adPlacement] đã qua [resolvePlacement], nên phải so cả bản `_2nd`. */
    fun preloadAfterShowNativeAd(adPlacement: String) {
        if (!AdsLoader.isAdsEnabled()) return

        if (adPlacement == AdsPlacementHolder.NATIVE_LFO1 || adPlacement == AdsPlacementHolder.NATIVE_LFO1_2ND) {
            loadAd(AdsPlacementHolder.NATIVE_LFO2)
        }
        if (adPlacement == AdsPlacementHolder.NATIVE_LFO2 || adPlacement == AdsPlacementHolder.NATIVE_LFO2_2ND) {
            loadAd(AdsPlacementHolder.NATIVE_RG1)
            loadAd(AdsPlacementHolder.NATIVE_ONB1)
        }
        if (adPlacement == AdsPlacementHolder.NATIVE_RG1) loadAd(AdsPlacementHolder.NATIVE_RG2)

        // Survey xong thì tới onboarding: nạp trước toàn bộ ô của chặng đó.
        if (adPlacement == AdsPlacementHolder.NATIVE_ONB1) loadAd(AdsPlacementHolder.NATIVE_ONB1_CLICK)
        if (adPlacement == AdsPlacementHolder.NATIVE_ONB1_CLICK) {
            loadAd(AdsPlacementHolder.NATIVE_ONB2)
            loadAd(AdsPlacementHolder.NATIVE_FULL_OB_2)
            loadAd(AdsPlacementHolder.NATIVE_ONB3)
            loadAd(AdsPlacementHolder.NATIVE_FULL_OB_3)
            loadAd(AdsPlacementHolder.NATIVE_WELCOME)
        }
        // Splash: nạp trước native full sẽ chen sau inter.
        if (adPlacement == AdsPlacementHolder.NATIVE_SPLASH) loadAd(AdsPlacementHolder.NATIVE_FULL_SPLASH)
    }
}
