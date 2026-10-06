package com.bblvn.composebaseproject.core

import com.bblvn.composebaseproject.data.domain.CreationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Người dùng được làm gì" — một chỗ duy nhất, gồm hai thứ: quyền PRO ([isPremium]) và số dư credit
 * ([creditBalance]). Mọi nơi trong app hỏi ở đây thay vì tự rải hằng số hay đọc thẳng
 * [SettingManager].
 *
 * Credit: 1 lượt gọi tạo ảnh = [COST_PER_GENERATION] credit, kể cả Regenerate và thử lại. User mới
 * có sẵn [FREE_STARTING_CREDITS]. Số dư lưu cục bộ (không server) nên sửa được bằng root hoặc xoá
 * dữ liệu — chấp nhận để ship sớm, giống cờ premium.
 *
 * Gói PRO hiện CHƯA kèm credit và CHƯA bỏ giá credit: tạo ảnh luôn tốn credit, vì mỗi lượt gọi API
 * là chi phí thật.
 */
object AppCapability {

    /** Credit tặng một lần cho người dùng mới. */
    const val FREE_STARTING_CREDITS = 1

    /** Giá của một lượt tạo ảnh, tính bằng credit. */
    const val COST_PER_GENERATION = 1

    /** Số credit nhận được cho mỗi lần xem hết một quảng cáo thưởng. */
    const val REWARD_CREDITS_PER_AD = 1

    /**
     * Tổng số lần được xem quảng cáo thưởng để nhận credit (tính trọn đời, lưu máy). Dùng hết thì
     * dialog hết credit không còn mời xem quảng cáo nữa, chỉ còn đường mua gói credit.
     */
    const val MAX_REWARDED_CREDIT_VIEWS = 1

    private val _isPremium = MutableStateFlow(false)

    /**
     * Quyền PRO (tắt ads, bỏ watermark, template VIP) — nguồn sự thật duy nhất.
     *
     * CỐ Ý chỉ giữ trong bộ nhớ, không ghi xuống đĩa: PRO là subscription và có thể hết hạn, lưu một
     * cờ true vĩnh viễn thì hết hạn rồi app vẫn mở khoá mọi thứ. Google Play (qua
     * `AppPurchase.isPurchased`) mới là nơi biết sự thật và nó tự cache offline. Giá trị này được đặt
     * lại bởi [com.bblvn.composebaseproject.ads.AdsLoader] mỗi lần mở app và mỗi khi billing báo thay
     * đổi; trong phiên thì `IapController` đặt ngay khi mua xong.
     *
     * Trước khi billing kết nối xong, giá trị này luôn là false kể cả với user đang trả phí.
     */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** Chỉ [com.bblvn.composebaseproject.ads.AdsLoader] và `IapController` được gọi hàm này. */
    fun setPremium(premium: Boolean) {
        _isPremium.value = premium
    }

    /** Số dư credit hiện tại, để giao diện quan sát. */
    val creditBalance: StateFlow<Int> get() = SettingManager.creditBalance

    /** Đủ credit cho một lượt tạo ảnh chưa. Dùng để chặn từ sớm, trước khi chuyển màn. */
    fun canAffordGeneration(): Boolean = creditBalance.value >= COST_PER_GENERATION

    /**
     * Trừ credit cho một lượt tạo ảnh, nguyên tử. Trả về false nếu không đủ (hoặc số dư chưa nạp
     * xong từ ổ đĩa).
     */
    fun trySpendForGeneration(): Boolean = SettingManager.trySpendCredits(COST_PER_GENERATION)

    /** Hoàn credit của một lượt tạo ảnh không thành công. */
    fun refundGeneration() = SettingManager.addCredits(COST_PER_GENERATION)

    /** Cộng credit (mua gói, xem quảng cáo thưởng…). */
    fun grantCredits(amount: Int) = SettingManager.addCredits(amount)

    /** Số lần đã xem quảng cáo thưởng để nhận credit, để giao diện quan sát. */
    val rewardedCreditViews: StateFlow<Int> get() = SettingManager.rewardedCreditViews

    /** Còn được xem quảng cáo thưởng để nhận credit không. */
    fun canWatchRewardForCredits(): Boolean = rewardedCreditViews.value < MAX_REWARDED_CREDIT_VIEWS

    /** Xem hết quảng cáo thưởng: tính một lượt vào hạn mức rồi cộng [REWARD_CREDITS_PER_AD] credit. */
    fun grantRewardedCredits() {
        SettingManager.recordRewardedCreditView()
        grantCredits(REWARD_CREDITS_PER_AD)
    }

    // ---------------------------------------------------------------- lượt mở khoá PRO một lần
    //
    // Tách hẳn khỏi credit: credit trả cho CHI PHÍ tạo ảnh, còn lượt mở khoá trả cho QUYỀN dùng một
    // template/tool PRO. Xem quảng cáo thưởng để mở khoá thì cộng một lượt vào danh sách; bắt đầu
    // tạo ảnh thì tiêu thụ một lượt (xem GenerationEngine), thất bại thì trả lại.

    /** Danh sách lượt mở khoá còn lại, để giao diện quan sát. */
    val unlockPasses: StateFlow<List<String>> get() = SettingManager.unlockPasses

    fun templateUnlockKey(templateId: String) = "tpl:$templateId"

    fun toolUnlockKey(toolName: String) = "tool:$toolName"

    /** Khoá lượt mở khoá của một nguồn tạo ảnh (template hoặc tool). */
    fun unlockKeyFor(type: CreationType, sourceId: String): String = when (type) {
        CreationType.TEMPLATE -> templateUnlockKey(sourceId)
        CreationType.TOOL -> toolUnlockKey(sourceId)
    }

    /** Còn ít nhất một lượt mở khoá cho [key] không. Premium thật sự không cần lượt nào. */
    fun hasUnlock(key: String): Boolean = key in unlockPasses.value

    fun grantUnlock(key: String) = SettingManager.addUnlockPass(key)

    /** Dùng một lượt; false nếu không có. */
    fun consumeUnlock(key: String): Boolean = SettingManager.consumeUnlockPass(key)

    /** Trả lại một lượt đã dùng cho lượt tạo ảnh không thành công. */
    fun refundUnlock(key: String) = SettingManager.addUnlockPass(key)
}
