package com.glass.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.AppItem
import com.glass.launcher.data.DOCK_SIZE
import com.glass.launcher.data.FolderItem
import com.glass.launcher.data.HomeItem
import com.glass.launcher.data.HomeLayout
import com.glass.launcher.data.Loc
import com.glass.launcher.data.WIDGET_ROWS
import com.glass.launcher.data.addToHome
import com.glass.launcher.data.pageRows
import com.glass.launcher.data.removeKey
import com.glass.launcher.data.withItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val labelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 6f),
)

@Composable
fun HomeScreen(
    ui: LauncherUiState,
    pager: PagerState,
    displayPages: List<List<HomeItem?>>,
    layout: HomeLayout?,
    appMap: Map<String, AppInfo>,
    actions: LauncherActions,
    vm: LauncherViewModel,
    isDefaultHome: Boolean,
    onDrop: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val g = LocalGlass.current
    // When a menu, folder or settings is open, the home screen behind it gets blurred.
    val overlayOpen = ui.menu != null || ui.openFolder != null || ui.settingsOpen
    val overlayBlur by animateFloatAsState(if (overlayOpen) 1f else 0f, spring(stiffness = 400f), label = "blur")
    val cols = layout?.cols ?: 4
    val rows = layout?.rows ?: 6

    // While dragging: hold the icon near the left/right edge to flip pages.
    LaunchedEffect(ui.drag.active) {
        if (!ui.drag.active) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        var held = 0L
        while (ui.drag.active) {
            delay(100)
            val x = ui.drag.pointer.x
            val dir = when {
                x < edge -> -1
                x > g.screen.width - edge -> 1
                else -> 0
            }
            if (dir == 0) { held = 0; continue }
            held += 100
            if (held >= 500) {
                val target = pager.currentPage + dir
                if (target in 0 until pager.pageCount) {
                    pager.animateScrollToPage(target)
                    ui.updateHover(ui.drag.pointer, pager.currentPage)
                }
                held = -400
            }
        }
    }

    val dragHandlers = DragHandlers(
        start = { item, loc, pos ->
            ui.menu = null
            ui.drag.item = item
            ui.drag.source = loc
            ui.drag.pointer = pos
            ui.updateHover(pos, pager.currentPage)
        },
        move = { pos ->
            ui.drag.pointer = pos
            ui.updateHover(pos, pager.currentPage)
        },
        end = onDrop,
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = ui.drawer.value.coerceIn(0f, 1f)
                val s = 1f - 0.07f * p - 0.03f * overlayBlur
                scaleX = s
                scaleY = s
                alpha = 1f - p
                val b = maxOf(p, overlayBlur) * 22.dp.toPx()
                renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
            }
            // Swipe up = app drawer. Swipe down = notifications.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        total = 0f
                    },
                    onVerticalDrag = { change, dy ->
                        if (ui.drag.active) return@detectVerticalDragGestures
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        total += dy
                        val p = (-total / ui.screenHeight * 1.4f).coerceIn(0f, 1f)
                        scope.launch { ui.drawer.snapTo(p) }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().y
                        val p = ui.drawer.value
                        when {
                            total > 80.dp.toPx() && p == 0f -> actions.expandNotifications()
                            p > 0.25f || (v < -900f && total < 0f) -> ui.openDrawer(velocity = -v / ui.screenHeight)
                            else -> ui.closeDrawer()
                        }
                    },
                    onDragCancel = { if (ui.drawer.value < 1f) ui.closeDrawer() },
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // While dragging, keep every page alive so the dragged icon never disappears.
                beyondViewportPageCount = if (ui.drag.active) pager.pageCount else 1,
                userScrollEnabled = !ui.drag.active,
            ) { page ->
                HomePage(
                    page = page,
                    items = displayPages.getOrNull(page) ?: emptyList(),
                    cols = cols,
                    rows = rows,
                    ui = ui,
                    appMap = appMap,
                    actions = actions,
                    vm = vm,
                    isDefaultHome = isDefaultHome,
                    drag = dragHandlers,
                    onEmptyLongPress = { pos ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        ui.menu = MenuRequest(
                            anchor = Rect(pos, 1f),
                            title = "Home Screen",
                            entries = listOf(
                                MenuEntry("Glass settings", Icons.Rounded.Settings) { ui.settingsOpen = true },
                                MenuEntry("Choose wallpaper", Icons.Rounded.Create) { actions.pickWallpaper() },
                            ),
                        )
                    },
                )
            }
            PageDots(count = pager.pageCount, current = pager.currentPage)
            Spacer(Modifier.height(10.dp))
            SearchPill(Modifier.align(Alignment.CenterHorizontally)) { ui.openDrawer(search = true) }
            Spacer(Modifier.height(12.dp))
            Dock(layout = layout, appMap = appMap, ui = ui, actions = actions, vm = vm, drag = dragHandlers)
            Spacer(Modifier.height(10.dp))
        }
    }
}

class DragHandlers(
    val start: (HomeItem, Loc, Offset) -> Unit,
    val move: (Offset) -> Unit,
    val end: () -> Unit,
)

@Composable
private fun HomePage(
    page: Int,
    items: List<HomeItem?>,
    cols: Int,
    rows: Int,
    ui: LauncherUiState,
    appMap: Map<String, AppInfo>,
    actions: LauncherActions,
    vm: LauncherViewModel,
    isDefaultHome: Boolean,
    drag: DragHandlers,
    onEmptyLongPress: (Offset) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInputWithRoot(onEmptyLongPress)
    ) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val tile = tileSizeFor(LocalGlass.current.tileSize, cellW, cellH)
        Column(Modifier.fillMaxSize()) {
            if (page == 0) {
                ClockWidget(
                    modifier = Modifier.fillMaxWidth().height(cellH * WIDGET_ROWS),
                    isDefaultHome = isDefaultHome,
                    onClick = actions::openClock,
                    onMakeDefault = actions::requestDefaultHome,
                    onLongPress = { onEmptyLongPress(it) },
                )
            }
            for (r in 0 until pageRows(page, rows)) {
                Row(Modifier.fillMaxWidth().height(cellH)) {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        HomeCell(
                            modifier = Modifier.width(cellW).fillMaxHeight(),
                            loc = Loc.Cell(page, index),
                            item = items.getOrNull(index),
                            tile = tile,
                            ui = ui,
                            appMap = appMap,
                            actions = actions,
                            vm = vm,
                            drag = drag,
                        )
                    }
                }
            }
        }
    }
}

fun tileSizeFor(wanted: Dp, cellW: Dp, cellH: Dp): Dp =
    minOf(wanted, cellW * 0.80f, cellH - 26.dp).coerceAtLeast(24.dp)

/** Long-press on an empty part of the page (reports the press position on screen). */
@Composable
private fun Modifier.pointerInputWithRoot(onLongPress: (Offset) -> Unit): Modifier {
    var origin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
        .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress(origin + it) }) }
}

@Composable
private fun HomeCell(
    modifier: Modifier,
    loc: Loc,
    item: HomeItem?,
    tile: Dp,
    ui: LauncherUiState,
    appMap: Map<String, AppInfo>,
    actions: LauncherActions,
    vm: LauncherViewModel,
    drag: DragHandlers,
    showLabel: Boolean = true,
) {
    DisposableEffect(loc) { onDispose { ui.cells.remove(loc) } }
    val hovered = ui.drag.active && ui.drag.hover == loc && ui.drag.source != loc
    Box(
        modifier.onGloballyPositioned { ui.cells[loc] = it.boundsInRoot() },
        contentAlignment = Alignment.Center,
    ) {
        if (item == null) {
            if (hovered) {
                GlassSurface(
                    Modifier.size(tile).graphicsLayer { alpha = 0.55f },
                    shape = tileShape(tile),
                    shadow = false,
                )
            }
        } else {
            HomeItemView(
                item = item,
                loc = loc,
                tile = tile,
                ui = ui,
                appMap = appMap,
                actions = actions,
                vm = vm,
                drag = drag,
                showLabel = showLabel,
                ghost = ui.drag.active && ui.drag.source == loc,
                highlight = hovered && ui.drag.hoverCenter,
            )
        }
    }
}

@Composable
private fun HomeItemView(
    item: HomeItem,
    loc: Loc,
    tile: Dp,
    ui: LauncherUiState,
    appMap: Map<String, AppInfo>,
    actions: LauncherActions,
    vm: LauncherViewModel,
    drag: DragHandlers,
    showLabel: Boolean,
    ghost: Boolean,
    highlight: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val grow by animateFloatAsState(
        if (highlight) 1.14f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "grow",
    )
    val label: String
    val gestures: Modifier
    when (item) {
        is AppItem -> {
            val app = appMap[item.key] ?: return
            label = app.label
            gestures = Modifier.launcherGestures(
                onTap = { b -> actions.launch(app, b) },
                onLongPress = { b ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    ui.menu = MenuRequest(b, app.label, appMenu(app, loc, actions, vm))
                },
                onDragStart = { p -> drag.start(item, loc, p) },
                onDrag = drag.move,
                onDragEnd = drag.end,
            )
            Column(
                Modifier.graphicsLayer { alpha = if (ghost) 0f else 1f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassAppTile(
                    app.icon, tile,
                    gestures.graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    },
                )
                if (showLabel) ItemLabel(label, tile)
            }
        }
        is FolderItem -> {
            label = item.name
            gestures = Modifier.launcherGestures(
                onTap = { ui.openFolder = item.id },
                onLongPress = { b ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    ui.menu = MenuRequest(
                        b, item.name, listOf(
                            MenuEntry("Open folder", Icons.Rounded.Add) { ui.openFolder = item.id },
                            MenuEntry("Remove folder", Icons.Rounded.Close, destructive = true) {
                                // Put every app from the folder back on the home screen.
                                vm.edit { l ->
                                    var r = l.withItem(loc, null) ?: l
                                    item.apps.forEach { r = r.addToHome(it) }
                                    r
                                }
                            },
                        )
                    )
                },
                onDragStart = { p -> drag.start(item, loc, p) },
                onDrag = drag.move,
                onDragEnd = drag.end,
            )
            Column(
                Modifier.graphicsLayer { alpha = if (ghost) 0f else 1f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassFolderTile(
                    item.apps.mapNotNull { appMap[it]?.icon }, tile,
                    gestures.graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    },
                )
                if (showLabel) ItemLabel(label, tile)
            }
        }
    }
}

@Composable
fun ItemLabel(text: String, tile: Dp) {
    Spacer(Modifier.height(5.dp))
    Text(
        text,
        style = labelStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(tile + 22.dp),
    )
}

/** The long-press menu for an app on the home screen or dock. */
fun appMenu(app: AppInfo, loc: Loc, actions: LauncherActions, vm: LauncherViewModel): List<MenuEntry> {
    val list = mutableListOf<MenuEntry>()
    if (loc is Loc.Dock) {
        list += MenuEntry("Move to home", Icons.Rounded.Home) {
            vm.edit { it.removeKey(app.key).addToHome(app.key) }
        }
    } else {
        list += MenuEntry("Add to dock", Icons.Rounded.Star) { vm.addToDock(app.key) }
    }
    list += MenuEntry("App info", Icons.Rounded.Info) { actions.appInfo(app) }
    list += MenuEntry(if (loc is Loc.Dock) "Remove from dock" else "Remove from home", Icons.Rounded.Close) {
        vm.removeFromHome(app.key)
        actions.toast("${app.label} is still in the app drawer")
    }
    list += MenuEntry("Uninstall", Icons.Rounded.Delete, destructive = true) { actions.uninstall(app) }
    return list
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().height(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            val color by animateColorAsState(
                if (selected) Color.White else Color.White.copy(alpha = 0.38f), label = "dot",
            )
            val w by animateDpAsState(
                if (selected) 16.dp else 7.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "dotw",
            )
            Box(
                Modifier
                    .padding(horizontal = 3.5.dp)
                    .size(width = w, height = 7.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun SearchPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(
        modifier
            .height(40.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        shape = CircleShape,
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Search", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Dock(
    layout: HomeLayout?,
    appMap: Map<String, AppInfo>,
    ui: LauncherUiState,
    actions: LauncherActions,
    vm: LauncherViewModel,
    drag: DragHandlers,
) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val tile = minOf(LocalGlass.current.tileSize, (screenW - 60.dp) / DOCK_SIZE * 0.80f)
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(tile + 26.dp),
        shape = RoundedCornerShape(tile * 0.5f),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until DOCK_SIZE) {
                val key = layout?.dock?.getOrNull(i)
                HomeCell(
                    modifier = Modifier.size(tile + 8.dp),
                    loc = Loc.Dock(i),
                    item = key?.let { AppItem(it) },
                    tile = tile,
                    ui = ui,
                    appMap = appMap,
                    actions = actions,
                    vm = vm,
                    drag = drag,
                    showLabel = false,
                )
            }
        }
    }
}

/** Big glass clock + date at the top of the first page. */
@Composable
private fun ClockWidget(
    modifier: Modifier,
    isDefaultHome: Boolean,
    onClick: () -> Unit,
    onMakeDefault: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", locale).format(Date(now))
    val date = SimpleDateFormat("EEEE, MMMM d", locale).format(Date(now))
    val textShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 10f)
    var clockOrigin by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }

    Box(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        GlassSurface(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { clockOrigin = it.boundsInRoot().topLeft }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress(clockOrigin + it) })
                },
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, shadow = textShadow,
                    ),
                )
                Text(
                    time,
                    style = TextStyle(
                        color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp, shadow = textShadow,
                    ),
                )
                if (!isDefaultHome) {
                    GlassSurface(
                        Modifier
                            .height(32.dp)
                            .clickable(interactionSource = null, indication = null, onClick = onMakeDefault),
                        shape = CircleShape,
                        shadow = false,
                    ) {
                        Text(
                            "Tap to make Glass your Home screen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
