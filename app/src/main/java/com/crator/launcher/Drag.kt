package com.crator.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

sealed interface DragSource {
    data class Home(val page: Int, val cell: Int) : DragSource
    data class Dock(val index: Int) : DragSource
    object Drawer : DragSource
    data class InFolder(val id: String) : DragSource
}

sealed interface DropTarget {
    data class Cell(val page: Int, val cell: Int) : DropTarget
    data class DockAt(val index: Int) : DropTarget
    data class Zone(val id: String) : DropTarget
}

class GridGeom(val rect: Rect, val cols: Int, val rows: Int)

class DragState {
    var item by mutableStateOf<String?>(null)
    var source by mutableStateOf<DragSource?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var moved by mutableStateOf(false)
    var lastPointer = Offset.Zero
    private var startPos = Offset.Zero
    private var onTap: (() -> Unit)? = null
    var currentPage = 0
    var onDrop: () -> Unit = {}
    var dockSize = 0
    val grids = HashMap<Int, GridGeom>()
    var dockRect: Rect? = null
    val dockSlots = HashMap<Int, Rect>()
    val zones = HashMap<String, Rect>()

    val active get() = item != null

    fun start(item: String, source: DragSource, onTapInstead: () -> Unit) {
        this.item = item; this.source = source
        startPos = lastPointer; pos = lastPointer
        moved = false; onTap = onTapInstead
    }

    fun update(p: Offset, slop: Float) {
        pos = p
        if (!moved && (p - startPos).getDistance() > slop) moved = true
    }

    fun end(): (() -> Unit)? {
        val t = if (!moved) onTap else null
        item = null; source = null; moved = false; onTap = null; zones.clear()
        return t
    }

    fun target(): DropTarget? {
        val p = pos
        zones.forEach { (id, r) -> if (r.contains(p)) return DropTarget.Zone(id) }
        dockRect?.let { r ->
            if (r.contains(p)) {
                val idx = (0 until dockSize).count { i -> dockSlots[i]?.let { it.center.x < p.x } ?: false }
                return DropTarget.DockAt(idx)
            }
        }
        val g = grids[currentPage] ?: return null
        if (!g.rect.contains(p)) return null
        val cw = g.rect.width / g.cols; val ch = g.rect.height / g.rows
        val c = ((p.x - g.rect.left) / cw).toInt().coerceIn(0, g.cols - 1)
        val r = ((p.y - g.rect.top) / ch).toInt().coerceIn(0, g.rows - 1)
        return DropTarget.Cell(currentPage, r * g.cols + c)
    }
}

/** Παρακολουθεί όλα τα αγγίγματα· όσο σέρνεις, τα «τρώει» για να μην κυλάει η σελίδα */
fun Modifier.dragObserver(drag: DragState): Modifier = this.then(
    Modifier.pointerInput(drag) {
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull() ?: continue
                drag.lastPointer = ch.position
                if (drag.active) {
                    drag.update(ch.position, viewConfiguration.touchSlop * 1.2f)
                    ev.changes.forEach { it.consume() }
                    if (ev.changes.none { it.pressed }) drag.onDrop()
                }
            }
        }
    }
)

@Composable
fun DragLayer(drag: DragState, prefs: Prefs, byKey: Map<String, AppInfo>) {
    val item = drag.item ?: return
    val density = LocalDensity.current
    val target = drag.target()
    Box(Modifier.fillMaxSize()) {
        // Επισήμανση θέσης
        if (target is DropTarget.Cell) {
            drag.grids[target.page]?.let { g ->
                val (sx0, sy0) = Item.span(item)
                val sx = sx0.coerceAtMost(g.cols); val sy = sy0.coerceAtMost(g.rows)
                var r = target.cell / g.cols; var c = target.cell % g.cols
                c = c.coerceAtMost(g.cols - sx); r = r.coerceAtMost(g.rows - sy)
                val cw = g.rect.width / g.cols; val ch = g.rect.height / g.rows
                with(density) {
                    Box(
                        Modifier
                            .offset { IntOffset((g.rect.left + c * cw).roundToInt(), (g.rect.top + r * ch).roundToInt()) }
                            .size((cw * sx).toDp(), (ch * sy).toDp())
                            .padding(4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    )
                }
            }
        }
        // Ζώνες πάνω
        if (drag.moved) {
            Row(
                Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val src = drag.source
                if (src != DragSource.Drawer) Zone("remove", "Αφαίρεση", Icons.Rounded.Close, drag, target, Modifier.weight(1f))
                if (Item.isApp(item)) {
                    Zone("info", "Πληροφορίες", Icons.Rounded.Info, drag, target, Modifier.weight(1f))
                    Zone("uninstall", "Απεγκατάσταση", Icons.Rounded.Delete, drag, target, Modifier.weight(1f))
                }
            }
        }
        // Το στοιχείο που σέρνεις
        val size = prefs.iconSize.dp * 1.12f
        val sizePx = with(density) { size.toPx() }
        val sc by animateFloatAsState(if (drag.moved) 1f else 1.08f, label = "dragScale")
        Box(
            Modifier
                .offset { IntOffset((drag.pos.x - sizePx / 2).roundToInt(), (drag.pos.y - sizePx * 0.8f).roundToInt()) }
                .size(size)
                .graphicsLayer { scaleX = sc; scaleY = sc }
                .shadow(14.dp, RoundedCornerShape(30)),
            contentAlignment = Alignment.Center
        ) {
            when {
                Item.isApp(item) -> byKey[item]?.let { AppIcon(it, size, prefs) }
                Item.isFolder(item) -> prefs.folders[Item.folderId(item)]?.let { FolderIcon(it, byKey, prefs, size) }
                else -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Widgets, null, tint = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun Zone(id: String, label: String, icon: ImageVector, drag: DragState, target: DropTarget?, modifier: Modifier) {
    val hot = target == DropTarget.Zone(id)
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (hot) Color(0xFFFF5A5F) else Color.Black.copy(alpha = 0.55f))
            .onGloballyPositioned { drag.zones[id] = it.boundsInWindow() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
