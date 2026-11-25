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
    CompositionLocalProvider(
        LocalDensity provides Density(d.density * prefs.dashScale / 100f, d.fontScale),
        LocalDashStyle provides (Color(prefs.dashColor) to prefs.dashOpacity / 100f)
    ) {
    FitToHeight(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Κεφαλίδα: παρατεταμένο πάτημα για αλλαγή τίτλου / απόκρυψη υπότιτλου
        Column(Modifier.padding(6.dp, 4.dp)) {
            Text(
                prefs.dashTitle.ifEmpty { " " }, color = Smoke.pearl, fontSize = 30.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-1).sp,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { editTitle = true })
            )
            if (prefs.dashShowSub) Text(
                "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, σε λειτουργία ${fmtUptime(stats.uptime)}",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {
                    prefs.dashShowSub = false
                    android.widget.Toast.makeText(ctx, "Κρύφτηκε· επαναφορά από Ρυθμίσεις → Ταμπλό", android.widget.Toast.LENGTH_SHORT).show()
                })
            )
        }

        // Συντομεύσεις
        if (prefs.dashShortcuts && visibleShortcuts.isNotEmpty()) Card {
            visibleShortcuts.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { s ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { s.run() }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val on = s.label == "Φακός" && torch
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(if (on) accent else Color.White.copy(alpha = 0.09f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(s.icon, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(19.dp)) }
                            Spacer(Modifier.height(3.dp))
                            Text(s.label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // CPU
        if (prefs.dashCpu) Card {
            Header(if (stats.cpuIsFreq) "CPU · συχνότητα" else "CPU · φόρτος", "%.0f%%".format(stats.cpuPct), accent)
            val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else Build.HARDWARE
            Sub("$soc · ${stats.cores.size} πυρήνες")
            LineChart(stats.cpuHist, 100f, accent, height = 52)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                stats.cores.forEach { (cur, max) ->
                    val f = if (max > 0) (cur.toFloat() / max).coerceIn(0.02f, 1f) else 0.02f
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((26 * f).dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.4f + 0.6f * f)))
                        Text(if (cur > 0) "${cur / 1000}" else "off", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    }
                }
            }
        }

        // RAM + Χώρος
        if (prefs.dashMem) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f)) {
                Header("RAM", "", accent)
                Ring(stats.ramUsed.toFloat() / stats.ramTotal, Color(0xFF7DE3C2), "${(stats.ramUsed * 100 / stats.ramTotal)}%", size = 70)
                Sub("${fmtBytes(stats.ramUsed)} / ${fmtBytes(stats.ramTotal)}" + if (stats.lowMem) " · χαμηλή!" else "")
                LineChart(stats.ramHist, 100f, Color(0xFF7DE3C2), height = 34)
            }
            Card(Modifier.weight(1f).clickable { Actions.settings(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }) {
                Header("Χώρος", "", accent)
                Ring(stats.stUsed.toFloat() / stats.stTotal, Color(0xFFFFB86B), "${(stats.stUsed * 100 / stats.stTotal)}%", size = 70)
                Sub("${fmtBytes(stats.stUsed)} / ${fmtBytes(stats.stTotal)}")
                Sub("Ελεύθερα ${fmtBytes(stats.stTotal - stats.stUsed)}, ${repo.apps.value.size} εφαρμογές")
            }
        }

        // Δίκτυο
        if (prefs.dashNet) Card {
            Header("Δίκτυο · ${stats.netType}", "↓ ${fmtRate(stats.rxRate)}", Color(0xFF6FC3FF))
            Sub(
                "↑ ${fmtRate(stats.txRate)}" +
                    (stats.rssi?.let { " · σήμα $it dBm" } ?: "") +
                    (if (stats.bandwidth > 0) " · ζώνη ~${stats.bandwidth / 1000} Mbps" else "")
            )
            DualChart(stats.downHist, stats.upHist, Color(0xFF6FC3FF), Color(0xFFFF7FB0), height = 46)
            Sub("Από την εκκίνηση: ↓ ${fmtBytes(stats.rxTotal)} · ↑ ${fmtBytes(stats.txTotal)}")
        }

        // Μπαταρία
        if (prefs.dashBattery) Card(Modifier.clickable { Actions.settings(ctx, Intent.ACTION_POWER_USAGE_SUMMARY) }) {
            Header("Μπαταρία · ${stats.batStatus}", "${stats.batLevel}%", Color(0xFF9BE37D))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(stats.batLevel / 100f, Color(0xFF9BE37D), "${stats.batLevel}%", size = 64)
                Spacer(Modifier.width(14.dp))
                Column {
                    Sub("%.1f °C, %.2f V, ${stats.batCurrent} mA".format(stats.batTemp, stats.batVolt / 1000f))
                    Sub("Υγεία: ${stats.batHealth}" + if (stats.batCycles >= 0) ", ${stats.batCycles} κύκλοι" else "")
                    LineChart(stats.batHist, null, Color(0xFF9BE37D), height = 30)
                }
            }
        }

        // Θερμικά
        if (prefs.dashThermal) Card {
            Header("Θερμική κατάσταση", stats.thermal, Color(0xFFFF8A65))
            stats.headroom?.let { h ->
                Sub("Περιθώριο πριν το throttling")
                Bar((1f - h).coerceIn(0f, 1f), Color(0xFFFF8A65))
            }
        }
    }
    }
    if (editTitle) RenameDialog(prefs.dashTitle, { editTitle = false }) { prefs.dashTitle = it }
}

private val LocalDashStyle = androidx.compose.runtime.compositionLocalOf { Smoke.tint to 0.58f }

val DASH_SHORTCUTS = listOf(
    "Ρυθμίσεις", "Wi-Fi", "Bluetooth", "Αρχεία", "Μπαταρία", "Οθόνη", "Ήχος", "Χώρος",
    "Εφαρμογές", "Δεδομένα", "Τοποθεσία", "Ασφάλεια", "Ειδοποιήσεις", "Φακός", "Developer", "Crator"
)

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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

/** Όλο το ταμπλό χωράει στην οθόνη: αν δεν χωράει, μικραίνει ομοιόμορφα αντί για scroll */
@Composable
fun FitToHeight(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        modifier = modifier
    ) { ms, c ->
        val p = ms[0].measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        val scale = if (c.hasBoundedHeight && p.height > c.maxHeight) c.maxHeight.toFloat() / p.height else 1f
        layout(c.maxWidth, if (c.hasBoundedHeight) c.maxHeight else p.height) {
            p.placeWithLayer(0, 0) {
                scaleX = scale; scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
        }
    }
}
