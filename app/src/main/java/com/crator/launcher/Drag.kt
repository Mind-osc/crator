package com.crator.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
    data class Cell(val page: Int, val cell: Int, val center: Boolean = false) : DropTarget
    data class DockAt(val index: Int) : DropTarget
    data class Zone(val id: String) : DropTarget
    /** Πάνω σε εικονίδιο/φάκελο μέσα στο συρτάρι (key = app key ή "f:<id>") */
    data class DrawerItem(val key: String) : DropTarget
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
    /** Όσο είναι ανοιχτό το συρτάρι, οι στόχοι είναι τα εικονίδια του συρταριού */
    var drawerOpen = false
    val drawerSlots = HashMap<String, Rect>()
    /** Swipe με δεύτερο δάχτυλο ενώ κρατάς εικονίδιο */
    var onSecondSwipe: () -> Unit = {}
    var lastSource: DragSource? = null
    /** Η πηγή που πρέπει να μένει κρυφή (όσο σέρνεις ή προσγειώνεται) */
    val hiddenSource: DragSource? get() = if (active && moved) source else if (settle != null) lastSource else null
    var onDrop: () -> Unit = {}
    var dockSize = 0
    val grids = HashMap<Int, GridGeom>()
    var dockRect: Rect? = null
    val dockSlots = HashMap<Int, Rect>()
    val zones = HashMap<String, Rect>()

    val active get() = item != null

    /** Προεπισκόπηση παραμερισμού: σελίδα, θέση εισαγωγής, μετακινήσεις */
    class Reflow(val page: Int, val insertAt: Int, val moves: Map<Int, Int>)
    var reflow by mutableStateOf<Reflow?>(null)

    /** Το στοιχείο που μόλις τοποθετήθηκε (για το «σκάσιμο») */
    var recent by mutableStateOf<String?>(null)

    class Settle(val item: String, val from: Offset, val to: Offset, val swallow: Boolean)
    var settle by mutableStateOf<Settle?>(null)
    val origin get() = startPos

    /** Κέντρο προορισμού για να «πετάξει» εκεί το εικονίδιο */
    fun destination(t: DropTarget?, item: String): Offset {
        return when (t) {
            null -> startPos
            is DropTarget.Zone -> zones[t.id]?.center ?: pos
            is DropTarget.DrawerItem -> drawerSlots[t.key]?.center ?: pos
            is DropTarget.DockAt -> (dockSlots[t.index] ?: dockSlots[t.index - 1])?.center ?: dockRect?.center ?: pos
            is DropTarget.Cell -> {
                val g = grids[t.page] ?: return pos
                val (sx0, sy0) = Item.span(item)
                val sx = sx0.coerceAtMost(g.cols); val sy = sy0.coerceAtMost(g.rows)
                val c = (t.cell % g.cols).coerceAtMost(g.cols - sx); val r = (t.cell / g.cols).coerceAtMost(g.rows - sy)
                val cw = g.rect.width / g.cols; val ch = g.rect.height / g.rows
                Offset(g.rect.left + (c + sx / 2f) * cw, g.rect.top + (r + sy / 2f) * ch)
            }
        }
    }

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
        lastSource = source
        reflow = null
        item = null; source = null; moved = false; onTap = null; zones.clear()
        return t
    }

    fun target(): DropTarget? {
        val p = pos
        zones.forEach { (id, r) -> if (r.contains(p)) return DropTarget.Zone(id) }
        if (drawerOpen) {
            val it = item
            drawerSlots.forEach { (k, r) ->
                if (k != it && r.contains(p)) {
                    // μόνο στο «σώμα» του εικονιδίου, όχι στις άκρες
                    val fx = (p.x - r.left) / r.width; val fy = (p.y - r.top) / r.height
                    if (fx in 0.15f..0.85f && fy in 0.05f..0.75f) return DropTarget.DrawerItem(k)
                }
            }
            return null
        }
        dockRect?.let { r ->
            if (r.contains(p)) {
                val idx = (0 until dockSize).count { i -> dockSlots[i]?.let { it.center.x < p.x } ?: false }
                return DropTarget.DockAt(idx)
            }
        }
        val g = grids[currentPage] ?: return null
        if (!g.rect.contains(p)) return null
        val cw = g.rect.width / g.cols; val ch = g.rect.height / g.rows
        val fx = (p.x - g.rect.left) / cw; val fy = (p.y - g.rect.top) / ch
        val c = fx.toInt().coerceIn(0, g.cols - 1)
        val r = fy.toInt().coerceIn(0, g.rows - 1)
        val lx = fx - c; val ly = fy - r
        // μόνο το «καρδιά» του εικονιδίου = φάκελος, όλο το υπόλοιπο = παραμερισμός (σαν το ταμπλό)
        val center = lx in 0.34f..0.66f && ly in 0.2f..0.6f
        return DropTarget.Cell(currentPage, r * g.cols + c, center)
    }
}

/** Παρακολουθεί όλα τα αγγίγματα· όσο σέρνεις, τα «τρώει» για να μην κυλάει η σελίδα */
fun Modifier.dragObserver(drag: DragState): Modifier = this.then(
    Modifier.pointerInput(drag) {
        awaitPointerEventScope {
            var dragId: androidx.compose.ui.input.pointer.PointerId? = null
            var secondDy = 0f
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (!drag.active) {
                    // το δάχτυλο που θα κάνει το παρατεταμένο πάτημα
                    val ch = ev.changes.firstOrNull { it.pressed } ?: ev.changes.firstOrNull() ?: continue
                    drag.lastPointer = ch.position
                    dragId = ch.id
                    secondDy = 0f
                    continue
                }
                val main = ev.changes.firstOrNull { it.id == dragId }
                if (main == null || !main.pressed) {
                    ev.changes.forEach { it.consume() }
                    drag.onDrop()
                    continue
                }
                drag.lastPointer = main.position
                drag.update(main.position, viewConfiguration.touchSlop * 1.2f)
                // δεύτερο δάχτυλο: swipe κάτω → κλείνει το συρτάρι
                ev.changes.filter { it.id != dragId && it.pressed && it.previousPressed }.forEach { o ->
                    secondDy += o.position.y - o.previousPosition.y
                }
                if (ev.changes.none { it.id != dragId && it.pressed }) secondDy = 0f
                if (secondDy > 140f) { secondDy = 0f; drag.onSecondSwipe() }
                ev.changes.forEach { it.consume() }
            }
        }
    }
)

@Composable
fun DragLayer(drag: DragState, prefs: Prefs, byKey: Map<String, AppInfo>) {
    val settle = drag.settle
    val item = drag.item ?: settle?.item ?: return
    val density = LocalDensity.current
    val target = if (drag.active) drag.target() else null
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // απαλό «κλικ» όταν αλλάζει ο στόχος
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target != null && drag.moved) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    }

    // ανύψωση + κλίση ανάλογα με την ταχύτητα (σαν να το κρατάς στο χέρι)
    val lift = androidx.compose.runtime.remember { Animatable(1f) }
    val tilt = androidx.compose.runtime.remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(drag.active) {
        if (drag.active) {
            launch { lift.animateTo(1.14f, Motion.pop()) }
            var last = drag.pos
            while (isActive && drag.active) {
                withFrameNanos { }
                val dx = drag.pos.x - last.x
                last = drag.pos
                val t = (dx * 0.8f).coerceIn(-14f, 14f)
                tilt.snapTo(tilt.value + (t - tilt.value) * 0.16f)
            }
        }
    }
    // προσγείωση: πετάει στη θέση του και σβήνει
    val fly = androidx.compose.runtime.remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val flyP = androidx.compose.runtime.remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(settle) {
        if (settle != null) {
            fly.snapTo(settle.from); flyP.snapTo(0f)
            launch { tilt.animateTo(0f, Motion.snappy()) }
            launch { lift.animateTo(if (settle.swallow) 0.2f else 1f, Motion.lively()) }
            launch { flyP.animateTo(1f, Motion.snappy()) }
            fly.animateTo(settle.to, Motion.lively())
            drag.settle = null
        }
    }
    val pos = if (settle != null) fly.value else drag.pos
    val ghostAlpha = if (settle != null) (1f - flyP.value * flyP.value).coerceIn(0f, 1f) else 1f

    Box(Modifier.fillMaxSize()) {
        // Επισήμανση θέσης (απαλό γυάλινο «κελί»)
        if (target is DropTarget.Cell && drag.moved && !(target.center && Item.isApp(item) && drag.grids[target.page] != null && drag.reflow == null && hoverOccupied(drag, prefs, target))) {
            drag.grids[target.page]?.let { g ->
                val (sx0, sy0) = Item.span(item)
                val sx = sx0.coerceAtMost(g.cols); val sy = sy0.coerceAtMost(g.rows)
                val c = (target.cell % g.cols).coerceAtMost(g.cols - sx); val r = (target.cell / g.cols).coerceAtMost(g.rows - sy)
                val cw = g.rect.width / g.cols; val ch = g.rect.height / g.rows
                val ox by animateFloatAsState(g.rect.left + c * cw, Motion.lively(), label = "hx")
                val oy by animateFloatAsState(g.rect.top + r * ch, Motion.lively(), label = "hy")
                with(density) {
                    Box(
                        Modifier
                            .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                            .size((cw * sx).toDp(), (ch * sy).toDp())
                            .padding(6.dp)
                            .smokedGlass(RoundedCornerShape(24.dp), alpha = 0.22f)
                    )
                }
            }
        }
        // Ζώνες
        androidx.compose.animation.AnimatedVisibility(
            drag.active && drag.moved,
            enter = androidx.compose.animation.slideInVertically(Motion.lively()) { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(Motion.snappy()) { -it } + androidx.compose.animation.fadeOut()
        ) {
            Row(
                Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (drag.source != DragSource.Drawer) Zone("remove", "Αφαίρεση", Icons.Rounded.Close, drag, target, Modifier.weight(1f))
                if (Item.isApp(item)) {
                    Zone("info", "Πληροφορίες", Icons.Rounded.Info, drag, target, Modifier.weight(1f))
                    Zone("uninstall", "Απεγκατάσταση", Icons.Rounded.Delete, drag, target, Modifier.weight(1f))
                }
            }
        }
        // Υπόδειξη μέσα στο συρτάρι
        androidx.compose.animation.AnimatedVisibility(
            drag.active && drag.moved && drag.drawerOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(Motion.lively(Motion.sheet)) { it },
            exit = androidx.compose.animation.fadeOut()
        ) {
            Text(
                "Πάνω σε εικονίδιο για φάκελο · swipe κάτω με δεύτερο δάχτυλο για την αρχική",
                color = Smoke.pearl, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.navigationBarsPadding().padding(16.dp)
                    .smokedGlass(RoundedCornerShape(18.dp), alpha = 0.7f).padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        // Το στοιχείο που σέρνεις
        val size = prefs.iconSize.dp
        val sizePx = with(density) { size.toPx() }
        Box(
            Modifier
                .offset { IntOffset((pos.x - sizePx / 2).roundToInt(), (pos.y - sizePx * 0.75f).roundToInt()) }
                .size(size)
                .graphicsLayer {
                    scaleX = lift.value; scaleY = lift.value
                    rotationZ = tilt.value
                    alpha = ghostAlpha
                    shadowElevation = 18.dp.toPx() * (lift.value - 0.9f).coerceIn(0f, 1f) * 4f
                    shape = RoundedCornerShape(30)
                    clip = false
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                Item.isApp(item) -> byKey[item]?.let { AppIcon(it, size, prefs) }
                Item.isFolder(item) -> prefs.folders[Item.folderId(item)]?.let { FolderIcon(it, byKey, prefs, size) }
                else -> Box(Modifier.fillMaxSize().smokedGlass(RoundedCornerShape(30), alpha = 0.7f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Widgets, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Zone(id: String, label: String, icon: ImageVector, drag: DragState, target: DropTarget?, modifier: Modifier) {
    val hot = target == DropTarget.Zone(id)
    val s by animateFloatAsState(if (hot) 1.06f else 1f, Motion.pop(), label = "zone")
    Row(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .smokedGlass(RoundedCornerShape(20.dp), alpha = if (hot) 0.8f else 0.6f, tint = if (hot) Smoke.danger else Smoke.tint)
            .onGloballyPositioned { drag.zones[id] = it.boundsInWindow() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Smoke.pearl, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Smoke.pearl, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun hoverOccupied(drag: DragState, prefs: Prefs, t: DropTarget.Cell): Boolean {
    val ex = (drag.source as? DragSource.Home)?.takeIf { it.page == t.page }?.cell
    return prefs.occupied(t.page, ex)[t.cell] != null
}
