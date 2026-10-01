package com.bblvn.composebaseproject.data.database.repo

import android.content.Context
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.data.database.AppDatabase
import com.bblvn.composebaseproject.data.database.dao.CreationDao
import com.bblvn.composebaseproject.data.database.entity.CreationEntity
import com.bblvn.composebaseproject.data.domain.Creation
import com.bblvn.composebaseproject.data.domain.CreationFilter
import com.bblvn.composebaseproject.data.domain.CreationStatus
import com.bblvn.composebaseproject.data.domain.CreationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for local history.
 *
 * Exposes one hot [StateFlow] that every screen observes; the Creations screen filters it in
 * memory (a few hundred rows at most) instead of running a query per chip.
 */
object CreationRepo {

    private lateinit var dao: CreationDao
    private lateinit var scope: CoroutineScope

    lateinit var creations: StateFlow<List<Creation>>
        private set

    fun init(context: Context, appScope: CoroutineScope) {
        if (::dao.isInitialized) return
        dao = AppDatabase.get(context).creationDao()
        scope = appScope
        creations = dao.observeAll()
            .map { rows -> rows.map(CreationEntity::toDomain) }
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }

    fun filtered(filter: CreationFilter): List<Creation> = creations.value.filter {
        when (filter) {
            CreationFilter.ALL -> true
            CreationFilter.TEMPLATE -> it.type == CreationType.TEMPLATE
            CreationFilter.TOOL -> it.type == CreationType.TOOL
            CreationFilter.SAVED -> it.savedToGallery
        }
    }

    fun byId(id: String): Creation? = creations.value.firstOrNull { it.id == id }

    suspend fun get(id: String): Creation? = dao.getById(id)?.toDomain()

    suspend fun insert(creation: Creation) = dao.insert(CreationEntity.fromDomain(creation))

    suspend fun update(creation: Creation) = dao.update(CreationEntity.fromDomain(creation))

    /** Read-modify-write on a single row; no-op when the row is gone. */
    suspend fun mutate(id: String, transform: (Creation) -> Creation) {
        val current = dao.getById(id)?.toDomain() ?: return
        dao.update(CreationEntity.fromDomain(transform(current)))
    }

    suspend fun markSaved(id: String) = mutate(id) { it.copy(savedToGallery = true) }

    /** Rows left PROCESSING by a process kill — resumed or failed on next launch (Risks #7). */
    suspend fun processing(): List<Creation> = dao.getProcessing().map(CreationEntity::toDomain)

    suspend fun markInterrupted(id: String, message: String) = mutate(id) {
        it.copy(status = CreationStatus.FAILED, errorMessage = message)
    }

    /**
     * Deletes the row AND its files. Callers must cancel a running job first (Risks #27) —
     * `GenerationEngine.deleteCreation` does both in the right order.
     */
    suspend fun delete(id: String) {
        val creation = dao.getById(id)?.toDomain()
        dao.deleteById(id)
        creation?.let { FileManager.deleteCreationFiles(it.resultImagePath, it.sourceImagePaths) }
    }

    suspend fun deleteAll() {
        val all = creations.value
        dao.deleteAll()
        all.forEach { FileManager.deleteCreationFiles(it.resultImagePath, it.sourceImagePaths) }
    }

    /**
     * Every local path still referenced — used by `FileManager.pruneOrphans`.
     *
     * Includes the in-flight [GenerationDraft] as well as persisted rows. A draft holds files in
     * `sources/` that have no DB row yet (the user is between the picker and submit), so a prune
     * triggered meanwhile — e.g. by opening Profile and clearing cache mid-selection — would
     * silently delete the photo they just chose and the pipeline would fail at upload.
     */
    fun referencedPaths(): Set<String> = buildSet {
        GenerationDraft.current.let { draft ->
            addAll(draft.slots.filterNotNull())
            addAll(draft.adjustedSlots.filterNotNull())
        }
        creations.value.forEach { creation ->
            creation.resultImagePath?.let(::add)
            creation.sourceImagePaths.forEach { path ->
                add(path)
                add(FileManager.adjustedSiblingOf(path).absolutePath)
            }
        }
    }
}
