package com.bblvn.composebaseproject.ads

/**
 * Tên placement quảng cáo — PHẢI trùng từng ký tự với key trên Firebase Remote Config. Sai một chữ
 * là vị trí đó im lặng không có quảng cáo. Chỉ khai báo `const val String` tên placement ở đây.
 */
object AdsPlacementHolder {
    // Base placement (giữ nguyên)

    const val NATIVE = "native"
    const val INTER = "inter"
    const val BANNER = "banner"
    const val REWARD = "reward"
    const val APP_OPEN = "app_open"

    // Placement theo kịch bản

    // ----- Splash -----
    const val NATIVE_SPLASH = "native_splash"
    const val INTER_SPLASH = "inter_splash"
    const val INTER_SPLASH_2ND = "inter_splash_2nd"
    const val NATIVE_FULL_SPLASH = "native_full_splash"

    // ----- Language (LFO) -----
    const val NATIVE_LFO1 = "native_lfo1"
    const val NATIVE_LFO2 = "native_lfo2"
    const val NATIVE_LFO1_2ND = "native_lfo1_2nd"
    const val NATIVE_LANGUAGE_SMALL = "native_language_small"
    const val NATIVE_LFO2_2ND = "native_lfo2_2nd"

    // ----- Survey (RG) -----
    const val NATIVE_RG1 = "native_rg1"
    const val NATIVE_RG2 = "native_rg2"

    // ----- Onboarding -----
    const val NATIVE_ONB1 = "native_onb1"
    const val NATIVE_ONB1_CLICK = "native_onb1_click"
    const val NATIVE_ONB2 = "native_onb2"
    const val NATIVE_FULL_OB_2 = "native_full_ob_2"
    const val NATIVE_ONB3 = "native_onb3"
    const val NATIVE_FULL_OB_3 = "native_full_ob_3"
    const val INTER_ONB = "inter_onb"

    // ----- Welcome / resume -----
    const val NATIVE_WELCOME = "native_welcome"
    const val NATIVE_RESUME = "native_resume"
    const val NATIVE_RELOAD = "native_reload"
    const val APP_OPEN_RESUME = "app_open_resume"
    const val INTER_WELCOME_BACK = "inter_welcome_back"

    // ----- Gỡ app -----
    const val BANNER_SPLASH_UNINSTALL = "banner_splash_uninstall"
    const val INTER_SPLASH_UNINSTALL = "inter_splash_uninstall"
    const val NATIVE_UNINSTALL = "native_uninstall"
    const val NATIVE_SURVEY_UNINSTALL = "native_survey_uninstall"

    // ----- Home / điều hướng chung -----
    const val BANNER_ALL = "banner_all"
    const val NATIVE_SWIPE_HOME = "native_swipe_home"
    const val NATIVE_COLLAPSE_HOME = "native_collapse_home"
    const val INTER_TAB = "inter_tab"
    const val NATIVE_FULL_INTER = "native_full_inter"

    // ----- Template -----
    const val NATIVE_TEMPLATE_LIST = "native_template_list"
    const val INTER_OPEN_TEMPLATE = "inter_open_template"
    const val NATIVE_TEMPLATE_DETAIL = "native_template_detail"

    // ----- Tool -----
    const val INTER_OPEN_TOOL = "inter_open_tool"
    const val NATIVE_TOOL_DETAIL = "native_tool_detail"

    // ----- Generate / result -----
    const val INTER_GENERATE = "inter_generate"
    const val NATIVE_GENERATING = "native_generating"
    const val NATIVE_RESULT = "native_result"
    const val INTER_SAVE = "inter_save"

    // ----- Reward -----
    const val REWARDED_UNLOCK_TEMPLATE = "rewarded_unlock_template"
    const val REWARDED_UNLOCK_TOOL = "rewarded_unlock_tool"
    const val REWARDED_REGENERATE = "rewarded_regenerate"
    const val REWARDED_HQ_SAVE = "rewarded_hq_save"

    // ----- Khác -----
    const val NATIVE_YOUR_PHOTOS = "native_your_photos"
    const val NATIVE_PAYWALL_CLOSE = "native_paywall_close"
}
