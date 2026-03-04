package com.saltatoryimpulse.braingate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.saltatoryimpulse.braingate.ui.theme.BrainGateTheme
import com.saltatoryimpulse.braingate.ui.screens.*

class MainActivity : ComponentActivity() {

    private var blockedPackage = mutableStateOf("")
    private var navController: NavHostController? = null

    // PERSISTENT ENFORCEMENT STATE: Isolated from the NavHost backstack
    private var isSystemLocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If launched for a system purge, show purge screen immediately to prevent other UI.
        val initialRoute = intent?.getStringExtra(Constants.EXTRA_START_ROUTE)
        if (initialRoute == "system_purge_screen") {
            isSystemLocked.value = true
        }

        setContent {
            // Apply application theme and set up root composition.
            BrainGateTheme {
                val viewModel: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val blockedCount by viewModel.blockedAppCount.collectAsState()

                val currentNavController = rememberNavController()
                navController = currentNavController

                // Check required permissions (accessibility, overlay, notifications)
                val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(this)
                val hasOverlay = PermissionUtils.canDrawOverlays(this)
                val hasNotifications = PermissionUtils.hasNotificationPermission(this)
                val needsOnboarding = !hasAccessibility || !hasOverlay || !hasNotifications

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // If the system is locked for a purge, render the purge screen; otherwise show NavHost.
                    if (isSystemLocked.value) {
                        SystemPurgeScreen(onComplete = {
                            isSystemLocked.value = false
                            finish()
                        })
                    } else {
                        NavHost(
                            navController = currentNavController,
                            startDestination = "splash"
                        ) {
                            composable("splash") {
                                SplashScreen(onTimeout = {
                                    handleRouting(intent, currentNavController, needsOnboarding)
                                })
                            }

                            composable("system_purge_screen") {
                                SystemPurgeScreen(onComplete = {
                                    isSystemLocked.value = false
                                    finish()
                                })
                            }

                            composable("welcome") {
                                WelcomeScreen { currentNavController.navigate("onboarding") }
                            }

                            composable("onboarding") {
                                OnboardingScreen(
                                    needsAccessibility = !PermissionUtils.isAccessibilityServiceEnabled(this@MainActivity),
                                    needsOverlay = !PermissionUtils.canDrawOverlays(this@MainActivity),
                                    needsNotifications = !PermissionUtils.hasNotificationPermission(this@MainActivity),
                                    onCheckPermissions = {
                                        if (PermissionUtils.isAccessibilityServiceEnabled(this@MainActivity) &&
                                            PermissionUtils.canDrawOverlays(this@MainActivity)) {
                                            currentNavController.navigate("home") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                // Home screen entry point.
                                HomeScreen(
                                    blockedAppCount = blockedCount,
                                    onOpenVault = { currentNavController.navigate("vault") },
                                    onOpenSettings = { currentNavController.navigate("settings") }
                                )
                            }

                            composable("settings") {
                                AppSelectionScreen { currentNavController.popBackStack() }
                            }

                            composable("vault") {
                                KnowledgeVaultScreen { currentNavController.popBackStack() }
                            }

                            composable("gatekeeper_screen") {
                                GatekeeperScreen(
                                    targetPackage = blockedPackage.value,
                                    onUnlockSuccess = { pkg -> performUnlock(pkg) },
                                    onCloseApp = { performClose() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle routing for initial launch and background-to-foreground transitions.
     * Receives intents from `GateService` and navigates to the requested route.
     */
    private fun handleRouting(intent: Intent?, nav: NavHostController?, needsOnboarding: Boolean) {
        val route = intent?.getStringExtra(Constants.EXTRA_START_ROUTE)
        val pkg = intent?.getStringExtra(Constants.EXTRA_BLOCKED_PACKAGE)

        when {
            // If system purge requested, lock and navigate to purge screen.
            route == "system_purge_screen" -> {
                isSystemLocked.value = true
                nav?.navigate("system_purge_screen") {
                    popUpTo(0) { inclusive = true }
                }
            }
            // If gatekeeper route specified with a package, navigate to gatekeeper.
            route == "gatekeeper_screen" && !pkg.isNullOrEmpty() -> {
                blockedPackage.value = pkg!!
                nav?.navigate("gatekeeper_screen") {
                    popUpTo("splash") { inclusive = true }
                    launchSingleTop = true
                }
            }
            // If required permissions are missing, show onboarding flow.
            needsOnboarding -> {
                nav?.navigate("welcome") {
                    popUpTo("splash") { inclusive = true }
                }
            }
            // Default: normal home route.
            else -> {
                nav?.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(this)
        val hasOverlay = PermissionUtils.canDrawOverlays(this)
        val needsOnboarding = !hasAccessibility || !hasOverlay

        handleRouting(intent, navController, needsOnboarding)
    }

    private fun performUnlock(packageToLaunch: String) {
        val unlockIntent = Intent(Constants.ACTION_UNLOCK).apply {
            putExtra(Constants.EXTRA_PACKAGE_NAME, packageToLaunch)
            setPackage(packageName) // Security: Prevent broadcast hijacking
        }
        sendBroadcast(unlockIntent)

        packageManager.getLaunchIntentForPackage(packageToLaunch)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
        finish()
    }

    private fun performClose() {
        sendBroadcast(Intent(Constants.ACTION_SHOW_TOAST).apply {
            putExtra(Constants.EXTRA_MESSAGE, "Focus preserved. Gate remains closed.")
            setPackage(packageName)
        })
        finish()
    }
}