package com.glass.launcher.data

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

/** One app that can be launched. [key] is "package/activity" and identifies it everywhere. */
data class AppInfo(
    val key: String,
    val packageName: String,
    val className: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystem: Boolean,
    val category: Int,
) {
    val component: ComponentName get() = ComponentName(packageName, className)
}

/** Something that sits in a home screen cell: an app or a folder. */
sealed interface HomeItem {
    val id: String
}

data class AppItem(val key: String) : HomeItem {
    override val id: String get() = "app:$key"
}

data class FolderItem(
    override val id: String,
    val name: String,
    val apps: List<String>,
) : HomeItem

/** A place on the home screen: a grid cell on a page, or a dock slot. */
sealed interface Loc {
    data class Cell(val page: Int, val index: Int) : Loc
    data class Dock(val index: Int) : Loc
}

/**
 * The whole home screen arrangement.
 * [pages] holds one list per page; each list has exactly one entry per cell (null = empty).
 * [known] remembers every app we've ever seen, so we can tell when something new was installed.
 */
data class HomeLayout(
    val cols: Int,
    val rows: Int,
    val pages: List<List<HomeItem?>>,
    val dock: List<String?>,
    val known: Set<String>,
)

const val DOCK_SIZE = 4

/** The clock widget takes this many rows at the top of the first page. */
const val WIDGET_ROWS = 2

fun pageRows(page: Int, rows: Int): Int =
    (rows - if (page == 0) WIDGET_ROWS else 0).coerceAtLeast(1)

fun capacity(page: Int, cols: Int, rows: Int): Int = cols * pageRows(page, rows)
