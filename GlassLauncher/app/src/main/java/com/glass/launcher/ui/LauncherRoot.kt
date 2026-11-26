package com.glass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.AppItem
import com.glass.launcher.data.FolderItem
import com.glass.launcher.data.Loc
import com.glass.launcher.data.drop
import com.glass.launcher.data.dropFromDrawer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Things only the Activity can do (start apps, open system screens...). */
interface LauncherActions {
    fun launch(app: AppInfo, bounds: Rect)
    fun appInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun openClock()
    fun expandNotifications()
    fun requestDefaultHome()
    fun pickWallpaper()
    fun requestAllFilesAccess()
    fun hasAllFilesAccess(): Boolean
    fun openSystemWallpaper()
    fun toast(message: String)
}

/** A bouncy spring used for opening/closing the drawer. */
val bouncySpring = spring<Float>(dampingRatio = 0.72f, stiffness = 280f)

/** UI-only state shared by the screens (what's open, what's being dragged...). */
class LauncherUiState(private val scope: CoroutineScope) {
    val drawer = Animatable(0f)
    var drawerPull by mutableFloatStateOf(0f)
    var focusSearch by mutableStateOf(false)
    var menu by mutableStateOf<MenuRequest?>(null)
    var openFolder by mutableStateOf<String?>(null)
    var settingsOpen by mutableStateOf(false)
    val drag = DragState()
    /** Where every home cell / dock slot is on screen, for drag & drop. */
    val cells = mutableMapOf<Loc, Rect>()
    var screenHeight = 1f
    var pager: PagerState? = null
    val currentPage: Int get() = pager?.currentPage ?: 0
    /** Called when the finger is lifted after dragging an icon. */
    var onDrop: () -> Unit = {}

    fun openDrawer(search: Boolean = false, velocity: Float = 0f) {
        focusSearch = search
        scope.launch { drawer.animateTo(1f, bouncySpring, initialVelocity = velocity) }
    }

    fun closeDrawer(velocity: Float = 0f) {
        scope.launch {
            if (drawerPull != 0f) {
                drawer.snapTo((drawer.value - drawerPull / screenHeight).coerceIn(0f, 1f))
                drawerPull = 0f
            }
            drawer.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 320f), initialVelocity = velocity)
        }
    }

    /** Finger let go after pulling the drawer down: close it, or spring back. */
    fun releasePull(velocityY: Float, threshold: Float) {
        if (drawerPull > threshold || velocityY > 1800f) {
            closeDrawer()
        } else {
            scope.launch {
                animate(drawerPull, 0f, animationSpec = spring(0.6f, Spring.StiffnessMedium)) { v, _ -> drawerPull = v }
            }
        }
    }
}

@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    actions: LauncherActions,
    isDefaultHome: Boolean,
    homeEvents: Flow<Boolean>,
) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val appMap by vm.appMap.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val backdrop by vm.backdrop.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(IntSize(1, 1)) }
    val scope = rememberCoroutineScope()
    val ui = remember { LauncherUiState(scope) }
    ui.screenHeight = screen.height.toFloat().coerceAtLeast(1f)

    val style = GlassStyle(
        blur = settings.blur.dp,
        opacity = settings.opacity,
        tint = Color(settings.tint),
        tileSize = settings.tileSize.dp,
        translucentIcons = settings.translucentIcons,
        backdrop = backdrop,
        screen = screen,
    )

    // Pages shown in the pager. While dragging we add an empty page at the end so you can make a new one.
    val pages = layout?.pages ?: emptyList()
    val displayPages = if (ui.drag.active && (pages.isEmpty() || pages.last().any { it != null })) {
        pages + listOf(List(layout?.let { com.glass.launcher.data.capacity(pages.size, it.cols, it.rows) } ?: 0) { null })
    } else pages
    val pageCount by androidx.compose.runtime.rememberUpdatedState(displayPages.size.coerceAtLeast(1))
    val pager = androidx.compose.foundation.pager.rememberPagerState { pageCount }

    // Pressing the Home button.
    LaunchedEffect(Unit) {
        homeEvents.collect { alreadyOnTop ->
            var closedSomething = false
            if (ui.menu != null) { ui.menu = null; closedSomething = true }
            if (ui.openFolder != null) { ui.openFolder = null; closedSomething = true }
            if (ui.settingsOpen) { ui.settingsOpen = false; closedSomething = true }
            if (ui.drawer.targetValue > 0f || ui.drawer.value > 0f) { ui.closeDrawer(); closedSomething = true }
            if (!closedSomething && alreadyOnTop && pager.currentPage != 0) pager.animateScrollToPage(0)
        }
    }

    val doDrop: () -> Unit = {
        val drag = ui.drag
        val target = drag.hover
        val item = drag.item
        val src = drag.source
        if (target != null && item != null) {
            if (src == null && item is AppItem) {
                vm.edit { it.dropFromDrawer(item.key, target, drag.hoverCenter, vm::folderName) }
            } else if (src != null) {
                vm.edit { it.drop(src, target, drag.hoverCenter, vm::folderName) }
            }
        }
        drag.clear()
    }
    ui.pager = pager
    ui.onDrop = doDrop

    CompositionLocalProvider(LocalGlass provides style) {
        Box(Modifier.fillMaxSize().onSizeChanged { screen = it }) {
            HomeScreen(
                ui = ui,
                pager = pager,
                displayPages = displayPages,
                layout = layout,
                appMap = appMap,
                actions = actions,
                vm = vm,
                isDefaultHome = isDefaultHome,
                onDrop = doDrop,
            )
            AppDrawer(ui = ui, apps = apps, actions = actions, vm = vm, cols = settings.cols)
            FolderOverlay(ui = ui, layout = layout, appMap = appMap, actions = actions, vm = vm)
            SettingsScreen(
                ui = ui,
                settings = settings,
                onChange = vm::updateSettings,
                previewApps = apps.take(4),
                isDefaultHome = isDefaultHome,
                hasRealBlur = backdrop != null,
                actions = actions,
                onResetLayout = vm::resetLayout,
            )
            MenuOverlay(ui)
            DragOverlay(ui, appMap)
        }
    }
}

/** The long-press popup menu, drawn as a glass card next to the icon. */
@Composable
private fun MenuOverlay(ui: LauncherUiState) {
    val req = ui.menu ?: return
    BackHandler { ui.menu = null }
    val anim = remember(req) { Animatable(0f) }
    LaunchedEffect(req) { anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 450f)) }
    val density = LocalDensity.current
    var placedBelow by remember(req) { mutableStateOf(true) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f * anim.value.coerceIn(0f, 1f)))
            .pointerInput(req) { detectTapGestures { ui.menu = null } }
    ) {
        GlassSurface(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val margin = with(density) { 12.dp.roundToPx() }
                    val gap = with(density) { 10.dp.roundToPx() }
                    val x = (req.anchor.center.x - p.width / 2f).roundToInt()
                        .coerceIn(margin, max(margin, constraints.maxWidth - p.width - margin))
                    val below = req.anchor.bottom.roundToInt() + gap
                    val fitsBelow = below + p.height < constraints.maxHeight - margin * 4
                    placedBelow = fitsBelow
                    val y = if (fitsBelow) below else (req.anchor.top.roundToInt() - gap - p.height).coerceAtLeast(margin)
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
                }
                .graphicsLayer {
                    val s = 0.6f + 0.4f * anim.value
                    scaleX = s
                    scaleY = s
                    alpha = anim.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, if (placedBelow) 0f else 1f)
                }
                .width(250.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(26.dp),
        ) {
            Box(Modifier.matchParentSize().background(Color(0xFF1E1E26).copy(alpha = 0.62f)))
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    req.title,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
                req.entries.forEach { entry ->
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.18f)))
                    val c = if (entry.destructive) Color(0xFFFF7A7A) else Color.White
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                ui.menu = null
                                entry.onClick()
                            }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.label, color = c, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(entry.icon, contentDescription = null, tint = c, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** The icon that follows your finger while dragging. */
@Composable
private fun DragOverlay(ui: LauncherUiState, appMap: Map<String, AppInfo>) {
    val item = ui.drag.item ?: return
    val size = LocalGlass.current.tileSize
    val half = with(LocalDensity.current) { size.toPx() / 2f }
    Box(
        Modifier
            .offset {
                val p: Offset = ui.drag.pointer
                IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt())
            }
            .graphicsLayer {
                scaleX = 1.15f
                scaleY = 1.15f
            }
    ) {
        when (item) {
            is AppItem -> GlassAppTile(appMap[item.key]?.icon, size)
            is FolderItem -> GlassFolderTile(item.apps.mapNotNull { appMap[it]?.icon }, size)
        }
    }
}

/** Finds which cell or dock slot is under the finger. */
fun LauncherUiState.updateHover(pos: Offset, currentPage: Int) {
    var found: Loc? = null
    var center = false
    for ((loc, r) in cells) {
        if (loc is Loc.Cell && loc.page != currentPage) continue
        if (r.width <= 0f || !r.contains(pos)) continue
        found = loc
        center = (pos - r.center).getDistance() < minOf(r.width, r.height) * 0.30f
        break
    }
    drag.hover = found
    drag.hoverCenter = center
}
