@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.crator.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun Dock(
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    drag: DragState,
    onLaunch: (AppInfo, android.graphics.Rect?) -> Unit,
    onFolder: (String, android.graphics.Rect?) -> Unit,
    onItemLong: (Int, String) -> Unit,
    onSearch: () -> Unit
) {
    val style = prefs.dockStyle
    val all = prefs.dock.withIndex().filter { (_, s) -> if (Item.isFolder(s)) prefs.folders.containsKey(Item.folderId(s)) else byKey.containsKey(s) }
    val items = if (style == DockStyle.Carousel) all else all.take(prefs.dockCount)
    drag.dockSize = prefs.dock.size
    val shape = RoundedCornerShape(if (style == DockStyle.Classic) 0.dp else prefs.dockCorner.dp)
    val bgColor = Color(prefs.dockColor).copy(alpha = prefs.dockOpacity / 100f)
    val size = prefs.dockIconSize.dp
    val outer = when (style) {
        DockStyle.Classic -> Modifier.fillMaxWidth()
        DockStyle.Island -> Modifier.padding(bottom = prefs.dockMargin.dp).wrapContentWidth()
        else -> Modifier.padding(start = 14.dp, end = 14.dp, bottom = prefs.dockMargin.dp).fillMaxWidth()
    }
    val bg = when (style) {
        DockStyle.Minimal, DockStyle.Arc -> Modifier
        DockStyle.Smoke -> Modifier.smokedGlass(shape, alpha = prefs.dockOpacity / 100f + 0.1f, tint = Color(prefs.dockColor))
        DockStyle.Glass -> Modifier
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))), shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
        else -> Modifier.background(bgColor, shape)
    }
    val shadow = if ((prefs.dockShadow || style == DockStyle.Smoke) && style != DockStyle.Minimal && style != DockStyle.Arc) Modifier.shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier
    val hot = drag.active && drag.moved && drag.target() is DropTarget.DockAt
    val dockScale by androidx.compose.animation.core.animateFloatAsState(if (hot) 1.03f else 1f, Motion.pop(), label = "dockHot")

    @Composable
    fun cell(i: Int, item: String, mod: Modifier) {
        val m = Modifier.onGloballyPositioned { drag.dockSlots[i] = it.boundsInWindow() }.then(mod)
        if (Item.isFolder(item)) {
            prefs.folders[Item.folderId(item)]?.let { f ->
                FolderCell(f, byKey, prefs, size, prefs.dockLabels, { b -> onFolder(Item.folderId(item), b) }, { onItemLong(i, item) }, m, pop = drag.recent == item)
            }
        } else byKey[item]?.let { app ->
            PopIn(drag.recent == item, modifier = m) {
                AppCell(app, prefs, size, prefs.dockLabels, { onLaunch(app, it) }, { onItemLong(i, item) })
            }
        }
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { drag.dockRect = it.boundsInWindow() }, contentAlignment = Alignment.Center) {
        Column(
            outer.graphicsLayer { scaleX = dockScale; scaleY = dockScale }.then(shadow).clip(shape).then(bg)
                .then(if (hot) Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), shape) else Modifier)
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            if (prefs.searchPos == SearchPos.InDock) SearchPill(prefs, Modifier.padding(6.dp, 0.dp, 6.dp, 8.dp), onClick = onSearch)
            if (items.isEmpty()) {
                Text("Σύρε εδώ εφαρμογές για το dock", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            } else if (style == DockStyle.Carousel) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    itemsIndexed(items, key = { _, it -> it.value + it.index }) { _, iv -> cell(iv.index, iv.value, Modifier.width(size + 12.dp)) }
                }
            } else {
                val arr = when {
                    style == DockStyle.Island -> Arrangement.spacedBy(10.dp)
                    prefs.dockAlign == DockAlign.Center -> Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                    prefs.dockAlign == DockAlign.Start -> Arrangement.spacedBy(10.dp, Alignment.Start)
                    prefs.dockAlign == DockAlign.End -> Arrangement.spacedBy(10.dp, Alignment.End)
                    else -> Arrangement.SpaceEvenly
                }
                Row(
                    if (style == DockStyle.Island) Modifier.padding(horizontal = 6.dp) else Modifier.fillMaxWidth(),
                    horizontalArrangement = arr, verticalAlignment = Alignment.Bottom
                ) {
                    val n = items.size
                    val dragged = drag.item
                    val gap = if (drag.active && drag.moved && dragged != null && !Item.isSized(dragged) &&
                        !(drag.source is DragSource.Dock && n <= 1)) (drag.target() as? DropTarget.DockAt)?.index else null
                    items.forEachIndexed { pos, iv -> key(iv.value) {
                        val half = (size + 8.dp) * 0.55f
                        val shiftX by animateDpAsState(
                            when { gap == null -> 0.dp; iv.index >= gap -> half; else -> -half },
                            Motion.lively(Motion.drag), label = "dockShift"
                        )
                        val lift = if (style == DockStyle.Arc && n > 1) {
                            val t = (pos - (n - 1) / 2f) / ((n - 1) / 2f)
                            (-22f * t * t).dp
                        } else 0.dp
                        val dragging = drag.hiddenSource == DragSource.Dock(iv.index)
                        cell(iv.index, iv.value, Modifier.width(size + 8.dp).offset(y = lift).graphicsLayer { alpha = if (dragging) 0f else 1f; translationX = shiftX.toPx() })
                    } }
                }
            }
        }
    }
}

@Composable
private fun AppSheet(
    app: AppInfo,
    prefs: Prefs,
    repo: AppRepository,
    activity: MainActivity,
    onClose: () -> Unit,
    onAddHome: () -> Unit,
    onWidgets: () -> Unit,
    hasWidgets: Boolean
) {
    val shortcuts = remember(app.key) { repo.shortcuts(app) }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    BottomCard(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52.dp, prefs)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(app.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(app.component.packageName, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        if (shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            shortcuts.forEach { si ->
                val ic = remember(si.id) { repo.shortcutIcon(si) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { repo.startShortcut(si); onClose() }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ic != null) androidx.compose.foundation.Image(ic, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text((si.shortLabel ?: si.longLabel ?: "").toString(), color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val onHome = prefs.isOnHome(app.key)
        val inDock = prefs.isInDock(app.key)
        val hidden = app.key in prefs.hidden
        ChipGrid(buildList<Pair<String, () -> Unit>> {
            add((if (onHome) "Αφαίρεση από αρχική" else "Στην αρχική") to { if (onHome) prefs.removeFromHome(app.key) else onAddHome(); onClose() })
            add((if (inDock) "Αφαίρεση από dock" else "Στο dock") to { if (inDock) prefs.removeFromDock(app.key) else prefs.addToDock(app.key); onClose() })
            if (hasWidgets) add("Widgets" to onWidgets)
            add("Μετονομασία" to { renaming = true })
            add("Εικονίδιο" to { picking = true })
            add((if (hidden) "Εμφάνιση" else "Απόκρυψη") to { prefs.hidden = if (hidden) prefs.hidden - app.key else prefs.hidden + app.key; onClose() })
            add("Πληροφορίες" to { Actions.appInfo(activity, app); onClose() })
            add("Απεγκατάσταση" to { Actions.uninstall(activity, app); onClose() })
        })
    }
    if (renaming) RenameDialog(app.label, { renaming = false }) { name ->
        prefs.labels = if (name.isBlank() || name == app.originalLabel) prefs.labels - app.key else prefs.labels + (app.key to name)
        repo.load(); onClose()
    }
    if (picking) IconPicker(app, prefs, repo) { picking = false; onClose() }
}

/** Μενού παρατεταμένου πατήματος σε κενό σημείο: μίνιμαλ μακρόστενη λίστα, εκεί που πάτησες */
@Composable
internal fun HomeMenu(
    prefs: Prefs,
    anchor: Offset,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onClose: () -> Unit,
    onEdit: () -> Unit = {}
) {
    AnchoredPopup(anchor, 10.dp, onClose, corner = 16.dp) {
        Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 150.dp, max = 200.dp).padding(4.dp)) {
            MenuLine(Icons.Rounded.Edit, Color(0xFFFF8FB8), "Επεξεργασία σελίδας", onEdit)
            MenuLine(Icons.Rounded.Widgets, Color(0xFFB37CFF), "Widgets", onWidgets)
            MenuLine(Icons.Rounded.Wallpaper, Color(0xFF34E0C6), "Ταπετσαρία", onWallpaper)
            MenuLine(Icons.Rounded.Tune, Color(0xFF4DA3FF), "Ρυθμίσεις", onSettings)
            Box(Modifier.padding(horizontal = 10.dp, vertical = 3.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
            MenuLine(Icons.Rounded.AddBox, Color(0xFFB9F35A), "Νέα σελίδα", onAddPage)
            MenuLine(Icons.Rounded.Remove, Color(0xFFFF5A6A), "Διαγραφή σελίδας", onRemovePage)
            MenuLine(
                if (prefs.lockLayout) Icons.Rounded.LockOpen else Icons.Rounded.Lock, Color(0xFFFFC23D),
                if (prefs.lockLayout) "Ξεκλείδωμα" else "Κλείδωμα"
            ) { prefs.lockLayout = !prefs.lockLayout; onClose() }
        }
    }
}

/**
 * Αλλαγή μεγέθους widget πάνω στην αρχική: λεπτές καμπύλες γωνίες, τις πιάνεις με το δάχτυλο
 * και το πλαίσιο ακολουθεί· στο άφημα κουμπώνει στο πλέγμα (αν χωράει).
 */
@Composable
internal fun WidgetResizeFrame(
    page: Int, cell: Int, item: String, prefs: Prefs, cw: androidx.compose.ui.unit.Dp, ch: androidx.compose.ui.unit.Dp,
    onCell: (Int) -> Unit, onDone: () -> Unit, onRemove: () -> Unit
) {
    val d = LocalDensity.current
    val cwPx = with(d) { cw.toPx() }; val chPx = with(d) { ch.toPx() }
    val (sx, sy) = Item.span(item)
    val r0 = cell / prefs.cols; val c0 = cell % prefs.cols
    var dl by remember(cell, item) { mutableStateOf(0f) }
    var dt by remember(cell, item) { mutableStateOf(0f) }
    var dr by remember(cell, item) { mutableStateOf(0f) }
    var db by remember(cell, item) { mutableStateOf(0f) }
    val L = c0 * cwPx + dl; val T = r0 * chPx + dt
    val R = (c0 + sx) * cwPx + dr; val B = (r0 + sy) * chPx + db
    // πού θα κουμπώσει — υπολογίζεται πάντα από τις ΤΡΕΧΟΥΣΕΣ τιμές (το gesture κρατάει παλιό closure)
    fun target(): IntArray {
        val l = c0 * cwPx + dl; val t = r0 * chPx + dt
        val rr = (c0 + sx) * cwPx + dr; val bb = (r0 + sy) * chPx + db
        val c = kotlin.math.round(l / cwPx).toInt().coerceIn(0, prefs.cols - 1)
        val r = kotlin.math.round(t / chPx).toInt().coerceIn(0, prefs.gridRows - 1)
        val w = (kotlin.math.round(rr / cwPx).toInt() - c).coerceIn(1, prefs.cols - c)
        val h = (kotlin.math.round(bb / chPx).toInt() - r).coerceIn(1, prefs.gridRows - r)
        val fit = prefs.fits(page, r * prefs.cols + c, w, h, exclude = cell)
        return intArrayOf(c, r, w, h, if (fit) 1 else 0)
    }
    val tg = target()
    val nc = tg[0]; val nr = tg[1]; val nsx = tg[2]; val nsy = tg[3]; val ok = tg[4] == 1
    fun commit() {
        val g = target()
        if (g[4] == 1 && (g[0] != c0 || g[1] != r0 || g[2] != sx || g[3] != sy)) {
            prefs.removeHomeAt(page, cell)
            val na = g[1] * prefs.cols + g[0]
            prefs.setHome(page, na, Item.resized(item, g[2], g[3]))
            onCell(na)
        }
        dl = 0f; dt = 0f; dr = 0f; db = 0f
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // στόχος κουμπώματος: απαλό γυάλινο πλαίσιο
    val tx by animateDpAsState(with(d) { (nc * cwPx).toDp() }, Motion.lively(Motion.drag), label = "tx")
    val ty by animateDpAsState(with(d) { (nr * chPx).toDp() }, Motion.lively(Motion.drag), label = "ty")
    val tw by animateDpAsState(with(d) { (nsx * cwPx).toDp() }, Motion.lively(Motion.drag), label = "tw")
    val th by animateDpAsState(with(d) { (nsy * chPx).toDp() }, Motion.lively(Motion.drag), label = "th")
    val busy = dl != 0f || dt != 0f || dr != 0f || db != 0f
    if (busy) Box(
        Modifier.offset(tx, ty).size(tw, th).padding(4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (ok) Color.White.copy(alpha = 0.10f) else Smoke.danger.copy(alpha = 0.16f))
    )
    // οι 4 καμπύλες γωνίες, εκεί που είναι το δάχτυλο
    val pad = with(d) { 3.dp.toPx() }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val arm = 22.dp.toPx(); val rad = 16.dp.toPx(); val sw = 2.4.dp.toPx()
        val col = Color.White.copy(alpha = 0.92f)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val l = L + pad; val t = T + pad; val r = R - pad; val b = B - pad
        fun corner(x: Float, y: Float, hx: Float, vy: Float) {
            // από το οριζόντιο χέρι, γύρω από την καμπύλη, στο κάθετο
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(x + hx * arm, y)
                lineTo(x + hx * rad, y)
                quadraticBezierTo(x, y, x, y + vy * rad)
                lineTo(x, y + vy * arm)
            }
            drawPath(p, col, style = st)
        }
        corner(l, t, 1f, 1f); corner(r, t, -1f, 1f); corner(l, b, 1f, -1f); corner(r, b, -1f, -1f)
    }
    // λαβές αφής 48dp σε κάθε γωνία
    val hs = 48.dp
    listOf(false to false, true to false, false to true, true to true).forEach { (right, bottom) ->
        val hx = with(d) { ((if (right) R else L)).toDp() } - hs / 2
        val hy = with(d) { ((if (bottom) B else T)).toDp() } - hs / 2
        Box(
            Modifier.offset(hx, hy).size(hs)
                .pointerInput(cell, item, right, bottom) {
                    detectDragGestures(
                        onDragStart = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                        onDragEnd = { commit() },
                        onDragCancel = { dl = 0f; dt = 0f; dr = 0f; db = 0f }
                    ) { ch2, a ->
                        ch2.consume()
                        if (right) dr += a.x else dl += a.x
                        if (bottom) db += a.y else dt += a.y
                    }
                }
        )
    }
    // μίνιμαλ μενού πάνω (ή κάτω) από το widget
    val above = r0 > 0
    val menuY = with(d) { (if (above) T else B).toDp() } + if (above) (-40).dp else 8.dp
    Row(
        Modifier.offset(with(d) { L.toDp() } + 6.dp, menuY)
            .shadow(16.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .smokedGlass(RoundedCornerShape(14.dp), alpha = 0.9f, tint = Color(0xFF09090C))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(IntrinsicSize.Max)) { MenuLine(Icons.Rounded.Delete, Smoke.danger, "Αφαίρεση", onRemove) }
        Box(Modifier.width(0.5.dp).height(16.dp).background(Color.White.copy(alpha = 0.1f)))
        Box(Modifier.width(IntrinsicSize.Max)) { MenuLine(Icons.Rounded.Check, Color(0xFF7DE3C2), "Τέλος", onDone) }
    }
}

@Composable
private fun SpanRow(label: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = minus), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = plus), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = Color.White)
        }
    }
}
