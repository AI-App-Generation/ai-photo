package com.bblvn.composebaseproject.features.iap

/** Một gói credit mua một lần (consumable): id trên Play Console và số credit nhận được. */
data class CreditPack(val productId: String, val credits: Int)

/**
 * Id sản phẩm trên Google Play Console.
 *  - Hai gói subscription tắt quảng cáo — xem `adFreeSubscriptionProductIds` trong GlobalApp.
 *  - Ba gói credit là sản phẩm mua một lần dạng tiêu thụ (consumable): KHÔNG nằm trong danh sách
 *    ad-free, và phải được consume sau khi cộng credit thì mới mua lại được.
 */
object Iap {
    /** Gói tuần — subscription. */
    const val weeklyId: String = "subs_weekly"

    /** Gói tháng — subscription. */
    const val monthlyId: String = "subs_monthly"

    val subscriptions = listOf(weeklyId, monthlyId)

    /** Sản phẩm mua đứt tắt quảng cáo: hiện chưa có. */
    val inAppProducts: List<String> = emptyList()

    /** Gói nạp credit, theo thứ tự hiển thị. */
    val creditPacks = listOf(
        CreditPack("topup_credit_1", 5),
        CreditPack("topup_credit_2", 12),
        CreditPack("topup_credit_3", 35),
    )

    /** Mọi id mua một lần phải khai với billing client (ad-free lẫn credit). */
    val allInApp: List<String> = inAppProducts + creditPacks.map { it.productId }

    val all = subscriptions + allInApp

    fun isSubscription(productId: String): Boolean = productId in subscriptions

    fun creditPack(productId: String): CreditPack? = creditPacks.firstOrNull { it.productId == productId }
}
