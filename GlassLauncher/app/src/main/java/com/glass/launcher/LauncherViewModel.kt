package com.glass.launcher

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.AppRepository
import com.glass.launcher.data.GlassSettings
import com.glass.launcher.data.HomeLayout
import com.glass.launcher.data.LayoutStore
import com.glass.launcher.data.SettingsStore
import com.glass.launcher.data.WallpaperBackdrop
import com.glass.launcher.data.addToDock
import com.glass.launcher.data.addToHome
import com.glass.launcher.data.createDefaultLayout
import com.glass.launcher.data.isPlaced
import com.glass.launcher.data.removeKey
import com.glass.launcher.data.repack
import com.glass.launcher.data.syncWith
import com.glass.launcher.data.trimmed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val layoutStore = LayoutStore(app)
    private val settingsStore = SettingsStore(app)
    private val backdropLoader = WallpaperBackdrop(app)
    private val refreshLock = Mutex()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Every launchable app, A-Z. */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _appMap = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val appMap: StateFlow<Map<String, AppInfo>> = _appMap.asStateFlow()

    private val _layout = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<GlassSettings> = _settings.asStateFlow()

    private val _backdrop = MutableStateFlow<ImageBitmap?>(null)
    /** A small copy of the wallpaper used to draw the blur inside glass. Null = no real blur. */
    val backdrop: StateFlow<ImageBitmap?> = _backdrop.asStateFlow()

    init {
        viewModelScope.launch { refreshApps() }
        viewModelScope.launch {
            repo.packageEvents()
                .onEach { repo.invalidate(it) }
                .collectLatest {
                    delay(400) // installs send several events in a row; wait for them to settle
                    refreshApps()
                }
        }
    }

    suspend fun refreshApps() = refreshLock.withLock {
        val list = repo.loadApps()
        _apps.value = list
        _appMap.value = list.associateBy { it.key }
        val keys = list.map { it.key }
        val s = _settings.value
        val current = _layout.value ?: layoutStore.load()
        val next = if (current == null) {
            val dockPkgs = repo.defaultDockPackages()
            val dockKeys = dockPkgs.mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg }?.key }
            createDefaultLayout(s.cols, s.rows, keys, dockKeys)
        } else {
            val synced = current.syncWith(keys)
            if (synced.cols != s.cols || synced.rows != s.rows) synced.repack(s.cols, s.rows) else synced
        }
        setLayout(next)
    }

    private fun setLayout(l: HomeLayout) {
        val t = l.trimmed()
        _layout.value = t
        viewModelScope.launch(Dispatchers.IO) { layoutStore.save(t) }
    }

    /** Change the layout. Returning null from [transform] means "do nothing". */
    fun edit(transform: (HomeLayout) -> HomeLayout?) {
        val cur = _layout.value ?: return
        transform(cur)?.let { setLayout(it) }
    }

    /** Returns false if the app was already on the home screen. */
    fun addToHome(key: String): Boolean {
        val cur = _layout.value ?: return false
        if (cur.isPlaced(key)) return false
        setLayout(cur.addToHome(key))
        return true
    }

    fun addToDock(key: String) = edit { it.addToDock(key) }

    fun removeFromHome(key: String) = edit { it.removeKey(key) }

    fun updateSettings(s: GlassSettings) {
        val old = _settings.value
        _settings.value = s
        settingsStore.save(s)
        if (old.cols != s.cols || old.rows != s.rows) edit { it.repack(s.cols, s.rows) }
    }

    fun resetLayout() {
        viewModelScope.launch {
            layoutStore.clear()
            _layout.value = null
            refreshApps()
        }
    }

    /** A nice name for a new folder, based on the app store category when both apps share one. */
    fun folderName(a: String, b: String): String {
        val map = _appMap.value
        val ca = map[a]?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
        val cb = map[b]?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
        if (ca == cb && ca != ApplicationInfo.CATEGORY_UNDEFINED) {
            ApplicationInfo.getCategoryTitle(getApplication(), ca)?.let { return it.toString() }
        }
        return "Folder"
    }

    fun reloadBackdrop(screenW: Int, screenH: Int) {
        viewModelScope.launch { _backdrop.value = backdropLoader.load(screenW, screenH) }
    }

    fun setWallpaper(uri: Uri, screenW: Int, screenH: Int, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = backdropLoader.setFromUri(uri, screenW, screenH)
            if (ok) _backdrop.value = backdropLoader.load(screenW, screenH)
            done(ok)
        }
    }
}
