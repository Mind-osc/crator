@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Στοιχεία του συρταριού: φάκελος ή εφαρμογή */
private sealed interface DrawerEntry {
    val key: String
    class F(val id: String, val folder: Folder) : DrawerEntry { override val key = "f:$id" }
    class A(val app: AppInfo) : DrawerEntry { override val key = app.key }
}

/** Λεπτή μπάρα αναζήτησης (ίδιο στυλ σε αρχική και συρτάρι) */
@Composable
fun SearchField(
    prefs: Prefs,
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    focus: FocusRequester?,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val h = prefs.searchHeight.dp
    Row(
        modifier.fillMaxWidth().height(h)
            .smokedGlass(RoundedCornerShape(h / 2), alpha = prefs.searchOpacity / 100f, tint = Color(prefs.searchColor))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color(prefs.searchTextColor), modifier = Modifier.size((h.value * 0.45f).dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = Color(prefs.searchTextColor).copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                query, onQuery, singleLine = true,
                textStyle = TextStyle(color = Smoke.pearl, fontSize = 14.sp, fontFamily = Manrope),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            )
        }
        if (query.isNotEmpty()) Icon(
            Icons.Rounded.Close, null, tint = Color(prefs.searchTextColor),
            modifier = Modifier.size((h.value * 0.45f).dp).clip(RoundedCornerShape(50)).clickable { onQuery("") }
        )
    }
}

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    prefs: Prefs,
    seed: String,
    searchMode: Boolean,
    byKey: Map<String, AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLong: (AppInfo) -> Unit,
    onFolder: (String, Rect?) -> Unit,
    onFolderLong: (String) -> Unit,
    onClose: () -> Unit,
    drag: DragState? = null,
    onPull: ((Float) -> Unit)? = null,
    onPullEnd: ((Float) -> Unit)? = null
) {
    if (searchMode) { SearchScreen(apps, prefs, seed, onLaunch, onLong, onClose); return }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { drag?.drawerSlots?.clear() } }
    val pullCb by rememberUpdatedState(onPull)
    val pullEndCb by rememberUpdatedState(onPullEnd)
    val hoverKey = drag?.let { d -> if (d.active && d.moved) (d.target() as? DropTarget.DrawerItem)?.key else null }
    val dragKey = drag?.let { d -> if (d.active && d.moved) d.item else null }
    var q by remember { mutableStateOf(seed) }
    val focus = remember { FocusRequester() }
    val openedAt = remember { System.currentTimeMillis() }
    val nq = q.norm().trim()
    val showSearch = prefs.drawerSearch || searchMode
    val folders = prefs.drawerFolders.mapNotNull { id -> prefs.folders[id]?.let { id to it } }
    val inFolders = folders.flatMap { it.second.apps }.toSet()
    val list: List<DrawerEntry> = remember(apps, nq, prefs.hidden, folders) {
        if (nq.isNotEmpty()) {
            apps.filter { it.key !in prefs.hidden && (it.label.norm().contains(nq) || it.component.packageName.contains(nq)) }
                .sortedBy { if (it.label.norm().startsWith(nq)) 0 else 1 }
                .map { DrawerEntry.A(it) }
        } else {
            folders.sortedBy { it.second.name.norm() }.map { DrawerEntry.F(it.first, it.second) } +
                apps.filter { it.key !in prefs.hidden && it.key !in inFolders }.map { DrawerEntry.A(it) }
        }
    }
    val close by rememberUpdatedState(onClose)
    val conn = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0) {
                    acc += available.y
                    if (acc > 260f) { acc = 0f; close() }
                } else if (available.y < 0) acc = 0f
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { acc = 0f; return Velocity.Zero }
        }
    }
    LaunchedEffect(Unit) { if (showSearch && (prefs.drawerKeyboard || searchMode)) runCatching { focus.requestFocus() } }

    val search: @Composable () -> Unit = {
        if (showSearch) SearchField(
            prefs, q, { q = it }, "Αναζήτηση σε ${apps.size} εφαρμογές", focus,
            onGo = { (list.firstOrNull() as? DrawerEntry.A)?.let { onLaunch(it.app, null) } },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }

    // Swipe κάτω για κλείσιμο: το συρτάρι ακολουθεί το δάχτυλο όταν η λίστα είναι στην κορυφή
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun atTop(): Boolean = if (prefs.drawerStyle == DrawerStyle.Grid)
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
    else listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = pull.value
                alpha = 1f - (pull.value / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.6f
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (drag?.active == true || !atTop()) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val vt = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    vt.addPosition(down.uptimeMillis, down.position)
                    var total = 0f
                    var pulled = 0f
                    var pulling = false
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (drag?.active == true) { pulling = false; break }
                        if (!ch.pressed) break
                        val dy = ch.position.y - ch.previousPosition.y
                        vt.addPosition(ch.uptimeMillis, ch.position)
                        if (!pulling) {
                            total += dy
                            if (total > slop) { pulling = true; pulled = 0f }
                            else if (total < -slop) break
                        } else pulled += dy
                        if (pulling) {
                            ch.consume()
                            // 1:1: το συρτάρι κατεβαίνει ακριβώς όσο το δάχτυλο (ένα σημείο ελέγχου στη ρίζα)
                            val cb = pullCb
                            if (cb != null) cb(pulled.coerceAtLeast(-60f))
                            else scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) }
                        }
                    }
                    if (pulling) {
                        val vel = vt.calculateVelocity().y
                        val endCb = pullEndCb
                        if (endCb != null) endCb(vel)
                        else if (pull.value > size.height * 0.22f || vel > 1800f) close()
                        else scope.launch { pull.animateTo(0f, Motion.lively(Motion.overlay)) }
                    }
                }
            }
            .background(Color(prefs.drawerColor).copy(alpha = prefs.drawerOpacity / 100f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Box(
            Modifier.fillMaxWidth().height(26.dp).pointerInput(Unit) {
                var t = 0f
                detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t > 80) close() }) { c, dy -> t += dy; c.consume() }
            },
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) }

        if (!prefs.drawerSearchBottom) search()

        val labelColor = Color(prefs.drawerLabelColor)
        if (prefs.drawerStyle == DrawerStyle.Grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(prefs.drawerCols),
                state = gridState,
                modifier = Modifier.weight(1f).nestedScroll(conn),
                contentPadding = PaddingValues(10.dp, 4.dp, 10.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(list, key = { _, it -> it.key }) { i, e ->
                    val hover by androidx.compose.animation.core.animateFloatAsState(if (hoverKey == e.key) 1f else 0f, Motion.lively(Motion.drag), label = "dh")
                    StaggerIn(
                        i,
                        Modifier.animateItemPlacement()
                            .onGloballyPositioned { c -> drag?.drawerSlots?.put(e.key, c.boundsInWindow()) }
                            .graphicsLayer { alpha = if (dragKey == e.key) 0.25f else 1f },
                        enabled = i < 28 && System.currentTimeMillis() - openedAt < 700
                    ) {
                        Box(contentAlignment = Alignment.TopCenter) {
                            if (hover > 0.01f) Box(
                                Modifier.padding(top = 2.dp).size(prefs.iconSize.dp * 1.4f)
                                    .graphicsLayer { val sc = 0.6f + 0.4f * hover; scaleX = sc; scaleY = sc; alpha = hover; translationY = -prefs.iconSize.dp.toPx() * 0.12f }
                                    .smokedGlass(shapeFor(if (prefs.iconShape == IconShape.Original) IconShape.Circle else prefs.iconShape, prefs.iconCorner), alpha = 0.45f)
                            )
                            val pop = drag?.recent == e.key
                            Box(Modifier.graphicsLayer { val sc = 1f - 0.12f * hover; scaleX = sc; scaleY = sc }) {
                                when (e) {
                                    is DrawerEntry.A -> AppCell(e.app, prefs, prefs.iconSize.dp, true, { onLaunch(e.app, it) }, { onLong(e.app) }, labelColor = labelColor)
                                    is DrawerEntry.F -> FolderCell(e.folder, byKey, prefs, prefs.iconSize.dp, true, { b -> onFolder(e.id, b) }, { onFolderLong(e.id) }, pop = pop)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).nestedScroll(conn), state = listState, contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 24.dp)) {
                items(list, key = { it.key }) { e ->
                    Row(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { c -> drag?.drawerSlots?.put(e.key, c.boundsInWindow()) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (hoverKey == e.key) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { when (e) { is DrawerEntry.A -> onLaunch(e.app, null); is DrawerEntry.F -> onFolder(e.id, null) } },
                                onLongClick = { when (e) { is DrawerEntry.A -> onLong(e.app); is DrawerEntry.F -> onFolderLong(e.id) } }
                            )
                            .padding(10.dp).animateItemPlacement(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (e) {
                            is DrawerEntry.A -> AppIcon(e.app, 40.dp, prefs)
                            is DrawerEntry.F -> FolderIcon(e.folder, byKey, prefs, 40.dp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            when (e) { is DrawerEntry.A -> e.app.label; is DrawerEntry.F -> e.folder.name },
                            style = labelStyle(prefs, 18).copy(textAlign = TextAlign.Start, color = labelColor)
                        )
                    }
                }
            }
        }

        if (prefs.drawerSearchBottom) search()
    }
}
