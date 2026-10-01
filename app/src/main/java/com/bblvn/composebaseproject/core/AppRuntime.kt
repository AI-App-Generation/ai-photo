package com.bblvn.composebaseproject.core

import android.content.Context
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.catalog.CatalogRepo
import com.bblvn.composebaseproject.corefeatures.generation.GenerationEngine
import com.bblvn.composebaseproject.data.database.repo.CreationRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Composition root for the non-UI half of the app — this project uses no DI framework
 * (skills/APP_STRUCTURE.md), so the singletons are wired here, once, from `GlobalApp.onCreate`.
 *
 * [scope] is the Application-level CoroutineScope that outlives every screen. The generation
 * pipeline runs on it so leaving the Generating screen does not kill a run in progress
 * (Risks #20).
 */
object AppRuntime {

    /** Application-lifetime scope. Never cancelled; a SupervisorJob keeps one failure isolated. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        initialised = true

        val app = context.applicationContext
        FileManager.init(app)
        CreationRepo.init(app, scope)
        // Order matters: the engine resumes interrupted rows on init, so the repo must be up.
        GenerationEngine.init(scope)
        CatalogRepo.init(scope)

        // Sweep watermarked share copies left behind by earlier sessions. Doing it here rather
        // than after each share avoids deleting a file the receiving app has not read yet.
        scope.launch { FileManager.clearStaleExports() }
    }
}
