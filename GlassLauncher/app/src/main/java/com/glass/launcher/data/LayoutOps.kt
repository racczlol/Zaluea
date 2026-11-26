package com.glass.launcher.data

import java.util.UUID

/*
 * Pure functions that change a HomeLayout. Each returns a NEW layout (nothing is modified in place),
 * which keeps the UI simple and makes saving easy.
 */

fun HomeLayout.cap(page: Int) = capacity(page, cols, rows)

fun HomeLayout.itemAt(loc: Loc): HomeItem? = when (loc) {
    is Loc.Cell -> pages.getOrNull(loc.page)?.getOrNull(loc.index)
    is Loc.Dock -> dock.getOrNull(loc.index)?.let { AppItem(it) }
}

/** Puts [item] at [loc]. Returns null if that's not allowed (e.g. a folder in the dock). */
fun HomeLayout.withItem(loc: Loc, item: HomeItem?): HomeLayout? {
    return when (loc) {
        is Loc.Cell -> {
            val newPages = pages.map { it.toMutableList() }.toMutableList()
            while (newPages.size <= loc.page) newPages.add(MutableList(cap(newPages.size)) { null })
            val page = newPages[loc.page]
            if (loc.index !in page.indices) return null
            page[loc.index] = item
            copy(pages = newPages)
        }
        is Loc.Dock -> {
            if (loc.index !in 0 until DOCK_SIZE) return null
            val key = when (item) {
                null -> null
                is AppItem -> item.key
                is FolderItem -> return null
            }
            copy(dock = dock.toMutableList().also { it[loc.index] = key })
        }
    }
}

/** Makes every page the right size and removes empty pages at the end (page 1 always stays). */
fun HomeLayout.trimmed(): HomeLayout {
    val fixed = pages.mapIndexed { i, page ->
        val c = cap(i)
        when {
            page.size == c -> page
            page.size > c -> page.take(c)
            else -> page + List(c - page.size) { null }
        }
    }.toMutableList()
    if (fixed.isEmpty()) fixed.add(List(cap(0)) { null })
    while (fixed.size > 1 && fixed.last().all { it == null }) fixed.removeAt(fixed.lastIndex)
    val fixedDock = (dock + List(DOCK_SIZE) { null }).take(DOCK_SIZE)
    return copy(pages = fixed, dock = fixedDock)
}

/** True if the app is anywhere on the home screen: a cell, inside a folder, or in the dock. */
fun HomeLayout.isPlaced(key: String): Boolean =
    key in dock || pages.any { page ->
        page.any { it is AppItem && it.key == key || it is FolderItem && key in it.apps }
    }

/** Removes an app from every place (cells, folders, dock). Folders left with one app turn back into that app. */
fun HomeLayout.removeKey(key: String): HomeLayout {
    val newPages = pages.map { page ->
        page.map { item ->
            when (item) {
                is AppItem -> if (item.key == key) null else item
                is FolderItem -> {
                    val left = item.apps - key
                    when (left.size) {
                        0 -> null
                        1 -> AppItem(left[0])
                        else -> item.copy(apps = left)
                    }
                }
                null -> null
            }
        }
    }
    return copy(pages = newPages, dock = dock.map { if (it == key) null else it })
}

fun HomeLayout.firstEmptyCell(): Loc.Cell {
    pages.forEachIndexed { p, page ->
        val i = page.indexOfFirst { it == null }
        if (i >= 0) return Loc.Cell(p, i)
    }
    return Loc.Cell(pages.size, 0)
}

fun HomeLayout.addToHome(key: String): HomeLayout =
    if (isPlaced(key)) this else (withItem(firstEmptyCell(), AppItem(key)) ?: this)

/** Puts the app in the dock. If the dock is full, the last dock app moves back to the home screen. */
fun HomeLayout.addToDock(key: String): HomeLayout {
    if (key in dock) return this
    var base = removeKey(key)
    var slot = base.dock.indexOfFirst { it == null }
    if (slot < 0) slot = DOCK_SIZE - 1
    val displaced = base.dock[slot]
    base = base.copy(dock = base.dock.toMutableList().also { it[slot] = key })
    return if (displaced != null) base.addToHome(displaced) else base
}

fun HomeLayout.findFolder(id: String): Pair<Loc.Cell, FolderItem>? {
    pages.forEachIndexed { p, page ->
        page.forEachIndexed { i, item ->
            if (item is FolderItem && item.id == id) return Loc.Cell(p, i) to item
        }
    }
    return null
}

fun HomeLayout.renameFolder(id: String, name: String): HomeLayout {
    val (loc, folder) = findFolder(id) ?: return this
    return withItem(loc, folder.copy(name = name)) ?: this
}

/** Takes an app out of a folder and puts it in the first empty spot on the home screen. */
fun HomeLayout.removeFromFolder(id: String, key: String): HomeLayout {
    val (loc, folder) = findFolder(id) ?: return this
    val left = folder.apps - key
    val replacement: HomeItem? = when (left.size) {
        0 -> null
        1 -> AppItem(left[0])
        else -> folder.copy(apps = left)
    }
    val base = withItem(loc, replacement) ?: return this
    return base.withItem(base.firstEmptyCell(), AppItem(key)) ?: base
}

fun newFolder(name: String, apps: List<String>) =
    FolderItem(id = UUID.randomUUID().toString(), name = name, apps = apps)

/**
 * What happens when you drop a dragged item.
 * - Drop an app onto the middle of another app -> make a folder.
 * - Drop an app onto the middle of a folder -> add it to the folder.
 * - Anything else -> the two spots swap (dropping on an empty spot simply moves it).
 */
fun HomeLayout.drop(
    source: Loc,
    target: Loc,
    onCenter: Boolean,
    folderName: (String, String) -> String,
): HomeLayout? {
    if (source == target) return null
    val s = itemAt(source) ?: return null
    val t = itemAt(target)
    if (onCenter && s is AppItem && t is AppItem && target is Loc.Cell) {
        val folder = newFolder(folderName(t.key, s.key), listOf(t.key, s.key))
        return withItem(target, folder)?.withItem(source, null)
    }
    if (onCenter && s is AppItem && t is FolderItem) {
        return withItem(target, t.copy(apps = t.apps + s.key))?.withItem(source, null)
    }
    return withItem(target, s)?.withItem(source, t)
}

/** Dropping an app that was dragged out of the app drawer. */
fun HomeLayout.dropFromDrawer(
    key: String,
    target: Loc,
    onCenter: Boolean,
    folderName: (String, String) -> String,
): HomeLayout {
    val base = removeKey(key)
    val t = base.itemAt(target)
    return when {
        t == null -> base.withItem(target, AppItem(key))
        onCenter && t is AppItem && target is Loc.Cell ->
            base.withItem(target, newFolder(folderName(t.key, key), listOf(t.key, key)))
        onCenter && t is FolderItem -> base.withItem(target, t.copy(apps = t.apps + key))
        target is Loc.Dock -> {
            val displaced = (t as? AppItem)?.key
            base.withItem(target, AppItem(key))?.let { if (displaced != null) it.addToHome(displaced) else it }
        }
        else -> null
    } ?: base.addToHome(key)
}

/** Re-flows every item into a grid of a new size (used when you change rows/columns in Settings). */
fun HomeLayout.repack(newCols: Int, newRows: Int): HomeLayout {
    val items = pages.flatten().filterNotNull()
    val result = mutableListOf<List<HomeItem?>>()
    var i = 0
    var p = 0
    while (i < items.size || result.isEmpty()) {
        val c = capacity(p, newCols, newRows)
        val chunk = items.subList(i, minOf(items.size, i + c))
        result.add(chunk + List(c - chunk.size) { null })
        i += chunk.size
        p++
    }
    return copy(cols = newCols, rows = newRows, pages = result).trimmed()
}

/** Brings the layout up to date with what's installed: removes uninstalled apps, adds new ones. */
fun HomeLayout.syncWith(installedSorted: List<String>): HomeLayout {
    val installed = installedSorted.toSet()
    var result = this
    val placedKeys = pages.flatten().flatMap {
        when (it) {
            is AppItem -> listOf(it.key)
            is FolderItem -> it.apps
            null -> emptyList()
        }
    } + dock.filterNotNull()
    placedKeys.filter { it !in installed }.forEach { result = result.removeKey(it) }
    installedSorted.filter { it !in known }.forEach { result = result.addToHome(it) }
    return result.copy(known = installed).trimmed()
}

/** The very first layout: favourites in the dock, everything else A-Z on the pages. */
fun createDefaultLayout(
    cols: Int,
    rows: Int,
    installedSorted: List<String>,
    dockKeys: List<String>,
): HomeLayout {
    val dock = (dockKeys.distinct().take(DOCK_SIZE) + List(DOCK_SIZE) { null }).take(DOCK_SIZE)
    val homeItems: List<HomeItem?> = installedSorted.filter { it !in dock }.map { AppItem(it) }
    val start = HomeLayout(
        cols = cols,
        rows = rows,
        pages = listOf(homeItems),
        dock = dock,
        known = installedSorted.toSet(),
    )
    return start.repack(cols, rows)
}
