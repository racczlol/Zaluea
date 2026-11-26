package com.glass.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything a piece of glass needs to know about how to look. Set once at the top of the app. */
data class GlassStyle(
    val blur: Dp = 18.dp,
    val opacity: Float = 0.16f,
    val tint: Color = Color.White,
    val tileSize: Dp = 62.dp,
    val translucentIcons: Boolean = false,
    val backdrop: ImageBitmap? = null,
    val screen: IntSize = IntSize(1, 1),
)

val LocalGlass = compositionLocalOf { GlassStyle() }

/**
 * A piece of "liquid glass":
 *  - blurred copy of the wallpaper behind it (RenderEffect blur), when we have the wallpaper picture
 *  - frosted, see-through tinted fill
 *  - shiny highlight gradient across the top edge, plus a faint glow at the bottom
 *  - thin bright border that catches the light
 *  - soft drop shadow
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blur: Dp = LocalGlass.current.blur,
    fillAlpha: Float = LocalGlass.current.opacity,
    shadow: Boolean = true,
    border: Boolean = true,
    highlight: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val g = LocalGlass.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .then(
                if (shadow) Modifier.dropShadow(
                    shape,
                    Shadow(radius = 14.dp, color = Color.Black, offset = DpOffset(0.dp, 4.dp), alpha = 0.22f),
                ) else Modifier
            )
            .clip(shape)
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val bmp = g.backdrop
        if (bmp != null) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (blur > 0.dp) {
                            val r = blur.toPx()
                            renderEffect = BlurEffect(r, r, TileMode.Clamp)
                        }
                    }
            ) {
                drawBackdropPatch(bmp, origin, g.screen)
            }
        }
        // frosted fill
        Box(Modifier.matchParentSize().background(g.tint.copy(alpha = fillAlpha.coerceIn(0f, 1f))))
        if (highlight) {
            // shiny highlight across the top
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.35f to Color.White.copy(alpha = 0.05f),
                        0.75f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.10f),
                    )
                )
            )
        }
        if (border) {
            Box(
                Modifier.matchParentSize().border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.95f),
                        0.45f to Color.White.copy(alpha = 0.22f),
                        1f to Color.White.copy(alpha = 0.55f),
                    ),
                    shape = shape,
                )
            )
        }
        content()
    }
}

/** Draws the bit of wallpaper that is exactly behind this piece of glass. */
private fun DrawScope.drawBackdropPatch(bmp: ImageBitmap, origin: Offset, screen: IntSize) {
    val bw = bmp.width
    val bh = bmp.height
    if (bw <= 1 || bh <= 1 || screen.width <= 1) return
    // The wallpaper fills the screen, cropped in the centre.
    val s = max(screen.width.toFloat() / bw, screen.height.toFloat() / bh)
    val ox = (screen.width - bw * s) / 2f
    val oy = (screen.height - bh * s) / 2f
    val sx = ((origin.x - ox) / s).roundToInt().coerceIn(0, bw - 1)
    val sy = ((origin.y - oy) / s).roundToInt().coerceIn(0, bh - 1)
    val sw = ceil(size.width / s).toInt().coerceIn(1, bw - sx)
    val sh = ceil(size.height / s).toInt().coerceIn(1, bh - sy)
    drawImage(
        image = bmp,
        srcOffset = IntOffset(sx, sy),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Low,
    )
}

/** Full-screen frosted background used behind the app drawer, folders and settings. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, dim: Float = 0.25f) {
    val g = LocalGlass.current
    Box(modifier.fillMaxSize()) {
        if (g.backdrop != null) {
            GlassSurface(
                Modifier.fillMaxSize(),
                shape = RectangleShape,
                blur = (g.blur * 1.6f).coerceAtLeast(24.dp),
                fillAlpha = g.opacity * 0.6f,
                shadow = false,
                border = false,
                highlight = false,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim + 0.25f)))
        }
    }
}

fun tileShape(size: Dp) = RoundedCornerShape(size * 0.28f)

/** An app icon sitting in the middle of a glass tile. */
@Composable
fun GlassAppTile(icon: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassSurface(modifier.size(size), shape = tileShape(size)) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.70f)
                    .graphicsLayer { alpha = if (g.translucentIcons) 0.72f else 1f },
            )
        }
    }
}

/** A folder: glass tile showing up to 9 tiny icons. */
@Composable
fun GlassFolderTile(icons: List<ImageBitmap>, size: Dp, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassSurface(modifier.size(size), shape = tileShape(size), fillAlpha = (g.opacity + 0.08f)) {
        // Up to 4 apps: a roomy 2x2 preview. More: a 3x3 preview like iOS.
        val n = if (icons.size <= 4) 2 else 3
        val pad = size * 0.14f
        val gap = size * 0.06f
        val mini = (size - pad * 2 - gap * (n - 1)) / n
        val rowsUsed = (icons.size.coerceAtMost(n * n) + n - 1) / n
        val topShift = (mini + gap) * (n - rowsUsed) / 2
        Box(Modifier.fillMaxSize().padding(pad)) {
            icons.take(n * n).forEachIndexed { i, icon ->
                val r = i / n
                val c = i % n
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .padding(start = (mini + gap) * c, top = topShift + (mini + gap) * r)
                        .size(mini)
                        .graphicsLayer { alpha = if (g.translucentIcons) 0.72f else 1f },
                )
            }
        }
    }
}
