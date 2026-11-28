@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

fun folderBg(f: Folder, prefs: Prefs): Color =
    (if (f.color != 0) Color(f.color) else Color(prefs.folderColor)).copy(alpha = prefs.folderOpacity / 100f)

@Composable
fun FolderIcon(f: Folder, byKey: Map<String, AppInfo>, prefs: Prefs, size: Dp, modifier: Modifier = Modifier) {
    val shape = shapeFor(if (prefs.iconShape == IconShape.Original) IconShape.Circle else prefs.iconShape, prefs.iconCorner)
    val apps = f.apps.mapNotNull { byKey[it] }
    Box(
        modifier.size(size)
            .then(if (prefs.iconShadow) Modifier.shadow(6.dp, shape, clip = false) else Modifier)
            .smokedGlass(shape, alpha = prefs.folderOpacity / 100f, tint = if (f.color != 0) Color(f.color) else Color(prefs.folderColor)),
        contentAlignment = Alignment.Center
    ) {
        when (prefs.folderStyle) {
            FolderStyle.Grid -> Column(Modifier.padding(size * 0.14f), verticalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                        row.forEach { AppIcon(it, size * 0.33f, prefs) }
                    }
                }
            }
            FolderStyle.Stack -> Box(Modifier.size(size * 0.7f)) {
                apps.take(3).reversed().forEachIndexed { i, a ->
                    val o = (2 - i - (3 - apps.take(3).size)).coerceAtLeast(0)
                    AppIcon(a, size * 0.5f, prefs, Modifier.offset(x = size * 0.1f * o, y = size * 0.1f * o))
                }
            }
            FolderStyle.Single -> apps.firstOrNull()?.let { AppIcon(it, size * 0.62f, prefs) }
        }
    }
}

@Composable
fun FolderCell(
    f: Folder,
    byKey: Map<String, AppInfo>,
    prefs: Prefs,
    size: Dp,
    showLabel: Boolean,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    pop: Boolean = false,
    labelAlpha: () -> Float = { 1f }
) {
    val haptic = LocalHapticFeedback.current
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val badge = if (prefs.badges) f.apps.sumOf { k -> byKey[k]?.let { Badges.of(it.component.packageName, prefs) } ?: 0 } else 0
    Column(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = { onClick(bounds[0]) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
        ).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PopIn(pop, ring = true, modifier = Modifier.onGloballyPositioned { c ->
            val r = c.boundsInWindow(); bounds[0] = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        }) {
            Box {
                FolderIcon(f, byKey, prefs, size)
                BadgeView(badge, prefs, size, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(f.name, style = labelStyle(prefs), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = labelAlpha() })
        }
    }
}

@Composable
fun FolderPopup(
    id: String,
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppLong: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val f = prefs.folders[id] ?: run { onClose(); return }
    var renaming by remember { mutableStateOf(false) }
    val apps = f.apps.mapNotNull { byKey[it] }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(22.dp).fillMaxWidth()
                .shadow(40.dp, RoundedCornerShape(36.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .smokedGlass(RoundedCornerShape(36.dp), alpha = 0.74f, tint = if (f.color != 0) Color(f.color) else Color(prefs.folderColor))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
        ) {
            Text(
                f.name, color = Smoke.pearl, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.3).sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { renaming = true }.padding(6.dp)
            )
            Spacer(Modifier.height(10.dp))
            apps.chunked(prefs.folderCols).forEachIndexed { ri, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { ci, a ->
                        StaggerIn(ri * prefs.folderCols + ci, Modifier.weight(1f)) {
                            AppCell(a, prefs, prefs.iconSize.dp, true, { b -> onLaunch(a, b) }, { onAppLong(a) })
                        }
                    }
                    repeat(prefs.folderCols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (renaming) RenameDialog(f.name, { renaming = false }) { n ->
        prefs.folders = prefs.folders + (id to f.copy(name = n.ifBlank { "Φάκελος" }))
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Μετονομασία") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(name.trim()); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}

private val FOLDER_SWATCHES = listOf(0, 0xFF2A2D40, 0xFF1F3A5F, 0xFF2F4F3A, 0xFF5A2A3C, 0xFF4A3A20, 0xFF3D2A5C, 0xFF111111, 0xFFFFFFFF).map { it.toInt() }

/** Μενού φακέλου (παρατεταμένο πάτημα χωρίς σύρσιμο) */
@Composable
fun FolderSheet(id: String, prefs: Prefs, byKey: Map<String, AppInfo>, onOpen: () -> Unit, onClose: () -> Unit) {
    val f = prefs.folders[id] ?: run { onClose(); return }
    var renaming by remember { mutableStateOf(false) }
    BottomCard(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(f, byKey, prefs, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(f.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${f.apps.size} εφαρμογές", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Χρώμα", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FOLDER_SWATCHES) { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(if (c == 0) Color(prefs.folderColor) else Color(c))
                        .border(if (c == f.color) 3.dp else 1.dp, Color.White.copy(alpha = if (c == f.color) 0.9f else 0.25f), CircleShape)
                        .clickable { prefs.folders = prefs.folders + (id to f.copy(color = c)) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ChipGrid(
            listOf(
                "Άνοιγμα" to { onOpen() },
                "Μετονομασία" to { renaming = true },
                "Αποσυναρμολόγηση" to { prefs.ungroupFolder(id); onClose() },
                "Διαγραφή φακέλου" to { prefs.replaceItem(Item.folder(id), null); prefs.folders = prefs.folders - id; onClose() }
            )
        )
    }
    if (renaming) RenameDialog(f.name, { renaming = false }) { n ->
        prefs.folders = prefs.folders + (id to f.copy(name = n.ifBlank { "Φάκελος" }))
    }
}

@Composable
fun BottomCard(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(10.dp).fillMaxWidth()
                .sheetMotion()
                .shadow(36.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .smokedGlass(RoundedCornerShape(28.dp), alpha = 0.86f, tint = Color(0xFF060608))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(10.dp)
        ) { content() }
    }
}

@Composable
fun ChipGrid(chips: List<Pair<String, () -> Unit>>) {
    chips.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, fn) ->
                Box(
                    Modifier.weight(1f).padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                        .clickable { fn() }.padding(vertical = 14.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Smoke.pearl, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Εμφάνιση στοιχείων με μικρή καθυστέρηση το ένα μετά το άλλο */
@Composable
fun StaggerIn(index: Int, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val a = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!enabled) return@LaunchedEffect
        delay((index * 22L / Motion.speed).toLong().coerceAtMost(260))
        a.animateTo(1f, Motion.lively())
    }
    Box(modifier.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        val s = 0.82f + 0.18f * a.value
        scaleX = s; scaleY = s
        translationY = (1f - a.value) * 18.dp.toPx()
    }, contentAlignment = Alignment.Center) { content() }
}
