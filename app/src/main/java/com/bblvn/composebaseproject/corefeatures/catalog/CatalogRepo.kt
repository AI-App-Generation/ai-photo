package com.bblvn.composebaseproject.corefeatures.catalog

import android.util.Log
import com.bblvn.composebaseproject.data.api.CdnAuth
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.core.json.JsonLoader
import com.bblvn.composebaseproject.data.api.ApiRepo
import com.bblvn.composebaseproject.data.domain.Catalog
import com.bblvn.composebaseproject.data.domain.Template
import com.bblvn.composebaseproject.data.domain.TemplateCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

/**
 * Owns the template catalog: fetch, sanitise, cache, expose.
 *
 * Stale-while-revalidate — the cached file is published immediately on launch so Home can render
 * without a spinner, while a network refresh runs in parallel and replaces it when it differs.
 * The cache stays usable indefinitely offline; [CACHE_TTL_MS] only decides whether we bother
 * refreshing eagerly.
 */
object CatalogRepo {

    private const val TAG = "CatalogRepo"
    private const val CACHE_FILE = "catalog.json"
    private const val CACHE_STAMP_FILE = "catalog_stamp.txt"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_SLIDE_TEMPLATES = 8

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    private var refreshJob: Job? = null

    fun init(appScope: CoroutineScope) {
        if (::scope.isInitialized) return
        scope = appScope
        scope.launch { load() }
    }

    /** Publish the cache (if any), then refresh from the network. */
    suspend fun load() {
        readCache()?.let { cached -> publish(cached) }
        refresh()
    }

    /** Force a network refresh; safe to call from a Retry button. */
    fun refreshAsync() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val result = ApiRepo.fetchCatalogJson()
        result.onSuccess { text ->
            val catalog = withContext(Dispatchers.Default) {
                runCatching { JsonLoader.json.decodeFromString<Catalog>(text) }.getOrNull()
            }
            if (catalog == null) {
                Log.w(TAG, "Catalog parsed to null; keeping previous state")
                failIfEmpty("Catalog parse failed")
                return@onSuccess
            }
            publish(catalog)
            FileManager.writeText(FileManager.cacheFile(CACHE_FILE), text)
            FileManager.writeText(
                FileManager.cacheFile(CACHE_STAMP_FILE),
                System.currentTimeMillis().toString(),
            )
        }.onFailure { error ->
            Log.w(TAG, "Catalog refresh failed", error)
            failIfEmpty(error.message ?: "Network error")
        }
    }

    /** Keeps whatever is already on screen; only surfaces an error when there is nothing to show. */
    private fun failIfEmpty(message: String) {
        if (_state.value !is CatalogState.Ready) {
            _state.value = CatalogState.Error(message)
        }
    }

    private suspend fun readCache(): Catalog? {
        val text = FileManager.readText(FileManager.cacheFile(CACHE_FILE)) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { JsonLoader.json.decodeFromString<Catalog>(text) }.getOrNull()
        }
    }

    suspend fun isCacheStale(): Boolean {
        val stamp = FileManager.readText(FileManager.cacheFile(CACHE_STAMP_FILE))
            ?.trim()?.toLongOrNull() ?: return true
        return System.currentTimeMillis() - stamp > CACHE_TTL_MS
    }

    private fun publish(catalog: Catalog) {
        val categories = sanitise(catalog)
        if (categories.isEmpty()) {
            failIfEmpty("Catalog is empty")
            return
        }
        val next = CatalogState.Ready(
            categories = categories,
            slideTemplates = deriveSlideTemplates(categories),
        )
        if (_state.value != next) _state.value = next
    }

    /**
     * The only filtering the rest of the app is allowed to rely on:
     *  - drop `enabled == false` templates (62 dead rows in the live catalog, Risks #9)
     *  - drop video templates entirely — Seedream returns images only (Risks #11)
     *  - drop categories left with nothing
     *  - PRESERVE the array order of `categories`; ids are not sorted and "Trending 🔥" is
     *    already first (Risks #10)
     */
    private fun sanitise(catalog: Catalog): List<TemplateCategory> = catalog.categories
        .map { category ->
            category.copy(
                templates = category.templates
                    .filter { it.enabled && !it.isVideo }
                    .map(::rewriteImageUrls)
            )
        }
        .filter { it.templates.isNotEmpty() }

    /**
     * Normalise catalog media values.
     *
     * The live catalog stores absolute URLs on Bunny private storage, so nothing is rewritten —
     * authentication is what makes them load, and that is handled by [CdnAuth.interceptor] on the
     * image loader. Relative paths are still resolved against the configured base, because the
     * upstream format allows them.
     */
    private fun rewriteImageUrls(template: Template): Template = template.copy(
        thumbUrl = CdnAuth.resolveUrl(template.thumbUrl),
        exampleImgs = template.exampleImgs.map(CdnAuth::resolveUrl),
        demoImgs = template.demoImgs.map(CdnAuth::resolveUrl),
    )

    private fun deriveSlideTemplates(categories: List<TemplateCategory>): List<Template> =
        categories.asSequence()
            .flatMap { it.templates.asSequence() }
            .filter { it.isShowOnSlide }
            .sortedBy { it.slideOrder }
            .take(MAX_SLIDE_TEMPLATES)
            .toList()

    // ---------------------------------------------------------------- lookups

    private val ready: CatalogState.Ready? get() = _state.value as? CatalogState.Ready

    val categories: List<TemplateCategory> get() = ready?.categories.orEmpty()

    val slideTemplates: List<Template> get() = ready?.slideTemplates.orEmpty()

    fun templateById(id: String): Template? = categories.asSequence()
        .flatMap { it.templates.asSequence() }
        .firstOrNull { it.id == id }

    fun categoryById(id: Int): TemplateCategory? = categories.firstOrNull { it.categoryId == id }

    /** Category a template belongs to — used for the "~30s · <category>" meta chips. */
    fun categoryOf(templateId: String): TemplateCategory? =
        categories.firstOrNull { category -> category.templates.any { it.id == templateId } }

    /** "More from this category", excluding the template being viewed. */
    fun relatedTemplates(templateId: String, limit: Int = 12): List<Template> =
        categoryOf(templateId)?.templates?.filter { it.id != templateId }?.take(limit).orEmpty()
}

sealed interface CatalogState {

    data object Loading : CatalogState

    data class Ready(
        val categories: List<TemplateCategory>,
        val slideTemplates: List<Template>,
    ) : CatalogState

    data class Error(val message: String) : CatalogState
}
