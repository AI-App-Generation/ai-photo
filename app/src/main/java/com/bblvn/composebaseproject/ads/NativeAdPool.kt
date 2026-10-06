package com.bblvn.composebaseproject.ads

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.bbl.ads.modules.AdsManager
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pool native ad theo PLACEMENT, tự quản lý cache, độc lập với `module-ads` (chỉ nạp được MỘT ad cho
 * mỗi placement). Gọi thẳng [NativeAdLoader] của GMA Next-Gen; placement và ad unit id vẫn lấy từ
 * `module-ads` (Remote Config) để dùng chung key với phần còn lại của app.
 *
 * Hai cách dùng:
 *  - [Session]: một carousel cần N ad cùng lúc ([com.bblvn.composebaseproject.ui.components.ads.AdCarousel]).
 *  - [Slots]: một danh sách cuộn cần mỗi ô một ad RIÊNG, nạp dần theo vị trí cuộn.
 *
 * Doanh thu / log của module KHÔNG áp dụng cho ad nạp ở đây (chủ đích, xem yêu cầu ban đầu).
 *
 * Mỗi placement có một [Bucket] riêng, gồm ba kho theo thứ tự ưu tiên:
 *  1. [Bucket.idle]   — ad MỚI, chưa ai nhìn thấy (nạp sẵn, hoặc chuyển từ session khác). Dùng trước.
 *  2. request         — nạp ad mới, nếu giãn cách [MIN_REQUEST_INTERVAL_MS] của placement cho phép.
 *  3. [Bucket.recent] — ad ĐÃ có impression/click gần nhất, dùng lại làm phương án dự phòng khi
 *     request bị chặn hoặc thất bại, để không trống/skeleton mãi (xem [REUSE_USED_ADS]).
 *
 * Khi rời màn: ad chưa hiện → về [Bucket.idle]; ad đã hiện → [Bucket.recent]; ad quá hạn
 * ([AD_TTL_MS]) → huỷ.
 *
 * Mọi hàm gọi trên main thread; callback của SDK được post về main trước khi chạm vào trạng thái.
 */
object NativeAdPool {

    /** Số ad mỗi carousel muốn có, và mức nạp-trước tối đa cho một placement. */
    const val TARGET_COUNT = 3

    /** Số ad tối đa giữ trong [Bucket.idle] của một placement. */
    private const val MAX_IDLE = 6

    /** Hạn dùng an toàn của một ad (SDK cho 1 giờ, chừa biên 5 phút). */
    private const val AD_TTL_MS = 55 * 60 * 1000L

    /**
     * Khoảng cách tối thiểu giữa hai lượt GỬI request của MỘT placement. Tính từ lúc lượt trước thực
     * sự gửi, KHÔNG đặt chỗ trước cho các lượt đang chờ (đặt chỗ nối đuôi từng làm skeleton kéo dài
     * vô hạn).
     */
    private const val MIN_REQUEST_INTERVAL_MS = 5_000L

    /** Quá thời gian này mà request chưa có kết quả thì coi như thất bại, để skeleton không treo. */
    private const val REQUEST_TIMEOUT_MS = 15_000L

    /**
     * Cho phép hiện lại ad đã có impression khi không lấy được ad mới. Tắt đi nếu AdMob coi việc
     * hiện lại là vấn đề (click trên ad đã qua impression có thể bị xem là lưu lượng bất thường).
     */
    private const val REUSE_USED_ADS = true

    private const val TAG = "NativeAdPool"

    private val main = Handler(Looper.getMainLooper())

    private var nextId = 0

    /** Trạng thái riêng của một placement. */
    private class Bucket(val placement: String) {
        /** Ad MỚI, chưa dùng, không gắn vào view nào. */
        val idle = ArrayDeque<Loaded>()

        /** Ad ĐÃ dùng (có impression/click) gần nhất, mới nhất ở cuối. */
        val recent = ArrayDeque<Loaded>()

        /** Các carousel session đang mở, để chuyển ad chưa hiện từ tab cũ sang tab mới. */
        val sessions = ArrayList<Session>()

        /** Thời điểm lượt request gần nhất thực sự được gửi. */
        var lastRequestAt = -MIN_REQUEST_INTERVAL_MS

        /** Số request nạp-trước đang bay hoặc đang chờ lịch. */
        var prefetchInFlight = 0

        /** Đã hẹn giờ một lần nạp-trước chưa, để không hẹn chồng. */
        var prefetchScheduled = false

        fun msUntilRequestAllowed(): Long =
            (lastRequestAt + MIN_REQUEST_INTERVAL_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private val buckets = HashMap<String, Bucket>()

    private fun bucket(placement: String): Bucket = buckets.getOrPut(placement) { Bucket(placement) }

    /** Một ad đã nạp. [id] ổn định, dùng làm key cho Pager (NativeAd không lưu được vào Bundle). */
    class Loaded internal constructor(
        val id: Int,
        val ad: NativeAd,
        internal val loadedAt: Long,
    ) {
        /**
         * View khung đang chứa ad này. Giữ lại để màn dùng lại khi item bị dispose rồi vẽ lại
         * (cuộn ra/vào màn), thay vì đăng ký cùng một NativeAd vào view thứ hai.
         */
        var pageView: View? = null

        /** NativeAdView bên trong [pageView], cần để [NativeAdView.destroy] khi dọn dẹp. */
        var adView: NativeAdView? = null

        @Volatile
        internal var impressed = false

        @Volatile
        internal var clicked = false

        /** Đã được user nhìn thấy / chạm vào. */
        internal val used: Boolean
            get() = impressed || clicked

        internal val expired: Boolean
            get() = SystemClock.elapsedRealtime() - loadedAt >= AD_TTL_MS

        /** Gỡ view đã gắn nhưng giữ ad, để có thể gắn lại vào view mới. */
        internal fun releaseViews() {
            adView?.destroy()
            adView = null
            pageView = null
        }

        /** Dọn sạch: view trước, rồi ad. */
        internal fun destroy() {
            releaseViews()
            ad.destroy()
        }
    }

    /** [ready] là ad đã có; [pending] là số ad còn đang nạp (hiện skeleton). */
    data class State(val ready: List<Loaded>, val pending: Int)

    /** Placement này có được phép hiện ad không (ads bật, mode khác 0, có ad unit id). */
    fun isEnabled(placement: String): Boolean =
        AdsLoader.isAdsEnabled() &&
            AdModeManager.isEnabled(placement) &&
            AdsManager.getInstance().isReady &&
            adUnitIds(placement).isNotEmpty()

    /**
     * Nạp trước cho lần mở sau để có ad mới ngay. Chỉ nạp phần còn thiếu so với [TARGET_COUNT]. Ưu
     * tiên THẤP: nếu giãn cách chưa cho phép thì hẹn giờ một lần chứ không chen vào trước request
     * của một màn đang chờ.
     */
    fun prefetch(placement: String) {
        val b = bucket(placement)
        if (!isEnabled(placement) || b.prefetchScheduled) return
        val need = TARGET_COUNT - b.idle.size - b.prefetchInFlight
        if (need <= 0) return

        val wait = b.msUntilRequestAllowed()
        if (wait > 0) {
            b.prefetchScheduled = true
            main.postDelayed({
                b.prefetchScheduled = false
                prefetch(placement)
            }, wait)
            return
        }

        b.prefetchInFlight += need
        Batch(b, need) { loaded ->
            b.prefetchInFlight--
            if (loaded != null) stash(b, loaded)
        }.run()
    }

    /**
     * Một lượt nạp [count] ad. Chỉ ghi nhận "đã gửi" vào lúc THỰC SỰ gửi; nếu lúc chạy mà giãn cách
     * chưa đủ (vì lượt khác vừa gửi) thì tự hẹn lại. [cancel] chỉ có tác dụng khi chưa gửi.
     */
    private class Batch(
        val bucket: Bucket,
        val count: Int,
        val onEach: (Loaded?) -> Unit,
    ) : Runnable {
        var fired = false
            private set
        private var cancelled = false

        override fun run() {
            if (cancelled) return
            val wait = bucket.msUntilRequestAllowed()
            if (wait > 0) {
                Log.i(TAG, "${bucket.placement}: hoãn ${wait}ms để giữ giãn cách request")
                main.postDelayed(this, wait)
                return
            }
            fired = true
            // Có thể đã tắt ads / premium trong lúc chờ: báo "không có ad" để bộ đếm của người gọi
            // không treo.
            if (!isEnabled(bucket.placement)) {
                repeat(count) { onEach(null) }
                return
            }
            bucket.lastRequestAt = SystemClock.elapsedRealtime()
            repeat(count) { loadOne(bucket.placement, onEach) }
        }

        fun cancel() {
            cancelled = true
            main.removeCallbacks(this)
        }
    }

    // ==========================================================================================
    // Session: một carousel cần N ad cùng lúc
    // ==========================================================================================

    /** Một lần hiển thị của carousel: lấy ad từ pool, nạp phần thiếu, trả ad về khi kết thúc. */
    class Session(val placement: String, private val target: Int = TARGET_COUNT) {

        private val bucket = bucket(placement)

        // Bắt đầu bằng skeleton đủ số ad để màn không giật; start() sẽ chỉnh lại theo thực tế.
        private val _state = MutableStateFlow(State(emptyList(), target))
        val state: StateFlow<State> = _state.asStateFlow()

        private var ready: List<Loaded> = emptyList()

        /** Số request của session này còn đang bay hoặc đang chờ lịch. */
        private var inFlight = 0

        private var started = false
        private var released = false

        /** Lượt nạp của session này, còn giữ để huỷ nếu session đóng trước khi tới lịch. */
        private var pendingBatch: Batch? = null

        /**
         * Id các ad "dùng tạm" lấy từ [Bucket.recent] trong lúc request bị chặn. Khi ad mới về,
         * chúng được thay dần bằng ad mới (xem [onLoaded]) thay vì ở lại tới hết phiên.
         */
        private val reusedIds = HashSet<Int>()

        fun start() {
            if (started || released) return
            started = true

            if (!isEnabled(placement)) {
                _state.value = State(emptyList(), 0)
                return
            }

            bucket.sessions.add(this)

            // 1) Ad mới chưa ai xem.
            ready = takeIdle(bucket, target)
            val need = target - ready.size

            // 2) Giãn cách đang chặn request → dùng TẠM ad gần nhất thay vì để skeleton chờ.
            if (need > 0 && bucket.msUntilRequestAllowed() > 0) {
                val reused = takeRecent(bucket, need)
                if (reused.isNotEmpty()) {
                    Log.i(TAG, "$placement: request bị chặn, dùng tạm ${reused.size} ad gần nhất")
                    ready = ready + reused
                    reused.forEach { reusedIds.add(it.id) }
                }
            }

            // 3) Luôn nạp đủ `need` ad mới (có thể bị hoãn tối đa vài giây). Ad mới về sẽ lấp chỗ
            //    còn trống, hoặc thay dần các ad dùng tạm ở trên. Skeleton chỉ hiện cho chỗ còn
            //    trống (xem [publish]).
            inFlight = need
            publish()

            if (need > 0) {
                val batch = Batch(bucket, need, ::onLoaded)
                pendingBatch = batch
                batch.run()
            } else {
                // Đã đủ ad mới sẵn có: nạp sẵn cho lần sau, ưu tiên thấp.
                prefetch(placement)
            }
        }

        /**
         * Rời màn. Ad quá hạn thì huỷ; ad đã hiện thì cất vào [Bucket.recent]; ad chưa hiện thì gỡ
         * view và chuyển cho carousel đang chờ (tab mới) hoặc vào [Bucket.idle]. Cuối cùng nạp sẵn
         * phần thiếu.
         */
        fun release() {
            if (released) return
            released = true
            bucket.sessions.remove(this)

            // Đóng trước khi tới lịch thì khỏi gửi request nào (đỡ tốn cho ad không ai xem).
            pendingBatch?.takeIf { !it.fired }?.cancel()
            pendingBatch = null

            for (loaded in ready) {
                loaded.releaseViews()
                when {
                    loaded.expired -> loaded.destroy()
                    loaded.used -> keepRecent(bucket, loaded)
                    else -> handOff(loaded)
                }
            }
            ready = emptyList()
            _state.value = State(emptyList(), 0)
            prefetch(placement)
        }

        private fun wantsMore(): Boolean = started && !released && ready.size < target

        /** Nhận một ad chuyển từ session khác. */
        private fun receive(loaded: Loaded) {
            ready = ready + loaded
            publish()
        }

        /**
         * Ưu tiên đưa thẳng cho session cùng placement còn thiếu ad (thường là tab vừa được mở, nó
         * mở trước khi tab cũ kịp rời nên đã tự đi nạp). Không có thì cất vào pool.
         */
        private fun handOff(loaded: Loaded) {
            val receiver = bucket.sessions.firstOrNull { it.wantsMore() }
            if (receiver != null) receiver.receive(loaded) else stash(bucket, loaded)
        }

        private fun onLoaded(loaded: Loaded?) {
            inFlight = (inFlight - 1).coerceAtLeast(0)
            if (loaded != null) {
                when {
                    // Về muộn sau khi đóng: giữ lại cho lần sau.
                    released -> stash(bucket, loaded)
                    // Còn chỗ trống: lấp vào.
                    ready.size < target -> ready = ready + loaded
                    // Đã đủ: thay một ad dùng tạm bằng ad mới này; không có thì để dành.
                    else -> if (!replaceReused(loaded)) stash(bucket, loaded)
                }
            }
            if (released) return

            // Mọi request xong mà vẫn không có ad nào (hết fill, lỗi mạng, timeout): dùng lại ad
            // gần nhất thay vì để carousel biến mất.
            if (inFlight == 0 && ready.isEmpty()) {
                val reused = takeRecent(bucket, target)
                if (reused.isNotEmpty()) {
                    Log.i(TAG, "$placement: nạp thất bại, dùng lại ${reused.size} ad gần nhất")
                    ready = reused
                }
            }
            publish()
        }

        /**
         * Thay một ad dùng tạm bằng [fresh], giữ nguyên vị trí để Pager không nhảy trang. Ad cũ
         * quay về [Bucket.recent] sau khi gỡ view ngay tại đây: nó không còn nằm trong [ready] nên
         * lúc session đóng sẽ không ai dọn view của nó nữa.
         */
        private fun replaceReused(fresh: Loaded): Boolean {
            val index = ready.indexOfFirst { it.id in reusedIds }
            if (index < 0) return false
            val old = ready[index]
            reusedIds.remove(old.id)
            ready = ready.toMutableList().also { it[index] = fresh }
            old.releaseViews()
            keepRecent(bucket, old)
            Log.i(TAG, "$placement: thay ad dùng tạm bằng ad mới")
            return true
        }

        private fun publish() {
            _state.value = State(
                ready = ready,
                // Chỉ hiện skeleton cho phần còn thiếu VÀ còn request đang bay/chờ lịch.
                pending = (target - ready.size).coerceIn(0, inFlight),
            )
        }
    }

    // ==========================================================================================
    // Slots: một danh sách cuộn, mỗi ô một ad RIÊNG
    // ==========================================================================================

    /**
     * Trạng thái các ô ad của một danh sách. [ads] là ô đã có ad (theo chỉ số ô), [pending] là các ô
     * còn đang chờ ad (hiện skeleton). Ô không nằm trong cả hai (thất bại, hoặc chưa yêu cầu) thì
     * KHÔNG vẽ gì, để danh sách liền mạch.
     */
    data class SlotsState(val ads: Map<Int, Loaded>, val pending: Set<Int>)

    /**
     * Cấp ad cho các ô trong một danh sách cuộn. Màn gọi [ensure] với số ô cần có (tăng dần khi
     * user cuộn); mỗi ô nhận một ad khác nhau, ưu tiên ad mới sẵn có, rồi nạp mới, rồi dùng tạm ad
     * cũ khi request bị chặn. Ad đã gắn vào ô giữ nguyên khi cuộn qua lại. [release] khi rời màn.
     */
    class Slots(val placement: String) {

        private val bucket = bucket(placement)

        private val _state = MutableStateFlow(SlotsState(emptyMap(), emptySet()))
        val state: StateFlow<SlotsState> = _state.asStateFlow()

        private val ads = LinkedHashMap<Int, Loaded>()

        /** Các ô đã được yêu cầu (có ad, đang chờ, hoặc đã thất bại). */
        private val requested = HashSet<Int>()

        /** Ô đang chờ ad về, theo thứ tự yêu cầu; ad về trước gán cho ô đứng trước. */
        private val waiting = ArrayDeque<Int>()

        private val batches = ArrayList<Batch>()
        private var released = false

        /**
         * Báo rằng các ô `[0, slotCount)` cần có ad. Gọi lặp lại an toàn: chỉ xử lý các ô mới. Ô nào
         * không lấy được ad (hết fill) sẽ tự biến mất khỏi danh sách.
         */
        fun ensure(slotCount: Int) {
            if (released || slotCount <= 0 || !isEnabled(placement)) return

            var needRequest = 0
            for (slot in 0 until slotCount) {
                if (!requested.add(slot)) continue
                val ready = takeIdle(bucket, 1).firstOrNull()
                    ?: if (bucket.msUntilRequestAllowed() > 0) takeRecent(bucket, 1).firstOrNull() else null
                if (ready != null) {
                    ads[slot] = ready
                } else {
                    waiting.addLast(slot)
                    needRequest++
                }
            }

            if (needRequest > 0) {
                val batch = Batch(bucket, needRequest, ::onLoaded)
                batches.add(batch)
                batch.run()
            }
            publish()
        }

        /** Rời màn: dọn như [Session.release]. Ad đã hiện về `recent`, ad chưa hiện về `idle`. */
        fun release() {
            if (released) return
            released = true
            batches.filter { !it.fired }.forEach { it.cancel() }
            batches.clear()

            for (loaded in ads.values) {
                loaded.releaseViews()
                when {
                    loaded.expired -> loaded.destroy()
                    loaded.used -> keepRecent(bucket, loaded)
                    else -> stash(bucket, loaded)
                }
            }
            ads.clear()
            waiting.clear()
            _state.value = SlotsState(emptyMap(), emptySet())
            prefetch(placement)
        }

        private fun onLoaded(loaded: Loaded?) {
            val slot = waiting.removeFirstOrNull()
            if (loaded != null) {
                // Về muộn sau khi đóng, hoặc không còn ô nào chờ: giữ lại cho lần sau.
                if (released || slot == null) stash(bucket, loaded) else ads[slot] = loaded
            }
            if (!released) publish()
        }

        private fun publish() {
            _state.value = SlotsState(ads = ads.toMap(), pending = waiting.toSet())
        }
    }

    // ------------------------------------------------------------------------------------------

    private fun takeIdle(b: Bucket, count: Int): List<Loaded> {
        val taken = ArrayList<Loaded>(count)
        while (taken.size < count && b.idle.isNotEmpty()) {
            val loaded = b.idle.removeFirst()
            if (loaded.expired) loaded.destroy() else taken.add(loaded)
        }
        return taken
    }

    /** Lấy ad đã dùng gần nhất (mới nhất trước), bỏ ad quá hạn. */
    private fun takeRecent(b: Bucket, count: Int): List<Loaded> {
        if (!REUSE_USED_ADS) return emptyList()
        val taken = ArrayList<Loaded>(count)
        while (taken.size < count && b.recent.isNotEmpty()) {
            val loaded = b.recent.removeLast()
            if (loaded.expired) loaded.destroy() else taken.add(loaded)
        }
        return taken
    }

    /** Cất ad đã dùng vào `recent`; vượt [TARGET_COUNT] thì huỷ cái cũ nhất. */
    private fun keepRecent(b: Bucket, loaded: Loaded) {
        if (!REUSE_USED_ADS) {
            loaded.destroy()
            return
        }
        b.recent.remove(loaded)
        b.recent.addLast(loaded)
        while (b.recent.size > TARGET_COUNT) b.recent.removeFirst().destroy()
    }

    /** Cất ad chưa dùng vào pool; hết hạn hoặc pool đầy thì huỷ luôn. */
    private fun stash(b: Bucket, loaded: Loaded) {
        if (loaded.expired || b.idle.size >= MAX_IDLE) {
            loaded.destroy()
        } else {
            b.idle.addLast(loaded)
        }
    }

    private fun adUnitIds(placement: String): List<String> {
        val config = AdsManager.getInstance().getPlacementConfig(placement) ?: return emptyList()
        // Trong module-ads đây là hàm thường (không phải property), nên gọi dạng hàm.
        return (config.getAdIdsList() + config.getBackupAdIdsList())
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Nạp MỘT ad, thử lần lượt từng ad unit id (chính rồi dự phòng) cho tới khi có ad.
     * Mỗi ad là một request riêng thay vì `load(request, count)`: bản nhiều-ad của AdMob không đi
     * qua mediation, mà app này chạy waterfall.
     *
     * [onDone] được gọi đúng MỘT lần (có ad, hoặc null khi thất bại / quá [REQUEST_TIMEOUT_MS]). Ad
     * về sau khi đã timeout không gọi lại [onDone] mà được cất thẳng vào pool.
     */
    private fun loadOne(placement: String, onDone: (Loaded?) -> Unit) {
        val ids = adUnitIds(placement)
        val b = bucket(placement)
        var settled = false

        val timeout = Runnable {
            if (settled) return@Runnable
            settled = true
            Log.w(TAG, "$placement: request quá ${REQUEST_TIMEOUT_MS}ms, bỏ chờ")
            onDone(null)
        }

        fun settle(loaded: Loaded?) {
            if (settled) {
                loaded?.let { stash(b, it) }
                return
            }
            settled = true
            main.removeCallbacks(timeout)
            onDone(loaded)
        }

        main.postDelayed(timeout, REQUEST_TIMEOUT_MS)

        fun attempt(index: Int) {
            if (index >= ids.size) {
                settle(null)
                return
            }
            val request = NativeAdRequest
                .Builder(ids[index], listOf(NativeAd.NativeAdType.NATIVE))
                // Media của template medium là ô vuông 120x120.
                .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.SQUARE)
                .build()

            NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    main.post {
                        Log.i(TAG, "$placement loaded (unit ${index + 1}/${ids.size})")
                        val loaded = Loaded(nextId++, nativeAd, SystemClock.elapsedRealtime())
                        // Theo dõi impression/click để biết ad nào đã "dùng" lúc màn rời đi.
                        nativeAd.adEventCallback = object : NativeAdEventCallback {
                            override fun onAdImpression() {
                                loaded.impressed = true
                            }

                            override fun onAdClicked() {
                                loaded.clicked = true
                            }
                        }
                        settle(loaded)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    main.post {
                        Log.e(TAG, "$placement unit ${index + 1}/${ids.size}: ${adError.message}")
                        attempt(index + 1)
                    }
                }
            })
        }

        attempt(0)
    }
}
