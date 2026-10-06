package com.bblvn.composebaseproject

import com.bblvn.composebaseproject.core.Analytics
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.application.AdsMultiDexApplication
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.configs.GlobalConfig
import com.bblvn.composebaseproject.ads.AdsLoader
import com.bblvn.composebaseproject.ads.AdsPolicy
import com.bblvn.composebaseproject.core.AppRuntime
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.features.iap.Iap
import com.bblvn.composebaseproject.features.iap.IapController
import com.bblvn.composebaseproject.features.uninstall.UninstallActivity
import com.bblvn.composebaseproject.features.uninstall.createUninstallShortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalApp : AdsMultiDexApplication(), coil.ImageLoaderFactory {

    private val _shouldShowWelcomeBack = MutableStateFlow(false)
    val shouldShowWelcomeBack = _shouldShowWelcomeBack.asStateFlow()

    override fun createDefaultGlobalConfig(): GlobalConfig {
        return GlobalConfig().apply {
            appId = BuildConfig.ADMOB_APP_ID
            isConsentRequiredBeforeInit = false
            isEnableRemoteConfig = true
            adLoggingType = "realtime_database"
            isVariantDev = BuildConfig.DEBUG
            loadStrategy = GlobalConfig.LOAD_STRATEGY_WATERFALL
            // Adjust: token app + token event dùng để đo doanh thu quảng cáo.
            adjustToken = "gl4yzp7vw1ds"
            adjustEventToken = "fjy52o"
            appsflyerToken = ""
            // Meta / Facebook SDK (trùng với @string/facebook_app_id + facebook_client_token
            // đang khai báo ở AndroidManifest cho meta-data của SDK).
            metaFacebookAppId = "2125791808013444"
            facebookClientToken = "194e9e2a036dc3da92a8915a8f53e03d"
            // Bắn doanh thu quảng cáo sang Facebook dưới dạng event "purchase" để Meta có tín hiệu
            // tối ưu chiến dịch.
            isEnableFacebookAdRevenuePurchaseLogging = true
            // Module-ads đối chiếu id mua được với hai mảng này để tự nhận ra user đã trả tiền (tắt ads),
            // nên phải khai đúng loại. Gói credit (consumable) cố ý KHÔNG nằm ở đây.
            adFreeSubscriptionProductIds = Iap.subscriptions.toTypedArray()
            adFreeInAppProductIds = Iap.inAppProducts.toTypedArray()
            testDevices = arrayOf("727854F084C6F0CDC793A5A192B72FCC", "8D9B9371B8704790A1C041FAF7C14071", "B7DE3A60025A6EBCAC171F608A5EB87B", "EB52EFE4C726897953885CB827CA2EFC")
        }
    }

    override fun onCreate() {
        super.onCreate()

        SettingManager.init(this)
        Analytics.init(this)
        // AI Photo: storage, history DB, generation engine, catalog (see core/AppRuntime.kt).
        AppRuntime.init(this)

        AdsManager.getInstance().apply {
            disableAppResumeWithActivity(com.google.android.libraries.ads.mobile.sdk.common.AdActivity::class.java)
            disableAppResumeWithActivity(MainActivity::class.java)
            // Luồng gỡ app là màn giữ chân, chen thêm quảng cáo mở app vào đó thì phản tác dụng.
            disableAppResumeWithActivity(UninstallActivity::class.java)
        }

        // Shortcut "Uninstall" ở menu giữ icon launcher — lối vào duy nhất khả thi, vì Android
        // không báo cho app biết lúc nó sắp bị gỡ.
        createUninstallShortcut(this)

        AdsLoader.initEarly(this)

        observeAppBackground()
        initBilling()
    }

    /**
     * Catalog thumbnails stream in by the hundred, so Coil's disk cache is capped at 256MB and
     * memory at 25% of the heap (spec section 9 / Risks #29). Screens must still request
     * card-sized images rather than full-resolution originals.
     */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .crossfade(200)
        // Every catalog image lives behind Bunny private storage and 401s without the key, so the
        // loader gets its own OkHttp client carrying CdnAuth. Without this the app looks broken in
        // a very specific way: the catalog JSON loads fine and every thumbnail stays a placeholder.
        .okHttpClient {
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(com.bblvn.composebaseproject.data.api.CdnAuth.interceptor)
                .build()
        }
        .memoryCache {
            coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

    private fun observeAppBackground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!AdsPolicy.isShowingInterAd && AdsLoader.isAdsEnabled()) {
                    _shouldShowWelcomeBack.value = true
                    AdsPolicy.preloadWelcomeBack()
                }
            }
        })
    }

    fun consumeWelcomeBack() {
        _shouldShowWelcomeBack.value = false
    }

    private fun initBilling() {
        val listIAPId: ArrayList<String> = ArrayList()
        val listSubsId: ArrayList<String> = ArrayList()
        listSubsId.addAll(Iap.subscriptions)
        listIAPId.addAll(Iap.allInApp)
        AppPurchase.getInstance().initBilling(this, listIAPId, listSubsId, this.globalConfig)
        IapController.registerPurchaseListener()
        // Quyền lợi KHÔNG được lưu trên máy nên phải dựng lại từ câu trả lời của Play; giá trị đó chỉ
        // có sau khi billing client bắt tay xong, nên cần listener này.
        IapController.registerBillingListener()
    }
}
