package com.saltatoryimpulse.braingate.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.Keep
import androidx.compose.ui.viewinterop.AndroidView
import com.saltatoryimpulse.braingate.BlockedApp
import com.saltatoryimpulse.braingate.data.IKnowledgeRepository
import org.koin.androidx.compose.get
import com.saltatoryimpulse.braingate.ui.components.BrainGateTopBar
import com.saltatoryimpulse.braingate.ui.components.CenteredLoader
import com.saltatoryimpulse.braingate.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppSelectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val repository: IKnowledgeRepository = get()
    val scope = rememberCoroutineScope()

    // Declare early so BackHandler priority chain can reference it directly (no LaunchedEffect lag)
    var appToUnblock by remember { mutableStateOf<BlockedApp?>(null) }

    // BUG-05: increment to force a re-fetch whenever the OS package list changes
    var cacheRefreshKey by remember { mutableStateOf(0) }

    // BUG-05: register a package-change receiver for the lifetime of this screen
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                AppCache.invalidate()
                cacheRefreshKey++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    // BUG-02: When the cooldown dialog is open, Back closes it (cancel path).
    // This BackHandler has higher priority (declared first) than the screen-level one below,
    // so Back cannot skip the 30-second wait by navigating away from the screen.
    BackHandler(enabled = appToUnblock != null) { appToUnblock = null }
    BackHandler { onBack() }

    val blockedApps by repository.getBlockedApps().collectAsState(initial = emptyList())
    val blockedPackageNames = remember(blockedApps) { blockedApps.map { it.packageName }.toSet() }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf<List<AppListItem>>(emptyList()) }

    var activeGates by remember { mutableStateOf<List<AppListItem>>(emptyList()) }
    var availableApps by remember { mutableStateOf<List<AppListItem>>(emptyList()) }

    // THE CACHE: UPGRADED to 200 to handle large device app libraries comfortably
    val iconCache = remember { LruCache<String, Drawable>(200) }

    // VELOCITY CAPPER: UPGRADED to 6000f for peak smoothness on 120Hz/144Hz displays
    val velocityCapper = remember {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                val maxVelocity = 6000f
                val cappedY = available.y.coerceIn(-maxVelocity, maxVelocity)
                val consumedY = available.y - cappedY
                return Velocity(0f, consumedY)
            }
        }
    }

    // TIER 1 WARM-UP: INSTANT METADATA RETRIEVAL
    // BUG-05: also re-runs whenever cacheRefreshKey increments (package added/removed)
    LaunchedEffect(cacheRefreshKey) {
        withContext(Dispatchers.IO) {
            installedApps = AppCache.getInstalledApps(pm)
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    // TIER 2 WARM-UP: PROACTIVE ICON PRE-FETCHING
    // Automatically loads the first 20 icons into memory before the user even scrolls.
    LaunchedEffect(installedApps) {
        if (installedApps.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                installedApps.take(20).forEach { app ->
                    if (iconCache.get(app.packageName) == null) {
                        try {
                            val drawable = pm.getApplicationIcon(app.packageName)
                            iconCache.put(app.packageName, drawable)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    // TWO-TIER SORTING ENGINE: Anchors scroll state to prevent row teleportation
    LaunchedEffect(installedApps, searchQuery, blockedPackageNames) {
        if (installedApps.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            val filtered = if (searchQuery.isBlank()) {
                installedApps
            } else {
                installedApps.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }

            val active = filtered.filter { blockedPackageNames.contains(it.packageName) }
                .sortedBy { it.name.lowercase() }

            val available = filtered.filter { !blockedPackageNames.contains(it.packageName) }
                .sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                activeGates = active
                availableApps = available
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = { BrainGateTopBar(onBack = onBack, title = "SELECT GATES") }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CenteredLoader(text = "SCANNING NEURAL PATHWAYS...")
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        placeholder = {
                            Text(
                                "Search apps...",
                                color = TextMediumEmphasis.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light)
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = PrimaryAccent) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextMediumEmphasis)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = SurfaceDark,
                            focusedContainerColor = SurfaceDark,
                            unfocusedBorderColor = OutlineSubtle,
                            focusedBorderColor = PrimaryAccent,
                            focusedTextColor = TextHighEmphasis
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val listState = rememberLazyListState()
                    val isScrolling = listState.isScrollInProgress

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.nestedScroll(velocityCapper),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            items = activeGates,
                            key = { "${it.packageName}_active" },
                            contentType = { "app_row" }
                        ) { app ->
                            AppRow(
                                app = app,
                                isBlocked = true,
                                pm = pm,
                                iconCache = iconCache,
                                isScrolling = isScrolling,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        scope.launch {
                                            repository.blockApp(BlockedApp(app.packageName, app.name))
                                        }
                                    } else {
                                        appToUnblock = BlockedApp(app.packageName, app.name)
                                    }
                                }
                            )
                        }

                        items(
                            items = availableApps,
                            key = { "${it.packageName}_available" },
                            contentType = { "app_row" }
                        ) { app ->
                            AppRow(
                                app = app,
                                isBlocked = false,
                                pm = pm,
                                iconCache = iconCache,
                                isScrolling = isScrolling,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        scope.launch {
                                            repository.blockApp(BlockedApp(app.packageName, app.name))
                                        }
                                    } else {
                                        appToUnblock = BlockedApp(app.packageName, app.name)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            appToUnblock?.let { app ->
                CooldownDialog(
                    app = app,
                            onConfirm = {
                        scope.launch {
                            repository.unblockApp(app)
                            appToUnblock = null
                        }
                    },
                    onCancel = { appToUnblock = null }
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppListItem,
    isBlocked: Boolean,
    pm: PackageManager,
    iconCache: LruCache<String, Drawable>,
    isScrolling: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // remember(app.packageName) prevents stale icons from rendering during fast scroll
    var iconDrawable by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }

    // DEFERRED GPU ALLOCATION WITH DEBOUNCE
    if (iconDrawable == null) {
        LaunchedEffect(app.packageName, isScrolling) {
            if (!isScrolling) {
                // 50ms delay keeps the CPU at 0% usage during fast "flings"
                delay(50)
                withContext(Dispatchers.IO) {
                    try {
                        val drawable = pm.getApplicationIcon(app.packageName)
                        iconCache.put(app.packageName, drawable)

                        withContext(Dispatchers.Main) {
                            iconDrawable = drawable
                        }
                    } catch (e: Exception) {
                        // Failsafe
                    }
                }
            }
        }
    }

    Surface(
        onClick = { onCheckedChange(!isBlocked) },
        color = if (isBlocked) SurfaceDark.copy(alpha = 0.8f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = if (isBlocked) androidx.compose.foundation.BorderStroke(1.dp, PrimaryAccent.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                // NATIVE BRIDGE: Hands drawing to GPU, skipping CPU rasterization
                if (iconDrawable != null) {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                // UPGRADE: Explicit Hardware Acceleration Hint
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(iconDrawable)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    color = TextHighEmphasis,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = TextMediumEmphasis.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isBlocked,
                // BUG-13: null makes Switch visual-only; Surface.onClick is the sole tap handler,
                // preventing the double-fire that caused state flicker when tapping the toggle.
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrimaryAccent,
                    checkedTrackColor = PrimaryAccent.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMediumEmphasis,
                    uncheckedTrackColor = SurfaceDark
                )
            )
        }
    }
}

@Composable
fun CooldownDialog(
    app: BlockedApp,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    // BUG-01: rememberSaveable survives configuration changes (rotation) so the
    // 30-second wait cannot be reset by flipping the device.
    var timeLeft by rememberSaveable { mutableStateOf(30) }
    // BUG-02: consume Back inside the dialog so it can only cancel, not bypass the timer
    BackHandler(enabled = true) { onCancel() }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    AlertDialog(
        onDismissRequest = { },
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "DISABLE GATE?",
                fontWeight = FontWeight.ExtraBold,
                color = TextHighEmphasis
            )
        },
        text = {
            Column {
                Text(
                    text = "You are attempting to unblock ${app.appName}.",
                    color = TextMediumEmphasis
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (timeLeft > 0) {
                    Text(
                        text = "Cooling down dopamine response...\nPlease wait $timeLeft seconds.",
                        color = PrimaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Cooldown complete. Are you sure you want to drop the gate?",
                        color = ErrorRed
                    )
                }
            }
        },
        // BUG-09: safe action (keep blocked) is the primary / visually dominant confirmButton.
        // Destructive action (unblock) is relegated to dismissButton (left-aligned, less weight).
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("KEEP IT BLOCKED", color = PrimaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                enabled = timeLeft == 0
            ) {
                Text(
                    text = "UNBLOCK APP",
                    color = if (timeLeft == 0) ErrorRed else TextMediumEmphasis.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Keep
data class AppListItem(
    val name: String,
    val packageName: String
)

// The Singleton Cache Engine
object AppCache {
    private var cachedApps: List<AppListItem>? = null

    /** BUG-05: called when packages are installed or removed so the list never goes stale. */
    fun invalidate() { cachedApps = null }

    suspend fun getInstalledApps(pm: PackageManager): List<AppListItem> {
        // If already loaded in RAM, return instantly (0ms)
        cachedApps?.let { return it }

        // If not, fetch it from the OS, cache it, and return
        return withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfoList = pm.queryIntentActivities(intent, 0)

            val apps = resolveInfoList.mapNotNull { resolveInfo ->
                try {
                    AppListItem(
                        name = resolveInfo.loadLabel(pm).toString(),
                        packageName = resolveInfo.activityInfo.packageName
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.packageName }

            cachedApps = apps
            apps
        }
    }
}