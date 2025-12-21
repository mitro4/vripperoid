package me.vripperoid.android

import android.app.Application
import me.vripperoid.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VRipperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VRipperApplication)
            modules(appModule)
        }
    }
}
