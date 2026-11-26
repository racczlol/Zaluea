package com.glass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.FolderItem
import com.glass.launcher.data.HomeLayout
import com.glass.launcher.data.findFolder
import com.glass.launcher.data.removeFromFolder
import com.glass.launcher.data.renameFolder

@Composable
fun FolderOverlay(
    ui: LauncherUiState,
    layout: HomeLayout?,
    appMap: Map<String, AppInfo>,
    actions: LauncherActions,
    vm: LauncherViewModel,
) {
    val folder: FolderItem? = ui.openFolder?.let { id -> layout?.findFolder(id)?.second }
    // Remember the last folder so it can animate closed.
    var shown by remember { mutableStateOf<FolderItem?>(null) }
    if (folder != null) shown = folder
    // The folder disappeared (e.g. its last app was removed) -> close it.
    androidx.compose.runtime.SideEffect {
        if (ui.openFolder != null && folder == null && layout != null) ui.openFolder = null
    }

    BackHandler(enabled = folder != null && ui.menu == null) { ui.openFolder = null }

    AnimatedVisibility(
        visible = folder != null,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(200)),
    ) {
        val f = shown ?: return@AnimatedVisibility
        val focus = LocalFocusManager.current
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        focus.clearFocus()
                        ui.openFolder = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The home screen behind is already blurred; just dim it a little.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
            Column(
                Modifier
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 380f), initialScale = 0.55f),
                        exit = scaleOut(tween(180), targetScale = 0.7f),
                    )
                    .fillMaxWidth(0.86f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var name by remember(f.id) { mutableStateOf(f.name) }
                BasicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        vm.edit { l -> l.renameFolder(f.id, it) }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White, fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                GlassSurface(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
                    shape = RoundedCornerShape(40.dp),
                ) {
                    val tile = LocalGlass.current.tileSize
                    val haptics = LocalHapticFeedback.current
                    Column(
                        Modifier
                            .padding(vertical = 22.dp, horizontal = 8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        f.apps.chunked(3).forEach { rowApps ->
                            Row(Modifier.fillMaxWidth()) {
                                rowApps.forEach { key ->
                                    val app = appMap[key]
                                    Column(
                                        Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        if (app != null) {
                                            GlassAppTile(
                                                app.icon, tile,
                                                Modifier.launcherGestures(
                                                    onTap = { b -> actions.launch(app, b) },
                                                    onLongPress = { b ->
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        ui.menu = MenuRequest(
                                                            b, app.label, listOf(
                                                                MenuEntry("Remove from folder", Icons.Rounded.Close) {
                                                                    vm.edit { it.removeFromFolder(f.id, app.key) }
                                                                },
                                                                MenuEntry("Add to dock", Icons.Rounded.Star) { vm.addToDock(app.key) },
                                                                MenuEntry("App info", Icons.Rounded.Info) { actions.appInfo(app) },
                                                                MenuEntry("Uninstall", Icons.Rounded.Delete, destructive = true) {
                                                                    actions.uninstall(app)
                                                                },
                                                            )
                                                        )
                                                    },
                                                    dragEnabled = false,
                                                ),
                                            )
                                            ItemLabel(app.label, tile)
                                        }
                                    }
                                }
                                repeat(3 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Text(
                    "Hold an app for options",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.width(260.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
