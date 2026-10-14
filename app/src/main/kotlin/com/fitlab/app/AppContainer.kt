package com.fitlab.app

import android.content.Context
import com.fitlab.app.data.db.FitLabDatabase
import com.fitlab.app.data.files.FileStore
import com.fitlab.app.data.repo.CompensationRepository
import com.fitlab.app.data.repo.PartRepository
import com.fitlab.app.data.repo.ProfileRepository
import com.fitlab.app.data.repo.TestRepository
import com.fitlab.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ręczne DI: jedna instancja zależności na proces. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = AppSettings(context)
    val fileStore = FileStore(context)
    val database: FitLabDatabase = FitLabDatabase.create(context)
    val profiles = ProfileRepository(database, settings)
    val compensation = CompensationRepository(database)
    val parts = PartRepository(database, fileStore)
    val tests = TestRepository(database, compensation)
}
