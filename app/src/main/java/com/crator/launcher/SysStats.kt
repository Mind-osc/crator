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
