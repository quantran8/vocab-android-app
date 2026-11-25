package com.vocabflow

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import com.vocabflow.app.AppContainer

/** Owns the [AppContainer] for the whole process, so stores outlive activity recreation (language change). */
class VocabFlowApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Rive.init(this)
        container = AppContainer(this)
    }
}
