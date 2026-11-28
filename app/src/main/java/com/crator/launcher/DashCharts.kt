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

internal object Live {
    val cpu = listOf(Color(0xFF7C5CFF), Color(0xFFFF4FA3))
    val ram = listOf(Color(0xFF34E0C6), Color(0xFF1FB6D9))
    val storage = listOf(Color(0xFF4DA3FF), Color(0xFF8FD0FF))
    val net = listOf(Color(0xFFFF5FA2), Color(0xFFFF8CC6))
    val battery = listOf(Color(0xFFE6E04A), Color(0xFFB9F35A))
    val thermal = listOf(Color(0xFFFF8A3D), Color(0xFFFF3B30))
}

/** Κατάμαυρη κάρτα, μεγάλες καμπύλες (σαν Live Activity) */
@Composable
internal fun LiveCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
internal fun DashTitle(t: String) = Text(t, color = Smoke.pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)

@Composable
internal fun Meta(t: String) = Text(t, color = Color(0xFF8E8E96), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

/** Μεγάλη τιμή σε συμπαγές χρώμα, με μικρότερη μονάδα (σαν «82 mmHg») */
@Composable
internal fun Num(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
        Text(unit, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp, start = 1.dp), maxLines = 1)
    }
}

@Composable
internal fun Axis(a: String, b: String, c: String) {
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
internal fun Segments(fracs: List<Float>, colors: List<List<Color>>, labels: List<String>, values: List<String>) {
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
