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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class Overlay { None, Drawer, Claude, Settings, Widgets }

@Composable
fun CratorRoot(activity: MainActivity, prefs: Prefs, repo: AppRepository) {
    val ctx = LocalContext.current
    val widgets = activity.widgets
    var overlay by remember { mutableStateOf(Overlay.None) }
    var settingsSection by remember { mutableStateOf<String?>(null) }
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
    var lastSheet by remember { mutableStateOf<AppInfo?>(null) }
    if (sheetApp != null && lastSheet !== sheetApp) lastSheet = sheetApp
    var claudeSeed by remember { mutableStateOf("") }
    var folderOpen by remember { mutableStateOf<String?>(null) }
    var folderSheet by remember { mutableStateOf<String?>(null) }
    var widgetSheet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var pickerPkg by remember { mutableStateOf<String?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var folderOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val apps = repo.apps.value
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val dashOffset = if (prefs.dashboardEnabled) 1 else 0
    val pageCount = prefs.homePages + dashOffset
    val pager = rememberPagerState(initialPage = dashOffset) { pageCount }
    val scope = rememberCoroutineScope()
    val chat = remember { ChatState() }
    val stats = remember { SysStats(activity) }
    val drag = remember { DragState() }
    val resumed = rememberResumed()
    val speed = prefs.animSpeed / 100f
    fun dur(ms: Int) = (ms / speed).toInt()
    val homeIndex = (pager.currentPage - dashOffset).coerceAtLeast(0)
    drag.currentPage = pager.currentPage - dashOffset
    val anySheet = sheetApp != null || folderOpen != null || folderSheet != null || widgetSheet != null || homeMenu

    fun closeAll() {
        overlay = Overlay.None; sheetApp = null; folderOpen = null; folderSheet = null; widgetSheet = null; homeMenu = false
    }

    fun openSettings(section: String?) {
        if (section == "__dash") { closeAll(); if (prefs.dashboardEnabled) scope.launch { pager.animateScrollToPage(0) }; return }
        settingsSection = section; overlay = Overlay.Settings
    }

    val homeEv = activity.homeEvents.intValue
    LaunchedEffect(homeEv) {
        if (homeEv > 0) {
            if (overlay != Overlay.None || anySheet) closeAll() else pager.animateScrollToPage(dashOffset)
        }
    }
    BackHandler(enabled = overlay != Overlay.None || anySheet) {
        when {
            sheetApp != null -> sheetApp = null
            widgetSheet != null -> widgetSheet = null
            folderSheet != null -> folderSheet = null
            folderOpen != null -> folderOpen = null
            homeMenu -> homeMenu = false
            overlay == Overlay.Settings && settingsSection != null -> settingsSection = null
            else -> overlay = Overlay.None
        }
    }
    LaunchedEffect(overlay) {
        activity.setBlurBehind(if (overlay == Overlay.Claude || overlay == Overlay.Drawer) prefs.blurStrength * 3 else 0)
    }

    fun act(a: GestureAction) {
        when (a) {
            GestureAction.None -> {}
            GestureAction.Claude -> { claudeSeed = ""; overlay = Overlay.Claude }
            GestureAction.Drawer -> overlay = Overlay.Drawer
            GestureAction.Notifications -> Actions.expandStatusBar(ctx, false)
            GestureAction.QuickSettings -> Actions.expandStatusBar(ctx, true)
            GestureAction.Dashboard -> if (prefs.dashboardEnabled) scope.launch { pager.animateScrollToPage(0) }
            GestureAction.LauncherSettings -> openSettings(null)
        }
    }

    fun openApp(app: AppInfo, b: android.graphics.Rect?) {
        Actions.launch(activity, app, b, prefs.openAnim)
        if (overlay == Overlay.Drawer || overlay == Overlay.Claude || folderOpen != null) {
            scope.launch { delay(350); overlay = Overlay.None; folderOpen = null }
        }
    }

    // ---------- Drag & drop ----------
    fun beginDrag(item: String, src: DragSource, onTap: () -> Unit) {
        if (prefs.lockLayout && src !is DragSource.Drawer) { onTap(); return }
        drag.start(item, src, onTap)
    }

    fun removeFromSource(src: DragSource?, item: String) {
        when (src) {
            is DragSource.Home -> prefs.removeHomeAt(src.page, src.cell)
            is DragSource.Dock -> prefs.dock = prefs.dock.filterIndexed { i, s -> !(i == src.index && s == item) }
            is DragSource.InFolder -> prefs.removeFromFolder(src.id, item)
            DragSource.Drawer, null -> {}
        }
    }

    fun handleDrop() {
        val item = drag.item ?: return
        val src = drag.source
        val target = if (drag.moved) drag.target() else null
        val from = drag.pos
        val dest = drag.destination(target, item)
        val tap = drag.end()
        if (tap != null) { tap(); return }
        drag.settle = DragState.Settle(item, from, dest, target is DropTarget.Zone)
        when (target) {
            null -> {}
            is DropTarget.Zone -> when (target.id) {
                "remove" -> {
                    removeFromSource(src, item)
                    if (Item.isWidget(item)) widgets.delete(Item.widgetId(item))
                }
                "info" -> byKey[item]?.let { Actions.appInfo(activity, it) }
                "uninstall" -> byKey[item]?.let { Actions.uninstall(activity, it) }
            }
            is DropTarget.DockAt -> {
                if (Item.isSized(item)) { Toast.makeText(ctx, "Τα widgets δεν μπαίνουν στο dock", Toast.LENGTH_SHORT).show(); return }
                if (src is DragSource.Dock) {
                    val d = prefs.dock.toMutableList()
                    if (src.index in d.indices) d.removeAt(src.index)
                    val idx = if (target.index > src.index) target.index - 1 else target.index
                    d.add(idx.coerceIn(0, d.size), item); prefs.dock = d
                } else {
                    if (item in prefs.dock) return
                    removeFromSource(src, item)
                    prefs.insertDock(target.index, item)
                }
                drag.recent = item
            }
            is DropTarget.Cell -> {
                val (sx, sy) = Item.span(item)
                var r = target.cell / prefs.cols; var c = target.cell % prefs.cols
                c = c.coerceAtMost(prefs.cols - sx).coerceAtLeast(0); r = r.coerceAtMost(prefs.rows - sy).coerceAtLeast(0)
                val anchor = r * prefs.cols + c
                val exclude = (src as? DragSource.Home)?.takeIf { it.page == target.page }?.cell
                if (src is DragSource.Home && src.page == target.page && src.cell == anchor) return
                val occ = prefs.occupied(target.page, exclude)
                val hit = occ[target.cell]
                val hitItem = hit?.let { prefs.pages.getOrNull(target.page)?.get(it) }
                when {
                    prefs.fits(target.page, anchor, sx, sy, exclude) -> {
                        removeFromSource(src, item)
                        prefs.setHome(target.page, anchor, item)
                        drag.recent = item
                    }
                    Item.isApp(item) && hitItem != null && Item.isFolder(hitItem) -> {
                        removeFromSource(src, item)
                        prefs.addToFolder(Item.folderId(hitItem), item)
                        drag.recent = hitItem
                    }
                    Item.isApp(item) && hitItem != null && Item.isApp(hitItem) && hitItem != item -> {
                        removeFromSource(src, item)
                        val f = prefs.createFolder(hitItem, item)
                        prefs.setHome(target.page, hit, f)
                        drag.recent = f
                    }
                    else -> Toast.makeText(ctx, "Δεν χωράει εδώ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    drag.onDrop = ::handleDrop
    LaunchedEffect(drag.recent) { if (drag.recent != null) { delay(1200); drag.recent = null } }

    // Όταν αρχίσεις να σέρνεις από συρτάρι/φάκελο, κλείνουν για να φανεί η αρχική
    LaunchedEffect(drag.moved) {
        if (drag.moved) { if (overlay == Overlay.Drawer) overlay = Overlay.None; folderOpen = null }
    }
    // Αλλαγή σελίδας όταν κρατάς στην άκρη
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(drag.active) {
        var since = 0L
        while (isActive && drag.active) {
            delay(80)
            val x = drag.pos.x
            val dir = when { x < edgePx -> -1; x > rootSize.width - edgePx -> 1; else -> 0 }
            if (dir == 0 || !drag.moved) { since = 0L; continue }
            val now = System.currentTimeMillis()
            if (since == 0L) { since = now; continue }
            if (now - since > 600) {
                val t = pager.currentPage + dir
                if (t >= dashOffset) {
                    if (t >= pager.pageCount && prefs.homePages < 8) { prefs.homePages = prefs.homePages + 1; delay(60) }
                    if (t < pager.pageCount) pager.animateScrollToPage(t)
                }
                since = 0L
            }
        }
    }

    val blurTarget = when {
        overlay != Overlay.None -> prefs.blurStrength.dp
        folderOpen != null || anySheet -> (prefs.blurStrength * 0.6f).dp
        else -> 0.dp
    }
    val blur by animateDpAsState(blurTarget, Motion.soft(), label = "blur")
    val recede by androidx.compose.animation.core.animateFloatAsState(
        when {
            homeMenu || overlay == Overlay.Widgets -> 0.9f
            folderOpen != null || anySheet || overlay == Overlay.Drawer || overlay == Overlay.Claude -> 0.95f
            drag.active && drag.moved -> 0.97f
            else -> 1f
        }, Motion.lively(), label = "recede"
    )
    val onDashboard = prefs.dashboardEnabled && pager.currentPage == 0
    val swipeDownA by rememberUpdatedState { act(prefs.swipeDown) }
    val swipeUpA by rememberUpdatedState { act(prefs.swipeUp) }
    val doubleA by rememberUpdatedState { act(prefs.doubleTap) }
    val longA by rememberUpdatedState { scope.launch { delay(140); if (!drag.active && sheetApp == null && folderSheet == null && widgetSheet == null) homeMenu = true } }
    val gesturesOn by rememberUpdatedState(!onDashboard && overlay == Overlay.None && !drag.active && !anySheet)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .dragObserver(drag)
            .background(Color.Black.copy(alpha = prefs.wallpaperDim / 100f))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = recede; scaleY = recede }
                .blur(blur)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (gesturesOn) {
                                if (total > 140) swipeDownA() else if (total < -140) swipeUpA()
                            }
                        }
                    ) { change, dy -> total += dy; if (gesturesOn) change.consume() }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { if (gesturesOn) doubleA() },
                        onLongPress = { if (gesturesOn) longA() }
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (!isDefaultHome(activity, resumed)) DefaultBanner(activity)
            if (prefs.searchPos == SearchPos.Top && !onDashboard) SearchPill(prefs, Modifier.padding(16.dp, 8.dp)) { act(GestureAction.Claude) }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = !drag.active) { page ->
                Box(Modifier.fillMaxSize().pageEffect(pager, page, prefs.pageEffect)) {
                    if (prefs.dashboardEnabled && page == 0) {
                        Dashboard(
                            stats, prefs, repo,
                            active = pager.currentPage == 0 && overlay == Overlay.None && resumed,
                            onOpenSettings = { openSettings(null) }
                        )
                    } else {
                        val idx = page - dashOffset
                        HomePage(
                            idx, prefs, byKey, drag, widgets,
                            onLaunch = ::openApp,
                            onItemLong = { cell, item ->
                                when {
                                    Item.isFolder(item) -> beginDrag(item, DragSource.Home(idx, cell)) { folderSheet = Item.folderId(item) }
                                    Item.isSized(item) -> beginDrag(item, DragSource.Home(idx, cell)) { widgetSheet = idx to cell }
                                    else -> byKey[item]?.let { app -> beginDrag(item, DragSource.Home(idx, cell)) { sheetApp = app } }
                                }
                            },
                            onFolder = { id, b -> b?.let { folderOrigin = TransformOrigin(it.exactCenterX() / rootSize.width.coerceAtLeast(1), it.exactCenterY() / rootSize.height.coerceAtLeast(1)) }; folderOpen = id },
                            onClaude = { act(GestureAction.Claude) },
                            onSettings = ::openSettings
                        )
                    }
                }
            }
            if (prefs.pageIndicator) PageIndicator(pager, prefs.dashboardEnabled)
            if (prefs.searchPos == SearchPos.AboveDock) SearchPill(prefs, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { act(GestureAction.Claude) }
            Dock(
                prefs, byKey, drag,
                onLaunch = ::openApp,
                onFolder = { id, b -> b?.let { folderOrigin = TransformOrigin(it.exactCenterX() / rootSize.width.coerceAtLeast(1), it.exactCenterY() / rootSize.height.coerceAtLeast(1)) }; folderOpen = id },
                onItemLong = { i, item ->
                    if (Item.isFolder(item)) beginDrag(item, DragSource.Dock(i)) { folderSheet = Item.folderId(item) }
                    else byKey[item]?.let { app -> beginDrag(item, DragSource.Dock(i)) { sheetApp = app } }
                },
                onSearch = { act(GestureAction.Claude) }
            )
        }

        androidx.compose.animation.AnimatedVisibility(overlay != Overlay.None, enter = fadeIn(tween(dur(220))), exit = fadeOut(tween(dur(220)))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        }

        AnimatedVisibility(
            overlay == Overlay.Drawer,
            enter = slideInVertically(Motion.lively()) { it / 3 } + fadeIn(tween(dur(220))) + scaleIn(Motion.lively(), 0.96f, TransformOrigin(0.5f, 1f)),
            exit = slideOutVertically(Motion.snappy()) { it / 3 } + fadeOut(tween(dur(180)))
        ) {
            AppDrawer(
                apps, prefs,
                onLaunch = ::openApp,
                onLong = { app -> beginDrag(app.key, DragSource.Drawer) { sheetApp = app } },
                onAskClaude = { q -> claudeSeed = q; overlay = Overlay.Claude },
                onClose = { overlay = Overlay.None }
            )
        }
        AnimatedVisibility(
            folderOpen != null,
            enter = fadeIn(tween(dur(160))) + scaleIn(Motion.lively(), 0.2f, folderOrigin),
            exit = fadeOut(tween(dur(170))) + scaleOut(Motion.snappy(), 0.2f, folderOrigin)
        ) {
            val last = remember { mutableStateOf("") }
            folderOpen?.let { last.value = it }
            FolderPopup(
                last.value, prefs, byKey,
                onLaunch = ::openApp,
                onAppLong = { app -> beginDrag(app.key, DragSource.InFolder(last.value)) { sheetApp = app } },
                onClose = { folderOpen = null }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Claude,
            enter = slideInVertically(Motion.lively()) { -it / 4 } + fadeIn(tween(dur(220))) + scaleIn(Motion.lively(), 0.88f, TransformOrigin(0.5f, 0f)),
            exit = slideOutVertically(Motion.snappy()) { -it / 4 } + fadeOut(tween(dur(180))) + scaleOut(Motion.snappy(), 0.94f, TransformOrigin(0.5f, 0f))
        ) {
            ClaudePanel(
                chat, prefs, apps, claudeSeed,
                onLaunch = ::openApp,
                onClose = { overlay = Overlay.None },
                onOpenSettings = { openSettings("claude") }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Settings,
            enter = slideInHorizontally(Motion.soft()) { it / 2 } + fadeIn(tween(dur(220))),
            exit = slideOutHorizontally(Motion.snappy()) { it / 2 } + fadeOut(tween(dur(180)))
        ) {
            SettingsScreen(
                activity, prefs, repo, chat, settingsSection,
                onSection = { settingsSection = it },
                onClose = { overlay = Overlay.None }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Widgets,
            enter = slideInVertically(Motion.lively()) { it / 3 } + fadeIn(tween(dur(220))),
            exit = slideOutVertically(Motion.snappy()) { it / 3 } + fadeOut(tween(dur(180)))
        ) {
            WidgetPicker(widgets, prefs, pickerPkg, homeIndex) { overlay = Overlay.None }
        }

        Presence(if (homeMenu) Unit else null) { HomeMenu(
            prefs,
            onWidgets = { homeMenu = false; pickerPkg = null; overlay = Overlay.Widgets },
            onWallpaper = { homeMenu = false; Actions.start(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Ταπετσαρία")) },
            onSettings = { homeMenu = false; openSettings(null) },
            onAddPage = { homeMenu = false; if (prefs.homePages < 8) { prefs.homePages = prefs.homePages + 1; scope.launch { delay(60); pager.animateScrollToPage(pager.pageCount - 1) } } },
            onRemovePage = {
                homeMenu = false
                if (prefs.homePages > 1) {
                    val idx = homeIndex
                    prefs.pages.getOrNull(idx)?.values?.filter { Item.isWidget(it) }?.forEach { widgets.delete(Item.widgetId(it)) }
                    prefs.pages = prefs.pages.filterIndexed { i, _ -> i != idx }
                    prefs.homePages = prefs.homePages - 1
                }
            },
            onClose = { homeMenu = false }
        ) }
        Presence(widgetSheet) { (p, c) ->
            WidgetSheet(prefs, widgets, p, c, onClose = { widgetSheet = null })
        }
        Presence(folderSheet) { id ->
            FolderSheet(id, prefs, byKey, onOpen = { folderSheet = null; folderOpen = id }, onClose = { folderSheet = null })
        }
        Presence(sheetApp) { app ->
            AppSheet(
                app, prefs, repo, activity,
                onClose = { sheetApp = null },
                onAddHome = { prefs.placeOnHome(app.key, homeIndex) },
                onWidgets = { sheetApp = null; pickerPkg = app.component.packageName; overlay = Overlay.Widgets },
                hasWidgets = remember(app.key) { widgets.providers().any { it.provider.packageName == app.component.packageName } }
            )
        }

        DragLayer(drag, prefs, byKey)
    }
}

@Composable
private fun HomePage(
    index: Int,
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    drag: DragState,
    widgets: WidgetController,
    onLaunch: (AppInfo, android.graphics.Rect?) -> Unit,
    onItemLong: (Int, String) -> Unit,
    onFolder: (String, android.graphics.Rect?) -> Unit,
    onClaude: () -> Unit,
    onSettings: (String?) -> Unit
) {
    val items = prefs.pages.getOrNull(index) ?: emptyMap()
    // στόχος φακέλου: εικονίδιο πάνω στο οποίο αιωρείται μια εφαρμογή
    val dragItem = drag.item
    val hoverAnchor = if (drag.active && drag.moved && dragItem != null && Item.isApp(dragItem)) {
        (drag.target() as? DropTarget.Cell)?.takeIf { it.page == index }?.let { t ->
            prefs.occupied(index)[t.cell]?.takeIf { a -> items[a]?.let { !Item.isSized(it) && it != dragItem } == true }
        }
    } else null
    Column(Modifier.fillMaxSize()) {
        if (index == 0 && prefs.clockStyle != ClockStyle.Hidden) ClockHeader(prefs)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .onGloballyPositioned { drag.grids[index] = GridGeom(it.boundsInWindow(), prefs.cols, prefs.rows) }
        ) {
            val cw = maxWidth / prefs.cols
            val ch = maxHeight / prefs.rows
            items.forEach { (cell, item) ->
                val (sx0, sy0) = Item.span(item)
                val sx = sx0.coerceAtMost(prefs.cols); val sy = sy0.coerceAtMost(prefs.rows)
                val r = cell / prefs.cols; val c = cell % prefs.cols
                if (r >= prefs.rows) return@forEach
                val dragging = drag.hiddenSource == DragSource.Home(index, cell)
                val hovered = hoverAnchor == cell
                val hoverS by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1f else 0f, Motion.lively(), label = "hover")
                Box(
                    Modifier
                        .offset(cw * c, ch * r)
                        .size(cw * sx, ch * sy)
                        .graphicsLayer { alpha = if (dragging) 0.25f else 1f },
                    contentAlignment = Alignment.Center
                ) {
                    if (hoverS > 0.01f) {
                        // γυάλινο «δαχτυλίδι» φακέλου που ανοίγει
                        Box(
                            Modifier
                                .size(prefs.iconSize.dp * 1.5f)
                                .graphicsLayer { scaleX = 0.6f + 0.4f * hoverS; scaleY = 0.6f + 0.4f * hoverS; alpha = hoverS }
                                .smokedGlass(shapeFor(if (prefs.iconShape == IconShape.Original) IconShape.Circle else prefs.iconShape, prefs.iconCorner), alpha = 0.45f)
                                .align(Alignment.Center)
                        )
                    }
                    val pop = drag.recent == item
                    when {
                        Item.isWidget(item) -> SystemWidget(Item.widgetId(item), widgets, prefs, { onItemLong(cell, item) })
                        Item.isCrator(item) -> CratorWidget(item, prefs, onClaude, onSettings, { onItemLong(cell, item) })
                        Item.isFolder(item) -> prefs.folders[Item.folderId(item)]?.let { f ->
                            FolderCell(f, byKey, prefs, prefs.iconSize.dp, prefs.showLabels, { b -> onFolder(Item.folderId(item), b) }, { onItemLong(cell, item) },
                                Modifier.graphicsLayer { val s = 1f - 0.1f * hoverS; scaleX = s; scaleY = s }, pop = pop)
                        }
                        else -> byKey[item]?.let { app ->
                            PopIn(pop) {
                                AppCell(app, prefs, prefs.iconSize.dp, prefs.showLabels, { b -> onLaunch(app, b) }, { onItemLong(cell, item) },
                                    Modifier.graphicsLayer { val s = 1f - 0.12f * hoverS; scaleX = s; scaleY = s })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(
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
        val m = mod.onGloballyPositioned { drag.dockSlots[i] = it.boundsInWindow() }
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
            if (prefs.searchPos == SearchPos.InDock) SearchPill(prefs, Modifier.padding(6.dp, 0.dp, 6.dp, 8.dp), onSearch)
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
                    items.forEachIndexed { pos, iv ->
                        val lift = if (style == DockStyle.Arc && n > 1) {
                            val t = (pos - (n - 1) / 2f) / ((n - 1) / 2f)
                            (-22f * t * t).dp
                        } else 0.dp
                        val dragging = drag.hiddenSource == DragSource.Dock(iv.index)
                        cell(iv.index, iv.value, Modifier.width(size + 8.dp).offset(y = lift).graphicsLayer { alpha = if (dragging) 0.25f else 1f })
                    }
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

/** Μενού παρατεταμένου πατήματος σε κενό σημείο (όπως στο Nova) */
@Composable
private fun HomeMenu(
    prefs: Prefs,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onClose: () -> Unit
) {
    BottomCard(onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MenuTile("Widgets", Icons.Rounded.Widgets, onWidgets)
            MenuTile("Ταπετσαρία", Icons.Rounded.Wallpaper, onWallpaper)
            MenuTile("Ρυθμίσεις", Icons.Rounded.Tune, onSettings)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MenuTile("Νέα σελίδα", Icons.Rounded.AddBox, onAddPage)
            MenuTile("Διαγραφή σελίδας", Icons.Rounded.Remove, onRemovePage)
            MenuTile(
                if (prefs.lockLayout) "Ξεκλείδωμα" else "Κλείδωμα",
                if (prefs.lockLayout) Icons.Rounded.LockOpen else Icons.Rounded.Lock
            ) { prefs.lockLayout = !prefs.lockLayout; onClose() }
        }
    }
}

@Composable
private fun MenuTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(100.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).smokedGlass(CircleShape, alpha = 0.35f, tint = MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Smoke.pearl)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Smoke.pearl, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Μενού widget: αλλαγή μεγέθους / αφαίρεση */
@Composable
private fun WidgetSheet(prefs: Prefs, widgets: WidgetController, page: Int, cell: Int, onClose: () -> Unit) {
    val item = prefs.pages.getOrNull(page)?.get(cell) ?: run { onClose(); return }
    val (sx, sy) = Item.span(item)
    val name = if (Item.isWidget(item)) widgets.info(Item.widgetId(item))?.loadLabel(LocalContext.current.packageManager) ?: "Widget"
    else CRATOR_WIDGETS.firstOrNull { it.type == Item.cratorType(item) }?.name ?: "Widget"
    fun resize(nx: Int, ny: Int) {
        if (nx < 1 || ny < 1) return
        if (prefs.fits(page, cell, nx, ny, exclude = cell)) prefs.setHome(page, cell, Item.resized(item, nx, ny))
    }
    BottomCard(onClose) {
        Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Μέγεθος: $sx × $sy", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        SpanRow("Πλάτος", { resize(sx - 1, sy) }, { resize(sx + 1, sy) })
        SpanRow("Ύψος", { resize(sx, sy - 1) }, { resize(sx, sy + 1) })
        Spacer(Modifier.height(8.dp))
        ChipGrid(listOf(
            "Αφαίρεση" to {
                prefs.removeHomeAt(page, cell)
                if (Item.isWidget(item)) widgets.delete(Item.widgetId(item))
                onClose()
            },
            "Κλείσιμο" to onClose
        ))
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

@Composable
private fun isDefaultHome(activity: MainActivity, resumed: Boolean): Boolean {
    return remember(resumed) {
        val rm = activity.getSystemService(RoleManager::class.java)
        rm == null || !rm.isRoleAvailable(RoleManager.ROLE_HOME) || rm.isRoleHeld(RoleManager.ROLE_HOME)
    }
}

@Composable
private fun DefaultBanner(activity: MainActivity) {
    Row(
        Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            .clickable { activity.requestHomeRole() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black)
        Spacer(Modifier.width(10.dp))
        Text("Όρισε το Crator ως αρχική οθόνη", color = Color.Black, fontWeight = FontWeight.SemiBold)
    }
}

fun Modifier.pageEffect(state: PagerState, page: Int, effect: PageEffect): Modifier = graphicsLayer {
    val o = (state.currentPage - page) + state.currentPageOffsetFraction
    val a = abs(o).coerceIn(0f, 1f)
    val w = size.width
    cameraDistance = 14f * density
    when (effect) {
        PageEffect.Slide -> {}
        PageEffect.Fade -> { translationX = o * w; alpha = 1f - a }
        PageEffect.Zoom -> { val s = 1f - 0.22f * a; scaleX = s; scaleY = s; alpha = 1f - 0.5f * a }
        PageEffect.Depth -> if (o > 0) { translationX = o * w; val s = 1f - 0.25f * a; scaleX = s; scaleY = s; alpha = 1f - a }
        PageEffect.Stack -> if (o < 0) { translationX = o * w; val s = 0.7f + 0.3f * (1f - a); scaleX = s; scaleY = s; alpha = 1f - a }
        PageEffect.Cube -> {
            transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
            rotationY = -90f * o.coerceIn(-1f, 1f)
        }
        PageEffect.CubeIn -> {
            transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
            rotationY = 90f * o.coerceIn(-1f, 1f)
        }
        PageEffect.Flip -> { translationX = o * w; rotationY = 180f * o; alpha = if (a < 0.5f) 1f else 0f }
        PageEffect.Windmill -> {
            translationX = o * w
            transformOrigin = TransformOrigin(0.5f, 1.6f)
            rotationZ = -40f * o
            alpha = 1f - 0.4f * a
        }
        PageEffect.Carousel -> { val s = 1f - 0.18f * a; scaleX = s; scaleY = s; rotationY = 35f * o }
        PageEffect.Accordion -> {
            translationX = o * w
            transformOrigin = TransformOrigin(if (o > 0) 0f else 1f, 0.5f)
            scaleX = 1f - a
        }
        PageEffect.Tilt -> { rotationX = 18f * a; val s = 1f - 0.1f * a; scaleX = s; scaleY = s }
    }
}

@Composable
private fun ClockHeader(prefs: Prefs) {
    val ctx = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50)
        }
    }
    val el = Locale("el", "GR")
    val time = SimpleDateFormat("HH:mm", el).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", el).format(now).replaceFirstChar { it.uppercase() }
    val open = Modifier.clickable { Actions.start(ctx, android.content.Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 3f), 12f)
    Column(Modifier.fillMaxWidth().padding(24.dp, 18.dp, 24.dp, 8.dp).then(open)) {
        when (prefs.clockStyle) {
            ClockStyle.Big -> {
                Text(time, style = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 84.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-3).sp, color = Smoke.pearl, shadow = shadow))
                Text(date, style = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, color = Smoke.pearl.copy(alpha = 0.82f), shadow = shadow))
            }
            ClockStyle.Minimal -> Row(verticalAlignment = Alignment.Bottom) {
                Text(time, fontSize = 34.sp, fontWeight = FontWeight.Light, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(date, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
            }
            ClockStyle.Stacked -> {
                val (h, m) = time.split(":")
                Text(h, fontSize = 92.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 84.sp)
                Text(m, fontSize = 92.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, lineHeight = 84.sp)
                Text(date, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
            }
            ClockStyle.Hidden -> {}
        }
    }
}

@Composable
private fun PageIndicator(pager: PagerState, dash: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until pager.pageCount) {
            val sel = pager.currentPage == i
            val w by animateDpAsState(if (sel) 18.dp else 6.dp, label = "dot")
            if (dash && i == 0) {
                Icon(Icons.Rounded.Insights, null, tint = Color.White.copy(alpha = if (sel) 1f else 0.5f), modifier = Modifier.padding(horizontal = 3.dp).size(12.dp))
            } else {
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(Color.White.copy(alpha = if (sel) 0.95f else 0.4f)))
            }
        }
    }
}

@Composable
fun SearchPill(prefs: Prefs, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .smokedGlass(RoundedCornerShape(25.dp), alpha = (prefs.dockOpacity / 100f + 0.15f).coerceAtMost(1f), tint = Color(prefs.dockColor))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Ρώτα τον Claude ή ψάξε", color = Smoke.mist, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Mic, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

