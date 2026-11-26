package com.glass.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** Finds every app that has an icon in the app list, and tells us when apps are installed/removed. */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val iconPx = 192

    @Suppress("DEPRECATION")
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { ri ->
                try {
                    val ai = ri.activityInfo
                    val key = ComponentName(ai.packageName, ai.name).flattenToShortString()
                    val icon = iconCache.getOrPut(key) {
                        ri.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap()
                    }
                    val appInfo = ai.applicationInfo
                    val flags = appInfo?.flags ?: 0
                    AppInfo(
                        key = key,
                        packageName = ai.packageName,
                        className = ai.name,
                        label = ri.loadLabel(pm).toString().trim(),
                        icon = icon,
                        isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                        category = appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED,
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    fun invalidate(packageName: String?) {
        if (packageName == null) iconCache.clear()
        else iconCache.keys.removeAll { it.startsWith("$packageName/") }
    }

    /** Emits a package name whenever an app is installed, updated or uninstalled. */
    fun packageEvents(): Flow<String?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(intent.data?.schemeSpecificPart)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Guesses good starter dock apps: Phone, Messages, Browser, Camera. */
    @Suppress("DEPRECATION")
    fun defaultDockPackages(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull { intent ->
            try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
                    ?.takeIf { it != "android" }
            } catch (e: Exception) {
                null
            }
        }
    }
}
