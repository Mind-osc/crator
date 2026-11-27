package com.crator.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

const val HOST_ID = 0x0C4A7
private const val REQ_BIND = 4101
private const val REQ_CONF = 4102

/** Host view που πιάνει το παρατεταμένο πάτημα (για drag/μενού) χωρίς να χαλάει τα κλικ του widget */
class CratorWidgetView(ctx: Context) : AppWidgetHostView(ctx) {
    var onLongPress: (() -> Unit)? = null
    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val lp = Runnable {
        if (isAttachedToWindow) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; longPressed = false
                removeCallbacks(lp); postDelayed(lp, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(lp)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(lp)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        if (longPressed) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) track(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
        return true
    }
}

class CratorWidgetHost(ctx: Context) : AppWidgetHost(ctx, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        CratorWidgetView(context)
}

class WidgetController(private val activity: Activity, private val prefs: Prefs) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host = CratorWidgetHost(activity)

    private class Pending(val id: Int, val info: AppWidgetProviderInfo, val page: Int)
    private var pending: Pending? = null

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun startAdd(info: AppWidgetProviderInfo, page: Int) {
        val id = host.allocateAppWidgetId()
        pending = Pending(id, info, page)
        val ok = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.provider) }.getOrDefault(false)
        if (ok) configureOrPlace() else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) { cancel() }
        }
    }

    /** Επιστρέφει true αν το αποτέλεσμα αφορούσε widget */
    fun onResult(req: Int, res: Int): Boolean {
        when (req) {
            REQ_BIND -> if (res == Activity.RESULT_OK) configureOrPlace() else cancel()
            REQ_CONF -> if (res == Activity.RESULT_OK) place() else cancel()
            else -> return false
        }
        return true
    }

    private fun configureOrPlace() {
        val p = pending ?: return
        val optional = (p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (p.info.configure != null && !optional) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQ_CONF, null)
            } catch (e: Exception) { place() }
        } else place()
    }

    private fun place() {
        val p = pending ?: return
        pending = null
        val (sx, sy) = defaultSpan(p.info)
        if (!prefs.placeOnHome(Item.widget(p.id, sx, sy), p.page)) {
            host.deleteAppWidgetId(p.id)
            Toast.makeText(activity, "Δεν υπάρχει χώρος για το widget", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancel() {
        pending?.let { runCatching { host.deleteAppWidgetId(it.id) } }
        pending = null
    }

    fun delete(id: Int) { runCatching { host.deleteAppWidgetId(id) } }

    fun defaultSpan(info: AppWidgetProviderInfo): Pair<Int, Int> {
        if (info.targetCellWidth > 0 && info.targetCellHeight > 0)
            return info.targetCellWidth.coerceIn(1, prefs.cols) to info.targetCellHeight.coerceIn(1, prefs.rows)
        val dm = activity.resources.displayMetrics
        val cw = dm.widthPixels / prefs.cols.toFloat()
        val ch = dm.heightPixels * 0.68f / prefs.rows
        return ceil(info.minWidth / cw).toInt().coerceIn(1, prefs.cols) to ceil(info.minHeight / ch).toInt().coerceIn(1, prefs.rows)
    }

    fun startListening() { runCatching { host.startListening() } }
    fun stopListening() { runCatching { host.stopListening() } }
}

/** Widget συστήματος μέσα στο Compose */
@Composable
fun SystemWidget(id: Int, widgets: WidgetController, prefs: Prefs, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val info = remember(id) { widgets.info(id) }
    val lp by rememberUpdatedState(onLongPress)
    val shape = RoundedCornerShape(prefs.widgetCorner.dp)
    if (info == null) {
        Box(modifier.padding(prefs.widgetPadding.dp).fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text("Widget μη διαθέσιμο", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        return
    }
    BoxWithConstraints(modifier.padding(prefs.widgetPadding.dp).fillMaxSize().clip(shape)) {
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            factory = { ctx ->
                widgets.host.createView(ctx, id, info).also { v ->
                    (v as? CratorWidgetView)?.onLongPress = { lp() }
                    // χωρίς το προεπιλεγμένο κενό του Android γύρω από το widget
                    v.setPadding(0, 0, 0, 0)
                }
            },
            update = { v -> v.setPadding(0, 0, 0, 0); runCatching { v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h))) } },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------- Δικά μας widgets (Crator) ----------------

class CratorWidgetDef(val type: String, val name: String, val desc: String, val sx: Int, val sy: Int, val icon: ImageVector)

val CRATOR_WIDGETS = listOf(
    CratorWidgetDef("settings", "Ρυθμίσεις Crator", "Γρήγορη πρόσβαση σε εικονίδια, dock, εφέ", 4, 1, Icons.Rounded.Tune),
    CratorWidgetDef("clock", "Ρολόι Crator", "Μεγάλο ρολόι με ημερομηνία", 4, 2, Icons.Rounded.AutoAwesome),
    CratorWidgetDef("claude", "Μπάρα αναζήτησης", "Αναζήτηση εφαρμογών από την αρχική", 4, 1, Icons.Rounded.Search),
    CratorWidgetDef("system", "Σύστημα mini", "RAM, χώρος, μπαταρία", 4, 1, Icons.Rounded.Palette),
    CratorWidgetDef("toggles", "Συντομεύσεις", "Wi-Fi, Bluetooth, φακός, ρυθμίσεις, αρχεία", 4, 1, Icons.Rounded.Settings)
)

@Composable
fun CratorWidget(
    item: String,
    prefs: Prefs,
    onClaude: () -> Unit,
    onSettings: (String?) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(prefs.widgetCorner.dp)
    val accent = MaterialTheme.colorScheme.primary
    val card = modifier.padding(prefs.widgetPadding.dp).fillMaxSize().clip(shape)
    val glass = Modifier.smokedGlass(shape, alpha = (prefs.dockOpacity / 100f + 0.1f).coerceAtMost(1f), tint = Color(prefs.dockColor))
    val press = Modifier.longPress(onLongPress)
    when (Item.cratorType(item)) {
        "clock" -> {
            val now by produceState(Date()) { while (true) { value = Date(); delay(60_000L - System.currentTimeMillis() % 60_000L + 50) } }
            val el = Locale("el", "GR")
            Column(card.then(press), verticalArrangement = Arrangement.Center) {
                Text(SimpleDateFormat("HH:mm", el).format(now), color = Smoke.pearl, fontSize = 72.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2.5).sp)
                Text(SimpleDateFormat("EEEE, d MMMM", el).format(now).replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            }
        }
        "claude" -> Box(card, contentAlignment = Alignment.Center) {
            SearchPill(prefs, Modifier.then(press), onClick = onClaude)
        }
        "system" -> {
            val s by produceState(Triple(0f, 0f, 0)) {
                while (true) {
                    value = withContext(Dispatchers.IO) {
                        val am = ctx.getSystemService(ActivityManager::class.java)
                        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                        val sf = StatFs(Environment.getDataDirectory().path)
                        val st = 1f - sf.availableBytes.toFloat() / sf.totalBytes
                        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                        Triple(1f - mi.availMem.toFloat() / mi.totalMem, st, lvl)
                    }
                    delay(5000)
                }
            }
            Row(card.then(glass).then(press).clickable { onSettings("__dash") }.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MiniStat("RAM", s.first, Color(0xFF7DE3C2))
                MiniStat("Χώρος", s.second, Color(0xFFFFB86B))
                MiniStat("Μπαταρία", s.third / 100f, Color(0xFF9BE37D))
            }
        }
        "toggles" -> {
            val items = listOf<Triple<String, ImageVector, () -> Unit>>(
                Triple("Wi-Fi", Icons.Rounded.Wifi) { Actions.settings(ctx, Settings.ACTION_WIFI_SETTINGS) },
                Triple("BT", Icons.Rounded.Bluetooth) { Actions.settings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) },
                Triple("Φακός", Icons.Rounded.FlashlightOn) { Actions.toggleTorch(ctx) },
                Triple("Αρχεία", Icons.Rounded.Folder) { Actions.openFiles(ctx) },
                Triple("Ρυθμίσεις", Icons.Rounded.Settings) { Actions.settings(ctx, Settings.ACTION_SETTINGS) }
            )
            Row(card.then(glass).then(press), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                items.forEach { (l, ic, fn) -> RoundTile(l, ic, accent, fn) }
            }
        }
        else -> { // settings
            val items = listOf<Triple<String, ImageVector, String?>>(
                Triple("Εικονίδια", Icons.Rounded.Palette, "icons"),
                Triple("Dock", Icons.Rounded.Dock, "dock"),
                Triple("Εφέ", Icons.Rounded.Animation, "effects"),
                Triple("Όλες", Icons.Rounded.Tune, null)
            )
            Row(card.then(glass).then(press), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                items.forEach { (l, ic, sec) -> RoundTile(l, ic, accent) { onSettings(sec) } }
            }
        }
    }
}

@Composable
private fun RoundTile(label: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).smokedGlass(CircleShape, alpha = 0.3f, tint = accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun MiniStat(label: String, f: Float, c: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(f, c, "${(f * 100).toInt()}%", size = 40)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

// ---------------- Επιλογή widget ----------------

@Composable
fun WidgetPicker(widgets: WidgetController, prefs: Prefs, filterPkg: String?, page: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var q by remember { mutableStateOf("") }
    val all = remember { widgets.providers() }
    val groups = remember(q) {
        all.filter { filterPkg == null || it.provider.packageName == filterPkg }
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                label to list
            }
            .filter { (label, list) -> q.isBlank() || label.norm().contains(q.norm()) || list.any { it.loadLabel(pm).norm().contains(q.norm()) } }
            .sortedBy { it.first.norm() }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFA0C0D14)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(12.dp))
            Text("Widgets", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp))
                .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Αναζήτηση widget", color = Color.White.copy(alpha = 0.45f))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            if (filterPkg == null && (q.isBlank() || "crator".contains(q.norm()))) {
                item {
                    GroupTitle("Crator")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(CRATOR_WIDGETS) { d ->
                            WidgetCard(d.name, "${d.sx}×${d.sy}", null, d.icon) {
                                if (!prefs.placeOnHome(Item.crator(d.type, d.sx, d.sy), page))
                                    Toast.makeText(ctx, "Δεν υπάρχει χώρος", Toast.LENGTH_SHORT).show()
                                onClose()
                            }
                        }
                    }
                }
            }
            items(groups, key = { it.first + it.second.size }) { (label, list) ->
                GroupTitle(label)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list) { info ->
                        val prev by produceState<ImageBitmap?>(null, info) {
                            value = withContext(Dispatchers.Default) {
                                runCatching {
                                    (info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0))?.let { d ->
                                        val w = d.intrinsicWidth.coerceIn(48, 480); val h = d.intrinsicHeight.coerceIn(48, 480)
                                        d.toBitmap(w, h).asImageBitmap()
                                    }
                                }.getOrNull()
                            }
                        }
                        val (sx, sy) = widgets.defaultSpan(info)
                        WidgetCard(info.loadLabel(pm), "$sx×$sy", prev, null) { widgets.startAdd(info, page); onClose() }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(t: String) =
    Text(t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(6.dp, 16.dp, 6.dp, 8.dp))

@Composable
private fun WidgetCard(name: String, size: String, preview: ImageBitmap?, icon: ImageVector?, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            when {
                preview != null -> Image(preview, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                icon != null -> Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = Color.White, fontSize = 13.sp, maxLines = 2)
        Text(size, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

/** Παρατεταμένο πάτημα χωρίς να κλέβει τα απλά κλικ των παιδιών */
fun Modifier.longPress(onLong: () -> Unit): Modifier = composed {
    val cb by rememberUpdatedState(onLong)
    pointerInput(Unit) { detectTapGestures(onLongPress = { cb() }) }
}
