package com.bblvn.composebaseproject.core

import com.bblvn.composebaseproject.data.domain.Template
import android.content.Context
import android.os.Bundle
import com.bblvn.composebaseproject.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Bắn event Firebase từ ngoài Compose.
 *
 * Tồn tại song song với `LogScreenView` vì hai loại event có vòng đời khác hẳn nhau:
 *  - `LogScreenView` là `@Composable`, khoá theo tên nên xoay máy hay recompose không bắn trùng —
 *    đúng cho event dạng "đã xem màn X".
 *  - [Analytics.log] là hàm thường, bắn đúng một lần tại nơi gọi — đúng cho event dạng hành động
 *    (`..._next`, `..._continue`), và gọi được từ `onClick`, ViewModel hay listener.
 *
 * Dùng application context lấy một lần lúc khởi động, để nơi gọi không phải tự xoay xở tìm context.
 */
object Analytics {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Đưa một chuỗi tuỳ ý (tên template…) về dạng `snake_case` dùng được làm tên event / giá trị
     * param: chữ thường, mọi cụm ký tự không phải chữ-số (khoảng trắng, `&`, `'`, `-`, `,`…) thành
     * một dấu `_`, bỏ `_` ở hai đầu. "Bloom & Glow" -> "bloom_glow", "Mother's Day" -> "mothers_day".
     *
     * Catalog không có sẵn trường snake_case nào: `id` là chuỗi ngẫu nhiên và KHÔNG duy nhất (nhiều
     * template dùng chung id 1..10), còn `template_name` là Title Case có dấu cách — nên tên mới là
     * thứ đáng dùng để phân biệt template, sau khi qua hàm này.
     */
    fun snakeCase(raw: String): String = raw
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
        .trim('_')

    /** Template được bấm vào ở [source] (`home_banner`, `home_row`, `see_all`). */
    fun logTemplateClick(template: Template, source: String) = log(
        "template_click",
        "template" to snakeCase(template.name),
        "source" to source,
    )

    fun log(eventName: String) = log(eventName, null)

    fun log(eventName: String, params: Bundle?) {
        // Bản debug không bắn, giống LogScreenView — tránh làm bẩn số liệu bằng lượt test.
        if (BuildConfig.DEBUG) return
        if (!::appContext.isInitialized) return
        FirebaseAnalytics.getInstance(appContext).logEvent(eventName, params)
    }

    fun log(eventName: String, vararg params: Pair<String, Any>) {
        log(
            eventName,
            Bundle().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            },
        )
    }
}
