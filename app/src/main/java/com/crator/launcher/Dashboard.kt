@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Composable
fun Dashboard(
    stats: SysStats, prefs: Prefs, repo: AppRepository, active: Boolean,
    onOpenSettings: () -> Unit, onSearch: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    SideEffect {
        DashHooks.openCrator = onOpenSettings
        DashHooks.openSearch = onSearch
    }

    // ----- Live widgets: άδειες, πόλη καιρού, σημείωμα -----
    var cityDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { LiveScope.recheckAccess() }
    // επιστροφή από τις ρυθμίσεις (π.χ. «Πρόσβαση χρήσης») → οι πηγές ξαναελέγχουν
    LaunchedEffect(active) { if (active) LiveScope.recheckAccess() }
    val onAccess: (Access) -> Unit = { a ->
        when (a) {
            Access.City -> cityDialog = true
            Access.Calendar -> permLauncher.launch(android.Manifest.permission.READ_CALENDAR)
            Access.Activity -> permLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            Access.Usage -> Actions.start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Access.Notifications -> {
                val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, android.content.ComponentName(ctx, BadgeService::class.java).flattenToString())
                if (!Actions.start(ctx, detail)) Actions.start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
    if (cityDialog) TextDialog("Πόλη για τον καιρό", prefs.weatherCity, "π.χ. Αθήνα", singleLine = true,
        onDone = { prefs.weatherCity = it.trim(); cityDialog = false }, onDismiss = { cityDialog = false })
    if (noteDialog) TextDialog("Σημείωμα", prefs.dashNote, "Γράψε κάτι…", singleLine = false,
        onDone = { prefs.dashNote = it; noteDialog = false }, onDismiss = { noteDialog = false })

    // Ξεκινάει μόνο όταν η σελίδα είναι ενεργή, σταματάει αμέσως μόλις φύγεις
    LaunchedEffect(active, prefs.dashRefresh, prefs.dashHistory) {
        if (!active) return@LaunchedEffect
        stats.resetRates()
        while (isActive) {
            suspendRunCatching { stats.sample(prefs.dashHistory) }
            delay(prefs.dashRefresh.toLong())
        }
    }

    val torch by DashHooks.torch
    var editTitle by remember { mutableStateOf(false) }
    val d = LocalDensity.current
    val byKey = repo.apps.value.associateBy { it.key }

    // ----- Επεξεργασία: ✕ σε κάθε κομμάτι, + για προσθήκη, σύρσιμο για σειρά -----
    var editMode by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var appPick by remember { mutableStateOf(false) }
    BackHandler(enabled = editMode) { if (addMenu) { addMenu = false; appPick = false } else editMode = false }
    LaunchedEffect(active) { if (!active) { editMode = false; addMenu = false; appPick = false } }
    val items = prefs.dashItemsFull().filter { !it.startsWith("app:") || byKey.containsKey(it.removePrefix("app:")) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(items) }
    if (draggingId == null && live != items) live = items
    val editScale by animateFloatAsState(if (editMode) 0.96f else 1f, Motion.lively(Motion.sheet), label = "edit")

    fun remove(id: String) { prefs.dashItems = prefs.dashItemsFull() - id }
    fun add(id: String) { if (id !in prefs.dashItemsFull()) prefs.dashItems = prefs.dashItemsFull() + id }

    @Composable
    fun button(id: String, mod: Modifier) {
        val app = if (id.startsWith("app:")) byKey[id.removePrefix("app:")] else null
        val act = if (id.startsWith("sc:")) CATALOG_BY_ID[id.removePrefix("sc:")] else null
        val on = act?.id == "torch" && torch
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        // επιλεγμένο: ανεβαίνει σαν την καρτέλα «Home» της σκούρας μπάρας
        val sel by animateFloatAsState(if (pressed || on) 1f else 0f, Motion.lively(Motion.icon), label = "sel")
        val fg = androidx.compose.ui.graphics.lerp(Color(0xFF9C9CA6), accent, sel)
        Box(mod.padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = sel; translationY = (1f - sel) * 6.dp.toPx() }) {
                val r = 18.dp.toPx()
                drawRoundRect(Color(0xFF2A2A30), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                // καμπύλο περίγραμμα πάνω (η «καμπούρα» της επιλεγμένης καρτέλας)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height * 0.62f)
                    lineTo(0f, r)
                    quadraticBezierTo(0f, 0f, r, 0f)
                    lineTo(size.width - r, 0f)
                    quadraticBezierTo(size.width, 0f, size.width, r)
                    lineTo(size.width, size.height * 0.62f)
                }
                drawPath(p, Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0f))), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                    .clickable(interactionSource = src, indication = null, enabled = !editMode) {
                        when {
                            app != null -> Actions.launch(ctx as android.app.Activity, app, null, prefs.openAnim)
                            act != null -> act.run(ctx)
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    app != null -> AppIcon(app, 34.dp, prefs)
                    act != null -> Icon(act.icon, null, tint = fg, modifier = Modifier.size(32.dp))
                }
                Box(Modifier.padding(top = 3.dp).size(4.dp).graphicsLayer { alpha = sel; scaleX = sel; scaleY = sel }.clip(CircleShape).background(accent))
            }
        }
    }

    @Composable
    fun unit(id: String, mod: Modifier) {
        if (isButton(id)) { button(id, mod); return }
        val canTap = !editMode
        when (id) {
            "media" -> MediaCard(mod, canTap, onAccess)
            "weather" -> WeatherCard(prefs.weatherCity, mod, canTap, onAccess)
            "temp" -> TempCard(prefs.weatherCity, mod, canTap, onAccess)
            "sun" -> SunCard(prefs.weatherCity, mod, canTap, onAccess)
            "air" -> AirCard(prefs.weatherCity, mod, canTap, onAccess)
            "moon" -> MoonCard(mod)
            "calendar" -> CalendarCard(mod, canTap, onAccess)
            "alarm" -> AlarmCard(mod, canTap)
            "date" -> DateCard(mod)
            "month" -> MonthCard(mod)
            "progress" -> ProgressCard(mod)
            "steps" -> StepsCard(prefs.stepGoal, mod, canTap, onAccess)
            "screen" -> ScreenTimeCard(mod, canTap, onAccess)
            "data" -> DataCard(mod, canTap, onAccess)
            "note" -> NoteCard(prefs.dashNote, mod, canTap) { noteDialog = true }
            "volume" -> VolumeCard(mod, canTap)
            "brightness" -> BrightnessCard(mod, canTap)
            "clock" -> DashClock(prefs.dashClockDark, mod.clickable(enabled = !editMode) {
                Actions.start(ctx, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            // Γλυκόζη (Live Activity): τιμή πάνω αριστερά, μπάρες με χρώμα ανάλογα με το ύψος, άξονας χρόνου
            "cpu" -> LiveCard(mod) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Num("%.0f".format(stats.cpuPct), "%", Live.cpu[1])
                    Spacer(Modifier.weight(1f))
                    Meta("CPU, ${stats.cores.size} πυρήνες")
                }
                GlucoseBars(stats.cpuHist, 100f, Live.cpu, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                Axis("−1 λ", "−30 δ", "τώρα")
            }
            // Ηλιοβασίλεμα: τμήματα-κάψουλες με ετικέτες πάνω και τιμές κάτω
            "ram" -> LiveCard(mod) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DashTitle("RAM")
                    Spacer(Modifier.weight(1f))
                    Text("${(stats.ramUsed * 100 / stats.ramTotal)}%", color = Live.ram[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                val used = stats.ramUsed.toFloat() / stats.ramTotal
                Segments(
                    listOf(used, 1f - used),
                    listOf(Live.ram, listOf(Color(0xFF3A3A44), Color(0xFF2A2A31))),
                    listOf("Σε χρήση", "Ελεύθερη"),
                    listOf(fmtBytes(stats.ramUsed), fmtBytes(stats.ramTotal - stats.ramUsed))
                )
            }
            // ISS Flyover: θόλος με κώνο φωτός και φωτεινό σημείο
            "storage" -> LiveCard(mod.clickable(enabled = !editMode) { Actions.settings(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DashTitle("Χώρος")
                    Spacer(Modifier.weight(1f))
                    Text("${(stats.stUsed * 100 / stats.stTotal)}%", color = Live.storage[1], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                DomeGauge(stats.stUsed.toFloat() / stats.stTotal, Live.storage, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
                Meta("Ελεύθερα ${fmtBytes(stats.stTotal - stats.stUsed)}")
            }
            // Πίεση (mmHg): συμμετρικές στήλες από τελείες γύρω από τη μέση
            "net" -> LiveCard(mod) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Num(fmtRate(stats.rxRate).substringBefore(" "), " " + fmtRate(stats.rxRate).substringAfter(" "), Live.net[0])
                    Spacer(Modifier.weight(1f))
                    Meta(stats.netType)
                }
                PulseDots(stats.downHist, Live.net, Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp))
                Meta("Αποστολή ${fmtRate(stats.txRate)}")
            }
            // Meter Remaining: λεπτός δακτύλιος, ετικέτα, μεγάλη τιμή δεξιά + μπάρες ιστορικού σαν το τρέξιμο
            "battery" -> LiveCard(mod.clickable(enabled = !editMode) { Actions.settings(ctx, Intent.ACTION_POWER_USAGE_SUMMARY) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RingMeter(stats.batLevel / 100f, Live.battery[0], Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        DashTitle("Μπαταρία")
                        Meta(stats.batStatus)
                    }
                    Text("${stats.batLevel}%", color = Smoke.pearl, fontSize = 18.sp, fontWeight = FontWeight.Medium, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                }
                RunBars(stats.batHist, Live.battery[0], Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                Meta("%.1f °C, ${stats.batCurrent} mA".format(stats.batTemp))
            }
            // Espresso: χάρακας με αριθμούς, ράμπα χρώματος ως την τιμή, ετικέτα και μεγάλη τιμή κάτω
            "thermal" -> LiveCard(mod) {
                Axis("0", "50", "100")
                val load = (1f - (stats.headroom ?: 0.85f)).coerceIn(0.03f, 1f)
                EspressoRuler(load, Live.thermal, Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("Θερμικά", color = Live.thermal[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(stats.thermal, color = Live.thermal[0], fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }

    // ----- Μετακίνηση σαν τα εικονίδια -----
    // Ένας χειριστής αφής σε όλο το πλέγμα (δεν ακυρώνεται ποτέ όταν αλλάζει η σειρά),
    // το κομμάτι ακολουθεί το δάχτυλο και τα υπόλοιπα γλιστράνε με κίνηση στις νέες θέσεις.
    var gridSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var pointer by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var grab by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    fun endDrag() { if (draggingId != null) { prefs.dashItems = live; draggingId = null } }
    LaunchedEffect(editMode) { if (!editMode) endDrag(); DashHooks.editing.value = editMode }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { DashHooks.editing.value = false } }

    CompositionLocalProvider(
        LocalDensity provides Density(d.density * prefs.dashScale / 100f, d.fontScale),
        LocalDashStyle provides (Color(prefs.dashColor) to prefs.dashOpacity / 100f)
    ) {
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Μόνο τα κουμπιά επεξεργασίας πάνω δεξιά (χωρίς τίτλο)
        // σταθερό ύψος: όταν εμφανίζονται τα κουμπιά, ο πίνακας ΔΕΝ μετακινείται (αλλιώς το σηκωμένο κομμάτι «πηδάει»)
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.AnimatedVisibility(
                editMode,
                enter = androidx.compose.animation.scaleIn(Motion.pop(Motion.sheet)) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).smokedGlass(CircleShape, alpha = 0.6f, tint = accent).clickable { addMenu = !addMenu; appPick = false },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, "Προσθήκη", tint = Smoke.pearl) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).smokedGlass(CircleShape, alpha = 0.6f).clickable { editMode = false; addMenu = false },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Check, "Τέλος", tint = Smoke.pearl) }
                }
            }
        }

        // Γραμμές: κάρτες ανά 2, κουμπιά ανά 4, με τη σειρά που τα έχεις βάλει
        val rows = mutableListOf<MutableList<String>>()
        val hasClock = "clock" in live
        var clockLive by remember { mutableStateOf<Float?>(null) } // dp όσο αλλάζεις μέγεθος
        val clockDp = clockLive ?: prefs.dashClockH.toFloat()
        for (id in live) {
            if (id == "clock") continue
            val cap = if (isButton(id)) 5 else 2
            val last = rows.lastOrNull()
            // φαρδιές/μεγάλες κάρτες πιάνουν όλη τη γραμμή· οι μισές μπαίνουν ανά δύο
            val half = isButton(id) || cardSpan(id) == CardSpan.Half
            val lastHalf = last != null && (isButton(last[0]) || cardSpan(last[0]) == CardSpan.Half)
            if (last != null && half && lastHalf && isButton(last[0]) == isButton(id) && last.size < cap) last += id else rows += mutableListOf(id)
        }

        // Θέσεις (σε px μέσα στο πλέγμα): γραμμή καρτών = 1 μερίδα, γραμμή κουμπιών = 0,62
        val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        val capsules = mutableListOf<androidx.compose.ui.unit.IntRect>()
        val cardRowPx = with(LocalDensity.current) { 100.dp.roundToPx() }
        val btnRowPx = with(LocalDensity.current) { 58.dp.roundToPx() }
        val padPx = with(LocalDensity.current) { 10.dp.roundToPx() }
        val pillPadPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        var board = androidx.compose.ui.unit.IntRect.Zero
        val dDash = LocalDensity.current.density
        var liftLive by remember { mutableStateOf<Float?>(null) } // px όσο το σέρνεις
        val liftPx = liftLive ?: (prefs.dashLift * dDash)
        val rects: Map<String, androidx.compose.ui.unit.IntRect> = run {
            val m = LinkedHashMap<String, androidx.compose.ui.unit.IntRect>()
            val fullW = gridSize.width; val fullH = gridSize.height
            // ο πίνακας: ίδιο κενό γύρω-γύρω (πάνω = πλάγια)
            val w = fullW - padPx * 2; val h = fullH - padPx * 2
            if (w > 0 && h > 0 && (rows.isNotEmpty() || hasClock)) {
                // σταθερά ύψη (κάρτα ~100dp, κουμπιά ~70dp)· αν δεν χωράνε, μικραίνουν αναλογικά
                // το ρολόι στέκεται πάνω από τον πίνακα, χωρίς φόντο (φαίνεται η ταπετσαρία)
                val clockPx = if (hasClock) (clockDp * dDash).toInt() else 0
                val want = (if (hasClock) listOf(clockPx) else emptyList()) + rows.map { if (isButton(it[0])) btnRowPx else if (cardSpan(it[0]) == CardSpan.Big) cardRowPx * 2 + gapPx else cardRowPx }
                val gaps = gapPx * (want.size - 1).coerceAtLeast(0)
                val total = want.sum() + gaps
                val k = if (total > h) (h - gaps).toFloat() / want.sum() else 1f
                val used = (want.sum() * k).toInt() + gaps
                val free = (h - used).coerceAtLeast(0)
                val lift = liftPx.toInt().coerceIn(0, free)
                var y = padPx + free - lift
                if (hasClock) {
                    val ch = (clockPx * k).toInt()
                    m["clock"] = androidx.compose.ui.unit.IntRect(padPx, y, padPx + w, y + ch)
                    y += ch + gapPx
                }
                board = if (rows.isEmpty()) androidx.compose.ui.unit.IntRect.Zero
                    else androidx.compose.ui.unit.IntRect(0, y - padPx, fullW, fullH - lift)
                val btnW = (w - pillPadPx * 2) / 5
                val rowWant = if (hasClock) want.drop(1) else want
                rows.forEachIndexed { r, row ->
                    val rh = (rowWant[r] * k).toInt()
                    if (isButton(row[0])) {
                        // κάψουλα όσο χρειάζεται, στο κέντρο· στενεύει όταν βγάζεις κουμπί
                        val pillW = row.size * btnW + pillPadPx * 2
                        val x0 = padPx + (w - pillW) / 2
                        capsules += androidx.compose.ui.unit.IntRect(x0, y, x0 + pillW, y + rh)
                        row.forEachIndexed { c, id ->
                            val x = x0 + pillPadPx + c * btnW
                            m[id] = androidx.compose.ui.unit.IntRect(x, y, x + btnW, y + rh)
                        }
                    } else if (cardSpan(row[0]) != CardSpan.Half) {
                        m[row[0]] = androidx.compose.ui.unit.IntRect(padPx, y, padPx + w, y + rh)
                    } else {
                        val cw = (w - gapPx) / 2
                        row.forEachIndexed { c, id ->
                            val x = padPx + c * (cw + gapPx)
                            m[id] = androidx.compose.ui.unit.IntRect(x, y, x + cw, y + rh)
                        }
                    }
                    y += rh + gapPx
                }
            }
            m
        }
        val rectsNow by androidx.compose.runtime.rememberUpdatedState(rects)
        val boardNow by androidx.compose.runtime.rememberUpdatedState(board)
        val liftNow by androidx.compose.runtime.rememberUpdatedState(liftPx)
        val hapticDash = androidx.compose.ui.platform.LocalHapticFeedback.current
        val liveNow by androidx.compose.runtime.rememberUpdatedState(live)

        // Η αφή (σαν τα εικονίδια):
        //  - εκτός επεξεργασίας: παρατεταμένο πάτημα → μπαίνει σε επεξεργασία και σηκώνεται στο δάχτυλο αμέσως
        //  - σε επεξεργασία: πάτημα + κίνηση → σύρσιμο. Το ✕ μένει απλό πάτημα.
        // Ο χειριστής είναι «remember», ώστε να μην ξαναστήνεται και να μη χάνει το δάχτυλο στη μέση.
        val editNow by androidx.compose.runtime.rememberUpdatedState(editMode)
        val endNow by androidx.compose.runtime.rememberUpdatedState({ endDrag() })
        val enterEdit by androidx.compose.runtime.rememberUpdatedState({ editMode = true })
        val handler: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit = remember {
            {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = rectsNow.entries.firstOrNull { (_, r) ->
                        down.position.x >= r.left && down.position.x < r.right && down.position.y >= r.top && down.position.y < r.bottom
                    }
                    if (hit == null) {
                        // παρατεταμένο πάτημα στον πίνακα (όχι σε κάρτα) → τον μετακινείς πάνω/κάτω όσο ακριβώς θες
                        val b = boardNow
                        if (down.position.y < b.top || down.position.y > b.bottom) return@awaitEachGesture
                        awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        hapticDash.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val startLift = liftNow
                        val maxLift = startLift + b.top.toFloat()
                        val y0 = down.position.y
                        liftLive = startLift
                        drag(down.id) { c ->
                            c.consume()
                            liftLive = (startLift + (y0 - c.position.y)).coerceIn(0f, maxLift)
                        }
                        prefs.dashLift = ((liftLive ?: startLift) / dDash).toInt()
                        liftLive = null
                        return@awaitEachGesture
                    }
                    val id = hit.key
                    val start = if (editNow) {
                        awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                    } else {
                        awaitLongPressOrCancellation(down.id)?.also { enterEdit() }
                    } ?: return@awaitEachGesture
                    start.consume()
                    grab = down.position - androidx.compose.ui.geometry.Offset(hit.value.left.toFloat(), hit.value.top.toFloat())
                    pointer = start.position
                    draggingId = id
                    var lastSwap = 0L
                    try {
                        drag(start.id) { c ->
                            c.consume()
                            pointer = c.position
                            val mine = rectsNow[id] ?: return@drag
                            val center = pointer - grab + androidx.compose.ui.geometry.Offset(mine.width / 2f, mine.height / 2f)
                            val now = System.currentTimeMillis()
                            if (now - lastSwap < 140) return@drag
                            // στόχος: το κομμάτι κάτω από το κέντρο, μόνο στο εσωτερικό 70% (όχι τρεμόπαιγμα στα όρια)
                            val target = rectsNow.entries.firstOrNull { (k, r) ->
                                if (k == id || k == "clock" || id == "clock") false else {
                                    val mx = r.width * 0.15f; val my = r.height * 0.15f
                                    center.x > r.left + mx && center.x < r.right - mx && center.y > r.top + my && center.y < r.bottom - my
                                }
                            } ?: return@drag
                            val l = liveNow
                            val from = l.indexOf(id); val to = l.indexOf(target.key)
                            if (from >= 0 && to >= 0 && from != to) {
                                live = l.toMutableList().also { x -> x.removeAt(from); x.add(to, id) }
                                lastSwap = now
                            }
                        }
                    } finally {
                        endNow()
                    }
                }
            }
        }
        val dragInput = Modifier.pointerInput(Unit, handler)

        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { gridSize = it }
                .then(dragInput)
        ) {
            // ο ανθρακί πίνακας: μόνο πίσω από τα widgets, καμπύλες ομόκεντρες με τις κάρτες (28 + 10)
            if (board.height > 0) {
                val d1 = LocalDensity.current
                val bs = RoundedCornerShape(38.dp)
                Box(
                    Modifier.offset { board.topLeft }
                        .size(with(d1) { board.width.toDp() }, with(d1) { board.height.toDp() })
                        // χωρίς σκιά: κάτω από ημιδιάφανο φόντο η σκιά φαίνεται μέσα του σαν γκρίζες λωρίδες στα πλάγια
                        .clip(bs)
                        .background(Color(prefs.dashColor).copy(alpha = prefs.dashOpacity / 100f))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.045f), Color.Transparent)))
                        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))), bs)
                )
            }
            // κάθε σειρά κουμπιών = μία μπάρα-κάψουλα (σαν τη μπάρα της Pinterest)
            capsules.forEach { r ->
                val d2 = LocalDensity.current
                Box(
                    Modifier.offset { r.topLeft }
                        .size(with(d2) { r.width.toDp() }, with(d2) { r.height.toDp() })
                        .shadow(10.dp, RoundedCornerShape(50), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black)
                        .border(0.6.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
                )
            }
            if (rows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Άδειο ταμπλό. Πάτα + για να προσθέσεις.", color = Smoke.mist, fontSize = 13.sp)
            }
            live.forEach { id ->
                val r = rects[id]
                if (r != null) androidx.compose.runtime.key(id) {
                    val isDrag = draggingId == id
                    val slide by androidx.compose.animation.core.animateIntOffsetAsState(
                        r.topLeft,
                        androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f),
                        label = "slide"
                    )
                    val lift by animateFloatAsState(if (isDrag) 1.07f else 1f, Motion.pop(Motion.drag), label = "lift")
                    Box(
                        Modifier
                            .zIndex(if (isDrag) 2f else 0f)
                            .offset {
                                if (isDrag) androidx.compose.ui.unit.IntOffset((pointer.x - grab.x).toInt(), (pointer.y - grab.y).toInt())
                                else slide
                            }
                            .layout { m, _ ->
                                val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(r.width, r.height))
                                layout(r.width, r.height) { p.place(0, 0) }
                            }
                            .graphicsLayer {
                                val sc = editScale * lift
                                scaleX = sc; scaleY = sc
                                if (isDrag) shadowElevation = 24f
                            }
                    ) {
                        unit(id, Modifier.fillMaxSize())
                        if (editMode && id == "clock") {
                            // μέγεθος: σύρε τη λαβή πάνω/κάτω (σαν τα widgets)
                            Box(
                                Modifier.align(Alignment.BottomCenter).offset(y = 14.dp).size(96.dp, 32.dp)
                                    .pointerInput(Unit) {
                                        var startDp = 0f
                                        var acc = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { startDp = prefs.dashClockH.toFloat(); acc = 0f; clockLive = startDp },
                                            onDragEnd = { prefs.dashClockH = (clockLive ?: startDp).toInt(); clockLive = null },
                                            onDragCancel = { clockLive = null }
                                        ) { c, dy ->
                                            c.consume(); acc += dy
                                            clockLive = (startDp + acc / density).coerceIn(56f, 320f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(48.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                            }
                            // μαύρα / λευκά γράμματα
                            Box(
                                Modifier.align(Alignment.TopStart).offset((-14).dp, (-14).dp).size(44.dp)
                                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { prefs.dashClockDark = !prefs.dashClockDark },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape)
                                        .background(if (prefs.dashClockDark) Color.White else Color.Black)
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("A", color = if (prefs.dashClockDark) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (editMode) {
                            val xs = if (isButton(id)) 22.dp else 26.dp
                            // ορατό 22–26dp, αλλά περιοχή αφής 44dp
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(14.dp, (-14).dp).size(44.dp)
                                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { remove(id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(xs).smokedGlass(CircleShape, alpha = 0.9f, tint = Smoke.danger), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Close, "Αφαίρεση", tint = Color.White, modifier = Modifier.size(xs * 0.55f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Μενού «+»: κάρτες, συντομεύσεις και εφαρμογές που δεν υπάρχουν ήδη
    if (addMenu && editMode) Box(
        Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { addMenu = false; appPick = false }
    )
    androidx.compose.animation.AnimatedVisibility(
        addMenu && editMode,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 10.dp),
        enter = androidx.compose.animation.scaleIn(Motion.lively(Motion.sheet), 0.6f, androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut(Motion.snappy(Motion.sheet), 0.8f, androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + androidx.compose.animation.fadeOut()
    ) {
        val current = prefs.dashItemsFull()
        Column(
            Modifier.width(290.dp).heightIn(max = 520.dp)
                .shadow(30.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .smokedGlass(RoundedCornerShape(26.dp), alpha = 0.92f, tint = Color(0xFF09090C))
                .padding(10.dp)
        ) {
            if (appPick) {
                var q by remember { mutableStateOf("") }
                SearchField(prefs, q, { q = it }, "Αναζήτηση εφαρμογής", null, onGo = {})
                Spacer(Modifier.height(6.dp))
                val nq = q.norm().trim()
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(repo.apps.value.filter { "app:${it.key}" !in current && (nq.isEmpty() || it.normLabel.contains(nq)) }, key = { it.key }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { add("app:${a.key}") }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(a, 30.dp, prefs)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, color = Smoke.pearl, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val info = DASH_INFO.filter { it.first !in current }
                    if (info.isNotEmpty()) {
                        MenuTitle("Κάρτες")
                        info.forEach { (id, name) -> AddRow(Icons.Rounded.Insights, name, accent) { add(id) } }
                    }
                    MenuTitle("Εφαρμογή ως κουμπί")
                    AddRow(Icons.Rounded.Apps, "Διάλεξε εφαρμογή…", accent) { appPick = true }
                    val sc = DASH_CATALOG.filter { "sc:${it.id}" !in current }
                    if (sc.isNotEmpty()) {
                        MenuTitle("Συντομεύσεις")
                        sc.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { a ->
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
                                            .clickable { add("sc:${a.id}") }.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(a.icon, null, tint = Smoke.pearl, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.height(4.dp))
                                        Text(a.label, color = Smoke.mist, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
    }
    }
    if (editTitle) RenameDialog(prefs.dashTitle, { editTitle = false }) { prefs.dashTitle = it }
}

@Composable
private fun MenuTitle(t: String) =
    Text(t, color = Smoke.mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp, 10.dp, 8.dp, 6.dp))

@Composable
private fun AddRow(icon: ImageVector, name: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, color = Smoke.pearl, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private val LocalDashStyle = androidx.compose.runtime.compositionLocalOf { Smoke.tint to 0.58f }

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .let { m -> val (c, a) = LocalDashStyle.current; m.smokedGlass(RoundedCornerShape(26.dp), alpha = a, tint = c) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
private fun Header(title: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Sub(t: String) = Text(t, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))

@Composable
private fun Bar(f: Float, color: Color) {
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(color))
    }
}

@Composable
private fun TextDialog(title: String, initial: String, hint: String, singleLine: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111114),
        title = { Text(title, color = Smoke.pearl) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                text, { text = it }, placeholder = { Text(hint) }, singleLine = singleLine,
                minLines = if (singleLine) 1 else 3, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(text) }) { Text("OK") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}
