package com.bblvn.composebaseproject.corefeatures.premium

import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.data.domain.ToolType
import kotlinx.coroutines.flow.StateFlow

/**
 * The ONE place the app asks "is this allowed?".
 *
 * Per C2 this is a thin wrapper over the flag the base project already owns,
 * [SettingManager.isPurchased] — no new DataStore key, no `premiumUntil` (there is no real billing
 * yet, so an expiry date would be a lie). Keeping every gate behind these functions means the
 * phase that wires real billing changes only this file, not 16 screens (Risks #33).
 *
 * Known limitation (Risks #34): the flag is local, so root/clear-data can forge it. Acceptable
 * until receipt verification exists.
 */
object PremiumManager {

    /** Observe this for UI that changes with entitlement (crown chip, badges, watermark notice). */
    val isPremium: StateFlow<Boolean> get() = SettingManager.isPurchased

    fun isPremiumNow(): Boolean = SettingManager.isPurchased.value

    // ---- the three gates named in the spec (section 7) ----

    /** (a) Template Detail CTA. */
    fun canUseTemplate(template: Template): Boolean = !template.isPremium || isPremiumNow()

    /** (b) Tool Detail CTA — Face Swap and Virtual Try-On are premium. */
    fun canUseTool(tool: ToolType): Boolean = !tool.isPremium || isPremiumNow()

    /** (c) The "High quality" switch in Settings. */
    fun canUseHighQuality(): Boolean = isPremiumNow()

    /** Free users get a watermark on exported/shared copies only (R6.7). */
    fun shouldWatermarkExports(): Boolean = !isPremiumNow()

    /** Ads are hidden entirely for premium users (C4). */
    fun shouldShowAds(): Boolean = !isPremiumNow()

    /**
     * Placeholder purchase. Flips the real flag so the whole unlock flow is testable end to end;
     * the body is what a later phase replaces with a billing call.
     */
    fun grantPremiumForTesting() {
        SettingManager.setPurchased(true)
    }
}
