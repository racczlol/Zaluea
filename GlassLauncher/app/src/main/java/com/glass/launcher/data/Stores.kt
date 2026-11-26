package com.glass.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** All the look-and-feel options from the Settings screen. */
data class GlassSettings(
    val blur: Float = 18f,            // dp
    val opacity: Float = 0.16f,       // 0..1 how milky the glass is
    val tint: Long = 0xFFFFFFFF,      // ARGB glass colour
    val tileSize: Float = 62f,        // dp
    val cols: Int = 4,
    val rows: Int = 6,
    val translucentIcons: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("glass_settings", Context.MODE_PRIVATE)

    fun load(): GlassSettings {
        val d = GlassSettings()
        return GlassSettings(
            blur = prefs.getFloat("blur", d.blur),
            opacity = prefs.getFloat("opacity", d.opacity),
            tint = prefs.getLong("tint", d.tint),
            tileSize = prefs.getFloat("tileSize", d.tileSize),
            cols = prefs.getInt("cols", d.cols),
            rows = prefs.getInt("rows", d.rows),
            translucentIcons = prefs.getBoolean("translucentIcons", d.translucentIcons),
        )
    }

    fun save(s: GlassSettings) {
        prefs.edit()
            .putFloat("blur", s.blur)
            .putFloat("opacity", s.opacity)
            .putLong("tint", s.tint)
            .putFloat("tileSize", s.tileSize)
            .putInt("cols", s.cols)
            .putInt("rows", s.rows)
            .putBoolean("translucentIcons", s.translucentIcons)
            .apply()
    }
}

/** Saves the home screen arrangement as a small JSON text. */
class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("glass_layout", Context.MODE_PRIVATE)

    fun load(): HomeLayout? = try {
        prefs.getString("layout", null)?.let { parse(JSONObject(it)) }
    } catch (e: Exception) {
        null
    }

    fun save(layout: HomeLayout) {
        prefs.edit().putString("layout", toJson(layout).toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun toJson(l: HomeLayout) = JSONObject().apply {
        put("cols", l.cols)
        put("rows", l.rows)
        put("dock", JSONArray().apply { l.dock.forEach { put(it ?: JSONObject.NULL) } })
        put("known", JSONArray(l.known.toList()))
        put("pages", JSONArray().apply {
            l.pages.forEach { page ->
                put(JSONArray().apply {
                    page.forEach { item ->
                        put(
                            when (item) {
                                null -> JSONObject.NULL
                                is AppItem -> JSONObject().put("t", "a").put("k", item.key)
                                is FolderItem -> JSONObject().put("t", "f").put("id", item.id)
                                    .put("n", item.name).put("apps", JSONArray(item.apps))
                            }
                        )
                    }
                })
            }
        })
    }

    private fun parse(o: JSONObject): HomeLayout {
        fun JSONArray.strings() = (0 until length()).map { getString(it) }
        val dockArr = o.getJSONArray("dock")
        val pagesArr = o.getJSONArray("pages")
        return HomeLayout(
            cols = o.getInt("cols"),
            rows = o.getInt("rows"),
            dock = (0 until dockArr.length()).map { if (dockArr.isNull(it)) null else dockArr.getString(it) },
            known = o.getJSONArray("known").strings().toSet(),
            pages = (0 until pagesArr.length()).map { p ->
                val arr = pagesArr.getJSONArray(p)
                (0 until arr.length()).map { i ->
                    if (arr.isNull(i)) null else {
                        val it = arr.getJSONObject(i)
                        when (it.getString("t")) {
                            "f" -> FolderItem(it.getString("id"), it.getString("n"), it.getJSONArray("apps").strings())
                            else -> AppItem(it.getString("k"))
                        }
                    }
                }
            },
        ).trimmed()
    }
}
