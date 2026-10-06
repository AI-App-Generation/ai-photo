package com.bblvn.composebaseproject.features.iap

import com.bblvn.composebaseproject.BuildConfig
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.core.AppCapability
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import android.app.Activity
import android.util.Log
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.function.BillingListener
import com.bbl.ads.modules.function.PurchaseListener
import com.bbl.ads.modules.function.UpdatePurchaseListener
import com.bblvn.composebaseproject.ads.AdsLoader

/**
 * Đầu mối mua hàng: đăng ký listener, mua gói, khôi phục.
 *
 * Quyền PRO là biến runtime ở `AppCapability.isPremium`, được dựng lại từ Google Play (xem
 * [AdsLoader.refreshPurchaseState]) chứ không lưu trên máy:
 *  - billing kết nối xong → đồng bộ lại ([registerBillingListener]);
 *  - Play làm mới danh sách mua (gia hạn, huỷ, hoàn tiền, khôi phục trên máy mới) → đồng bộ lại, và
 *    đây cũng là đường để quyền lợi TỰ MẤT khi gói hết hạn;
 *  - vừa mua xong → bật ngay trong phiên, không đợi Play làm mới ([registerPurchaseListener]).
 *
 * Mọi callback của lib dùng chung cho subscription lẫn mua một lần; chỉ khác hàm gọi mua ([buy]).
 */
object IapController {

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * Giá hiển thị (đã định dạng theo tiền tệ của user), cập nhật dần khi Google Play trả về.
     *
     * Là StateFlow chứ không phải giá trị đọc một lần: lúc mở paywall billing client có thể chưa
     * kịp tải xong chi tiết sản phẩm, và khi nó xong thì giao diện phải tự hiện giá ra.
     */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    /** Gọi một lần lúc khởi động, NGAY SAU `AppPurchase.initBilling()`. */
    fun registerBillingListener() {
        AppPurchase.getInstance().setBillingListener(object : BillingListener {
            override fun onInitBillingFinished(resultCode: Int) {
                Log.d(TAG, "Billing ready, code=$resultCode")
                // Trước thời điểm này isPurchased luôn là false, kể cả với user đang trả phí.
                AdsLoader.refreshPurchaseState()
                warmUpPrices()
                consumeLeftovers()
            }
        })

        AppPurchase.getInstance().setUpdatePurchaseListener(object : UpdatePurchaseListener {
            override fun onUpdateFinished() {
                if (!AppPurchase.getInstance().initBillingFinish) return
                AdsLoader.refreshPurchaseState()
            }
        })
    }

    fun registerPurchaseListener() {
        AppPurchase.getInstance().setPurchaseListener(object : PurchaseListener {
            override fun onProductPurchased(rawProductId: String?, transactionDetails: String?) {
                Log.d(TAG, "Success: $rawProductId | $transactionDetails")
                val productId = resolveProductId(rawProductId)
                if (productId == null || productId !in Iap.all) return

                val pack = Iap.creditPack(productId)
                if (pack != null) {
                    grantCreditPack(pack, transactionDetails)
                    return
                }

                // Bật PRO ngay trong phiên (tắt ads, mở khoá template/tool, bỏ watermark).
                AdsLoader.setActive(isPurchased = true)
            }

            override fun displayErrorMessage(errorMsg: String?) {
                Log.e(TAG, "Error: $errorMsg")
            }

            override fun onUserCancelBilling() {
                Log.d(TAG, "User canceled")
            }
        })
    }

    private val _creditPurchased = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Phát số credit vừa cộng sau một lần mua gói credit thành công (để màn mua đóng lại + báo). */
    val creditPurchased: SharedFlow<Int> = _creditPurchased.asSharedFlow()

    /**
     * Cộng credit cho một đơn gói credit rồi consume để mua lại được.
     *
     * Lib chỉ acknowledge chứ không consume (cờ consume toàn cục sẽ consume luôn cả subscription nên
     * không dùng được), do đó consume thủ công ở đây. Thứ tự "ghi nhận → cộng → consume" là cố ý:
     * app chết giữa chừng thì đơn đã nằm trong danh sách đã xử lý, lần sau không cộng lại.
     */
    private fun grantCreditPack(pack: CreditPack, transactionDetails: String?) {
        val orderId = runCatching {
            JSONObject(transactionDetails.orEmpty()).let { json ->
                json.optString("orderId").ifBlank { json.optString("purchaseToken") }
            }
        }.getOrDefault("")
        if (SettingManager.claimPurchase(orderId)) {
            AppCapability.grantCredits(pack.credits)
            _creditPurchased.tryEmit(pack.credits)
            Log.d(TAG, "Granted ${pack.credits} credits for ${pack.productId} (order=$orderId)")
        } else {
            Log.w(TAG, "Order $orderId already granted, only consuming")
        }
        consumeCreditPack(pack.productId)
    }

    private fun consumeCreditPack(productId: String) {
        runCatching { AppPurchase.getInstance().consumePurchase(productId) }
            .onFailure { Log.w(TAG, "consume $productId failed", it) }
    }

    /**
     * Billing vừa sẵn sàng: consume các gói credit còn treo (đã cộng nhưng chưa kịp consume vì app
     * chết hoặc mạng rớt), nếu không Play sẽ báo "đã sở hữu" khi user mua lại.
     */
    private fun consumeLeftovers() {
        Iap.creditPacks.forEach { consumeCreditPack(it.productId) }
    }

    /** Subscription và mua một lần dùng hai hàm khác nhau; callback thì chung. */
    fun buy(activity: Activity, productId: String) {
        pendingProductId = productId
        if (Iap.isSubscription(productId)) {
            AppPurchase.getInstance().subscribe(activity, productId, 0)
        } else {
            AppPurchase.getInstance().purchase(activity, productId)
        }
    }

    /**
     * Khôi phục: hỏi lại Google Play danh sách mua. Kết quả về qua `UpdatePurchaseListener` rồi
     * [AdsLoader.refreshPurchaseState] bật lại PRO nếu user đang có gói hợp lệ.
     */
    fun restore() {
        AppPurchase.getInstance().updatePurchaseStatus()
    }

    /**
     * Nạp giá ngay từ lúc billing sẵn sàng, lặp lại cho tới khi đủ các gói.
     *
     * `onInitBillingFinished` báo billing client đã kết nối, nhưng chi tiết sản phẩm còn được tải
     * tiếp sau đó một nhịp. Nạp sẵn ở đây thì paywall mở ra là có giá luôn.
     */
    fun warmUpPrices() {
        if (isWarmingUp) return
        isWarmingUp = true
        scope.launch {
            repeat(WARM_UP_ATTEMPTS) { attempt ->
                if (refreshPrices().containsAll(Iap.all)) {
                    Log.d(TAG, "Prices ready after ${attempt + 1} attempt(s): ${_prices.value}")
                    isWarmingUp = false
                    return@launch
                }
                delay(WARM_UP_DELAY_MS)
            }
            isWarmingUp = false
            // Hết lượt mà vẫn thiếu: gần như luôn là bản cài không khớp với bản trên Play (khác chữ
            // ký hoặc chưa có bản nào trên track nào), chứ không phải lỗi mã nguồn.
            Log.w(TAG, "Gave up fetching prices. Have=${_prices.value.keys}, want=${Iap.all}")
        }
    }

    /**
     * Hỏi lại giá từ Google Play và trả về danh sách id ĐÃ có giá. Chỉ ghi đè những id lấy được:
     * một lần hỏi hụt không được phép xoá mất giá đang hiển thị.
     */
    fun refreshPrices(productIds: List<String> = Iap.all): Set<String> {
        val fetched = productIds.mapNotNull { id ->
            priceOf(id).takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap()
        if (fetched.isNotEmpty()) _prices.value = _prices.value + fetched
        return _prices.value.keys
    }

    private fun priceOf(productId: String): String = if (Iap.isSubscription(productId)) {
        AppPurchase.getInstance().getPriceSub(productId)
    } else {
        AppPurchase.getInstance().getPrice(productId)
    }

    /** Id sản phẩm user vừa bấm mua; chỉ dùng để đỡ cho hộp thoại mua giả của lib ở bản debug. */
    @Volatile
    private var pendingProductId: String? = null

    /**
     * Bản debug (`isVariantDev`) lib hiện hộp thoại mua GIẢ và luôn báo về id `android.test.purchased`
     * thay vì id thật, nên không khớp sản phẩm nào. Chỉ ở debug, quy id giả đó về sản phẩm đang mua
     * để luồng cộng credit / bật PRO chạy được. Bản release không bao giờ đi nhánh này.
     */
    private fun resolveProductId(rawProductId: String?): String? =
        if (BuildConfig.DEBUG && rawProductId == FAKE_TEST_PRODUCT_ID) pendingProductId else rawProductId

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isWarmingUp = false

    private const val WARM_UP_ATTEMPTS = 40
    private const val WARM_UP_DELAY_MS = 500L
    private const val FAKE_TEST_PRODUCT_ID = "android.test.purchased"
    private const val TAG = "IAP"
}
