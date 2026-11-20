package com.example

import android.app.Application
import com.example.data.AppContainer

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.voiceManager.release()
        super.onTerminate()
    }
}
