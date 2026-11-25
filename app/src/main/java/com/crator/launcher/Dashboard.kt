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
fun rememberResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var r by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ -> r = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return r
}

/** Δεδομένα συστήματος. Δειγματοληψία ΜΟΝΟ όταν είναι ανοιχτή η σελίδα. */
class SysStats(private val activity: Activity) {
    val cpuHist = mutableStateListOf<Float>()
    val ramHist = mutableStateListOf<Float>()
    val downHist = mutableStateListOf<Float>()
    val upHist = mutableStateListOf<Float>()
    val batHist = mutableStateListOf<Float>()

    var cpuPct by mutableStateOf(0f)
    var cpuIsFreq by mutableStateOf(true)
    var cores by mutableStateOf<List<Pair<Long, Long>>>(emptyList())
    var ramUsed by mutableStateOf(0L)
    var ramTotal by mutableStateOf(1L)
    var lowMem by mutableStateOf(false)
    var stUsed by mutableStateOf(0L)
    var stTotal by mutableStateOf(1L)
    var netType by mutableStateOf("—")
    var rxRate by mutableStateOf(0L)
    var txRate by mutableStateOf(0L)
    var rxTotal by mutableStateOf(0L)
    var txTotal by mutableStateOf(0L)
    var bandwidth by mutableStateOf(0)
    var rssi by mutableStateOf<Int?>(null)
    var batLevel by mutableStateOf(0)
    var batTemp by mutableStateOf(0f)
    var batVolt by mutableStateOf(0)
    var batCurrent by mutableStateOf(0)
    var batStatus by mutableStateOf("—")
    var batHealth by mutableStateOf("—")
    var batCycles by mutableStateOf(-1)
    var thermal by mutableStateOf("—")
    var headroom by mutableStateOf<Float?>(null)
    var uptime by mutableStateOf(0L)

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastT = 0L
    private var lastCpu: LongArray? = null
    private val maxFreq = HashMap<Int, Long>()

    fun resetRates() { lastRx = -1; lastTx = -1; lastCpu = null }

    private fun readLong(p: String): Long? = runCatching { File(p).readText().trim().toLong() }.getOrNull()

    private fun <T> push(list: MutableList<T>, v: T, max: Int) {
        list.add(v); while (list.size > max) list.removeAt(0)
    }

    @SuppressLint("MissingPermission")
    suspend fun sample(maxHist: Int) {
        // Βαριές αναγνώσεις στο IO
        val cpu = withContext(Dispatchers.IO) {
            val n = Runtime.getRuntime().availableProcessors()
            val list = (0 until n).map { i ->
                val cur = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq") ?: 0L
                val max = maxFreq.getOrPut(i) { readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq") ?: 0L }
                cur to max
            }
            val stat = runCatching { File("/proc/stat").bufferedReader().use { it.readLine() } }.getOrNull()
            list to stat
        }
        cores = cpu.first
        val statLine = cpu.second
        var pct: Float? = null
        if (statLine != null && statLine.startsWith("cpu ")) {
            val v = statLine.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }.toLongArray()
            val prev = lastCpu
            if (prev != null && v.size >= 4 && prev.size == v.size) {
                val idle = (v[3] + v.getOrElse(4) { 0 }) - (prev[3] + prev.getOrElse(4) { 0 })
                val total = v.sum() - prev.sum()
                if (total > 0) pct = (1f - idle.toFloat() / total) * 100f
            }
            lastCpu = v
        }
        if (pct == null) {
            val valid = cores.filter { it.second > 0 }
            pct = if (valid.isEmpty()) 0f else valid.map { it.first.toFloat() / it.second }.average().toFloat() * 100f
            cpuIsFreq = true
        } else cpuIsFreq = false
        cpuPct = pct.coerceIn(0f, 100f)
        push(cpuHist, cpuPct, maxHist)

        // RAM
        val am = activity.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        ramTotal = mi.totalMem; ramUsed = mi.totalMem - mi.availMem; lowMem = mi.lowMemory
        push(ramHist, ramUsed * 100f / ramTotal, maxHist)

        // Αποθηκευτικός χώρος
        runCatching {
            val sf = StatFs(Environment.getDataDirectory().path)
            stTotal = sf.blockCountLong * sf.blockSizeLong
            stUsed = stTotal - sf.availableBlocksLong * sf.blockSizeLong
        }

        // Δίκτυο
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes()
        if (lastRx >= 0 && now > lastT) {
            val dt = (now - lastT) / 1000f
            rxRate = ((rx - lastRx) / dt).toLong().coerceAtLeast(0)
            txRate = ((tx - lastTx) / dt).toLong().coerceAtLeast(0)
        }
        lastRx = rx; lastTx = tx; lastT = now
        rxTotal = rx; txTotal = tx
        push(downHist, rxRate / 1024f, maxHist)
        push(upHist, txRate / 1024f, maxHist)
        val cm = activity.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        netType = when {
            caps == null -> "Εκτός σύνδεσης"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Κινητά δεδομένα"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Άλλο"
        }
        bandwidth = caps?.linkDownstreamBandwidthKbps ?: 0
        rssi = if (netType == "Wi-Fi") runCatching {
            @Suppress("DEPRECATION")
            activity.applicationContext.getSystemService(WifiManager::class.java).connectionInfo.rssi
        }.getOrNull() else null

        // Μπαταρία
        val bi = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, 0); val sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batLevel = if (sc > 0) lvl * 100 / sc else lvl
            batTemp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            batVolt = bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            batStatus = when (bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> when (bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Φόρτιση (ασύρματη)"
                    BatteryManager.BATTERY_PLUGGED_USB -> "Φόρτιση (USB)"
                    else -> "Φόρτιση"
                }
                BatteryManager.BATTERY_STATUS_FULL -> "Πλήρης"
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Αποφόρτιση"
                else -> "—"
            }
            batHealth = when (bi.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Καλή"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Υπερθέρμανση"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Νεκρή"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Υπέρταση"
                BatteryManager.BATTERY_HEALTH_COLD -> "Κρύα"
                else -> "Άγνωστη"
            }
            batCycles = bi.getIntExtra("android.os.extra.CYCLE_COUNT", -1)
        }
        val bm = activity.getSystemService(BatteryManager::class.java)
        var cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (cur != Int.MIN_VALUE) {
            if (abs(cur) > 20000) cur /= 1000  // μA → mA
            batCurrent = cur
        }
        push(batHist, batTemp, maxHist)

        // Θερμικά
        val pm = activity.getSystemService(PowerManager::class.java)
        thermal = when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Κανονική"
            PowerManager.THERMAL_STATUS_LIGHT -> "Ελαφριά"
            PowerManager.THERMAL_STATUS_MODERATE -> "Μέτρια"
            PowerManager.THERMAL_STATUS_SEVERE -> "Σοβαρή"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Κρίσιμη"
            PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "Έκτακτη"
            else -> "—"
        }
        headroom = runCatching { pm.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() }
        uptime = SystemClock.elapsedRealtime()
    }
}

fun fmtBytes(b: Long): String {
    val u = listOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, if (i >= 3) "%.1f %s" else "%.0f %s", v, u[i])
}

fun fmtRate(bps: Long) = fmtBytes(bps) + "/s"

fun fmtUptime(ms: Long): String {
    val m = ms / 60000; val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
    return if (d > 0) "${d}μ ${h}ω ${mm}λ" else "${h}ω ${mm}λ"
}

private class Shortcut(val label: String, val icon: ImageVector, val run: () -> Unit)

@Composable
fun Dashboard(stats: SysStats, prefs: Prefs, repo: AppRepository, active: Boolean, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    // Ξεκινάει μόνο όταν η σελίδα είναι ενεργή, σταματάει αμέσως μόλις φύγεις
    LaunchedEffect(active, prefs.dashRefresh, prefs.dashHistory) {
        if (!active) return@LaunchedEffect
        stats.resetRates()
        while (isActive) {
            runCatching { stats.sample(prefs.dashHistory) }
            delay(prefs.dashRefresh.toLong())
        }
    }

    var torch by remember { mutableStateOf(false) }
    val shortcuts = listOf(
        Shortcut("Ρυθμίσεις", Icons.Rounded.Settings) { Actions.settings(ctx, Settings.ACTION_SETTINGS) },
        Shortcut("Wi-Fi", Icons.Rounded.Wifi) { Actions.settings(ctx, Settings.ACTION_WIFI_SETTINGS) },
        Shortcut("Bluetooth", Icons.Rounded.Bluetooth) { Actions.settings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) },
        Shortcut("Αρχεία", Icons.Rounded.Folder) { Actions.openFiles(ctx) },
        Shortcut("Μπαταρία", Icons.Rounded.BatteryChargingFull) { Actions.settings(ctx, Intent.ACTION_POWER_USAGE_SUMMARY) },
        Shortcut("Οθόνη", Icons.Rounded.BrightnessMedium) { Actions.settings(ctx, Settings.ACTION_DISPLAY_SETTINGS) },
        Shortcut("Ήχος", Icons.Rounded.VolumeUp) { Actions.settings(ctx, Settings.ACTION_SOUND_SETTINGS) },
        Shortcut("Χώρος", Icons.Rounded.Storage) { Actions.settings(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
        Shortcut("Εφαρμογές", Icons.Rounded.Apps) { Actions.settings(ctx, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) },
        Shortcut("Δεδομένα", Icons.Rounded.DataUsage) { Actions.settings(ctx, Settings.ACTION_DATA_USAGE_SETTINGS) },
        Shortcut("Τοποθεσία", Icons.Rounded.LocationOn) { Actions.settings(ctx, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        Shortcut("Ασφάλεια", Icons.Rounded.Security) { Actions.settings(ctx, Settings.ACTION_SECURITY_SETTINGS) },
        Shortcut("Ειδοποιήσεις", Icons.Rounded.Notifications) { Actions.expandStatusBar(ctx, false) },
        Shortcut("Φακός", Icons.Rounded.FlashlightOn) { torch = Actions.toggleTorch(ctx) },
        Shortcut("Developer", Icons.Rounded.DeveloperMode) { Actions.settings(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
        Shortcut("Crator", Icons.Rounded.Tune) { onOpenSettings() }
    )

    var editTitle by remember { mutableStateOf(false) }
    val d = LocalDensity.current
    val visibleShortcuts = shortcuts.filter { it.label !in prefs.dashHiddenShortcuts }

    // ----- Λειτουργία επεξεργασίας: ✕ για απόκρυψη, + για προσθήκη, σύρσιμο για σειρά -----
    var editMode by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = editMode) { editMode = false; addMenu = false }
    LaunchedEffect(active) { if (!active) { editMode = false; addMenu = false } }
    val order = prefs.dashOrderFull()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(order) }
    if (draggingId == null && live != order) live = order
    val visible = live.filter { prefs.dashEnabled(it) && (it != "shortcuts" || visibleShortcuts.isNotEmpty()) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val hidden = DASH_UNITS.filter { !prefs.dashEnabled(it.first) }
    val editScale by animateFloatAsState(if (editMode) 0.97f else 1f, Motion.lively(Motion.sheet), label = "edit")

    @Composable
    fun unit(id: String, mod: Modifier) {
        when (id) {
            "shortcuts" -> Column(mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleShortcuts.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { s ->
                            val on = s.label == "Φακός" && torch
                            val (tc, ta) = LocalDashStyle.current
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .smokedGlass(RoundedCornerShape(22.dp), alpha = if (on) 0.9f else ta, tint = if (on) accent else tc)
                                    .clickable(enabled = !editMode) { s.run() },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(s.icon, null, tint = if (on) Color.Black else Smoke.pearl, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(s.label, color = if (on) Color.Black else Smoke.pearl.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            "cpu" -> Card(mod) {
                Header("CPU", "%.0f%%".format(stats.cpuPct), accent)
                Sub("${stats.cores.size} πυρήνες" + if (stats.cpuIsFreq) ", συχνότητα" else "")
                Spacer(Modifier.weight(1f))
                LineChart(stats.cpuHist, 100f, accent, height = 40)
                Row(Modifier.fillMaxWidth().height(14.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    stats.cores.forEach { (cur, max) ->
                        val f = if (max > 0) (cur.toFloat() / max).coerceIn(0.08f, 1f) else 0.08f
                        Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.4f + 0.6f * f)))
                    }
                }
            }
            "ram" -> Card(mod) {
                Header("RAM", "", accent)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Ring(stats.ramUsed.toFloat() / stats.ramTotal, Color(0xFF7DE3C2), "${(stats.ramUsed * 100 / stats.ramTotal)}%", size = 66)
                }
                Sub("${fmtBytes(stats.ramUsed)} / ${fmtBytes(stats.ramTotal)}")
            }
            "storage" -> Card(mod.clickable(enabled = !editMode) { Actions.settings(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }) {
                Header("Χώρος", "", accent)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Ring(stats.stUsed.toFloat() / stats.stTotal, Color(0xFFFFB86B), "${(stats.stUsed * 100 / stats.stTotal)}%", size = 66)
                }
                Sub("Ελεύθερα ${fmtBytes(stats.stTotal - stats.stUsed)}")
            }
            "net" -> Card(mod) {
                Header("Δίκτυο", "↓ ${fmtRate(stats.rxRate)}", Color(0xFF6FC3FF))
                Sub("↑ ${fmtRate(stats.txRate)}, ${stats.netType}")
                Spacer(Modifier.weight(1f))
                DualChart(stats.downHist, stats.upHist, Color(0xFF6FC3FF), Color(0xFFFF7FB0), height = 44)
            }
            "battery" -> Card(mod.clickable(enabled = !editMode) { Actions.settings(ctx, Intent.ACTION_POWER_USAGE_SUMMARY) }) {
                Header("Μπαταρία", "", Color(0xFF9BE37D))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Ring(stats.batLevel / 100f, Color(0xFF9BE37D), "${stats.batLevel}%", size = 66)
                }
                Sub("%.1f °C, ${stats.batCurrent} mA".format(stats.batTemp))
            }
            "thermal" -> Card(mod) {
                Header("Θερμικά", "", Color(0xFFFF8A65))
                Spacer(Modifier.weight(1f))
                Text(stats.thermal, color = Color(0xFFFF8A65), fontSize = 24.sp, fontWeight = FontWeight.Light)
                stats.headroom?.let { h -> Bar((1f - h).coerceIn(0f, 1f), Color(0xFFFF8A65)) }
                Spacer(Modifier.weight(1f))
                Sub(stats.batStatus)
            }
        }
    }

    // θέσεις για τη μετακίνηση σε πλέγμα
    val slots = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var container by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var dragOff by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var lastSwap by remember { mutableStateOf(0L) }

    CompositionLocalProvider(
        LocalDensity provides Density(d.density * prefs.dashScale / 100f, d.fontScale),
        LocalDashStyle provides (Color(prefs.dashColor) to prefs.dashOpacity / 100f)
    ) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Κεφαλίδα
        Row(Modifier.fillMaxWidth().padding(6.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    prefs.dashTitle.ifEmpty { " " }, color = Smoke.pearl, fontSize = 30.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-1).sp,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { editTitle = true })
                )
                if (prefs.dashShowSub) Text(
                    "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, σε λειτουργία ${fmtUptime(stats.uptime)}",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {
                        prefs.dashShowSub = false
                        android.widget.Toast.makeText(ctx, "Κρύφτηκε. Επαναφορά από Ρυθμίσεις → Ταμπλό", android.widget.Toast.LENGTH_SHORT).show()
                    })
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                editMode,
                enter = androidx.compose.animation.scaleIn(Motion.pop(Motion.sheet)) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hidden.isNotEmpty()) Box(
                        Modifier.size(40.dp).smokedGlass(CircleShape, alpha = 0.6f, tint = accent).clickable { addMenu = !addMenu },
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

        // Πλέγμα: μικρά tiles σε δύο στήλες, οι συντομεύσεις σε όλο το πλάτος
        val rows = mutableListOf<List<String>>()
        var pending: String? = null
        for (id in visible) {
            if (id == "shortcuts") {
                pending?.let { rows += listOf(it) }; pending = null
                rows += listOf(id)
            } else if (pending == null) pending = id
            else { rows += listOf(pending!!, id); pending = null }
        }
        pending?.let { rows += listOf(it) }

        @Composable
        fun cell(id: String, mod: Modifier) = androidx.compose.runtime.key(id) {
            val isDrag = draggingId == id
            val lift by animateFloatAsState(if (isDrag) 1.05f else 1f, Motion.pop(Motion.drag), label = "lift")
            Box(
                mod
                    .onGloballyPositioned { c -> container?.let { if (it.isAttached && c.isAttached) slots[id] = it.localBoundingBoxOf(c) } }
                    .zIndex(if (isDrag) 1f else 0f)
                    .graphicsLayer {
                        if (isDrag) { translationX = dragOff.x; translationY = dragOff.y }
                        val sc = editScale * lift
                        scaleX = sc; scaleY = sc
                    }
                    .then(if (!editMode) Modifier.longPress { editMode = true } else Modifier)
            ) {
                unit(id, Modifier.fillMaxSize())
                if (editMode) {
                    Box(
                        Modifier.matchParentSize().pointerInput(id) {
                            detectDragGestures(
                                onDragStart = { draggingId = id; dragOff = androidx.compose.ui.geometry.Offset.Zero },
                                onDragEnd = { prefs.dashOrder = live; draggingId = null; dragOff = androidx.compose.ui.geometry.Offset.Zero },
                                onDragCancel = { prefs.dashOrder = live; draggingId = null; dragOff = androidx.compose.ui.geometry.Offset.Zero }
                            ) { c, amount ->
                                c.consume()
                                dragOff += amount
                                val mine = slots[id] ?: return@detectDragGestures
                                val center = mine.center + dragOff
                                val now = System.currentTimeMillis()
                                if (now - lastSwap < 160) return@detectDragGestures
                                val hit = slots.entries.firstOrNull { (k, r) -> k != id && k in live && prefs.dashEnabled(k) && r.contains(center) }
                                if (hit != null) {
                                    live = live.toMutableList().also { l -> val x = l.indexOf(id); val y = l.indexOf(hit.key); l[x] = hit.key; l[y] = id }
                                    dragOff -= (hit.value.center - mine.center)
                                    lastSwap = now
                                }
                            }
                        }
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(26.dp)
                            .smokedGlass(CircleShape, alpha = 0.9f, tint = Smoke.danger)
                            .clickable { prefs.setDashEnabled(id, false) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, "Αφαίρεση", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }

        // Γεμίζει ακριβώς την οθόνη: γραμμές με tiles = 1 μερίδα, κάθε γραμμή κουμπιών = 0,62
        val scRows = ((visibleShortcuts.size + 3) / 4).coerceAtLeast(1)
        Column(
            Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { container = it },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { row ->
                if (row.size == 1 && row[0] == "shortcuts") cell("shortcuts", Modifier.fillMaxWidth().weight(scRows * 0.62f))
                else Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { id -> cell(id, Modifier.weight(1f).fillMaxHeight()) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // Μενού «+»: όσες κάρτες έχουν αφαιρεθεί
    androidx.compose.animation.AnimatedVisibility(
        addMenu && editMode && hidden.isNotEmpty(),
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp),
        enter = androidx.compose.animation.scaleIn(Motion.lively(Motion.sheet), 0.6f, androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut(Motion.snappy(Motion.sheet), 0.8f, androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + androidx.compose.animation.fadeOut()
    ) {
        Column(Modifier.widthIn(min = 180.dp).smokedGlass(RoundedCornerShape(22.dp), alpha = 0.8f).padding(6.dp)) {
            hidden.forEach { (uid, name) ->
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                        prefs.setDashEnabled(uid, true)
                        if (DASH_UNITS.count { !prefs.dashEnabled(it.first) } == 0) addMenu = false
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = Smoke.pearl, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    }
    }
    if (editTitle) RenameDialog(prefs.dashTitle, { editTitle = false }) { prefs.dashTitle = it }
}

val DASH_UNITS = listOf(
    "cpu" to "CPU", "ram" to "RAM", "storage" to "Χώρος", "net" to "Δίκτυο",
    "battery" to "Μπαταρία", "thermal" to "Θερμικά", "shortcuts" to "Συντομεύσεις"
)

fun Prefs.dashEnabled(id: String): Boolean = when (id) {
    "shortcuts" -> dashShortcuts; "cpu" -> dashCpu; "ram" -> dashMem; "storage" -> dashStorage
    "net" -> dashNet; "battery" -> dashBattery; "thermal" -> dashThermal; else -> false
}

fun Prefs.setDashEnabled(id: String, v: Boolean) {
    when (id) {
        "shortcuts" -> dashShortcuts = v; "cpu" -> dashCpu = v; "ram" -> dashMem = v; "storage" -> dashStorage = v
        "net" -> dashNet = v; "battery" -> dashBattery = v; "thermal" -> dashThermal = v
    }
}

fun Prefs.dashOrderFull(): List<String> {
    val ids = DASH_UNITS.map { it.first }
    return dashOrder.filter { it in ids }.distinct() + ids.filter { it !in dashOrder }
}

private val LocalDashStyle = androidx.compose.runtime.compositionLocalOf { Smoke.tint to 0.58f }

val DASH_SHORTCUTS = listOf(
    "Ρυθμίσεις", "Wi-Fi", "Bluetooth", "Αρχεία", "Μπαταρία", "Οθόνη", "Ήχος", "Χώρος",
    "Εφαρμογές", "Δεδομένα", "Τοποθεσία", "Ασφάλεια", "Ειδοποιήσεις", "Φακός", "Developer", "Crator"
)

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
fun Ring(f: Float, color: Color, label: String, size: Int = 96) {
    Box(Modifier.padding(vertical = 4.dp).size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * f.coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(label, color = Color.White, fontSize = (size / 5).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LineChart(values: List<Float>, max: Float?, color: Color, height: Int = 80) {
    Canvas(Modifier.padding(top = 8.dp).fillMaxWidth().height(height.dp)) {
        if (values.size < 2) return@Canvas
        val mx = max ?: ((values.maxOrNull() ?: 1f) * 1.15f).coerceAtLeast(1f)
        val mn = if (max == null) ((values.minOrNull() ?: 0f) * 0.9f) else 0f
        val range = (mx - mn).coerceAtLeast(0.001f)
        val step = size.width / (values.size - 1)
        val line = Path(); val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - ((v - mn) / range).coerceIn(0f, 1f) * size.height
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.5.dp.toPx(), Offset((values.size - 1) * step, size.height - ((values.last() - mn) / range).coerceIn(0f, 1f) * size.height))
    }
}

@Composable
fun DualChart(a: List<Float>, b: List<Float>, ca: Color, cb: Color, height: Int = 80) {
    Canvas(Modifier.padding(top = 6.dp).fillMaxWidth().height(height.dp)) {
        val mx = ((a + b).maxOrNull() ?: 1f).coerceAtLeast(8f) * 1.15f
        listOf(a to ca, b to cb).forEach { (vals, c) ->
            if (vals.size < 2) return@forEach
            val step = size.width / (vals.size - 1)
            val p = Path()
            vals.forEachIndexed { i, v ->
                val x = i * step; val y = size.height - (v / mx).coerceIn(0f, 1f) * size.height
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Όλο το ταμπλό χωράει στην οθόνη και πιάνει όλο το πλάτος: αν δεν χωράει, μικραίνει χωρίς να αφήνει κενά στα πλάγια */
@Composable
fun FitToHeight(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val col: @Composable () -> Unit = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val inf = androidx.compose.ui.unit.Constraints.Infinity
        val p1 = subcompose("a", col).first().measure(c.copy(minHeight = 0, maxHeight = inf))
        if (!c.hasBoundedHeight || p1.height <= c.maxHeight) {
            return@SubcomposeLayout layout(c.maxWidth, if (c.hasBoundedHeight) c.maxHeight else p1.height) { p1.place(0, 0) }
        }
        val s1 = c.maxHeight.toFloat() / p1.height
        val w2 = (c.maxWidth / s1).toInt()
        val p2 = subcompose("b", col).first().measure(androidx.compose.ui.unit.Constraints(minWidth = w2, maxWidth = w2, minHeight = 0, maxHeight = inf))
        val sc = minOf(1f, c.maxHeight.toFloat() / p2.height)
        layout(c.maxWidth, c.maxHeight) {
            p2.placeWithLayer(0, 0) {
                scaleX = sc; scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }
}
