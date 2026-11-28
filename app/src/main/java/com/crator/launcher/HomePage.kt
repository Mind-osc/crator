@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.crator.launcher

import androidx.compose.material.icons.rounded.Edit

import androidx.compose.ui.zIndex

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

object BannerOnce { var shown = false }

@Composable
internal fun HomePage(
    index: Int,
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    drag: DragState,
    widgets: WidgetController,
    onLaunch: (AppInfo, android.graphics.Rect?) -> Unit,
    onItemLong: (Int, String) -> Unit,
    onFolder: (String, android.graphics.Rect?) -> Unit,
    onSearch: () -> Unit,
    onSettings: (String?) -> Unit,
    bottomPad: androidx.compose.ui.unit.Dp = 0.dp,
    editCell: Int? = null,
    onEditCell: (Int?) -> Unit = {},
    onRemoveWidget: (Int) -> Unit = {},
    editing: Boolean = false,
    onToggleEdit: () -> Unit = {}
) {
    val items = prefs.pages.getOrNull(index) ?: emptyMap()
    // στόχος φακέλου: εικονίδιο πάνω στο οποίο αιωρείται μια εφαρμογή
    val dragItem = drag.item
    val reflow = drag.reflow?.takeIf { it.page == index }?.moves ?: emptyMap()
    val hoverAnchor = if (drag.active && drag.moved && dragItem != null && Item.isApp(dragItem) && reflow.isEmpty()) {
        (drag.target() as? DropTarget.Cell)?.takeIf { it.page == index && it.center }?.let { t ->
            prefs.occupied(index)[t.cell]?.takeIf { a -> items[a]?.let { !Item.isSized(it) && it != dragItem } == true }
        }
    } else null
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        if (index == 0 && prefs.clockStyle != ClockStyle.Hidden) ClockHeader(prefs)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .onGloballyPositioned { drag.grids[index] = GridGeom(it.boundsInWindow(), prefs.cols, prefs.gridRows, prefs.fine) }
        ) {
            val cw = maxWidth / prefs.cols
            val ch = maxHeight / prefs.gridRows
            // Το εικονίδιο μικραίνει ώστε να χωράει πάντα και η ετικέτα στο κελί
            val labelH = if (prefs.showLabels) with(LocalDensity.current) { (prefs.labelSize * 1.4f * prefs.labelLines).sp.toDp() } + 5.dp else 0.dp
            // το εικονίδιο πιάνει μόνο όσο είναι (+λίγο αέρα), όχι στρογγυλεμένο προς τα πάνω,
            // ώστε να μπαίνει κλιμακωτά και σφιχτά πάνω από άλλη σειρά
            val baseIcon = minOf(prefs.iconSize.dp, cw - 10.dp)
            val needH = baseIcon + labelH + 6.dp
            val span = if (prefs.fine <= 1) 1 else kotlin.math.round(needH / ch).toInt().coerceIn(1, prefs.fine)
            androidx.compose.runtime.SideEffect { if (prefs.appSpanY != span) prefs.appSpanY = span }
            val fitIcon = if (prefs.fine > 1 && !prefs.showLabels) baseIcon.coerceAtLeast(24.dp)
                else minOf(prefs.iconSize.dp, ch * span - labelH - (if (prefs.fine > 1) 4.dp else 10.dp), cw - 10.dp).coerceAtLeast(24.dp)
            // ενδιάμεσες γραμμές: τελείες-οδηγοί όσο σέρνεις
            if (prefs.fine > 1 && drag.active && drag.moved && drag.currentPage == index) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val gw = size.width / prefs.cols; val gh = size.height / prefs.gridRows
                    for (rr in 0..prefs.gridRows) for (cc in 0..prefs.cols) {
                        drawCircle(Color.White.copy(alpha = if (rr % prefs.fine == 0) 0.35f else 0.18f), 1.5.dp.toPx(), androidx.compose.ui.geometry.Offset(cc * gw, rr * gh))
                    }
                }
            }
            items.forEach { (cell, item) -> key(item) {
                val (sx0, sy0) = prefs.spanOf(item)
                val sx = sx0.coerceAtMost(prefs.cols); val sy = sy0.coerceAtMost(prefs.gridRows)
                val shown = reflow[cell] ?: cell
                val r = shown / prefs.cols; val c = shown % prefs.cols
                val ox by animateDpAsState(cw * c, Motion.lively(Motion.drag), label = "ox")
                val oy by animateDpAsState(ch * r, Motion.lively(Motion.drag), label = "oy")
                if (r < prefs.gridRows) {
                val dragging = drag.hiddenSource == DragSource.Home(index, cell)
                val hovered = hoverAnchor == cell
                val hoverS by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1f else 0f, Motion.lively(), label = "hover")
                Box(
                    Modifier
                        .offset(ox, oy)
                        .size(cw * sx, ch * sy)
                        .graphicsLayer { alpha = if (dragging) 0f else 1f },
                    contentAlignment = Alignment.Center
                ) {
                    if (hoverS > 0.01f) {
                        // γυάλινο «δαχτυλίδι» φακέλου που ανοίγει
                        Box(
                            Modifier
                                .size(fitIcon * 1.5f)
                                .graphicsLayer { scaleX = 0.6f + 0.4f * hoverS; scaleY = 0.6f + 0.4f * hoverS; alpha = hoverS }
                                .smokedGlass(shapeFor(if (prefs.iconShape == IconShape.Original) IconShape.Circle else prefs.iconShape, prefs.iconCorner), alpha = 0.45f)
                                .align(Alignment.Center)
                        )
                    }
                    // edit: πάτημα σε widget → πλαίσιο μεγέθους (χωρίς παρατεταμένο πάτημα)
                    if (editing && Item.isSized(item)) Box(
                        Modifier.matchParentSize().zIndex(5f).padding(2.dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(prefs.widgetCorner.dp))
                            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onEditCell(cell) }
                    )
                    val pop = drag.recent == item
                    Box(if (Item.isSized(item)) Modifier.fillMaxSize() else Modifier.wrapContentSize(Alignment.Center, unbounded = true), contentAlignment = Alignment.Center) {
                    when {
                        Item.isWidget(item) -> SystemWidget(Item.widgetId(item), widgets, prefs, { onItemLong(cell, item) })
                        Item.isCrator(item) -> CratorWidget(item, prefs, onSearch, onSettings, { onItemLong(cell, item) })
                        Item.isFolder(item) -> prefs.folders[Item.folderId(item)]?.let { f ->
                            FolderCell(f, byKey, prefs, fitIcon, prefs.showLabels, { b -> onFolder(Item.folderId(item), b) }, { onItemLong(cell, item) },
                                Modifier.graphicsLayer { val s = 1f - 0.1f * hoverS; scaleX = s; scaleY = s }, pop = pop)
                        }
                        else -> byKey[item]?.let { app ->
                            PopIn(pop) {
                                AppCell(app, prefs, fitIcon, prefs.showLabels, { b -> onLaunch(app, b) }, { onItemLong(cell, item) },
                                    Modifier.graphicsLayer { val s = 1f - 0.12f * hoverS; scaleX = s; scaleY = s })
                            }
                        }
                    }
                    }
                }
                }
            } }
            // αλλαγή μεγέθους widget: πάτημα έξω = τέλος
            val editItem = editCell?.let { items[it] }?.takeIf { Item.isSized(it) }
            if (editCell != null && editItem != null) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onEditCell(null) } })
                WidgetResizeFrame(index, editCell, editItem, prefs, cw, ch,
                    onCell = { onEditCell(it) }, onDone = { onEditCell(null) }, onRemove = { onRemoveWidget(editCell) })
            }
        }
        Spacer(Modifier.height(bottomPad))
    }
    // κουμπί «Τέλος» — εμφανίζεται μόνο σε επεξεργασία (μπαίνεις από το παρατεταμένο πάτημα στη σελίδα)
    if (editing) Box(
        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 12.dp).size(34.dp)
            .smokedGlass(CircleShape, alpha = if (editing) 0.85f else 0.35f, tint = if (editing) MaterialTheme.colorScheme.primary else Smoke.graphite)
            .clickable(onClick = onToggleEdit),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, if (editing) "Τέλος" else "Επεξεργασία", tint = Color.White, modifier = Modifier.size(17.dp))
    }
    }
}
