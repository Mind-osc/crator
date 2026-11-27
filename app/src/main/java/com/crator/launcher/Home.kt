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
    var barH by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var popupAnchor by remember { mutableStateOf(Offset.Zero) }
    var folderAnchor by remember { mutableStateOf(Offset.Zero) }
    var popupCtx by remember { mutableStateOf(PopupCtx()) }
    var drawerSeed by remember { mutableStateOf("") }
    var drawerSearchMode by remember { mutableStateOf(false) }
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
    fun dur(ms: Int) = Motion.fadeMs(ms, Motion.overlay)
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

    // Banner «όρισε ως αρχική»: εμφανίζεται για λίγο μία φορά ανά εκκίνηση
    val isDefault = isDefaultHome(activity, resumed)
    var bannerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isDefault) {
        if (!isDefault && !BannerOnce.shown) {
            BannerOnce.shown = true
            delay(700); bannerVisible = true
            delay(5000); bannerVisible = false
        }
        if (isDefault) bannerVisible = false
    }

    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == android.app.Activity.RESULT_OK && !t.isNullOrBlank()) { drawerSeed = t; drawerSearchMode = true; overlay = Overlay.Drawer }
    }
    fun startVoice() {
        val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Ποια εφαρμογή;")
        runCatching { voiceLauncher.launch(i) }
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
    // Συρτάρι «διαδραστικό» (σαν Nova): η πρόοδος 0..1 ακολουθεί το δάχτυλο
    val drawerP = remember { androidx.compose.animation.core.Animatable(0f) }
    var drawerDragging by remember { mutableStateOf(false) }
    var drawerReleasing by remember { mutableStateOf(false) }
    // απόσταση που διανύει το δάχτυλο για 0→1: το συρτάρι κινείται ακριβώς όσο το δάχτυλο (1:1)
    fun travel() = (rootSize.height * 0.22f).coerceAtLeast(1f)
    fun drawerTrack(raw: Float) {
        // λάστιχο: πέρα από το πλήρες άνοιγμα κινείται μόνο το 1/5 της απόστασης
        val p = if (raw > 1f) 1f + (raw - 1f) * 0.2f else raw
        drawerDragging = true
        scope.launch { drawerP.snapTo(p.coerceIn(0f, 1.08f)) }
    }
    fun drawerRelease(velPxY: Float, closing: Boolean = false) {
        // ανοίγει/κλείνει με απόσταση Ή ταχύτητα, και η κίνηση ξεκινά με την ταχύτητα του δαχτύλου.
        // στο κλείσιμο (από ανοιχτό) αρκεί μικρή κίνηση: ~15% της διαδρομής ή ελαφρύ τίναγμα
        val open = if (closing) when { velPxY > 140f -> false; velPxY < -350f -> true; else -> drawerP.value > 0.88f }
        else when { velPxY < -350f -> true; velPxY > 350f -> false; else -> drawerP.value > 0.3f }
        drawerDragging = false
        drawerReleasing = true
        overlay = if (open) Overlay.Drawer else if (overlay == Overlay.Drawer) Overlay.None else overlay
        scope.launch {
            try { drawerP.animateTo(if (open) 1f else 0f, Motion.fling(Motion.overlay), initialVelocity = -velPxY / travel()) }
            finally { drawerReleasing = false }
        }
    }
    val drawerShown by remember { androidx.compose.runtime.derivedStateOf { drawerP.value > 0.001f } }
    LaunchedEffect(overlay) {
        if (!drawerDragging && !drawerReleasing) {
            if (overlay == Overlay.Drawer) drawerP.animateTo(1f, Motion.settle(Motion.overlay))
            else drawerP.animateTo(0f, Motion.settle(Motion.overlay))
        }
    }
    LaunchedEffect(overlay) {
        if (overlay != Overlay.Drawer) { drawerSearchMode = false; drawerSeed = "" }
        activity.setBlurBehind(if (overlay == Overlay.Claude || overlay == Overlay.Drawer) prefs.blurStrength * 3 else 0)
    }

    fun openSearch(seed: String = "") {
        drawerSeed = seed; drawerSearchMode = true; overlay = Overlay.Drawer
    }

    fun act(a: GestureAction) {
        when (a) {
            GestureAction.None -> {}
            GestureAction.Claude -> openSearch()
            GestureAction.Drawer -> overlay = Overlay.Drawer
            GestureAction.Notifications -> Actions.expandStatusBar(ctx, false)
            GestureAction.QuickSettings -> Actions.expandStatusBar(ctx, true)
            GestureAction.Dashboard -> if (prefs.dashboardEnabled) scope.launch { pager.animateScrollToPage(0) }
            GestureAction.LauncherSettings -> openSettings(null)
        }
    }

    fun openFolder(id: String, b: android.graphics.Rect?) {
        folderAnchor = b?.let { Offset(it.exactCenterX(), it.exactCenterY()) } ?: drag.lastPointer
        folderOpen = id
    }


    fun openApp(app: AppInfo, b: android.graphics.Rect?) {
        Actions.launch(activity, app, b, prefs.openAnim)
        if (overlay == Overlay.Drawer || overlay == Overlay.Claude || folderOpen != null) {
            scope.launch { delay(350); overlay = Overlay.None; folderOpen = null }
        }
    }

    // ---------- Drag & drop ----------
    fun beginDrag(item: String, src: DragSource, onTap: () -> Unit) {
        popupAnchor = drag.lastPointer
        popupCtx = PopupCtx(
            inFolder = (src as? DragSource.InFolder)?.id ?: folderOpen,
            fromDrawer = src == DragSource.Drawer && folderOpen == null
        )
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
        val pendingReflow = drag.reflow
        val from = drag.pos
        val dest = drag.destination(target, item)
        val tap = drag.end()
        if (tap != null) { tap(); return }
        drag.settle = DragState.Settle(item, from, dest, target is DropTarget.Zone)
        when (target) {
            null -> {}
            is DropTarget.DrawerItem -> {
                if (!Item.isApp(item)) return
                val k = target.key
                if (k.startsWith("f:")) {
                    val id = k.removePrefix("f:")
                    prefs.addToFolder(id, item)
                    drag.recent = k
                } else if (k != item) {
                    val id = System.currentTimeMillis().toString(36)
                    prefs.folders = prefs.folders + (id to Folder("Φάκελος", listOf(k, item)))
                    prefs.drawerFolders = prefs.drawerFolders + id
                    drag.recent = "f:$id"
                }
                // αν ήταν ήδη σε άλλο φάκελο του συρταριού, βγαίνει από εκεί
                prefs.drawerFolders.forEach { fid ->
                    val f = prefs.folders[fid]
                    if (f != null && item in f.apps && "f:$fid" != drag.recent) {
                        val rest = f.apps - item
                        if (rest.isEmpty()) { prefs.folders = prefs.folders - fid; prefs.drawerFolders = prefs.drawerFolders - fid }
                        else prefs.folders = prefs.folders + (fid to f.copy(apps = rest))
                    }
                }
            }
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
                val (sx, sy) = prefs.spanOf(item)
                var r = target.cell / prefs.cols; var c = target.cell % prefs.cols
                c = c.coerceAtMost(prefs.cols - sx).coerceAtLeast(0); r = r.coerceAtMost(prefs.gridRows - sy).coerceAtLeast(0)
                val anchor = r * prefs.cols + c
                val exclude = (src as? DragSource.Home)?.takeIf { it.page == target.page }?.cell
                if (src is DragSource.Home && src.page == target.page && src.cell == anchor) return
                val occ = prefs.occupied(target.page, exclude)
                val hit = occ[target.cell]
                val hitItem = hit?.let { prefs.pages.getOrNull(target.page)?.get(it) }
                // φάκελος μόνο όταν αφήνεις πάνω σε άλλο εικονίδιο/φάκελο — αλλιώς ποτέ «Δεν χωράει» σε κενό
                val folderDrop = target.center && Item.isApp(item) && hitItem != null && hitItem != item &&
                    (Item.isApp(hitItem) || Item.isFolder(hitItem))
                val canShift = !Item.isSized(item) && hit != null && !folderDrop && prefs.fine <= 1
                // με ενδιάμεσες γραμμές: αν δεν χωράει ακριβώς, κουμπώνει στην πιο κοντινή ελεύθερη θέση
                val near = if (prefs.fine > 1 && !folderDrop && !prefs.fits(target.page, anchor, sx, sy, exclude))
                    prefs.nearestFit(target.page, r, c, sx, sy, exclude) else null
                when {
                    near != null -> {
                        if (src is DragSource.Home && src.page == target.page && src.cell == near) return
                        removeFromSource(src, item)
                        prefs.setHome(target.page, near, item)
                        drag.recent = item
                    }
                    canShift -> {
                        val moves = pendingReflow?.takeIf { it.page == target.page && it.insertAt == target.cell }?.moves
                            ?: prefs.reflowMoves(target.page, target.cell, exclude)
                        if (moves != null) {
                            removeFromSource(src, item)
                            prefs.applyReflow(target.page, moves, target.cell, item)
                            drag.recent = item
                        } else Toast.makeText(ctx, "Η σελίδα είναι γεμάτη", Toast.LENGTH_SHORT).show()
                    }
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
    drag.spanOf = { prefs.spanOf(it) }
    drag.snapOf = snap@{ t, it ->
        if (prefs.fine <= 1) return@snap null
        val (sx, sy) = prefs.spanOf(it)
        val ex0 = (drag.source as? DragSource.Home)?.takeIf { s -> s.page == t.page }?.cell
        val hitA = prefs.occupied(t.page, ex0)[t.cell]
        val hitI = hitA?.let { a -> prefs.pages.getOrNull(t.page)?.get(a) }
        if (t.center && Item.isApp(it) && hitI != null && (Item.isApp(hitI) || Item.isFolder(hitI))) return@snap null
        val r = (t.cell / prefs.cols).coerceAtMost(prefs.gridRows - sy).coerceAtLeast(0)
        val c = (t.cell % prefs.cols).coerceAtMost(prefs.cols - sx).coerceAtLeast(0)
        val ex = (drag.source as? DragSource.Home)?.takeIf { s -> s.page == t.page }?.cell
        if (prefs.fits(t.page, r * prefs.cols + c, sx, sy, ex)) null else prefs.nearestFit(t.page, r, c, sx, sy, ex)
    }
    // Παραμερισμός: όταν κρατάς λίγο στην άκρη ενός εικονιδίου, τα άλλα κάνουν χώρο
    LaunchedEffect(drag.active) {
        if (!drag.active) return@LaunchedEffect
        snapshotFlow {
            val it = drag.item
            val t = if (drag.moved) drag.target() as? DropTarget.Cell else null
            if (it == null || t == null || Item.isSized(it)) null
            else if (t.center) null
            else t.page to t.cell
        }.distinctUntilChanged().collectLatest { key ->
            if (key == null) { delay(120); drag.reflow = null; return@collectLatest }
            val (page, cell) = key
            if (drag.reflow?.let { it.page == page && it.insertAt == cell } == true) return@collectLatest
            delay(320)
            val ex = (drag.source as? DragSource.Home)?.takeIf { it.page == page }?.cell
            val moves = prefs.reflowMoves(page, cell, ex)
            drag.reflow = if (moves.isNullOrEmpty()) null else DragState.Reflow(page, cell, moves)
        }
    }
    LaunchedEffect(drag.recent) { if (drag.recent != null) { delay(1200); drag.recent = null } }

    // Όταν αρχίσεις να σέρνεις από συρτάρι/φάκελο, κλείνουν για να φανεί η αρχική
    drag.drawerOpen = overlay == Overlay.Drawer
    drag.onSecondSwipe = { if (overlay == Overlay.Drawer) { overlay = Overlay.None; folderOpen = null } }
    LaunchedEffect(drag.moved) {
        if (drag.moved && overlay != Overlay.Drawer) folderOpen = null
    }
    // Αλλαγή σελίδας όταν κρατάς στην άκρη
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(drag.active) {
        var since = 0L
        while (isActive && drag.active) {
            delay(80)
            val x = drag.pos.x
            val dir = when { x < edgePx -> -1; x > rootSize.width - edgePx -> 1; else -> 0 }
            if (dir == 0 || !drag.moved || drag.drawerOpen) { since = 0L; continue }
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
        overlay != Overlay.None && overlay != Overlay.Drawer -> prefs.blurStrength.dp
        sheetApp != null || folderSheet != null || widgetSheet != null || homeMenu -> (prefs.blurStrength * 0.6f).dp
        else -> 0.dp
    }
    val blur by animateDpAsState(blurTarget, Motion.soft(Motion.overlay), label = "blur")
    val recede by androidx.compose.animation.core.animateFloatAsState(
        when {
            homeMenu || overlay == Overlay.Widgets -> 0.9f
            folderOpen != null || anySheet || overlay == Overlay.Claude -> 0.95f
            drag.active && drag.moved -> 0.97f
            else -> 1f
        }, Motion.lively(Motion.overlay), label = "recede"
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
                .graphicsLayer {
                    val p = drawerP.value.coerceIn(0f, 1f)
                    val sc = recede * (1f - 0.04f * p)
                    scaleX = sc; scaleY = sc
                    alpha = 1f - 0.92f * p
                }
                .blur(blur)
                .pointerInput(Unit) {
                    var total = 0f
                    var base = 0f
                    val vt = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            total = 0f; vt.resetTracking()
                            // «πιάνει» το συρτάρι ακόμα κι αν είναι στη μέση της κίνησης
                            base = drawerP.value.coerceIn(0f, 1f)
                            if (base > 0.01f) scope.launch { drawerP.stop() }
                        },
                        onDragEnd = {
                            if (drawerDragging) drawerRelease(vt.calculateVelocity().y)
                            else if (gesturesOn) {
                                if (total > 140) swipeDownA() else if (total < -140) swipeUpA()
                            }
                        },
                        onDragCancel = { if (drawerDragging) drawerRelease(0f) }
                    ) { change, dy ->
                        total += dy
                        if (!gesturesOn && !drawerDragging) return@detectVerticalDragGestures
                        change.consume()
                        vt.addPosition(change.uptimeMillis, change.position)
                        // swipe πάνω: το συρτάρι είναι το δάχτυλο (1:1) όσο ακουμπάς
                        if (prefs.swipeUp == GestureAction.Drawer && (drawerDragging || base > 0.01f || total < -12f)) {
                            drawerTrack(base - total / travel())
                        }
                    }
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
            if (prefs.searchPos == SearchPos.Top && !onDashboard) SearchPill(prefs, Modifier.padding(16.dp, 8.dp), onVoice = { startVoice() }) { openSearch() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(
                state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !drag.active && !DashHooks.editing.value,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 400f * Motion.speed * Motion.page * Motion.speed * Motion.page)
                )
            ) { page ->
                Box(Modifier.fillMaxSize().pageEffect(pager, page, prefs.pageEffect)) {
                    if (prefs.dashboardEnabled && page == 0) {
                        Dashboard(
                            stats, prefs, repo,
                            active = pager.currentPage == 0 && overlay == Overlay.None && resumed,
                            onOpenSettings = { openSettings(null) },
                            onClaude = { openSearch() },
                            onSearch = { openSearch() }
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
                            onFolder = ::openFolder,
                            onClaude = { openSearch() },
                            onSettings = ::openSettings,
                            bottomPad = barH
                        )
                    }
                }
            }
            // Κάτω μπάρα (τελείες, αναζήτηση, dock): γλιστράει έξω όσο πηγαίνεις στο ταμπλό
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { barH = with(density) { it.height.toDp() } }
                    .graphicsLayer {
                        val pos = pager.currentPage + pager.currentPageOffsetFraction
                        val d = if (prefs.dashboardEnabled) (1f - pos).coerceIn(0f, 1f) else 0f
                        translationY = d * (size.height + 40f)
                        alpha = 1f - d
                    }
            ) {
            if (prefs.pageIndicator) PageIndicator(pager, prefs.dashboardEnabled)
            if (prefs.searchPos == SearchPos.AboveDock) SearchPill(prefs, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onVoice = { startVoice() }) { openSearch() }
            Dock(
                prefs, byKey, drag,
                onLaunch = ::openApp,
                onFolder = ::openFolder,
                onItemLong = { i, item ->
                    if (Item.isFolder(item)) beginDrag(item, DragSource.Dock(i)) { folderSheet = Item.folderId(item) }
                    else byKey[item]?.let { app -> beginDrag(item, DragSource.Dock(i)) { sheetApp = app } }
                },
                onSearch = { openSearch() }
            )
            }
            }
        }

        androidx.compose.animation.AnimatedVisibility(overlay != Overlay.None && overlay != Overlay.Drawer, enter = fadeIn(tween(dur(220))), exit = fadeOut(tween(dur(220)))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        }

        if (overlay == Overlay.Drawer || drawerDragging || drawerShown) Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = drawerP.value
                alpha = (p * 1.6f).coerceIn(0f, 1f)
                translationY = (1f - p) * travel()
            }
        ) {
            AppDrawer(
                apps, prefs, drawerSeed, drawerSearchMode, byKey,
                onLaunch = ::openApp,
                onLong = { app -> beginDrag(app.key, DragSource.Drawer) { sheetApp = app } },
                onFolder = ::openFolder,
                onFolderLong = { id -> popupAnchor = drag.lastPointer; folderSheet = id },
                onClose = { overlay = Overlay.None },
                drag = drag,
                onPull = { px -> drawerTrack(1f - px / (travel() * 0.5f)) }, // κλείσιμο με μισή κίνηση
                onPullEnd = { v -> drawerRelease(v, closing = true) }
            )
        }
        Presence(folderOpen) { id ->
            FolderMini(
                id, folderAnchor, prefs, byKey,
                onLaunch = ::openApp,
                onAppLong = { app ->
                    val src = if (id in prefs.drawerFolders) DragSource.Drawer else DragSource.InFolder(id)
                    beginDrag(app.key, src) { sheetApp = app }
                },
                onClose = { folderOpen = null }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Settings,
            enter = slideInHorizontally(Motion.soft(Motion.overlay)) { it / 2 } + fadeIn(tween(dur(220))),
            exit = slideOutHorizontally(Motion.snappy(Motion.overlay)) { it / 2 } + fadeOut(tween(dur(180)))
        ) {
            SettingsScreen(
                activity, prefs, repo, chat, settingsSection,
                onSection = { settingsSection = it },
                onClose = { overlay = Overlay.None }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Widgets,
            enter = slideInVertically(Motion.lively(Motion.overlay)) { it / 3 } + fadeIn(tween(dur(220))),
            exit = slideOutVertically(Motion.snappy(Motion.overlay)) { it / 3 } + fadeOut(tween(dur(180)))
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
            FolderMenuPopup(id, popupAnchor, prefs, onOpen = { folderSheet = null; folderAnchor = popupAnchor; folderOpen = id }, onClose = { folderSheet = null })
        }
        Presence(sheetApp) { app ->
            AppPopup(
                app, popupAnchor, popupCtx, prefs, repo, activity,
                onClose = { sheetApp = null; folderOpen = null },
                onAddHome = { prefs.placeOnHome(app.key, homeIndex) },
                onWidgets = { sheetApp = null; pickerPkg = app.component.packageName; overlay = Overlay.Widgets },
                hasWidgets = remember(app.key) { widgets.providers().any { it.provider.packageName == app.component.packageName } }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            bannerVisible && overlay == Overlay.None,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(Motion.lively()) { -it * 2 } + fadeIn(tween(dur(200))),
            exit = slideOutVertically(Motion.snappy()) { -it * 2 } + fadeOut(tween(dur(160)))
        ) {
            DefaultBanner(activity) { bannerVisible = false }
        }

        DragLayer(drag, prefs, byKey)
    }
}

object BannerOnce { var shown = false }

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
    onSettings: (String?) -> Unit,
    bottomPad: androidx.compose.ui.unit.Dp = 0.dp
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
                    val pop = drag.recent == item
                    Box(if (Item.isSized(item)) Modifier.fillMaxSize() else Modifier.wrapContentSize(Alignment.Center, unbounded = true), contentAlignment = Alignment.Center) {
                    when {
                        Item.isWidget(item) -> SystemWidget(Item.widgetId(item), widgets, prefs, { onItemLong(cell, item) })
                        Item.isCrator(item) -> CratorWidget(item, prefs, onClaude, onSettings, { onItemLong(cell, item) })
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
        }
        Spacer(Modifier.height(bottomPad))
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

/** Μενού παρατεταμένου πατήματος σε κενό σημείο: σκούρο γυαλί, συμπαγές, πλακίδια με έντονα χρώματα */
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MenuTile("Widgets", Icons.Rounded.Widgets, Color(0xFFB37CFF), Modifier.weight(1f), onWidgets)
                MenuTile("Ταπετσαρία", Icons.Rounded.Wallpaper, Color(0xFF34E0C6), Modifier.weight(1f), onWallpaper)
                MenuTile("Ρυθμίσεις", Icons.Rounded.Tune, Color(0xFF4DA3FF), Modifier.weight(1f), onSettings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MenuTile("Νέα σελίδα", Icons.Rounded.AddBox, Color(0xFFB9F35A), Modifier.weight(1f), onAddPage)
                MenuTile("Διαγραφή σελίδας", Icons.Rounded.Remove, Color(0xFFFF5A6A), Modifier.weight(1f), onRemovePage)
                MenuTile(
                    if (prefs.lockLayout) "Ξεκλείδωμα" else "Κλείδωμα",
                    if (prefs.lockLayout) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    Color(0xFFFFC23D), Modifier.weight(1f)
                ) { prefs.lockLayout = !prefs.lockLayout; onClose() }
            }
        }
    }
}

@Composable
private fun MenuTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.95f else 1f, Motion.snappy(Motion.icon), label = "tile")
    // σαν τις κάρτες του ταμπλό: κατάμαυρο, χωρίς χρωματιστό περίγραμμα, μόνο το εικονίδιο σε χρώμα
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .height(64.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .shadow(8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(if (pressed) Color(0xFF16161B) else Color.Black)
            .border(0.6.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Smoke.pearl, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
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
private fun DefaultBanner(activity: MainActivity, onDismiss: () -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .smokedGlass(RoundedCornerShape(24.dp), alpha = 0.72f)
            .clickable { onDismiss(); activity.requestHomeRole() }
            .pointerInput(Unit) {
                var t = 0f
                detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t < -30) onDismiss() }) { c, dy -> t += dy; c.consume() }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Κάνε το Crator αρχική οθόνη", color = Smoke.pearl, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Πάτα εδώ ή αργότερα από τις Ρυθμίσεις", color = Smoke.mist, fontSize = 12.sp)
        }
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
fun SearchPill(prefs: Prefs, modifier: Modifier = Modifier, onVoice: (() -> Unit)? = null, onClick: () -> Unit) {
    val h = prefs.searchHeight.dp
    val tc = Color(prefs.searchTextColor)
    Row(
        modifier
            .fillMaxWidth()
            .height(h)
            .smokedGlass(RoundedCornerShape(h / 2), alpha = prefs.searchOpacity / 100f, tint = Color(prefs.searchColor))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = tc, modifier = Modifier.size((h.value * 0.45f).dp))
        Spacer(Modifier.width(10.dp))
        Text("Αναζήτηση", color = tc, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (onVoice != null) Icon(
            Icons.Rounded.Mic, "Φωνητική αναζήτηση", tint = tc,
            modifier = Modifier.size((h.value * 0.45f).dp).clip(CircleShape).clickable(onClick = onVoice)
        )
    }
}

