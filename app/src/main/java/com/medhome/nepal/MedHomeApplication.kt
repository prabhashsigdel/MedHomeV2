package com.medhome.nepal

import android.app.Application

class MedHomeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.sessionManager.start()
    }
}
