package com.glass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.GlassSettings
import kotlin.math.roundToInt

private val tintChoices = listOf(
    0xFFFFFFFF, // clear white
    0xFFBFE3FF, // sky
    0xFFC8F5DD, // mint
    0xFFDCCBFF, // lavender
    0xFFFFC9E3, // pink
    0xFFFFDDBE, // peach
    0xFFFFF0B3, // gold
    0xFF2A2A33, // smoky dark
)

@Composable
fun SettingsScreen(
    ui: LauncherUiState,
    settings: GlassSettings,
    onChange: (GlassSettings) -> Unit,
    previewApps: List<AppInfo>,
    isDefaultHome: Boolean,
    hasRealBlur: Boolean,
    actions: LauncherActions,
    onResetLayout: () -> Unit,
) {
    BackHandler(enabled = ui.settingsOpen) { ui.settingsOpen = false }
    AnimatedVisibility(
        visible = ui.settingsOpen,
        enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it / 3 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 4 },
    ) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            GlassBackground(dim = 0.3f)
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Glass Settings", color = Color.White, fontSize = 30.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    GlassPillButton("Done") { ui.settingsOpen = false }
                }

                // Live preview of the glass look
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    previewApps.forEach { GlassAppTile(it.icon, LocalGlass.current.tileSize) }
                }

                if (!isDefaultHome) {
                    SettingsCard {
                        Text("Glass isn't your Home screen yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        GlassPillButton("Make Glass my Home screen", Modifier.fillMaxWidth()) { actions.requestDefaultHome() }
                    }
                }

                SettingsCard {
                    SectionTitle("Glass look")
                    SliderRow("Blur strength", "${settings.blur.roundToInt()}", settings.blur, 0f..40f) {
                        onChange(settings.copy(blur = it))
                    }
                    SliderRow("Glass opacity", "${(settings.opacity * 100).roundToInt()}%", settings.opacity, 0f..0.6f) {
                        onChange(settings.copy(opacity = it))
                    }
                    Text("Tint color", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        tintChoices.forEach { c ->
                            val selected = c == settings.tint
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(Color(c), CircleShape)
                                    .border(if (selected) 2.5.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.5f), CircleShape)
                                    .clickable(interactionSource = null, indication = null) {
                                        onChange(settings.copy(tint = c))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check, null, modifier = Modifier.size(18.dp),
                                        tint = if (c == 0xFF2A2A33) Color.White else Color.Black.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                    SwitchRow("See-through icons", settings.translucentIcons) {
                        onChange(settings.copy(translucentIcons = it))
                    }
                }

                SettingsCard {
                    SectionTitle("Layout")
                    SliderRow("Icon size", "${settings.tileSize.roundToInt()}", settings.tileSize, 44f..84f) {
                        onChange(settings.copy(tileSize = it))
                    }
                    StepperRow("Columns", settings.cols, 3..6) { onChange(settings.copy(cols = it)) }
                    StepperRow("Rows", settings.rows, 4..8) { onChange(settings.copy(rows = it)) }
                }

                SettingsCard {
                    SectionTitle("Wallpaper & blur")
                    Text(
                        if (hasRealBlur) "Real blur is ON - the glass blurs your wallpaper."
                        else "Real blur is OFF. Android only shares the wallpaper picture with apps in one of these two ways:",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassPillButton("Choose a wallpaper photo", Modifier.fillMaxWidth()) { actions.pickWallpaper() }
                    Text(
                        "Sets it as your Home wallpaper and turns on blur. No permission needed.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    if (!actions.hasAllFilesAccess()) {
                        GlassPillButton("Use my current wallpaper", Modifier.fillMaxWidth()) { actions.requestAllFilesAccess() }
                        Text(
                            "Opens a system page: switch on \"Allow access to manage all files\", then come back. Only used to read the wallpaper.",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        )
                    }
                    GlassPillButton("Samsung wallpaper settings", Modifier.fillMaxWidth()) { actions.openSystemWallpaper() }
                }

                SettingsCard {
                    SectionTitle("Reset")
                    var confirm by remember { mutableStateOf(false) }
                    GlassPillButton(
                        if (confirm) "Tap again to reset Home screen" else "Reset Home screen layout",
                        Modifier.fillMaxWidth(),
                        textColor = Color(0xFFFF8A8A),
                    ) {
                        if (confirm) {
                            onResetLayout()
                            confirm = false
                            ui.settingsOpen = false
                        } else confirm = true
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.12f)))
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp),
    )
}

private val glassSliderColors
    @Composable get() = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(valueText, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, colors = glassSliderColors)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        GlassPillButton("-", Modifier.width(48.dp)) { if (value > range.first) onChange(value - 1) }
        Text(
            "$value", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        GlassPillButton("+", Modifier.width(48.dp)) { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
fun GlassPillButton(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier
            .height(42.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        shape = CircleShape,
        shadow = false,
    ) {
        Text(
            text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
        )
    }
}
