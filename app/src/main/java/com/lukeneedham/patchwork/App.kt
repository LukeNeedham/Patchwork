package com.lukeneedham.patchwork

import android.app.Application
import com.lukeneedham.patchwork.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.DebugKitConfig

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // First, so that Logger and the crash handler are ready before anything else runs
        DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}
