package com.saltatoryimpulse.scrolliosis

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.saltatoryimpulse.scrolliosis.data.IKnowledgeRepository
import com.saltatoryimpulse.scrolliosis.overlay.OverlayController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Robolectric.buildService
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GateServiceTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher + Job())

    private lateinit var fakeRepo: IKnowledgeRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        fakeRepo = object : IKnowledgeRepository {
            override fun getBlockedApps() = throw UnsupportedOperationException()
            override fun getAllEntries() = throw UnsupportedOperationException()
            override suspend fun insertEntry(entry: KnowledgeEntry) = Unit
            override suspend fun deleteEntry(entry: KnowledgeEntry) = Unit
            override suspend fun getRandomCustomPrompt(): KnowledgeEntry? = null
            override suspend fun blockApp(app: BlockedApp) = Unit
            override suspend fun unblockApp(app: BlockedApp) = Unit
            override suspend fun isAppBlocked(pkg: String): Boolean {
                // Treat this package as blocked
                return pkg == "com.example.app"
            }
        }

        val app = ApplicationProvider.getApplicationContext<Context>() as Application
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlay = OverlayController(app, wm, testScope)

        val testModule = module {
            single<IKnowledgeRepository> { fakeRepo }
            single { overlay }
        }

        startKoin { modules(testModule) }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun checkBlockingLogic_startsActivity_whenAppBlocked() {
        val svcController = buildService(GateService::class.java).create()
        val service = svcController.get()

        // Replace private serviceScope with testScope so coroutines run under our dispatcher
        val scopeField = service.javaClass.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(service, testScope)

        // Ensure cooldown is in the past
        val cooldownField = service.javaClass.getDeclaredField("cooldownEndTime")
        cooldownField.isAccessible = true
        cooldownField.setLong(service, 0L)

        // Invoke private method checkBlockingLogic for the blocked package
        val method = service.javaClass.getDeclaredMethod("checkBlockingLogic", String::class.java)
        method.isAccessible = true
        method.invoke(service, "com.example.app")

        // Advance dispatcher to execute launched coroutine
        testScope.testScheduler.advanceUntilIdle()

        // Verify an activity was started (Home intent or MainActivity)
        val app = ApplicationProvider.getApplicationContext<Context>() as Application
        val shadowApp = Shadows.shadowOf(app)
        val started = shadowApp.nextStartedActivity
        assertNotNull("Expected an activity to be started", started)
    }
}
