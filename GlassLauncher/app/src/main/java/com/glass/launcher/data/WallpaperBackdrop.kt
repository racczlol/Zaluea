package com.glass.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The "real blur" needs a copy of the wallpaper picture, so each glass tile can blur the part
 * of the wallpaper that is behind it. Android only shares the wallpaper picture with apps that have
 * "All files access", so we support two ways:
 *  1. You pick a photo in Settings -> we set it as wallpaper AND keep a copy (no permission needed).
 *  2. You allow "All files access" -> we read the current wallpaper directly.
 * Without either, the glass is still see-through and frosted, just without blur.
 */
class WallpaperBackdrop(private val context: Context) {
    private val wm = WallpaperManager.getInstance(context)
    private val prefs = context.getSharedPreferences("glass_backdrop", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "glass_backdrop.jpg")

    suspend fun load(screenW: Int, screenH: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        // 1) Try reading the real wallpaper (works if "All files access" is allowed).
        try {
            @Suppress("MissingPermission")
            val d = wm.drawable
            if (d != null && d.intrinsicWidth > 1 && d.intrinsicHeight > 1) {
                val full = d.toBitmap(d.intrinsicWidth, d.intrinsicHeight)
                return@withContext centerCropSmall(full, screenW, screenH).asImageBitmap()
            }
        } catch (_: Throwable) {
        }
        // 2) Our saved copy, but only if the wallpaper hasn't been changed elsewhere since.
        try {
            val savedId = prefs.getInt("wallpaper_id", -1)
            if (file.exists() && savedId == wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)) {
                return@withContext BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            }
        } catch (_: Throwable) {
        }
        null
    }

    /** Sets the picked photo as the home screen wallpaper and keeps a small copy for blurring. */
    suspend fun setFromUri(uri: Uri, screenW: Int, screenH: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = max(screenW.toFloat() / info.size.width, screenH.toFloat() / info.size.height)
                if (s < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * s).roundToInt().coerceAtLeast(1),
                        (info.size.height * s).roundToInt().coerceAtLeast(1),
                    )
                }
            }
            val cropped = centerCrop(bmp, screenW, screenH)
            wm.setBitmap(cropped, null, true, WallpaperManager.FLAG_SYSTEM)
            val small = cropped.scale(screenW / 2, screenH / 2)
            file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            prefs.edit().putInt("wallpaper_id", wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)).apply()
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun centerCropSmall(src: Bitmap, w: Int, h: Int): Bitmap =
        centerCrop(src, w, h).scale(w / 2, h / 2)

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val cw = (w / s).roundToInt().coerceIn(1, src.width)
        val ch = (h / s).roundToInt().coerceIn(1, src.height)
        val x = (src.width - cw) / 2
        val y = (src.height - ch) / 2
        return Bitmap.createBitmap(src, x, y, cw, ch).scale(w, h)
    }
}
