package com.bblvn.composebaseproject.core

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Context.localDataStore by preferencesDataStore(name = "local_data")

object SettingManager {

    private lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    // For app
    private val IS_FIRST_TIME = booleanPreferencesKey("isFirstTime")
    private val APP_LOCALE = stringPreferencesKey("appLocale")

    // For business
    private val ONBOARDING_FLOW_DONE = booleanPreferencesKey("onBoardingFlowDone")
    private val CHECK_CONSENT_DONE = booleanPreferencesKey("checkConsentDone")

    // For AI Photo. NOTE: premium is NOT here — it is a runtime flag in AppCapability, never persisted.
    private val HIGH_QUALITY = booleanPreferencesKey("highQuality")
    private val AUTO_SAVE = booleanPreferencesKey("autoSave")
    private val CREDIT_BALANCE = intPreferencesKey("creditBalance")
    private val UNLOCK_PASSES = stringPreferencesKey("unlockPasses")
    private val CLAIMED_PURCHASES = stringPreferencesKey("claimedPurchases")
    private val REWARDED_CREDIT_VIEWS = intPreferencesKey("rewardedCreditViews")

    private val _isFirstTime = MutableStateFlow(true)
    val isFirstTime: StateFlow<Boolean> = _isFirstTime.asStateFlow()

    private val _appLocale = MutableStateFlow("")
    val appLocale = _appLocale.asStateFlow()


    private val _onBoardingFlowDone = MutableStateFlow(false)
    val onBoardingFlowDone: StateFlow<Boolean> = _onBoardingFlowDone.asStateFlow()

    private val _checkConsentDone = MutableStateFlow(false)
    val checkConsentDone: StateFlow<Boolean> = _checkConsentDone.asStateFlow()

    /** "High quality" in Settings. Only honoured for premium users (see PremiumManager). */
    private val _highQuality = MutableStateFlow(false)
    val highQuality: StateFlow<Boolean> = _highQuality.asStateFlow()

    /** Export every successful result straight to the gallery. */
    private val _autoSave = MutableStateFlow(false)
    val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()

    /**
     * Số dư credit (xem [AppCapability]). Giá trị gốc 0 chỉ tồn tại tới khi đọc xong ổ đĩa; user mới
     * được tặng [AppCapability.FREE_STARTING_CREDITS] đúng một lần, ngay lúc đó.
     */
    private val _creditBalance = MutableStateFlow(0)
    val creditBalance: StateFlow<Int> = _creditBalance.asStateFlow()

    /**
     * Lượt dùng thử MỘT LẦN template/tool PRO (mở bằng cách xem quảng cáo thưởng). Mỗi phần tử là một
     * lượt, khoá dạng `tpl:<id>` / `tool:<TÊN>`; xem ad hai lần cho cùng một mục thì có hai phần tử.
     * Là danh sách chứ không phải tập hợp vì phải giữ được phần tử trùng.
     */
    private val _unlockPasses = MutableStateFlow<List<String>>(emptyList())
    val unlockPasses: StateFlow<List<String>> = _unlockPasses.asStateFlow()

    private val _rewardedCreditViews = MutableStateFlow(0)

    /** Số lần đã xem quảng cáo thưởng để nhận credit (xem [AppCapability.MAX_REWARDED_CREDIT_VIEWS]). */
    val rewardedCreditViews: StateFlow<Int> = _rewardedCreditViews.asStateFlow()

    /** Mã đơn Play đã cộng credit rồi (giữ vài chục cái gần nhất), để một đơn không cộng hai lần. */
    private val claimedPurchases = MutableStateFlow<List<String>>(emptyList())

    /** Đã đọc số dư từ ổ đĩa chưa. Chưa thì không cho trừ, tránh trừ trên số 0 giả. */
    @Volatile
    private var creditsLoaded = false

    fun init(context: Context) {
        val appContext = context.applicationContext
        dataStore = appContext.localDataStore

        scope.launch {
            val prefs = dataStore.data.first()

            _isFirstTime.value = prefs[IS_FIRST_TIME] ?: true
            val savedLocale = prefs[APP_LOCALE] ?: Locale.getDefault().language
            _appLocale.value = savedLocale

            _onBoardingFlowDone.value = prefs[ONBOARDING_FLOW_DONE] ?: false
            _checkConsentDone.value = prefs[CHECK_CONSENT_DONE] ?: false
            _highQuality.value = prefs[HIGH_QUALITY] ?: false
            _autoSave.value = prefs[AUTO_SAVE] ?: false

            val savedCredits = prefs[CREDIT_BALANCE]
            _creditBalance.value = savedCredits ?: AppCapability.FREE_STARTING_CREDITS
            creditsLoaded = true

            _rewardedCreditViews.value = prefs[REWARDED_CREDIT_VIEWS] ?: 0

            claimedPurchases.value = prefs[CLAIMED_PURCHASES]
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty()

            _unlockPasses.value = prefs[UNLOCK_PASSES]
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (savedCredits == null) {
                dataStore.edit { it[CREDIT_BALANCE] = AppCapability.FREE_STARTING_CREDITS }
            }

            if (_isFirstTime.value) dataStore.edit { it[IS_FIRST_TIME] = false }

            withContext(Dispatchers.Main) {
                applyLocale(savedLocale)
            }

            Log.i("SettingManager", "isFirstTime=${_isFirstTime.value}, locale=$savedLocale")
        }
    }

    private fun applyLocale(localeId: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(localeId)
        )
    }
    fun setAppLocale(localeId: String) {
        val changed = _appLocale.value != localeId
        _appLocale.value = localeId
        applyLocale(localeId)

        if (changed) {
            scope.launch {
                dataStore.edit {
                    it[APP_LOCALE] = localeId
                }
            }
        }
    }

    /**
     * Trừ [amount] credit, nguyên tử: trả về false (và không đổi gì) nếu không đủ. So-và-đặt trên
     * StateFlow nên hai lần bấm gần nhau không thể cùng trừ một số dư.
     */
    fun trySpendCredits(amount: Int): Boolean {
        if (amount <= 0 || !creditsLoaded) return false
        while (true) {
            val current = _creditBalance.value
            if (current < amount) return false
            if (_creditBalance.compareAndSet(current, current - amount)) break
        }
        persistCredits()
        return true
    }

    /** Cộng [amount] credit (hoàn, mua, thưởng). */
    fun addCredits(amount: Int) {
        if (amount <= 0) return
        _creditBalance.update { it + amount }
        persistCredits()
    }

    /**
     * Đăng ký [orderId] là đã xử lý. Trả về true đúng MỘT lần cho mỗi mã (so-và-đặt nguyên tử), nên
     * callback billing bị gọi lặp (hoặc app chết giữa lúc cộng và consume) cũng không cộng hai lần.
     */
    fun claimPurchase(orderId: String): Boolean {
        if (orderId.isBlank()) return true
        while (true) {
            val current = claimedPurchases.value
            if (orderId in current) return false
            if (claimedPurchases.compareAndSet(current, (current + orderId).takeLast(CLAIMED_PURCHASES_KEPT))) break
        }
        scope.launch {
            dataStore.edit { it[CLAIMED_PURCHASES] = claimedPurchases.value.joinToString("\n") }
        }
        return true
    }

    /** Ghi nhận một lần xem quảng cáo thưởng để nhận credit. */
    fun recordRewardedCreditView() {
        _rewardedCreditViews.update { it + 1 }
        scope.launch { dataStore.edit { it[REWARDED_CREDIT_VIEWS] = _rewardedCreditViews.value } }
    }

    /** Cộng một lượt mở khoá cho [key]. */
    fun addUnlockPass(key: String) {
        if (key.isBlank()) return
        _unlockPasses.update { it + key }
        persistUnlockPasses()
    }

    /** Dùng một lượt của [key]; false nếu không còn lượt nào. So-và-đặt nên không dùng được hai lần. */
    fun consumeUnlockPass(key: String): Boolean {
        while (true) {
            val current = _unlockPasses.value
            val index = current.indexOf(key)
            if (index < 0) return false
            val next = current.toMutableList().also { it.removeAt(index) }
            if (_unlockPasses.compareAndSet(current, next)) break
        }
        persistUnlockPasses()
        return true
    }

    private fun persistUnlockPasses() {
        scope.launch {
            dataStore.edit { it[UNLOCK_PASSES] = _unlockPasses.value.joinToString("\n") }
        }
    }

    /** Ghi giá trị MỚI NHẤT tại thời điểm chạy, nên nhiều lần ghi chồng nhau vẫn kết thúc đúng số dư. */
    private fun persistCredits() {
        scope.launch { dataStore.edit { it[CREDIT_BALANCE] = _creditBalance.value } }
    }

    fun setHighQuality(enabled: Boolean) {
        if (_highQuality.value == enabled) return
        _highQuality.value = enabled
        scope.launch { dataStore.edit { it[HIGH_QUALITY] = enabled } }
    }

    fun setAutoSave(enabled: Boolean) {
        if (_autoSave.value == enabled) return
        _autoSave.value = enabled
        scope.launch { dataStore.edit { it[AUTO_SAVE] = enabled } }
    }

    fun setOnBoardingFlowDone() {
        if (_onBoardingFlowDone.value) return

        _onBoardingFlowDone.value = true

        scope.launch {
            dataStore.edit {
                it[ONBOARDING_FLOW_DONE] = true
            }
        }
    }

    fun setCheckConsentDone() {
        if (_checkConsentDone.value) return

        _checkConsentDone.value = true

        scope.launch {
            dataStore.edit {
                it[CHECK_CONSENT_DONE] = true
            }
        }
    }

    private const val CLAIMED_PURCHASES_KEPT = 50
}
