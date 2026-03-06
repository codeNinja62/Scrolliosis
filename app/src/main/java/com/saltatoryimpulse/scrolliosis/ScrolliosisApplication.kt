package com.saltatoryimpulse.scrolliosis

import android.app.Application
import com.saltatoryimpulse.scrolliosis.di.appKoinModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ScrolliosisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ScrolliosisApplication)
            modules(appKoinModule)
        }
    }
}
