@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.graphics.Rect
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Χρώματα ενεργειών */
private object Act {
    val info = Color(0xFF6FB6FF)
    val danger = Color(0xFFFF5A5F)
    val hide = Color(0xFFA9ACBD)
    val edit = Color(0xFFFFD479)
    val icon = Color(0xFFC792EA)
    val widgets = Color(0xFF7DE3C2)
    val home = Color(0xFF8C9BFF)
    val dock = Color(0xFF6FC3FF)
    val folder = Color(0xFFFFB86B)
}

/**
 * Popup δίπλα σε ένα σημείο: ανοίγει κάτω ή πάνω, αριστερά ή δεξιά, ανάλογα με το πού είναι το εικονίδιο.
 * centered = στο κέντρο του σημείου (για φακέλους).
 */
@Composable
fun AnchoredPopup(
    anchor: Offset,
    gap: Dp,
    onDismiss: () -> Unit,
    centered: Boolean = false,
    side: Boolean = false,
    corner: Dp = 26.dp,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val top = with(density) { 40.dp.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val below = anchor.y < hPx * 0.55f
        val left = anchor.x < wPx / 2f
        val origin = when {
            centered -> TransformOrigin.Center
            side -> TransformOrigin(if (left) 0f else 1f, 0.5f)
            else -> TransformOrigin(if (left) 0.12f else 0.88f, if (below) 0f else 1f)
        }
        Layout(
            content = {
                Box(
                    Modifier
                        .popupMotion(origin)
                        .shadow(30.dp, RoundedCornerShape(corner), ambientColor = Color.Black, spotColor = Color.Black)
                        .smokedGlass(RoundedCornerShape(corner), alpha = 0.9f, tint = Color(0xFF09090C))
                        .pointerInput(Unit) { detectTapGestures { } }
                ) { content() }
            }
        ) { ms, c ->
            val p = ms[0].measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = (c.maxWidth - 2 * margin).toInt().coerceAtLeast(0)))
            val x: Float
            val y: Float
            if (centered) {
                x = anchor.x - p.width / 2f
                y = anchor.y - p.height / 2f
            } else if (side) {
                // δίπλα στο εικονίδιο: δεξιά αν είναι αριστερά στην οθόνη, αλλιώς αριστερά
                x = if (left) anchor.x + gapPx else anchor.x - gapPx - p.width
                y = anchor.y - p.height / 2f
            } else {
                val nudge = with(density) { 22.dp.toPx() }
                x = if (left) anchor.x - nudge else anchor.x - p.width + nudge
                y = if (below) anchor.y + gapPx else anchor.y - gapPx - p.height
            }
            val cx = x.coerceIn(margin, (wPx - p.width - margin).coerceAtLeast(margin))
            val cy = y.coerceIn(top, (hPx - p.height - margin).coerceAtLeast(top))
            layout(c.maxWidth, c.maxHeight) { p.place(cx.roundToInt(), cy.roundToInt()) }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, tint: Color, name: String, badge: ImageVector? = null, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(0.6.dp, tint.copy(alpha = 0.35f), CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(ctx, name, Toast.LENGTH_SHORT).show()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, name, tint = tint, modifier = Modifier.size(21.dp))
        if (badge != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).size(14.dp).clip(CircleShape).background(tint),
                contentAlignment = Alignment.Center
            ) { Icon(badge, null, tint = Color.Black, modifier = Modifier.size(11.dp)) }
        }
    }
}

/** Μίνιμαλ γραμμή μενού: πολύ μικρό εικονίδιο αριστερά, μικρά γράμματα */
@Composable
private fun MenuLine(icon: ImageVector, tint: Color, name: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(9.dp))
        Text(name, color = Smoke.pearl, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TileGrid(tiles: List<@Composable () -> Unit>) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { it() } }
        }
    }
}

/** Πού άνοιξε το popup: για να δείξει τις σωστές ενέργειες */
class PopupCtx(val inFolder: String? = null, val fromDrawer: Boolean = false)

@Composable
fun AppPopup(
    app: AppInfo,
    anchor: Offset,
    ctx: PopupCtx,
    prefs: Prefs,
    repo: AppRepository,
    activity: MainActivity,
    hasWidgets: Boolean,
    onClose: () -> Unit,
    onAddHome: () -> Unit,
    onWidgets: () -> Unit
) {
    val shortcuts = remember(app.key) { repo.shortcuts(app) }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var folderPick by remember { mutableStateOf(false) }
    val gap = (prefs.iconSize * 0.75f).dp

    AnchoredPopup(anchor, gap, onClose, side = true, corner = 16.dp) {
        Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 140.dp, max = 190.dp).padding(4.dp)) {
            if (shortcuts.isNotEmpty() && !folderPick) {
                Column {
                    shortcuts.forEach { si ->
                        val ic = remember(si.id) { repo.shortcutIcon(si) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { repo.startShortcut(si); onClose() }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ic != null) androidx.compose.foundation.Image(ic, null, Modifier.size(14.dp)) else Spacer(Modifier.size(14.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(
                                (si.shortLabel ?: si.longLabel ?: "").toString(), color = Smoke.pearl, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 10.dp, vertical = 3.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
            }
            if (folderPick) {
                // επιλογή φακέλου συρταριού
                Column(Modifier.padding(8.dp)) {
                    prefs.drawerFolders.mapNotNull { id -> prefs.folders[id]?.let { id to it } }.forEach { (id, f) ->
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { prefs.addToFolder(id, app.key); onClose() }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = Act.folder, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(f.name, color = Smoke.pearl, fontSize = 14.sp)
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                            val id = System.currentTimeMillis().toString(36)
                            prefs.folders = prefs.folders + (id to Folder("Φάκελος", listOf(app.key)))
                            prefs.drawerFolders = prefs.drawerFolders + id
                            onClose()
                        }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, null, tint = Act.folder, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Νέος φάκελος", color = Smoke.pearl, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                val onHome = prefs.isOnHome(app.key)
                val inDock = prefs.isInDock(app.key)
                val hidden = app.key in prefs.hidden
                val tiles = buildList<@Composable () -> Unit> {
                    add { MenuLine(Icons.Rounded.Info, Act.info, "Πληροφορίες") { Actions.appInfo(activity, app); onClose() } }
                    add {
                        MenuLine(Icons.Rounded.Home, Act.home, if (onHome) "Αφαίρεση από αρχική" else "Στην αρχική") {
                            if (onHome) prefs.removeFromHome(app.key) else onAddHome(); onClose()
                        }
                    }
                    add {
                        MenuLine(Icons.Rounded.Dock, Act.dock, if (inDock) "Αφαίρεση από dock" else "Στο dock") {
                            if (inDock) prefs.removeFromDock(app.key) else prefs.addToDock(app.key); onClose()
                        }
                    }
                    if (ctx.inFolder != null) add {
                        MenuLine(Icons.Rounded.FolderOpen, Act.folder, "Έξω από τον φάκελο") {
                            val id = ctx.inFolder ?: return@MenuLine
                            if (id in prefs.drawerFolders) {
                                val f = prefs.folders[id]
                                if (f != null) {
                                    val rest = f.apps - app.key
                                    if (rest.isEmpty()) { prefs.folders = prefs.folders - id; prefs.drawerFolders = prefs.drawerFolders - id }
                                    else prefs.folders = prefs.folders + (id to f.copy(apps = rest))
                                }
                            } else {
                                prefs.removeFromFolder(id, app.key)
                                prefs.placeOnHome(app.key, 0)
                            }
                            onClose()
                        }
                    } else if (ctx.fromDrawer) add {
                        MenuLine(Icons.Rounded.CreateNewFolder, Act.folder, "Σε φάκελο συρταριού") { folderPick = true }
                    }
                    if (hasWidgets) add { MenuLine(Icons.Rounded.Widgets, Act.widgets, "Widgets") { onWidgets() } }
                    add { MenuLine(Icons.Rounded.Edit, Act.edit, "Μετονομασία") { renaming = true } }
                    add { MenuLine(Icons.Rounded.Palette, Act.icon, "Εικονίδιο") { picking = true } }
                    add {
                        MenuLine(if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, Act.hide, if (hidden) "Εμφάνιση" else "Απόκρυψη") {
                            prefs.hidden = if (hidden) prefs.hidden - app.key else prefs.hidden + app.key; onClose()
                        }
                    }
                    add { MenuLine(Icons.Rounded.Delete, Act.danger, "Απεγκατάσταση") { Actions.uninstall(activity, app); onClose() } }
                }
                Column { tiles.forEach { it() } }
            }
        }
    }
    if (renaming) RenameDialog(app.label, { renaming = false }) { name ->
        prefs.labels = if (name.isBlank() || name == app.originalLabel) prefs.labels - app.key else prefs.labels + (app.key to name)
        repo.load(); onClose()
    }
    if (picking) IconPicker(app, prefs, repo) { picking = false; onClose() }
}

private val FOLDER_SWATCHES = listOf(0, 0xFF2A2D40, 0xFF1F3A5F, 0xFF2F4F3A, 0xFF5A2A3C, 0xFF4A3A20, 0xFF3D2A5C).map { it.toInt() }

/** Μενού φακέλου: χρώμα + ενέργειες, μικρό και δίπλα στον φάκελο */
@Composable
fun FolderMenuPopup(id: String, anchor: Offset, prefs: Prefs, onOpen: () -> Unit, onClose: () -> Unit) {
    val f = prefs.folders[id] ?: run { onClose(); return }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val inDrawer = id in prefs.drawerFolders
    AnchoredPopup(anchor, (prefs.iconSize * 0.75f).dp, onClose) {
        Column(Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(4.dp, 2.dp, 4.dp, 10.dp)) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.8f, 1f) }))
                        .border(0.6.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable { picking = true }
                )
                FOLDER_SWATCHES.forEach { c ->
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (c == 0) Color(prefs.folderColor) else Color(c))
                            .border(if (c == f.color) 2.dp else 0.6.dp, Color.White.copy(alpha = if (c == f.color) 0.9f else 0.3f), CircleShape)
                            .clickable { prefs.folders = prefs.folders + (id to f.copy(color = c)) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(Icons.Rounded.FolderOpen, Act.folder, "Άνοιγμα") { onOpen() }
                ActionTile(Icons.Rounded.Edit, Act.edit, "Μετονομασία") { renaming = true }
                if (!inDrawer) ActionTile(Icons.Rounded.UnfoldMore, Act.home, "Αποσυναρμολόγηση") { prefs.ungroupFolder(id); onClose() }
                ActionTile(Icons.Rounded.Delete, Act.danger, "Διαγραφή φακέλου") {
                    prefs.replaceItem(Item.folder(id), null)
                    prefs.folders = prefs.folders - id
                    prefs.drawerFolders = prefs.drawerFolders - id
                    onClose()
                }
            }
        }
    }
    if (renaming) RenameDialog(f.name, { renaming = false }) { n ->
        prefs.folders = prefs.folders + (id to f.copy(name = n.ifBlank { "Φάκελος" }))
    }
    if (picking) ColorPickerDialog(if (f.color != 0) f.color else prefs.folderColor, { picking = false }) { c ->
        prefs.folders = prefs.folders + (id to f.copy(color = c))
    }
}

/** Μίνιμαλ παράθυρο φακέλου: το μέγεθος ακολουθεί πόσες εφαρμογές έχει */
@Composable
fun FolderMini(
    id: String,
    anchor: Offset,
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppLong: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val f = prefs.folders[id] ?: run { onClose(); return }
    var renaming by remember { mutableStateOf(false) }
    val apps = f.apps.mapNotNull { byKey[it] }
    val n = apps.size
    val cols = when {
        n <= 3 -> n.coerceAtLeast(1)
        n == 4 -> 2
        n <= 9 -> 3
        else -> prefs.folderCols.coerceAtLeast(4)
    }
    val icon = minOf(prefs.iconSize, 52).dp
    val cellW = icon + 22.dp
    AnchoredPopup(anchor, 0.dp, onClose, centered = true) {
        Column(Modifier.padding(14.dp, 12.dp, 14.dp, 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                f.name, color = Smoke.mist, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { renaming = true }.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            var i = 0
            apps.chunked(cols).forEach { row ->
                Row {
                    row.forEach { a ->
                        val idx = i++
                        StaggerIn(idx, Modifier.width(cellW)) {
                            AppCell(a, prefs, icon, true, { b -> onLaunch(a, b) }, { onAppLong(a) })
                        }
                    }
                }
            }
        }
    }
    if (renaming) RenameDialog(f.name, { renaming = false }) { nn ->
        prefs.folders = prefs.folders + (id to f.copy(name = nn.ifBlank { "Φάκελος" }))
    }
}
