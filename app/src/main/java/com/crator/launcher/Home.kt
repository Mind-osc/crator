@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.crator.launcher

import android.app.role.RoleManager
import android.provider.AlarmClock
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class Overlay { None, Drawer, Claude, Settings }

@Composable
fun CratorRoot(activity: MainActivity, prefs: Prefs, repo: AppRepository) {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Overlay.None) }
    var settingsSection by remember { mutableStateOf<String?>(null) }
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var claudeSeed by remember { mutableStateOf("") }
    var lastSheet by remember { mutableStateOf<AppInfo?>(null) }
    if (sheetApp != null && lastSheet !== sheetApp) lastSheet = sheetApp
    val apps = repo.apps.value
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val dashOffset = if (prefs.dashboardEnabled) 1 else 0
    val pageCount = prefs.homePages + dashOffset
    val pager = rememberPagerState(initialPage = dashOffset) { pageCount }
    val scope = rememberCoroutineScope()
    val chat = remember { ChatState() }
    val stats = remember { SysStats(activity) }
    val resumed = rememberResumed()
    val speed = prefs.animSpeed / 100f
    fun dur(ms: Int) = (ms / speed).toInt()

    // Κουμπί Home: κλείνει ό,τι είναι ανοιχτό ή γυρνάει στην πρώτη σελίδα
    val homeEv = activity.homeEvents.intValue
    LaunchedEffect(homeEv) {
        if (homeEv > 0) {
            if (overlay != Overlay.None || sheetApp != null || moving != null) {
                overlay = Overlay.None; sheetApp = null; moving = null
            } else pager.animateScrollToPage(dashOffset)
        }
    }
    BackHandler(enabled = overlay != Overlay.None || sheetApp != null || moving != null) {
        when {
            sheetApp != null -> sheetApp = null
            moving != null -> moving = null
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
            GestureAction.LauncherSettings -> { settingsSection = null; overlay = Overlay.Settings }
        }
    }

    fun openApp(app: AppInfo, b: android.graphics.Rect?) {
        Actions.launch(activity, app, b, prefs.openAnim)
        if (overlay == Overlay.Drawer || overlay == Overlay.Claude) {
            scope.launch { delay(350); overlay = Overlay.None }
        }
    }

    val blur by animateDpAsState(if (overlay != Overlay.None) prefs.blurStrength.dp else 0.dp, tween(dur(300)), label = "blur")
    val onDashboard = prefs.dashboardEnabled && pager.currentPage == 0
    val swipeDownA by rememberUpdatedState { act(prefs.swipeDown) }
    val swipeUpA by rememberUpdatedState { act(prefs.swipeUp) }
    val doubleA by rememberUpdatedState { act(prefs.doubleTap) }
    val gesturesOn by rememberUpdatedState(!onDashboard && overlay == Overlay.None && moving == null)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.wallpaperDim / 100f))) {
        // ---------------- Αρχική ----------------
        Column(
            Modifier
                .fillMaxSize()
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
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (gesturesOn) doubleA() }) }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (!isDefaultHome(activity, resumed)) DefaultBanner(activity)
            if (prefs.searchPos == SearchPos.Top && !onDashboard) SearchPill(prefs, Modifier.padding(16.dp, 8.dp)) { act(GestureAction.Claude) }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Box(Modifier.fillMaxSize().pageEffect(pager, page, prefs.pageEffect)) {
                    if (prefs.dashboardEnabled && page == 0) {
                        Dashboard(
                            stats, prefs, repo,
                            active = pager.currentPage == 0 && overlay == Overlay.None && resumed,
                            onOpenSettings = { settingsSection = null; overlay = Overlay.Settings }
                        )
                    } else {
                        val idx = page - dashOffset
                        HomePage(
                            idx, prefs, byKey, moving,
                            onLaunch = ::openApp,
                            onLong = { sheetApp = it },
                            onDrop = { cell -> moving?.let { prefs.moveTo(it, idx, cell) }; moving = null }
                        )
                    }
                }
            }
            if (prefs.pageIndicator) PageIndicator(pager, prefs.dashboardEnabled)
            if (moving != null) MovingBar { moving = null }
            if (prefs.searchPos == SearchPos.AboveDock) SearchPill(prefs, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { act(GestureAction.Claude) }
            Dock(prefs, byKey, onLaunch = ::openApp, onLong = { sheetApp = it }, onSearch = { act(GestureAction.Claude) })
        }

        if (overlay != Overlay.None) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))

        AnimatedVisibility(
            overlay == Overlay.Drawer,
            enter = slideInVertically(tween(dur(380))) { it / 2 } + fadeIn(tween(dur(260))),
            exit = slideOutVertically(tween(dur(280))) { it / 2 } + fadeOut(tween(dur(200)))
        ) {
            AppDrawer(
                apps, prefs,
                onLaunch = ::openApp,
                onLong = { sheetApp = it },
                onAskClaude = { q -> claudeSeed = q; overlay = Overlay.Claude },
                onClose = { overlay = Overlay.None }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Claude,
            enter = slideInVertically(tween(dur(420))) { -it / 3 } + fadeIn(tween(dur(260))) + scaleIn(tween(dur(420)), 0.92f, TransformOrigin(0.5f, 0f)),
            exit = slideOutVertically(tween(dur(260))) { -it / 3 } + fadeOut(tween(dur(200))) + scaleOut(tween(dur(260)), 0.94f, TransformOrigin(0.5f, 0f))
        ) {
            ClaudePanel(
                chat, prefs, apps, claudeSeed,
                onLaunch = ::openApp,
                onClose = { overlay = Overlay.None },
                onOpenSettings = { settingsSection = "claude"; overlay = Overlay.Settings }
            )
        }
        AnimatedVisibility(
            overlay == Overlay.Settings,
            enter = slideInHorizontally(tween(dur(340))) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(dur(280))) { it } + fadeOut()
        ) {
            SettingsScreen(
                activity, prefs, repo, chat, settingsSection,
                onSection = { settingsSection = it },
                onClose = { overlay = Overlay.None }
            )
        }
        AnimatedVisibility(
            sheetApp != null,
            enter = fadeIn(tween(dur(180))) + slideInVertically(tween(dur(300))) { it / 3 },
            exit = fadeOut(tween(dur(150)))
        ) {
            val app = lastSheet ?: return@AnimatedVisibility
            AppSheet(
                app, prefs, repo, activity,
                onClose = { sheetApp = null },
                onMove = { moving = app.key; sheetApp = null; overlay = Overlay.None },
                onAddHome = {
                    val target = (pager.currentPage - dashOffset).coerceAtLeast(0)
                    prefs.addToHome(app.key, target)
                }
            )
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
private fun HomePage(
    index: Int,
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    moving: String?,
    onLaunch: (AppInfo, android.graphics.Rect?) -> Unit,
    onLong: (AppInfo) -> Unit,
    onDrop: (Int) -> Unit
) {
    val items = prefs.pages.getOrNull(index) ?: emptyMap()
    Column(Modifier.fillMaxSize()) {
        if (index == 0 && prefs.clockStyle != ClockStyle.Hidden) ClockHeader(prefs)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            for (r in 0 until prefs.rows) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until prefs.cols) {
                        val cell = r * prefs.cols + c
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            val app = items[cell]?.let { byKey[it] }
                            if (moving != null && app?.key != moving) {
                                Box(
                                    Modifier
                                        .size(prefs.iconSize.dp)
                                        .clip(RoundedCornerShape(30))
                                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(30))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { onDrop(cell) }
                                )
                            }
                            if (app != null) {
                                AppCell(
                                    app, prefs, prefs.iconSize.dp, prefs.showLabels,
                                    onClick = { b -> if (moving != null) onDrop(cell) else onLaunch(app, b) },
                                    onLongClick = { onLong(app) },
                                    modifier = Modifier.graphicsLayer { alpha = if (moving == app.key) 0.4f else 1f }
                                )
                            }
                        }
                    }
                }
            }
        }
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
                Text(time, fontSize = 76.sp, fontWeight = FontWeight.Thin, color = Color.White, style = androidx.compose.ui.text.TextStyle(shadow = shadow))
                Text(date, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.text.TextStyle(shadow = shadow))
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
private fun MovingBar(onCancel: () -> Unit) {
    Row(
        Modifier.padding(16.dp, 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Πάτα ένα κελί για μετακίνηση (σύρε για άλλη σελίδα)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Άκυρο") }
    }
}

@Composable
fun SearchPill(prefs: Prefs, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = Color(prefs.dockColor).copy(alpha = (prefs.dockOpacity / 100f + 0.15f).coerceAtMost(1f))
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Ρώτα τον Claude ή ψάξε…", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Mic, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Dock(
    prefs: Prefs,
    byKey: Map<String, AppInfo>,
    onLaunch: (AppInfo, android.graphics.Rect?) -> Unit,
    onLong: (AppInfo) -> Unit,
    onSearch: () -> Unit
) {
    val style = prefs.dockStyle
    val all = prefs.dock.mapNotNull { byKey[it] }
    val items = if (style == DockStyle.Carousel) all else all.take(prefs.dockCount)
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
        DockStyle.Glass -> Modifier
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))), shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
        else -> Modifier.background(bgColor, shape)
    }
    val shadow = if (prefs.dockShadow && style != DockStyle.Minimal && style != DockStyle.Arc) Modifier.shadow(12.dp, shape) else Modifier
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(outer.then(shadow).clip(shape).then(bg).padding(vertical = 10.dp, horizontal = 8.dp)) {
            if (prefs.searchPos == SearchPos.InDock) {
                SearchPill(prefs, Modifier.padding(6.dp, 0.dp, 6.dp, 8.dp), onSearch)
            }
            if (items.isEmpty()) {
                Text("Πάτα παρατεταμένα μια εφαρμογή → «Στο dock»", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            } else if (style == DockStyle.Carousel) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    items(items, key = { it.key }) { app ->
                        AppCell(app, prefs, size, prefs.dockLabels, { onLaunch(app, it) }, { onLong(app) }, Modifier.width(size + 12.dp))
                    }
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
                    items.forEachIndexed { i, app ->
                        val lift = if (style == DockStyle.Arc && n > 1) {
                            val t = (i - (n - 1) / 2f) / ((n - 1) / 2f)
                            (-22f * t * t).dp
                        } else 0.dp
                        AppCell(
                            app, prefs, size, prefs.dockLabels, { onLaunch(app, it) }, { onLong(app) },
                            Modifier.width(size + 8.dp).offset(y = lift)
                        )
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
    onMove: () -> Unit,
    onAddHome: () -> Unit
) {
    val shortcuts = remember(app.key) { repo.shortcuts(app) }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF2161722))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp)
        ) {
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
            val chips = buildList<Pair<String, () -> Unit>> {
                add((if (onHome) "Αφαίρεση από αρχική" else "Στην αρχική") to { if (onHome) prefs.removeFromHome(app.key) else onAddHome(); onClose() })
                add((if (inDock) "Αφαίρεση από dock" else "Στο dock") to { if (inDock) prefs.removeFromDock(app.key) else prefs.addToDock(app.key); onClose() })
                if (onHome) add("Μετακίνηση" to onMove)
                add("Μετονομασία" to { renaming = true })
                add("Εικονίδιο" to { picking = true })
                add((if (hidden) "Εμφάνιση" else "Απόκρυψη") to { prefs.hidden = if (hidden) prefs.hidden - app.key else prefs.hidden + app.key; onClose() })
                add("Πληροφορίες" to { Actions.appInfo(activity, app); onClose() })
                add("Απεγκατάσταση" to { Actions.uninstall(activity, app); onClose() })
            }
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
    }
    if (renaming) {
        var name by remember { mutableStateOf(app.label) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Μετονομασία") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.labels = if (name.isBlank() || name == app.originalLabel) prefs.labels - app.key else prefs.labels + (app.key to name.trim())
                    repo.load(); renaming = false; onClose()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Άκυρο") } }
        )
    }
    if (picking) IconPicker(app, prefs, repo) { picking = false; onClose() }
}
