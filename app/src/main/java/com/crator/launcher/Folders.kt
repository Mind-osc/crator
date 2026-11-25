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

fun folderBg(f: Folder, prefs: Prefs): Color =
    (if (f.color != 0) Color(f.color) else Color(prefs.folderColor)).copy(alpha = prefs.folderOpacity / 100f)

@Composable
fun FolderIcon(f: Folder, byKey: Map<String, AppInfo>, prefs: Prefs, size: Dp, modifier: Modifier = Modifier) {
    val shape = shapeFor(if (prefs.iconShape == IconShape.Original) IconShape.Circle else prefs.iconShape, prefs.iconCorner)
    val apps = f.apps.mapNotNull { byKey[it] }
    Box(
        modifier.size(size)
            .then(if (prefs.iconShadow) Modifier.shadow(5.dp, shape, clip = false) else Modifier)
            .clip(shape).background(folderBg(f, prefs))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape),
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val badge = if (prefs.badges) f.apps.sumOf { k -> byKey[k]?.let { Badges.of(it.component.packageName) } ?: 0 } else 0
    Column(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
        ).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            FolderIcon(f, byKey, prefs, size)
            BadgeView(badge, prefs, size, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(f.name, style = labelStyle(prefs), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
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
            Modifier.padding(24.dp).fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background((if (f.color != 0) Color(f.color) else Color(prefs.folderColor)).copy(alpha = 0.94f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(32.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp)
        ) {
            Text(
                f.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { renaming = true }.padding(6.dp)
            )
            Spacer(Modifier.height(10.dp))
            apps.chunked(prefs.folderCols).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { a ->
                        AppCell(a, prefs, prefs.iconSize.dp, true, { b -> onLaunch(a, b) }, { onAppLong(a) }, Modifier.weight(1f))
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
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Color(0xF2161722))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp)
        ) { content() }
    }
}

@Composable
fun ChipGrid(chips: List<Pair<String, () -> Unit>>) {
    chips.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, fn) ->
                Box(
                    Modifier.weight(1f).padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickable { fn() }.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Color.White, fontSize = 14.sp) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
