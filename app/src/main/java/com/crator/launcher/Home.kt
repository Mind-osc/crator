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

enum class Overlay { None, Drawer, Settings, Widgets }

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
    }
    // Στην αναζήτηση φαίνεται καθαρά η ταπετσαρία: χωρίς θόλωμα πίσω από το παράθυρο
    LaunchedEffect(overlay, drawerSearchMode) {
        activity.setBlurBehind(if (overlay == Overlay.Drawer && !drawerSearchMode) prefs.blurStrength * 3 else 0)
    }

    fun openSearch(seed: String = "") {
        drawerSeed = seed; drawerSearchMode = true; overlay = Overlay.Drawer
    }

    fun act(a: GestureAction) {
        when (a) {
            GestureAction.None -> {}
            GestureAction.Search -> openSearch()
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
        if (overlay == Overlay.Drawer || folderOpen != null) {
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
        // το widget που αλλάζει μέγεθος ΔΕΝ θολώνει: φαίνονται οι αλλαγές ζωντανά
        sheetApp != null || folderSheet != null || homeMenu -> (prefs.blurStrength * 0.6f).dp
        else -> 0.dp
    }
    val blur by animateDpAsState(blurTarget, Motion.soft(Motion.overlay), label = "blur")
    val recede by androidx.compose.animation.core.animateFloatAsState(
        when {
            overlay == Overlay.Widgets -> 0.9f
            folderOpen != null || anySheet -> 0.95f
            drag.active && drag.moved -> 0.97f
            else -> 1f
        }, Motion.lively(Motion.overlay), label = "recede"
    )
    val onDashboard = prefs.dashboardEnabled && pager.currentPage == 0
    val swipeDownA by rememberUpdatedState { act(prefs.swipeDown) }
    val swipeUpA by rememberUpdatedState { act(prefs.swipeUp) }
    val doubleA by rememberUpdatedState { act(prefs.doubleTap) }
    val longA by rememberUpdatedState { scope.launch { delay(140); if (!drag.active && sheetApp == null && folderSheet == null && widgetSheet == null) { popupAnchor = drag.lastPointer; homeMenu = true } } }
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
                .then(if (blur > 0.3.dp) Modifier.blur(blur) else Modifier) // χωρίς blur = χωρίς επιπλέον layer στη GPU
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
                            onSearch = { openSearch() },
                            onSettings = ::openSettings,
                            bottomPad = barH,
                            editCell = widgetSheet?.takeIf { it.first == idx }?.second,
                            onEditCell = { c -> widgetSheet = if (c == null) null else idx to c },
                            onRemoveWidget = { c ->
                                val it0 = prefs.pages.getOrNull(idx)?.get(c)
                                prefs.removeHomeAt(idx, c)
                                if (it0 != null && Item.isWidget(it0)) widgets.delete(Item.widgetId(it0))
                                widgetSheet = null
                            }
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
                onPull = { px -> drawerTrack(1f - px / (travel() * 0.35f)) }, // κλείσιμο με μικρή κίνηση του δαχτύλου
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
                activity, prefs, repo, settingsSection,
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
            prefs, popupAnchor,
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
