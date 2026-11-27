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

/** Συντόμευση ταμπλό: κάθε κουμπί είναι ξεχωριστό κομμάτι («sc:<id>» ή «app:<component>») */
class DashAction(val id: String, val label: String, val icon: ImageVector, val run: (android.content.Context) -> Unit)

/** Callbacks που χρειάζονται κάποιες συντομεύσεις */
object DashHooks {
    var openCrator: () -> Unit = {}
    var openClaude: () -> Unit = {}
    var openSearch: () -> Unit = {}
    /** Σε επεξεργασία ταμπλό: σταματά το σύρσιμο σελίδων ώστε να μην «κλέβει» το δάχτυλο */
    val editing = androidx.compose.runtime.mutableStateOf(false)
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
    DashAction("claude", "Claude", Icons.Rounded.AutoAwesome) { c ->
        c.packageManager.getLaunchIntentForPackage("com.anthropic.claude")?.let { Actions.start(c, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ?: Actions.openUrl(c, "market://details?id=com.anthropic.claude")
    },
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
                    app != null -> AppIcon(app, 24.dp, prefs)
                    act != null -> Icon(act.icon, null, tint = fg, modifier = Modifier.size(23.dp))
                }
                Box(Modifier.padding(top = 3.dp).size(4.dp).graphicsLayer { alpha = sel; scaleX = sel; scaleY = sel }.clip(CircleShape).background(accent))
            }
        }
    }

    @Composable
    fun unit(id: String, mod: Modifier) {
        if (isButton(id)) { button(id, mod); return }
        when (id) {
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
                    Title("RAM")
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
                    Title("Χώρος")
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
                        Title("Μπαταρία")
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
        for (id in live) {
            val cap = if (isButton(id)) 5 else 2
            val last = rows.lastOrNull()
            if (last != null && isButton(last[0]) == isButton(id) && last.size < cap) last += id else rows += mutableListOf(id)
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
            if (w > 0 && h > 0 && rows.isNotEmpty()) {
                // σταθερά ύψη (κάρτα ~100dp, κουμπιά ~70dp)· αν δεν χωράνε, μικραίνουν αναλογικά
                val want = rows.map { if (isButton(it[0])) btnRowPx else cardRowPx }
                val total = want.sum() + gapPx * (rows.size - 1)
                val k = if (total > h) (h - gapPx * (rows.size - 1)).toFloat() / want.sum() else 1f
                val used = (want.sum() * k).toInt() + gapPx * (rows.size - 1)
                val free = (h - used).coerceAtLeast(0)
                val lift = liftPx.toInt().coerceIn(0, free)
                var y = padPx + free - lift
                board = androidx.compose.ui.unit.IntRect(0, y - padPx, fullW, fullH - lift)
                val btnW = (w - pillPadPx * 2) / 5
                rows.forEachIndexed { r, row ->
                    val rh = (want[r] * k).toInt()
                    if (isButton(row[0])) {
                        // κάψουλα όσο χρειάζεται, στο κέντρο· στενεύει όταν βγάζεις κουμπί
                        val pillW = row.size * btnW + pillPadPx * 2
                        val x0 = padPx + (w - pillW) / 2
                        capsules += androidx.compose.ui.unit.IntRect(x0, y, x0 + pillW, y + rh)
                        row.forEachIndexed { c, id ->
                            val x = x0 + pillPadPx + c * btnW
                            m[id] = androidx.compose.ui.unit.IntRect(x, y, x + btnW, y + rh)
                        }
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
                                if (k == id) false else {
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
                        .shadow(24.dp, bs, ambientColor = Color.Black, spotColor = Color.Black)
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

// ---------------- Στυλ «Live Activity» ----------------

private object Live {
    val cpu = listOf(Color(0xFF7C5CFF), Color(0xFFFF4FA3))
    val ram = listOf(Color(0xFF34E0C6), Color(0xFF1FB6D9))
    val storage = listOf(Color(0xFF4DA3FF), Color(0xFF8FD0FF))
    val net = listOf(Color(0xFFFF5FA2), Color(0xFFFF8CC6))
    val battery = listOf(Color(0xFFE6E04A), Color(0xFFB9F35A))
    val thermal = listOf(Color(0xFFFF8A3D), Color(0xFFFF3B30))
}

/** Κατάμαυρη κάρτα, μεγάλες καμπύλες (σαν Live Activity) */
@Composable
private fun LiveCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.Black)
            .border(0.6.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        content = content
    )
}

@Composable
private fun Title(t: String) = Text(t, color = Smoke.pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)

@Composable
private fun Meta(t: String) = Text(t, color = Color(0xFF8E8E96), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

/** Μεγάλη τιμή σε συμπαγές χρώμα, με μικρότερη μονάδα (σαν «82 mmHg») */
@Composable
private fun Num(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
        Text(unit, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp, start = 1.dp), maxLines = 1)
    }
}

@Composable
private fun Axis(a: String, b: String, c: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(a, color = Color(0xFF8A8A92), fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(b, color = Color(0xFF8A8A92), fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(c, color = Color(0xFF8A8A92), fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

private fun lerpC(cs: List<Color>, t: Float) = androidx.compose.ui.graphics.lerp(cs[0], cs[1], t.coerceIn(0f, 1f))

/** Μπάρες γλυκόζης: κάψουλες που «αιωρούνται», χρώμα από μοβ σε ροζ ανάλογα με το ύψος */
@Composable
fun GlucoseBars(values: List<Float>, max: Float?, colors: List<Color>, modifier: Modifier) {
    Canvas(modifier) {
        val slots = 18
        val v = values.takeLast(slots)
        if (v.isEmpty()) return@Canvas
        val mx = max ?: (v.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = size.width / slots
        val bw = (step * 0.5f).coerceAtMost(6.dp.toPx())
        val start = slots - v.size
        // λεπτές οριζόντιες γραμμές πλέγματος
        for (k in 1..2) drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, size.height * k / 3f), Offset(size.width, size.height * k / 3f), 0.6.dp.toPx())
        v.forEachIndexed { i, x ->
            val t = (x / mx).coerceIn(0f, 1f)
            val top = size.height * (1f - (0.12f + 0.88f * t))
            val len = size.height * (0.22f + 0.3f * t)
            val bottom = (top + len).coerceAtMost(size.height)
            val cx = (start + i) * step + step / 2
            drawLine(
                Brush.verticalGradient(listOf(lerpC(colors, t), lerpC(colors, t * 0.6f)), startY = top, endY = bottom),
                Offset(cx, top), Offset(cx, bottom), strokeWidth = bw, cap = StrokeCap.Round
            )
        }
    }
}

/** Τελείες σαν τη μέτρηση πίεσης: συμμετρικές στήλες γύρω από τη μεσαία γραμμή */
@Composable
fun PulseDots(values: List<Float>, colors: List<Color>, modifier: Modifier) {
    Canvas(modifier) {
        val cols = 15
        val v = values.takeLast(cols)
        val mx = (v.maxOrNull() ?: 1f).coerceAtLeast(6f)
        val stepX = size.width / cols
        val r = (stepX * 0.2f).coerceAtMost(3.dp.toPx())
        val half = ((size.height / 2) / (r * 3f)).toInt().coerceIn(2, 7)
        val stepY = (size.height / 2) / half
        val mid = size.height / 2
        val start = cols - v.size
        for (c in 0 until cols) {
            val x = c * stepX + stepX / 2
            val n = if (c >= start) ((v[c - start] / mx) * half).toInt().coerceIn(0, half) else 0
            drawCircle(if (c >= start) colors[0] else Color.White.copy(alpha = 0.1f), r, Offset(x, mid))
            for (k in 1..half) {
                val on = k <= n
                val col = if (on) lerpC(colors, k / half.toFloat()).copy(alpha = 1f - 0.45f * (k / half.toFloat())) else Color.White.copy(alpha = 0.06f)
                drawCircle(col, r, Offset(x, mid - k * stepY))
                drawCircle(col, r, Offset(x, mid + k * stepY))
            }
        }
    }
}

/** Θόλος του ISS Flyover: γκρι τόξο, κώνος φωτός ως την τιμή, φωτεινό σημείο */
@Composable
fun DomeGauge(f: Float, colors: List<Color>, modifier: Modifier) {
    Canvas(modifier) {
        val sw = 3.dp.toPx()
        val rad = minOf(size.width / 2f, size.height) - sw * 2
        val c = Offset(size.width / 2f, size.height - sw)
        val tl = Offset(c.x - rad, c.y - rad)
        val arc = Size(rad * 2, rad * 2)
        val sweep = 180f * f.coerceIn(0f, 1f)
        // κώνος φωτός από το κέντρο
        val wedge = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y)
            arcTo(androidx.compose.ui.geometry.Rect(tl, arc), 180f, sweep, false)
            close()
        }
        drawPath(wedge, Brush.verticalGradient(listOf(colors[0].copy(alpha = 0.75f), colors[0].copy(alpha = 0.05f)), startY = c.y - rad, endY = c.y))
        drawArc(Color.White.copy(alpha = 0.22f), 180f, 180f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(colors[1], 180f, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        val a = Math.toRadians((180f + sweep).toDouble())
        val p = Offset(c.x + rad * kotlin.math.cos(a).toFloat(), c.y + rad * kotlin.math.sin(a).toFloat())
        drawCircle(colors[1].copy(alpha = 0.3f), sw * 3.2f, p)
        drawCircle(Color.White, sw * 1.6f, p)
    }
}

/** Τμήματα-κάψουλες (σαν «Now / Sunset / Dusk»): πλάτος ανάλογο με την τιμή */
@Composable
private fun Segments(fracs: List<Float>, colors: List<List<Color>>, labels: List<String>, values: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        fracs.forEachIndexed { i, f ->
            Column(Modifier.weight(f.coerceAtLeast(0.18f))) {
                Text(labels[i], color = Color(0xFF9C9CA6), fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Box(
                    Modifier.padding(vertical = 3.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(colors[i]))
                ) {
                    if (i == 0) Box(Modifier.align(Alignment.CenterStart).padding(start = 3.dp).size(8.dp).clip(CircleShape).background(Color.White))
                }
                Text(values[i], color = Smoke.pearl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Λεπτός δακτύλιος (σαν το «Meter Remaining») */
@Composable
fun RingMeter(f: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val sw = 3.dp.toPx()
        val tl = Offset(sw / 2, sw / 2)
        val sz = Size(size.width - sw, size.height - sw)
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, tl, sz, style = Stroke(sw))
        drawArc(color, -90f, 360f * f.coerceIn(0f, 1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Πυκνές λεπτές μπάρες (σαν την κάρτα τρεξίματος) */
@Composable
fun RunBars(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val slots = 28
        val v = values.takeLast(slots)
        if (v.isEmpty()) return@Canvas
        val mn = (v.minOrNull() ?: 0f) - 0.5f
        val mx = (v.maxOrNull() ?: 1f) + 0.5f
        val step = size.width / slots
        val bw = (step * 0.45f).coerceAtMost(4.dp.toPx())
        val start = slots - v.size
        v.forEachIndexed { i, x ->
            val t = ((x - mn) / (mx - mn)).coerceIn(0.1f, 1f)
            val h = size.height * t
            val cx = (start + i) * step + step / 2
            drawLine(color.copy(alpha = 0.35f + 0.65f * ((i + 1f) / v.size)), Offset(cx, size.height), Offset(cx, size.height - h), strokeWidth = bw, cap = StrokeCap.Round)
        }
    }
}

/** Χάρακας του espresso: πυκνές γραμμές, ράμπα χρώματος ως την τιμή με λάμψη, οι υπόλοιπες γκρι */
@Composable
fun EspressoRuler(f: Float, colors: List<Color>, modifier: Modifier) {
    Canvas(modifier) {
        val n = 34
        val step = size.width / n
        val sw = (step * 0.42f).coerceAtMost(3.5.dp.toPx())
        val active = (f * (n - 1)).toInt().coerceIn(0, n - 1)
        for (i in 0 until n) {
            val x = i * step + step / 2
            val on = i <= active
            val major = i % 10 == 0
            val h = when { i == active -> size.height; major -> size.height * 0.78f; else -> size.height * 0.55f }
            val y0 = (size.height - h) / 2
            if (on) {
                val col = lerpC(colors, i / (n - 1f))
                drawLine(col.copy(alpha = 0.25f), Offset(x, y0), Offset(x, y0 + h), strokeWidth = sw * 2.6f, cap = StrokeCap.Round)
                drawLine(col, Offset(x, y0), Offset(x, y0 + h), strokeWidth = sw, cap = StrokeCap.Round)
            } else {
                drawLine(Color.White.copy(alpha = if (major) 0.3f else 0.16f), Offset(x, y0), Offset(x, y0 + h), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}
