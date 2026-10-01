package com.bblvn.composebaseproject.core

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val IS_PURCHASED = booleanPreferencesKey("isPurchased")
    private val ONBOARDING_FLOW_DONE = booleanPreferencesKey("onBoardingFlowDone")
    private val CHECK_CONSENT_DONE = booleanPreferencesKey("checkConsentDone")

    // For AI Photo. NOTE: premium is NOT here — isPurchased above is the one premium flag (C2).
    private val HIGH_QUALITY = booleanPreferencesKey("highQuality")
    private val AUTO_SAVE = booleanPreferencesKey("autoSave")

    private val _isFirstTime = MutableStateFlow(true)
    val isFirstTime: StateFlow<Boolean> = _isFirstTime.asStateFlow()

    private val _appLocale = MutableStateFlow("")
    val appLocale = _appLocale.asStateFlow()


    private val _isPurchased = MutableStateFlow(false)
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

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

    fun init(context: Context) {
        val appContext = context.applicationContext
        dataStore = appContext.localDataStore

        scope.launch {
            val prefs = dataStore.data.first()

            _isFirstTime.value = prefs[IS_FIRST_TIME] ?: true
            val savedLocale = prefs[APP_LOCALE] ?: Locale.getDefault().language
            _appLocale.value = savedLocale

            _isPurchased.value = prefs[IS_PURCHASED] ?: false
            _onBoardingFlowDone.value = prefs[ONBOARDING_FLOW_DONE] ?: false
            _checkConsentDone.value = prefs[CHECK_CONSENT_DONE] ?: false
            _highQuality.value = prefs[HIGH_QUALITY] ?: false
            _autoSave.value = prefs[AUTO_SAVE] ?: false

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

    fun setPurchased(purchased: Boolean) {
        if (!purchased || _isPurchased.value) return

        _isPurchased.value = true

        scope.launch {
            dataStore.edit {
                it[IS_PURCHASED] = true
            }
        }
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
}