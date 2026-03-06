package com.saltatoryimpulse.scrolliosis.ui.screens

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView
import com.saltatoryimpulse.scrolliosis.BlockedApp
import com.saltatoryimpulse.scrolliosis.data.IKnowledgeRepository
import com.saltatoryimpulse.scrolliosis.data.InstalledAppCatalog
import com.saltatoryimpulse.scrolliosis.data.InstalledAppInfo
import com.saltatoryimpulse.scrolliosis.ui.components.CenteredLoader
import com.saltatoryimpulse.scrolliosis.ui.components.ScrolliosisTopBar
import com.saltatoryimpulse.scrolliosis.ui.theme.BackgroundDark
import com.saltatoryimpulse.scrolliosis.ui.theme.ErrorRed
import com.saltatoryimpulse.scrolliosis.ui.theme.OutlineSubtle
import com.saltatoryimpulse.scrolliosis.ui.theme.PrimaryAccent
import com.saltatoryimpulse.scrolliosis.ui.theme.SurfaceDark
import com.saltatoryimpulse.scrolliosis.ui.theme.TextHighEmphasis
import com.saltatoryimpulse.scrolliosis.ui.theme.TextMediumEmphasis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.get

@Composable
fun AppSelectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository: IKnowledgeRepository = get()
    val installedAppCatalog: InstalledAppCatalog = get()
    val scope = rememberCoroutineScope()

    var appToUnblock by remember { mutableStateOf<BlockedApp?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                scope.launch {
                    installedAppCatalog.refreshCatalog()
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(enabled = appToUnblock != null) { appToUnblock = null }
    BackHandler { onBack() }

    val blockedApps by repository.getBlockedApps().collectAsState(initial = emptyList())
    val blockedPackageNames = remember(blockedApps) { blockedApps.map { it.packageName }.toSet() }
    val installedApps by installedAppCatalog.apps.collectAsState()
    val isRefreshing by installedAppCatalog.isRefreshing.collectAsState()

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

    LaunchedEffect(Unit) {
        installedAppCatalog.refreshCatalog()
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val activeGates = remember(filteredApps, blockedPackageNames) {
        filteredApps.filter { blockedPackageNames.contains(it.packageName) }
    }
    val availableApps = remember(filteredApps, blockedPackageNames) {
        filteredApps.filter { !blockedPackageNames.contains(it.packageName) }
    }
    val isLoading = installedApps.isEmpty() && isRefreshing

    Scaffold(
        containerColor = BackgroundDark,
        topBar = { ScrolliosisTopBar(onBack = onBack, title = "SELECT GATES") }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CenteredLoader(text = "SCANNING INSTALLED APPS...")
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
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = PrimaryAccent)
                        },
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
                                installedAppCatalog = installedAppCatalog,
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
                                installedAppCatalog = installedAppCatalog,
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
    app: InstalledAppInfo,
    isBlocked: Boolean,
    installedAppCatalog: InstalledAppCatalog,
    isScrolling: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var iconDrawable by remember(app.packageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.packageName, isScrolling) {
        if (iconDrawable == null && !isScrolling) {
            delay(50)
            iconDrawable = installedAppCatalog.loadIcon(app.packageName)
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
                if (iconDrawable != null) {
                    AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(iconDrawable)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = app.name.take(1).uppercase(),
                        color = TextMediumEmphasis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
    var timeLeft by rememberSaveable { mutableStateOf(30) }
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