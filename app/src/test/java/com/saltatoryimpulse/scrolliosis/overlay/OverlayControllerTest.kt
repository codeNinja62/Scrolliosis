package com.saltatoryimpulse.scrolliosis.overlay

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OverlayControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher + Job())

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cancel()
    }

    @Test
    fun showToastAndRelease_noExceptions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val controller = OverlayController(context, wm, testScope)

        controller.prepareBlockingShield()
        controller.showBlockingShield()
        // Show a toast and ensure controller created
        controller.showCustomToast("hello")
        // mount a short timer (expiration in near future)
        val expiration = System.currentTimeMillis() + 2000L
        controller.mountTimerForPackage("com.example", expiration) {
            // onExpire callback should be callable
        }

        // release should not throw
        controller.release()
        assertNotNull(controller)
    }
}
