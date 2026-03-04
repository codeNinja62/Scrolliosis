package com.saltatoryimpulse.braingate

import android.app.Application
import com.saltatoryimpulse.braingate.di.appKoinModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BrainGateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@BrainGateApplication)
            modules(appKoinModule)
        }
    }
}
