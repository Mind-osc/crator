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

/** Συντόμευση ταμπλό: κάθε κουμπί είναι ξεχωριστό κομμάτι («sc:<id>» ή «app:<component>») */
class DashAction(val id: String, val label: String, val icon: ImageVector, val run: (android.content.Context) -> Unit)

/** Callbacks που χρειάζονται κάποιες συντομεύσεις */
object DashHooks {
    var openCrator: () -> Unit = {}
    var openSearch: () -> Unit = {}
    /** Σε επεξεργασία ταμπλό: σταματά το σύρσιμο σελίδων ώστε να μην «κλέβει» το δάχτυλο */
    val editing = androidx.compose.runtime.mutableStateOf(false)
    val torch = androidx.compose.runtime.mutableStateOf(false)
}

internal fun sel(cat: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat)

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
internal val CATALOG_BY_ID = DASH_CATALOG.associateBy { it.id }
private val DEFAULT_SC = DASH_CATALOG.take(16).map { "sc:${it.id}" }

val DASH_INFO = listOf(
    "cpu" to "CPU", "ram" to "RAM", "storage" to "Χώρος", "net" to "Δίκτυο",
    "battery" to "Μπαταρία", "thermal" to "Θερμικά", "clock" to "Ρολόι"
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

internal fun isButton(id: String) = id.startsWith("sc:") || id.startsWith("app:")
