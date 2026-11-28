package com.crator.launcher

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/* Συλλογή Live widgets: κατάμαυρες κάρτες, μεγάλες καμπύλες, ένα ζευγάρι χρωμάτων ανά widget. */

/** Πόσο χώρο πιάνει κάθε κάρτα στο ταμπλό */
enum class CardSpan { Half, Wide, Big }

fun cardSpan(id: String): CardSpan = when (id) {
    "media", "weather", "calendar", "month" -> CardSpan.Wide
    else -> CardSpan.Half
}

internal object LivePal {
    val media = listOf(Color(0xFF1ED760), Color(0xFF0FB5AE))
    val sky = listOf(Color(0xFF5AC8FA), Color(0xFFFFB340))
    val cal = listOf(Color(0xFFB57BFF), Color(0xFF7C5CFF))
    val alarm = listOf(Color(0xFFFFB340), Color(0xFFFF6B3D))
    val steps = listOf(Color(0xFFB9F35A), Color(0xFF34E0C6))
    val screen = listOf(Color(0xFFFF4FA3), Color(0xFFB57BFF))
    val data = listOf(Color(0xFF34E0C6), Color(0xFF4DA3FF))
    val sun = listOf(Color(0xFFFFD60A), Color(0xFFFF8A3D))
    val moon = listOf(Color(0xFFF2F0FF), Color(0xFF8E8AC0))
    val air = listOf(Color(0xFF34E0C6), Color(0xFFFF5F57))
    val note = listOf(Color(0xFFFFE066), Color(0xFFFFB340))
    val date = listOf(Color(0xFFFF6B6B), Color(0xFFFF4FA3))
}

private val dim = Color(0xFF8E8E96)
private val track = Color(0xFF26262C)
private val tnum = TextStyle(fontFeatureSettings = "tnum")

private fun accessText(a: Access) = when (a) {
    Access.Notifications -> "Πάτα: πρόσβαση στις ειδοποιήσεις"
    Access.Calendar -> "Πάτα για άδεια ημερολογίου"
    Access.Activity -> "Πάτα για άδεια φυσικής δραστηριότητας"
    Access.Usage -> "Πάτα: «Πρόσβαση χρήσης»"
    Access.City -> "Πάτα και γράψε την πόλη σου"
}

/** Κοινό καλούπι: φορτώνει / θέλει άδεια / σφάλμα / περιεχόμενο */
@Composable
private fun <T> FeedCard(
    feed: Feed<T>,
    title: String,
    color: Color,
    mod: Modifier,
    enabled: Boolean,
    onAccess: (Access) -> Unit,
    onRetry: (() -> Unit)? = null,
    content: @Composable ColumnScope.(T) -> Unit
) {
    when (feed) {
        is Feed.Ready -> LiveCard(mod) { content(feed.value) }
        is Feed.NeedsAccess -> LiveCard(mod.clickable(enabled = enabled) { onAccess(feed.access) }) {
            Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(accessText(feed.access), color = Smoke.pearl, fontSize = 12.sp, lineHeight = 15.sp)
        }
        is Feed.Failed -> LiveCard(mod.clickable(enabled = enabled && onRetry != null) { onRetry?.invoke() }) {
            Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Meta(feed.message)
        }
        Feed.Loading -> LiveCard(mod) {
            Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Meta("Φορτώνει…")
        }
    }
}

@Composable
private fun Big(text: String, color: Color, size: Int = 26) =
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, style = tnum, maxLines = 1)

private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

private fun dur(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}ω ${m % 60}λ" else "${m}λ"
}

private fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}

fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> Icons.Rounded.WbCloudy
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Dehaze
    in 51..67, in 80..82 -> Icons.Rounded.Umbrella
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.FlashOn
    else -> Icons.Rounded.Cloud
}

private fun dayName(epochSec: Long): String =
    SimpleDateFormat("EEE", Locale("el")).format(Date(epochSec * 1000)).trimEnd('.').replaceFirstChar { it.uppercase() }

// =============================================================== Μουσική (Spotify, YouTube Music, …)

@Composable
fun MediaCard(mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by MediaFeed.get(ctx).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Μουσική", LivePal.media[0], mod, enabled, onAccess) { np ->
        if (np == null) {
            Row(Modifier.fillMaxSize().clickable(enabled = enabled) { openMusic(ctx) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(track), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = LivePal.media[0], modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    DashTitle("Δεν παίζει τίποτα")
                    Meta("Πάτα για μουσική")
                }
            }
            return@FeedCard
        }
        val pos by produceState(np.positionNow(), np) {
            while (np.playing) { value = np.positionNow(); delay(1000) }
            value = np.positionNow()
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(track)
                    .clickable(enabled = enabled) { MediaFeed.open(ctx, np) },
                contentAlignment = Alignment.Center
            ) {
                val art = np.art
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = LivePal.media[0], modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(np.title, color = Smoke.pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Meta(np.artist)
                Spacer(Modifier.weight(1f))
                if (np.durationMs > 0) {
                    val f = (pos.toFloat() / np.durationMs).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(track)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(Brush.horizontalGradient(LivePal.media)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    MediaButton(Icons.Rounded.SkipPrevious, 30, enabled) { MediaFeed.previous(ctx, np) }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(LivePal.media[0]).clickable(enabled = enabled) { MediaFeed.playPause(ctx, np) },
                        contentAlignment = Alignment.Center
                    ) { Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp)) }
                    MediaButton(Icons.Rounded.SkipNext, 30, enabled) { MediaFeed.next(ctx, np) }
                }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, size: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Smoke.pearl, modifier = Modifier.size(22.dp))
    }
}

private fun openMusic(ctx: Context) {
    val spotify = ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
    if (spotify != null && Actions.start(ctx, spotify)) return
    Actions.start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
}

// =============================================================== Καιρός

@Composable
fun WeatherCard(city: String, mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by WeatherFeed.get(ctx, city).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Καιρός", LivePal.sky[0], mod, enabled, onAccess, onRetry = { onAccess(Access.City) }) { w ->
        Row(Modifier.fillMaxSize().clickable(enabled = enabled) { onAccess(Access.City) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.9f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(w.code, w.isDay), null, tint = LivePal.sky[if (w.isDay && w.code <= 2) 1 else 0], modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Big("${w.temp.roundToInt()}°", Smoke.pearl, 28)
                }
                Text(WeatherFeed.describe(w.code), color = LivePal.sky[0], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Meta("${w.place} · αίσθηση ${w.feels.roundToInt()}°")
            }
            Row(Modifier.weight(1.3f), horizontalArrangement = Arrangement.SpaceBetween) {
                w.days.drop(1).take(5).forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayName(d.epochSec), color = dim, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Icon(weatherIcon(d.code, true), null, tint = if (d.code <= 2) LivePal.sky[1] else LivePal.sky[0], modifier = Modifier.padding(vertical = 3.dp).size(16.dp))
                        Text("${d.max.roundToInt()}°", color = Smoke.pearl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = tnum)
                        Text("${d.min.roundToInt()}°", color = dim, fontSize = 10.sp, style = tnum)
                    }
                }
            }
        }
    }
}

/** Μικρή κάρτα: θερμοκρασία και καμπύλη 24 ωρών */
@Composable
fun TempCard(city: String, mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by WeatherFeed.get(ctx, city).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Θερμοκρασία", LivePal.sky[0], mod, enabled, onAccess, onRetry = { onAccess(Access.City) }) { w ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Big("${w.temp.roundToInt()}°", Smoke.pearl, 22)
            Spacer(Modifier.weight(1f))
            Icon(weatherIcon(w.code, w.isDay), null, tint = LivePal.sky[1], modifier = Modifier.size(20.dp))
        }
        val temps = w.hours.map { it.second.toFloat() }
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)) {
            if (temps.size < 2) return@Canvas
            val lo = temps.min(); val hi = temps.max().coerceAtLeast(lo + 1f)
            val step = size.width / (temps.size - 1)
            val p = Path()
            temps.forEachIndexed { i, t ->
                val x = i * step; val y = size.height * (1f - (t - lo) / (hi - lo))
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, Brush.horizontalGradient(LivePal.sky), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Color.White, 3.dp.toPx(), Offset(0f, size.height * (1f - (temps[0] - lo) / (hi - lo))))
        }
        Meta("Μέγ ${w.days.firstOrNull()?.max?.roundToInt() ?: "–"}° · Ελ ${w.days.firstOrNull()?.min?.roundToInt() ?: "–"}°")
    }
}

/** Ήλιος: τόξο από την ανατολή ως τη δύση, με τη θέση του τώρα */
@Composable
fun SunCard(city: String, mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by WeatherFeed.get(ctx, city).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Ήλιος", LivePal.sun[0], mod, enabled, onAccess, onRetry = { onAccess(Access.City) }) { w ->
        val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
        val up = now in w.sunrise..w.sunset
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (up) "Δύση" else "Ανατολή", color = LivePal.sun[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(hm(if (up) w.sunset else w.sunrise), color = Smoke.pearl, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = tnum)
        }
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            val sw = 3.dp.toPx()
            val r = minOf(size.width / 2f, size.height) - sw
            val c = Offset(size.width / 2f, size.height)
            val box = Size(r * 2, r * 2); val tl = Offset(c.x - r, c.y - r)
            drawArc(track, 180f, 180f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
            val f = ((now - w.sunrise).toFloat() / (w.sunset - w.sunrise).coerceAtLeast(1)).coerceIn(0f, 1f)
            drawArc(Brush.horizontalGradient(LivePal.sun), 180f, 180f * f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
            val a = PI + PI * f
            val dot = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            drawCircle(LivePal.sun[0].copy(alpha = 0.25f), 9.dp.toPx(), dot)
            drawCircle(if (up) LivePal.sun[0] else dim, 5.dp.toPx(), dot)
        }
        Meta("${hm(w.sunrise)} — ${hm(w.sunset)}")
    }
}

/** Ποιότητα αέρα (ευρωπαϊκός δείκτης) και UV */
@Composable
fun AirCard(city: String, mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by WeatherFeed.get(ctx, city).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Αέρας", LivePal.air[0], mod, enabled, onAccess, onRetry = { onAccess(Access.City) }) { w ->
        val aqi = w.aqi
        val (label, f) = when {
            aqi == null -> "—" to 0f
            aqi <= 20 -> "Καλή" to 0.1f
            aqi <= 40 -> "Μέτρια" to 0.3f
            aqi <= 60 -> "Ανεκτή" to 0.5f
            aqi <= 80 -> "Κακή" to 0.7f
            aqi <= 100 -> "Πολύ κακή" to 0.85f
            else -> "Επικίνδυνη" to 1f
        }
        val col = androidx.compose.ui.graphics.lerp(LivePal.air[0], LivePal.air[1], f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Big(aqi?.toString() ?: "—", col, 22)
            Text(" AQI", color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            Text(label, color = Smoke.pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(LivePal.air))) {
            Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.02f)).fillMaxHeight()) {
                Box(Modifier.align(Alignment.CenterEnd).size(10.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(Modifier.height(6.dp))
        Meta("UV ${"%.0f".format(w.uv)}" + (w.pm25?.let { " · PM2.5 ${it.roundToInt()}" } ?: ""))
    }
}

// =============================================================== Φεγγάρι

@Composable
fun MoonCard(mod: Modifier) {
    val (age, illum) = Moon.now()
    LiveCard(mod) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp)) {
                val r = size.minDimension / 2f
                val c = center
                drawCircle(Color(0xFF2A2A33), r, c)
                // φωτισμένο κομμάτι: μισός δίσκος + έλλειψη (τερματοδιάγραμμα)
                val waxing = age < 14.765
                val k = (1 - 2 * illum).toFloat() // 1 = νέα, -1 = πανσέληνος
                val p = Path().apply {
                    val steps = 48
                    for (i in 0..steps) {
                        val t = -PI / 2 + PI * i / steps
                        val x = r * cos(t).toFloat() * (if (waxing) 1f else -1f)
                        val y = r * sin(t).toFloat()
                        if (i == 0) moveTo(c.x + x, c.y + y) else lineTo(c.x + x, c.y + y)
                    }
                    for (i in steps downTo 0) {
                        val t = -PI / 2 + PI * i / steps
                        val x = r * k * cos(t).toFloat() * (if (waxing) 1f else -1f)
                        val y = r * sin(t).toFloat()
                        lineTo(c.x + x, c.y + y)
                    }
                    close()
                }
                drawPath(p, Brush.radialGradient(LivePal.moon, c, r * 1.2f))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(Moon.name(age), color = LivePal.moon[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp)
                Spacer(Modifier.height(2.dp))
                Big("${(illum * 100).roundToInt()}%", Smoke.pearl, 18)
                Meta("Πανσέληνος σε ${Moon.daysToFull(age)} μ.")
            }
        }
    }
}

// =============================================================== Ημερολόγιο

@Composable
fun CalendarCard(mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by CalendarFeed.get(ctx).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Ημερολόγιο", LivePal.cal[0], mod, enabled, onAccess) { list ->
        Column(Modifier.fillMaxSize().clickable(enabled = enabled) {
            Actions.start(ctx, Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Επόμενα", color = LivePal.cal[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Meta(SimpleDateFormat("EEEE d MMM", Locale("el")).format(Date()))
            }
            Spacer(Modifier.height(4.dp))
            if (list.isEmpty()) {
                Spacer(Modifier.weight(1f))
                Text("Κανένα ραντεβού τις επόμενες 2 εβδομάδες", color = Smoke.pearl, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
            } else list.take(3).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp, 18.dp).clip(CircleShape).background(if (e.color != 0) Color(e.color) else LivePal.cal[0]))
                    Spacer(Modifier.width(8.dp))
                    Text(e.title, color = Smoke.pearl, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Text(whenText(e), color = dim, fontSize = 11.sp, style = tnum, maxLines = 1)
                }
            }
        }
    }
}

private fun whenText(e: CalEvent): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = e.begin }
    val sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = tomorrow.get(Calendar.YEAR) == c.get(Calendar.YEAR) && tomorrow.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    val day = when {
        e.begin <= System.currentTimeMillis() -> "Τώρα"
        sameDay -> "Σήμερα"
        isTomorrow -> "Αύριο"
        else -> SimpleDateFormat("EEE d", Locale("el")).format(Date(e.begin))
    }
    return if (e.allDay || day == "Τώρα") day else "$day ${hm(e.begin)}"
}

// =============================================================== Ξυπνητήρι

@Composable
fun AlarmCard(mod: Modifier, enabled: Boolean) {
    val ctx = LocalContext.current
    val next by AlarmFeed.get(ctx).collectAsStateWithLifecycle(null)
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    LiveCard(mod.clickable(enabled = enabled) { Actions.start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Alarm, null, tint = LivePal.alarm[0], modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ξυπνητήρι", color = LivePal.alarm[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        val t = next
        if (t == null) {
            Big("—", Smoke.pearl, 22)
            Meta("Κανένα ενεργό")
        } else {
            Big(hm(t), Smoke.pearl, 24)
            Meta("σε ${dur((t - now).coerceAtLeast(0))} · " + SimpleDateFormat("EEE", Locale("el")).format(Date(t)))
        }
    }
}

// =============================================================== Ημερομηνία & μήνας

@Composable
fun DateCard(mod: Modifier) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val el = Locale("el")
    LiveCard(mod) {
        Text(SimpleDateFormat("EEEE", el).format(Date(now)).replaceFirstChar { it.uppercase() }, color = LivePal.date[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(SimpleDateFormat("d", el).format(Date(now)), color = Smoke.pearl, fontSize = 34.sp, fontFamily = Orbitron, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("LLLL", el).format(Date(now)), color = dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
        }
    }
}

@Composable
fun MonthCard(mod: Modifier) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val el = Locale("el")
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Δευτέρα πρώτη
    val rows = (lead + days + 6) / 7
    LiveCard(mod) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // αριστερά: μήνας και σημερινή ημέρα
            Column(Modifier.width(74.dp)) {
                Text(SimpleDateFormat("LLLL", el).format(Date(now)).replaceFirstChar { it.uppercase() }, color = LivePal.date[0], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("$today", color = Smoke.pearl, fontSize = 30.sp, fontFamily = Orbitron, fontWeight = FontWeight.Bold, maxLines = 1)
                Meta(SimpleDateFormat("EEEE", el).format(Date(now)))
            }
            Spacer(Modifier.width(8.dp))
            // δεξιά: πυκνό πλέγμα μήνα
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Δ", "Τ", "Τ", "Π", "Π", "Σ", "Κ").forEachIndexed { i, d ->
                        Text(d, color = if (i >= 5) LivePal.date[1] else dim, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                    }
                }
                for (r in 0 until rows) Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val n = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (n in 1..days) {
                                if (n == today) Box(Modifier.size(15.dp).clip(CircleShape).background(Brush.linearGradient(LivePal.date)))
                                Text("$n", color = if (n == today) Color.White else if (c >= 5) LivePal.date[1].copy(alpha = 0.8f) else Smoke.pearl,
                                    fontSize = 9.sp, lineHeight = 10.sp, fontWeight = if (n == today) FontWeight.Bold else FontWeight.Medium, style = tnum, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Πρόοδος: ημέρα / εβδομάδα / μήνας / έτος */
@Composable
fun ProgressCard(mod: Modifier) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val dayF = (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) / 1440f
    val weekF = (((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + dayF) / 7f
    val monthF = (c.get(Calendar.DAY_OF_MONTH) - 1 + dayF) / c.getActualMaximum(Calendar.DAY_OF_MONTH)
    val yearF = (c.get(Calendar.DAY_OF_YEAR) - 1 + dayF) / c.getActualMaximum(Calendar.DAY_OF_YEAR)
    LiveCard(mod) {
        listOf("Ημέρα" to dayF, "Εβδομάδα" to weekF, "Μήνας" to monthF, "Έτος" to yearF).forEachIndexed { i, (label, f) ->
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = dim, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(54.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.02f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(LivePal.cal[1], LivePal.screen[0]))))
                }
                Text(" ${(f * 100).toInt()}%", color = Smoke.pearl, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, style = tnum, modifier = Modifier.width(34.dp), maxLines = 1)
            }
        }
    }
}

// =============================================================== Βήματα

@Composable
fun StepsCard(goal: Int, mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by StepsFeed.get(ctx).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Βήματα", LivePal.steps[0], mod, enabled, onAccess) { steps ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Βήματα", color = LivePal.steps[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Big("%,d".format(steps).replace(',', '.'), Smoke.pearl, 22)
                Meta("στόχος %,d".format(goal).replace(',', '.'))
            }
            val f = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp)) {
                val sw = 7.dp.toPx()
                val box = Size(size.minDimension - sw, size.minDimension - sw)
                val tl = Offset(sw / 2, sw / 2)
                drawArc(track, -90f, 360f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(LivePal.steps + LivePal.steps[0]), -90f, 360f * f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
    }
}

// =============================================================== Χρόνος οθόνης & δεδομένα

@Composable
fun ScreenTimeCard(mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by UsageFeed.screen(ctx).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Χρόνος οθόνης", LivePal.screen[0], mod, enabled, onAccess) { st ->
        Column(Modifier.fillMaxSize().clickable(enabled = enabled) {
            val pm = ctx.packageManager
            val i = pm.getLaunchIntentForPackage("com.samsung.android.forest") ?: pm.getLaunchIntentForPackage("com.google.android.apps.wellbeing")
            if (i == null || !Actions.start(ctx, i)) Actions.start(ctx, Intent(Settings.ACTION_SETTINGS))
        }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Big(dur(st.totalMs), Smoke.pearl, 20)
                Spacer(Modifier.weight(1f))
                Meta("σήμερα")
            }
            Spacer(Modifier.weight(1f))
            val max = st.top.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
            st.top.take(2).forEachIndexed { i, (name, ms) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(58.dp))
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(track)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((ms.toFloat() / max).coerceIn(0.04f, 1f)).clip(CircleShape).background(LivePal.screen[i]))
                    }
                    Text(" ${dur(ms)}", color = Smoke.pearl, fontSize = 10.sp, style = tnum, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun DataCard(mod: Modifier, enabled: Boolean, onAccess: (Access) -> Unit) {
    val ctx = LocalContext.current
    val feed by UsageFeed.data(ctx).collectAsStateWithLifecycle(Feed.Loading)
    FeedCard(feed, "Δεδομένα", LivePal.data[0], mod, enabled, onAccess) { d ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Δεδομένα σήμερα", color = LivePal.data[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        val total = (d.wifiBytes + d.mobileBytes).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(track)) {
            val wf = d.wifiBytes.toFloat() / total
            if (wf > 0f) Box(Modifier.weight(wf.coerceAtLeast(0.01f)).fillMaxHeight().background(LivePal.data[0]))
            if (wf < 1f) Box(Modifier.weight((1f - wf).coerceAtLeast(0.01f)).fillMaxHeight().background(LivePal.data[1]))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Meta("Wi-Fi")
                Text(bytes(d.wifiBytes), color = LivePal.data[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tnum)
            }
            Column(horizontalAlignment = Alignment.End) {
                Meta("Κινητά")
                Text(bytes(d.mobileBytes), color = LivePal.data[1], fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tnum)
            }
        }
    }
}

// =============================================================== Σημείωμα

@Composable
fun NoteCard(text: String, mod: Modifier, enabled: Boolean, onEdit: () -> Unit) {
    LiveCard(mod.clickable(enabled = enabled, onClick = onEdit)) {
        Text("Σημείωμα", color = LivePal.note[0], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text.ifBlank { "Πάτα για να γράψεις…" },
            color = if (text.isBlank()) dim else Smoke.pearl, fontSize = 13.sp, lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
    }
}
