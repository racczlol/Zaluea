package com.glass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.AppItem
import kotlin.math.max
import kotlin.math.roundToInt

private sealed interface DrawerRow {
    data class Header(val letter: String) : DrawerRow
    data class App(val app: AppInfo) : DrawerRow
}

@Composable
fun AppDrawer(
    ui: LauncherUiState,
    apps: List<AppInfo>,
    actions: LauncherActions,
    vm: LauncherViewModel,
    cols: Int,
) {
    // Only re-draw the whole drawer when these change, not on every animation frame.
    val closed by remember { derivedStateOf { ui.drawer.value <= 0.001f && ui.drawer.targetValue == 0f } }
    val mostlyOpen by remember { derivedStateOf { ui.drawer.value > 0.5f } }
    val fullyOpen by remember { derivedStateOf { ui.drawer.value > 0.9f } }
    val draggingFromHere = ui.drag.active && ui.drag.source == null
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    // When the drawer is fully closed: reset the search box.
    LaunchedEffect(closed, draggingFromHere) {
        if (closed && !draggingFromHere) {
            query = ""
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    if (closed && !draggingFromHere) return

    BackHandler(enabled = mostlyOpen && !ui.settingsOpen && ui.menu == null) { ui.closeDrawer() }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val closeThreshold = with(density) { 140.dp.toPx() }
    val h = ui.screenHeight

    val pullToClose = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (ui.drawerPull > 0f && available.y < 0f) {
                    val used = max(available.y, -ui.drawerPull)
                    ui.drawerPull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    ui.drawerPull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (ui.drawerPull > 0f) {
                    ui.releasePull(available.y, closeThreshold)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
    }
    val rows: List<DrawerRow> = remember(filtered) { filtered.map { DrawerRow.App(it) } }

    val launchFirst = {
        filtered.firstOrNull()?.let { actions.launch(it, androidx.compose.ui.geometry.Rect.Zero) }
    }

    Box(Modifier.fillMaxSize()) {
        // Frosted background. It stops at the screen edge, so the bounce never shows a gap.
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (max(0f, 1f - ui.drawer.value) * h + ui.drawerPull).roundToInt()) }
                .graphicsLayer { alpha = ui.drawer.value.coerceIn(0f, 1f) }
        ) { GlassBackground(dim = 0.22f) }

        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, ((1f - ui.drawer.value) * h + ui.drawerPull).roundToInt()) }
                .pointerInput(Unit) { detectTapGestures { } } // block taps from reaching the home screen
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header: handle + search + settings. Drag it down to close.
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> ui.drawerPull = max(0f, ui.drawerPull + d) },
                        onDragStopped = { v -> ui.releasePull(v, closeThreshold) },
                    )
                    .padding(horizontal = 16.dp)
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp, bottom = 12.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .background(Color.White.copy(alpha = 0.6f), CircleShape)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        query = query,
                        onQuery = { query = it },
                        requestFocus = ui.focusSearch && fullyOpen,
                        onGo = { launchFirst() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    GlassSurface(
                        Modifier
                            .size(46.dp)
                            .clickable(interactionSource = null, indication = null) { ui.settingsOpen = true },
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Rounded.Settings, "Settings", tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val cellW = maxWidth / cols
                val tile = tileSizeFor(LocalGlass.current.tileSize, cellW, 120.dp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize().nestedScroll(pullToClose),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        rows,
                        key = { r -> if (r is DrawerRow.App) r.app.key else "h:" + (r as DrawerRow.Header).letter },
                        span = { r -> GridItemSpan(if (r is DrawerRow.Header) maxLineSpan else 1) },
                    ) { r ->
                        when (r) {
                            is DrawerRow.Header -> Text(
                                r.letter,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
                            )
                            is DrawerRow.App -> DrawerAppCell(
                                app = r.app,
                                tile = tile,
                                onTap = { b -> actions.launch(r.app, b) },
                                onLongPress = { b ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    ui.menu = MenuRequest(b, r.app.label, drawerMenu(r.app, actions, vm))
                                },
                                onDragStart = { pos ->
                                    // Drag an app out of the drawer onto the home screen.
                                    ui.menu = null
                                    keyboard?.hide()
                                    ui.drag.item = AppItem(r.app.key)
                                    ui.drag.source = null
                                    ui.drag.pointer = pos
                                    ui.closeDrawer()
                                },
                                onDrag = { pos ->
                                    ui.drag.pointer = pos
                                    ui.updateHover(pos, ui.currentPage)
                                },
                                onDragEnd = ui.onDrop,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerAppCell(
    app: AppInfo,
    tile: androidx.compose.ui.unit.Dp,
    onTap: (androidx.compose.ui.geometry.Rect) -> Unit,
    onLongPress: (androidx.compose.ui.geometry.Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassAppTile(
            app.icon, tile,
            Modifier.launcherGestures(
                onTap = onTap,
                onLongPress = onLongPress,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            ),
        )
        ItemLabel(app.label, tile)
    }
}

private fun drawerMenu(app: AppInfo, actions: LauncherActions, vm: LauncherViewModel) = listOf(
    MenuEntry("Add to home", Icons.Rounded.Home) {
        if (vm.addToHome(app.key)) actions.toast("Added ${app.label} to Home")
        else actions.toast("${app.label} is already on your Home screen")
    },
    MenuEntry("Add to dock", Icons.Rounded.Star) { vm.addToDock(app.key) },
    MenuEntry("App info", Icons.Rounded.Info) { actions.appInfo(app) },
    MenuEntry("Uninstall", Icons.Rounded.Delete, destructive = true) { actions.uninstall(app) },
)

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    requestFocus: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if (requestFocus) runCatching { fr.requestFocus() } }
    GlassSurface(modifier.height(46.dp), shape = CircleShape) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search apps", color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, "Clear", tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(interactionSource = null, indication = null) { onQuery("") },
                )
            }
        }
    }
}
