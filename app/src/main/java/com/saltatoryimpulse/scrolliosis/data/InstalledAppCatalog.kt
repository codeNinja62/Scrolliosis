package com.saltatoryimpulse.scrolliosis.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Keep
data class InstalledAppInfo(
    val name: String,
    val packageName: String
)

class InstalledAppCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val refreshMutex = Mutex()
    private val memoryIconCache = LruCache<String, Drawable>(200)

    private val catalogDir = File(appContext.filesDir, "scrolliosis_app_catalog")
    private val iconDir = File(catalogDir, "icons")
    private val metadataFile = File(catalogDir, "apps.json")

    private val _apps = MutableStateFlow(readMetadataFromDisk())
    val apps: StateFlow<List<InstalledAppInfo>> = _apps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun primeInstalledApps() {
        refreshCatalog(forceIconRefresh = true)
    }

    suspend fun refreshCatalog() {
        refreshCatalog(forceIconRefresh = false)
    }

    suspend fun loadIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        memoryIconCache.get(packageName)
            ?: readIconFromDisk(packageName)?.also { memoryIconCache.put(packageName, it) }
            ?: loadAndPersistFreshIcon(packageName)
    }

    private suspend fun refreshCatalog(forceIconRefresh: Boolean) {
        refreshMutex.withLock {
            _isRefreshing.value = true
            try {
                val cachedApps = if (_apps.value.isEmpty()) readMetadataFromDisk() else _apps.value
                val cachedByPackage = cachedApps.associateBy { it.packageName }
                val resolvedApps = queryLauncherApps()
                val currentApps = resolvedApps.map { it.app }
                val currentPackages = currentApps.map { it.packageName }.toSet()
                val removedPackages = cachedByPackage.keys - currentPackages

                removedPackages.forEach { packageName ->
                    memoryIconCache.remove(packageName)
                    iconFile(packageName).delete()
                }

                resolvedApps.forEach { resolved ->
                    val cached = cachedByPackage[resolved.app.packageName]
                    val iconMissing = !iconFile(resolved.app.packageName).exists()
                    val needsIconRefresh = forceIconRefresh || cached == null || cached.name != resolved.app.name || iconMissing
                    if (needsIconRefresh) {
                        persistIcon(resolved.app.packageName, resolved.resolveInfo.loadIcon(packageManager))
                    }
                }

                writeMetadata(currentApps)
                _apps.value = currentApps
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun queryLauncherApps(): List<ResolvedInstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolveInfo ->
                runCatching {
                    ResolvedInstalledApp(
                        app = InstalledAppInfo(
                            name = resolveInfo.loadLabel(packageManager).toString(),
                            packageName = resolveInfo.activityInfo.packageName
                        ),
                        resolveInfo = resolveInfo
                    )
                }.getOrNull()
            }
            .distinctBy { it.app.packageName }
            .sortedBy { it.app.name.lowercase() }
    }

    private fun readMetadataFromDisk(): List<InstalledAppInfo> {
        if (!metadataFile.exists()) return emptyList()

        return runCatching {
            val rawJson = metadataFile.readText()
            val entries = JSONArray(rawJson)
            buildList(entries.length()) {
                for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    val name = entry.optString("name")
                    val packageName = entry.optString("packageName")
                    if (name.isNotBlank() && packageName.isNotBlank()) {
                        add(InstalledAppInfo(name = name, packageName = packageName))
                    }
                }
            }
        }.getOrElse {
            metadataFile.delete()
            emptyList()
        }
    }

    private fun writeMetadata(apps: List<InstalledAppInfo>) {
        if (!catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        val json = JSONArray().apply {
            apps.forEach { app ->
                put(
                    JSONObject()
                        .put("name", app.name)
                        .put("packageName", app.packageName)
                )
            }
        }

        metadataFile.writeText(json.toString())
    }

    private fun iconFile(packageName: String): File = File(iconDir, "$packageName.png")

    private fun readIconFromDisk(packageName: String): Drawable? {
        val file = iconFile(packageName)
        if (!file.exists()) return null

        return runCatching {
            FileInputStream(file).use { inputStream ->
                Drawable.createFromStream(inputStream, file.name)
            }
        }.getOrNull()
    }

    private fun persistIcon(packageName: String, drawable: Drawable) {
        if (!iconDir.exists()) {
            iconDir.mkdirs()
        }

        val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1).coerceAtMost(256)
        val bitmap = drawable.toBitmap(width = size, height = size)
        val file = iconFile(packageName)

        FileOutputStream(file).use { outputStream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        memoryIconCache.put(packageName, drawable)
    }

    private fun loadAndPersistFreshIcon(packageName: String): Drawable? {
        return runCatching {
            packageManager.getApplicationIcon(packageName)
        }.getOrNull()?.also { drawable ->
            persistIcon(packageName, drawable)
        }
    }

    private data class ResolvedInstalledApp(
        val app: InstalledAppInfo,
        val resolveInfo: ResolveInfo
    )
}