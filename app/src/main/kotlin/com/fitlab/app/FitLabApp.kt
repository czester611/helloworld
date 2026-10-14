package com.fitlab.app

import android.app.Application
import com.fitlab.app.platform.OpenCvInit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FitLabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        OpenCvInit.ensure()
        container.applicationScope.launch(Dispatchers.IO) { container.profiles.ensureDefaults() }
    }
}
