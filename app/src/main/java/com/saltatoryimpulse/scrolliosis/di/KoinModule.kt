package com.saltatoryimpulse.scrolliosis.di

import android.content.Context
import android.view.WindowManager
import com.saltatoryimpulse.scrolliosis.AppDatabase
import com.saltatoryimpulse.scrolliosis.data.InstalledAppCatalog
import com.saltatoryimpulse.scrolliosis.data.IKnowledgeRepository
import com.saltatoryimpulse.scrolliosis.data.KnowledgeRepository
import com.saltatoryimpulse.scrolliosis.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appKoinModule = module {
    // Repository bound to its interface for easier testing/mocking.
    // DIRECT BOOT: Use device-protected storage so the blocked-apps list is readable
    // before the user's first unlock after a cold reboot (minSdk 26 >= API 24 requirement).
    single<IKnowledgeRepository> {
        val dbContext = androidContext().createDeviceProtectedStorageContext()
        KnowledgeRepository(AppDatabase.getDatabase(dbContext).knowledgeDao())
    }

    single { InstalledAppCatalog(androidContext()) }

    // Provide a singleton OverlayController using the application context and a dedicated scope
    single {
        val ctx: Context = androidContext()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        OverlayController(ctx, wm, scope)
    }
}
