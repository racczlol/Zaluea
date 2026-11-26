package com.glass.launcher

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import com.glass.launcher.data.AppInfo
import com.glass.launcher.ui.LauncherActions
import com.glass.launcher.ui.LauncherRoot
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity(), LauncherActions {

    private val vm: LauncherViewModel by viewModels()
    private val isDefaultHome = mutableStateOf(false)

    /** true = Home was pressed while we were already showing. */
    private val homeEvents = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDefaultHome()
        if (!isDefaultHome.value) openHomeSettings()
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val b = windowManager.currentWindowMetrics.bounds
            toast("Setting wallpaper...")
            vm.setWallpaper(uri, b.width(), b.height()) { ok ->
                toast(if (ok) "Wallpaper set - blur is on!" else "Couldn't use that picture")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // The Back button never closes the Home screen (open panels handle Back themselves).
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Color.White, background = Color.Transparent),
            ) {
                LauncherRoot(
                    vm = vm,
                    actions = this,
                    isDefaultHome = isDefaultHome.value,
                    homeEvents = homeEvents,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val alreadyOnTop = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            homeEvents.tryEmit(alreadyOnTop)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultHome()
        val b = windowManager.currentWindowMetrics.bounds
        vm.reloadBackdrop(b.width(), b.height())
        // Keep the wallpaper centred, so the blur inside the glass lines up with it.
        window.decorView.post {
            try {
                val token = window.decorView.windowToken
                if (token != null) WallpaperManager.getInstance(this).setWallpaperOffsets(token, 0.5f, 0.5f)
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshDefaultHome() {
        isDefaultHome.value = try {
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun safeStart(intent: Intent, options: Bundle? = null): Boolean = try {
        startActivity(intent, options)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun openHomeSettings() {
        safeStart(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            safeStart(Intent(Settings.ACTION_SETTINGS))
    }

    // ---- LauncherActions ----

    override fun launch(app: AppInfo, bounds: Rect) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        var options: Bundle? = null
        if (bounds.width > 0f && bounds.height > 0f) {
            val r = android.graphics.Rect(
                bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt(),
            )
            intent.sourceBounds = r
            // The app "grows" out of its icon.
            options = ActivityOptions.makeScaleUpAnimation(window.decorView, r.left, r.top, r.width(), r.height())
                .toBundle()
        }
        if (!safeStart(intent, options)) toast("Couldn't open ${app.label}")
    }

    override fun appInfo(app: AppInfo) {
        safeStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun uninstall(app: AppInfo) {
        if (app.isSystem) {
            toast("${app.label} is built into your phone and can't be uninstalled. You can disable it in App info.")
            appInfo(app)
            return
        }
        @Suppress("DEPRECATION")
        val ok = safeStart(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        if (!ok) appInfo(app)
    }

    override fun openClock() {
        safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Pulls down the notification panel (like swiping down on Samsung's own Home screen). */
    override fun expandNotifications() {
        try {
            val sbm = getSystemService("statusbar")
            sbm?.javaClass?.getMethod("expandNotificationsPanel")?.invoke(sbm)
        } catch (_: Throwable) {
        }
    }

    override fun requestDefaultHome() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            try {
                roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            } catch (_: Exception) {
            }
        }
        openHomeSettings()
    }

    override fun pickWallpaper() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun requestAllFilesAccess() {
        val ok = safeStart(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        )
        if (!ok) safeStart(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    override fun hasAllFilesAccess(): Boolean = try {
        Environment.isExternalStorageManager()
    } catch (_: Exception) {
        false
    }

    override fun openSystemWallpaper() {
        safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
    }

    override fun toast(message: String) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
