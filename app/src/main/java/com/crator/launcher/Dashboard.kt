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

/** Συντόμευση ταμπλό: κάθε κουμπί είναι ξεχωριστό κομμάτι («sc:<id>» ή «app:<component>») */
class DashAction(val id: String, val label: String, val icon: ImageVector, val run: (android.content.Context) -> Unit)

/** Callbacks που χρειάζονται κάποιες συντομεύσεις */
object DashHooks {
    var openCrator: () -> Unit = {}
    var openClaude: () -> Unit = {}
    var openSearch: () -> Unit = {}
    val torch = androidx.compose.runtime.mutableStateOf(false)
}

private fun sel(cat: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat)

val DASH_CATALOG: List<DashAction> = listOf(
    DashAction("settings", "Ρυθμίσεις", Icons.Rounded.Settings) { Actions.settings(it, Settings.ACTION_SETTINGS) },
    DashAction("wifi", "Wi-Fi", Icons.Rounded.Wifi) { Actions.settings(it, Settings.ACTION_WIFI_SETTINGS) },
    DashAction("bluetooth", "Bluetooth", Icons.Rounded.Bluetooth) { Actions.settings(it, Settings.ACTION_BLUETOOTH_SETTINGS) },
    DashAction("files", "Αρχεία", Icons.Rounded.Folder) { Actions.openFiles(it) },
    DashAction("battery", "Μπαταρία", Icons.Rounded.BatteryChargingFull) { Actions.settings(it, Intent.ACTION_POWER_USAGE_SUMMARY) },
    DashAction("display", "Οθόνη", Icons.Rounded.BrightnessMedium) { Actions.settings(it, Settings.ACTION_DISPLAY_SETTINGS) },
    DashAction("sound", "Ήχος", Icons.Rounded.VolumeUp) { Actions.settings(it, Settings.ACTION_SOUND_SETTINGS) },
    DashAction("storage", "Χώρος", Icons.Rounded.Storage) { Actions.settings(it, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
    DashAction("apps", "Εφαρμογές", Icons.Rounded.Apps) { Actions.settings(it, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) },
    DashAction("data", "Δεδομένα", Icons.Rounded.DataUsage) { Actions.settings(it, Settings.ACTION_DATA_USAGE_SETTINGS) },
    DashAction("location", "Τοποθεσία", Icons.Rounded.LocationOn) { Actions.settings(it, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
    DashAction("security", "Ασφάλεια", Icons.Rounded.Security) { Actions.settings(it, Settings.ACTION_SECURITY_SETTINGS) },
    DashAction("notifications", "Ειδοποιήσεις", Icons.Rounded.Notifications) { Actions.expandStatusBar(it, false) },
    DashAction("torch", "Φακός", Icons.Rounded.FlashlightOn) { DashHooks.torch.value = Actions.toggleTorch(it) },
    DashAction("developer", "Developer", Icons.Rounded.DeveloperMode) { Actions.settings(it, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
    DashAction("crator", "Crator", Icons.Rounded.Tune) { DashHooks.openCrator() },
    // επιπλέον, για προσθήκη από το +
    DashAction("quick", "Γρήγορες ρυθμ.", Icons.Rounded.Dashboard) { Actions.expandStatusBar(it, true) },
    DashAction("nfc", "NFC", Icons.Rounded.Nfc) { Actions.settings(it, Settings.ACTION_NFC_SETTINGS) },
    DashAction("hotspot", "Hotspot", Icons.Rounded.WifiTethering) { Actions.settings(it, Settings.ACTION_WIRELESS_SETTINGS) },
    DashAction("airplane", "Πτήση", Icons.Rounded.AirplanemodeActive) { Actions.settings(it, Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
    DashAction("dnd", "Μην ενοχλείτε", Icons.Rounded.DoNotDisturbOn) { Actions.settings(it, Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS) },
    DashAction("saver", "Εξοικονόμηση", Icons.Rounded.BatterySaver) { Actions.settings(it, Settings.ACTION_BATTERY_SAVER_SETTINGS) },
    DashAction("night", "Νυχτερινό φως", Icons.Rounded.DarkMode) { Actions.settings(it, Settings.ACTION_NIGHT_DISPLAY_SETTINGS) },
    DashAction("vpn", "VPN", Icons.Rounded.VpnKey) { Actions.settings(it, Settings.ACTION_VPN_SETTINGS) },
    DashAction("cast", "Προβολή", Icons.Rounded.Cast) { Actions.settings(it, Settings.ACTION_CAST_SETTINGS) },
    DashAction("accessibility", "Προσβασιμότητα", Icons.Rounded.Accessibility) { Actions.settings(it, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
    DashAction("language", "Γλώσσα", Icons.Rounded.Language) { Actions.settings(it, Settings.ACTION_LOCALE_SETTINGS) },
    DashAction("keyboard", "Πληκτρολόγιο", Icons.Rounded.Keyboard) { Actions.settings(it, Settings.ACTION_INPUT_METHOD_SETTINGS) },
    DashAction("datetime", "Ώρα", Icons.Rounded.Schedule) { Actions.settings(it, Settings.ACTION_DATE_SETTINGS) },
    DashAction("wallpaper", "Ταπετσαρία", Icons.Rounded.Wallpaper) { Actions.start(it, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Ταπετσαρία")) },
    DashAction("camera", "Κάμερα", Icons.Rounded.PhotoCamera) { Actions.start(it, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
    DashAction("alarm", "Ξυπνητήρι", Icons.Rounded.Alarm) { Actions.start(it, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) },
    DashAction("timer", "Χρονόμετρο", Icons.Rounded.Timer) { Actions.start(it, Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS)) },
    DashAction("calculator", "Αριθμομηχανή", Icons.Rounded.Calculate) { Actions.start(it, sel(Intent.CATEGORY_APP_CALCULATOR)) },
    DashAction("calendar", "Ημερολόγιο", Icons.Rounded.Event) { Actions.start(it, sel(Intent.CATEGORY_APP_CALENDAR)) },
    DashAction("contacts", "Επαφές", Icons.Rounded.Contacts) { Actions.start(it, sel(Intent.CATEGORY_APP_CONTACTS)) },
    DashAction("phone", "Τηλέφωνο", Icons.Rounded.Call) { Actions.start(it, Intent(Intent.ACTION_DIAL)) },
    DashAction("messages", "Μηνύματα", Icons.Rounded.Sms) { Actions.start(it, sel(Intent.CATEGORY_APP_MESSAGING)) },
    DashAction("browser", "Browser", Icons.Rounded.Public) { Actions.start(it, sel(Intent.CATEGORY_APP_BROWSER)) },
    DashAction("maps", "Χάρτες", Icons.Rounded.Map) { Actions.start(it, sel(Intent.CATEGORY_APP_MAPS)) },
    DashAction("music", "Μουσική", Icons.Rounded.MusicNote) { Actions.start(it, sel(Intent.CATEGORY_APP_MUSIC)) },
    DashAction("email", "Email", Icons.Rounded.Email) { Actions.start(it, sel(Intent.CATEGORY_APP_EMAIL)) },
    DashAction("gallery", "Φωτογραφίες", Icons.Rounded.PhotoLibrary) { Actions.start(it, sel(Intent.CATEGORY_APP_GALLERY)) },
    DashAction("store", "Play Store", Icons.Rounded.Shop) { Actions.openUrl(it, "market://search?q=") },
    DashAction("claude", "Claude", Icons.Rounded.AutoAwesome) { DashHooks.openClaude() },
    DashAction("search", "Αναζήτηση", Icons.Rounded.Search) { DashHooks.openSearch() }
)
private val CATALOG_BY_ID = DASH_CATALOG.associateBy { it.id }
private val DEFAULT_SC = DASH_CATALOG.take(16).map { "sc:${it.id}" }

val DASH_INFO = listOf(
    "cpu" to "CPU", "ram" to "RAM", "storage" to "Χώρος", "net" to "Δίκτυο",
    "battery" to "Μπαταρία", "thermal" to "Θερμικά"
)
val DASH_DEFAULT_ITEMS = listOf("cpu", "net", "battery", "thermal") + DEFAULT_SC + listOf("ram", "storage")

/** Λίστα στοιχείων του ταμπλό (με μεταφορά από τις παλιές ρυθμίσεις) */
fun Prefs.dashItemsFull(): List<String> {
    if (dashItems.isNotEmpty()) return dashItems
    if (dashOrder.isEmpty() && dashHiddenShortcuts.isEmpty()) return DASH_DEFAULT_ITEMS
    val out = mutableListOf<String>()
    val oldOrder = dashOrder.ifEmpty { listOf("shortcuts", "cpu", "ram", "storage", "net", "battery", "thermal") }
    val all = (oldOrder + listOf("cpu", "ram", "storage", "net", "battery", "thermal", "shortcuts")).distinct()
    val oldLabels = DASH_CATALOG.take(16)
    for (u in all) when (u) {
        "shortcuts" -> if (dashShortcuts) oldLabels.filter { it.label !in dashHiddenShortcuts }.forEach { out += "sc:${it.id}" }
        "cpu" -> if (dashCpu) out += u
        "ram", "mem" -> if (dashMem && "ram" !in out) out += "ram"
        "storage" -> if (dashStorage) out += u
        "net" -> if (dashNet) out += u
        "battery" -> if (dashBattery) out += u
        "thermal" -> if (dashThermal) out += u
    }
    return out.distinct()
}

private fun isButton(id: String) = id.startsWith("sc:") || id.startsWith("app:")

@Composable
fun Dashboard(
    stats: SysStats, prefs: Prefs, repo: AppRepository, active: Boolean,
    onOpenSettings: () -> Unit, onClaude: () -> Unit = {}, onSearch: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    DashHooks.openCrator = onOpenSettings
    DashHooks.openClaude = onClaude
    DashHooks.openSearch = onSearch

    // Ξεκινάει μόνο όταν η σελίδα είναι ενεργή, σταματάει αμέσως μόλις φύγεις
    LaunchedEffect(active, prefs.dashRefresh, prefs.dashHistory) {
        if (!active) return@LaunchedEffect
        stats.resetRates()
        while (isActive) {
            runCatching { stats.sample(prefs.dashHistory) }
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
        val (tc, ta) = LocalDashStyle.current
        val app = if (id.startsWith("app:")) byKey[id.removePrefix("app:")] else null
        val act = if (id.startsWith("sc:")) CATALOG_BY_ID[id.removePrefix("sc:")] else null
        val on = act?.id == "torch" && torch
        Column(
            mod.smokedGlass(RoundedCornerShape(22.dp), alpha = if (on) 0.9f else ta, tint = if (on) accent else tc)
                .clickable(enabled = !editMode) {
                    when {
                        app != null -> Actions.launch(ctx as android.app.Activity, app, null, prefs.openAnim)
                        act != null -> act.run(ctx)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                app != null -> AppIcon(app, 30.dp, prefs)
                act != null -> Icon(act.icon, null, tint = if (on) Color.Black else Smoke.pearl, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                app?.label ?: act?.label ?: "", color = if (on) Color.Black else Smoke.pearl.copy(alpha = 0.85f),
                fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    @Composable
    fun unit(id: String, mod: Modifier) {
        if (isButton(id)) { button(id, mod); return }
        when (id) {
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
        for (id in live) {
            val cap = if (isButton(id)) 4 else 2
            val last = rows.lastOrNull()
            if (last != null && isButton(last[0]) == isButton(id) && last.size < cap) last += id else rows += mutableListOf(id)
        }

        @Composable
        fun cell(id: String, mod: Modifier) = androidx.compose.runtime.key(id) {
            val isDrag = draggingId == id
            val lift by animateFloatAsState(if (isDrag) 1.06f else 1f, Motion.pop(Motion.drag), label = "lift")
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
                                onDragEnd = { prefs.dashItems = live; draggingId = null; dragOff = androidx.compose.ui.geometry.Offset.Zero },
                                onDragCancel = { prefs.dashItems = live; draggingId = null; dragOff = androidx.compose.ui.geometry.Offset.Zero }
                            ) { c, amount ->
                                c.consume()
                                dragOff += amount
                                val mine = slots[id] ?: return@detectDragGestures
                                val center = mine.center + dragOff
                                val now = System.currentTimeMillis()
                                if (now - lastSwap < 160) return@detectDragGestures
                                val hit = slots.entries.firstOrNull { (k, r) -> k != id && k in live && r.contains(center) }
                                if (hit != null) {
                                    // μετακίνηση στη θέση του άλλου (τα υπόλοιπα κυλάνε μία θέση)
                                    val from = live.indexOf(id); val to = live.indexOf(hit.key)
                                    live = live.toMutableList().also { l -> l.removeAt(from); l.add(to, id) }
                                    dragOff -= (hit.value.center - mine.center)
                                    lastSwap = now
                                }
                            }
                        }
                    )
                    val xs = if (isButton(id)) 22.dp else 26.dp
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(xs)
                            .smokedGlass(CircleShape, alpha = 0.9f, tint = Smoke.danger)
                            .clickable { remove(id) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, "Αφαίρεση", tint = Color.White, modifier = Modifier.size(xs * 0.55f)) }
                }
            }
        }

        // Γεμίζει ακριβώς την οθόνη: γραμμή καρτών = 1 μερίδα, γραμμή κουμπιών = 0,62
        Column(
            Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { container = it },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { row ->
                val btn = isButton(row[0])
                Row(Modifier.fillMaxWidth().weight(if (btn) 0.62f else 1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { id -> cell(id, Modifier.weight(1f).fillMaxHeight()) }
                    repeat((if (btn) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (rows.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Άδειο ταμπλό. Πάτα + για να προσθέσεις.", color = Smoke.mist, fontSize = 13.sp)
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
                .smokedGlass(RoundedCornerShape(26.dp), alpha = 0.82f)
                .padding(10.dp)
        ) {
            if (appPick) {
                var q by remember { mutableStateOf("") }
                SearchField(prefs, q, { q = it }, "Αναζήτηση εφαρμογής", null, onGo = {})
                Spacer(Modifier.height(6.dp))
                val nq = q.norm().trim()
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(repo.apps.value.filter { "app:${it.key}" !in current && (nq.isEmpty() || it.label.norm().contains(nq)) }, key = { it.key }) { a ->
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
