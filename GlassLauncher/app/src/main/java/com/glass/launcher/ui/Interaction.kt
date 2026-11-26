package com.glass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.glass.launcher.data.HomeItem
import com.glass.launcher.data.Loc
import kotlinx.coroutines.launch

/** One row in the long-press popup menu. */
data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

data class MenuRequest(val anchor: Rect, val title: String, val entries: List<MenuEntry>)

/** What is being dragged right now, and where the finger is. */
class DragState {
    var item by mutableStateOf<HomeItem?>(null)
    /** Where it came from. Null means "dragged out of the app drawer". */
    var source by mutableStateOf<Loc?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<Loc?>(null)
    var hoverCenter by mutableStateOf(false)
    val active: Boolean get() = item != null

    fun clear() {
        item = null
        source = null
        hover = null
        hoverCenter = false
    }
}

private class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

/**
 * The one gesture handler used by every app icon:
 *  - tap -> [onTap]
 *  - hold -> [onLongPress] (shows the menu)
 *  - hold then move -> [onDragStart] / [onDrag] / [onDragEnd] (positions are in screen coordinates)
 * It also does the bouncy "squish" when you press.
 */
@Composable
fun Modifier.launcherGestures(
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    dragEnabled: Boolean = true,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val dStart by rememberUpdatedState(onDragStart)
    val dMove by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDragEnd)
    val canDrag by rememberUpdatedState(dragEnabled)
    val holder = remember { CoordsHolder() }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun bounds(): Rect = holder.coords?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
    fun press(down: Boolean) {
        scope.launch {
            if (down) scale.animateTo(0.86f, spring(stiffness = Spring.StiffnessMedium))
            else scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
        }
    }

    return this
        .onGloballyPositioned { holder.coords = it }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume() // so the page behind doesn't also see a long-press
                press(true)
                var up: PointerInputChange? = null
                val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    up = waitForUpOrCancellation()
                }
                if (finished != null) {
                    // Released (or cancelled) before the long-press time.
                    press(false)
                    up?.let {
                        it.consume()
                        tap(bounds())
                    }
                    return@awaitEachGesture
                }
                // Long press!
                press(false)
                long(bounds())
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val c = holder.coords
                    if (c == null || !c.isAttached) break
                    val rootPos = c.localToRoot(change.position)
                    if (!dragging && canDrag && (change.position - down.position).getDistance() > slop * 1.5f) {
                        dragging = true
                        dStart(rootPos)
                    }
                    if (dragging) dMove(rootPos)
                    change.consume()
                }
                if (dragging) dEnd()
            }
        }
}
