@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.crator.launcher

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.CalendarContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.floor

/*
 * Πηγές δεδομένων για τα Live widgets του ταμπλό.
 *
 * Κανόνες (για να μην υπάρχουν bugs):
 *  - Κάθε πηγή είναι ΕΝΑ κοινό SharedFlow: όσες κάρτες κι αν τη δείχνουν, τρέχει μία φορά.
 *  - Ξεκινά μόνο όταν φαίνεται κάποια κάρτα και σταματά 5″ αφού φύγεις (WhileSubscribed) — μπαταρία.
 *  - Ενημερώνεται από callbacks του συστήματος όπου υπάρχουν· polling μόνο όπου δεν γίνεται αλλιώς.
 *  - Κάθε κατάσταση (φορτώνει / θέλει άδεια / σφάλμα / έτοιμο) είναι ρητή, ποτέ crash για άδεια.
 *  - Βαριά δουλειά εκτός main thread· η ακύρωση περνά πάντα (suspendRunCatching).
 */

sealed interface Feed<out T> {
    data object Loading : Feed<Nothing>
    data class Ready<T>(val value: T) : Feed<T>
    data class NeedsAccess(val access: Access) : Feed<Nothing>
    data class Failed(val message: String) : Feed<Nothing>
}

enum class Access { Notifications, Calendar, Activity, Usage, City }

/** Το scope των πηγών ζει όσο η διεργασία· οι ίδιες οι πηγές σταματούν όταν δεν τις βλέπει κανείς. */
object LiveScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Αυξάνεται όταν επιστρέφεις στην εφαρμογή ή δίνεις άδεια: οι πηγές ξαναελέγχουν τις άδειες */
    val accessTick = MutableStateFlow(0)
    fun recheckAccess() { accessTick.value++ }
}

/** Ένα SharedFlow ανά κλειδί (π.χ. πόλη)· αλλάζει το κλειδί → νέα ροή, η παλιά σβήνει μόνη της */
private class Shared<K, T>(private val make: (K) -> Flow<T>) {
    private var key: Any? = Unset
    private var flow: SharedFlow<T>? = null
    fun get(k: K): SharedFlow<T> {
        val f = flow
        if (f != null && key == k) return f
        key = k
        return make(k).shareIn(LiveScope.scope, SharingStarted.WhileSubscribed(5_000), replay = 1).also { flow = it }
    }
    private object Unset
}

private fun Context.has(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

// ------------------------------------------------------------ Μουσική

data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float
) {
    fun positionNow(now: Long = android.os.SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + ((now - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

object MediaFeed {
    private val shared = Shared<Context, Feed<NowPlaying?>> { ctx ->
        LiveScope.accessTick.flatMapLatest { sessions(ctx) }
    }

    fun get(ctx: Context): SharedFlow<Feed<NowPlaying?>> = shared.get(ctx.applicationContext)

    private fun listener(ctx: Context) = ComponentName(ctx, BadgeService::class.java)

    private fun sessions(ctx: Context): Flow<Feed<NowPlaying?>> = callbackFlow {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val main = Handler(Looper.getMainLooper())
        var current: MediaController? = null
        var lastArtKey: Any? = null
        var lastArt: ImageBitmap? = null

        fun snapshot(c: MediaController?): NowPlaying? {
            c ?: return null
            val md = c.metadata
            val st = c.playbackState
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
            val raw = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            // το εξώφυλλο μικραίνει μία φορά ανά τραγούδι (όχι σε κάθε αλλαγή θέσης)
            if (raw !== lastArtKey) {
                lastArtKey = raw
                lastArt = raw?.let { b ->
                    val s = 256f / maxOf(b.width, b.height)
                    (if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else b).asImageBitmap()
                }
            }
            return NowPlaying(
                pkg = c.packageName,
                title = title,
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
                art = lastArt,
                playing = st?.state == PlaybackState.STATE_PLAYING,
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionMs = st?.position ?: 0L,
                positionAt = st?.lastPositionUpdateTime ?: android.os.SystemClock.elapsedRealtime(),
                speed = st?.playbackSpeed ?: 1f
            )
        }

        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { trySend(Feed.Ready(snapshot(current))) }
            override fun onPlaybackStateChanged(state: PlaybackState?) { trySend(Feed.Ready(snapshot(current))) }
            override fun onSessionDestroyed() { current = null; trySend(Feed.Ready(null)) }
        }

        fun pick(list: List<MediaController>?) {
            val next = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
            if (next?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(cb)
                current = next
                next?.registerCallback(cb, main)
            }
            trySend(Feed.Ready(snapshot(current)))
        }

        val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        val ok = try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener(ctx), main)
            pick(msm.getActiveSessions(listener(ctx)))
            true
        } catch (e: SecurityException) {
            trySend(Feed.NeedsAccess(Access.Notifications)); false
        }
        awaitClose {
            if (ok) runCatching { msm.removeOnActiveSessionsChangedListener(sessionsChanged) }
            current?.unregisterCallback(cb)
        }
    }

    /** Τα κουμπιά παίρνουν πάντα φρέσκο controller (όχι κάποιον που μπορεί να έχει «πεθάνει») */
    private fun controller(ctx: Context, pkg: String): MediaController? = try {
        ctx.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener(ctx)).firstOrNull { it.packageName == pkg }
    } catch (e: SecurityException) { null }

    fun playPause(ctx: Context, np: NowPlaying) {
        val t = controller(ctx, np.pkg)?.transportControls ?: return
        if (np.playing) t.pause() else t.play()
    }
    fun next(ctx: Context, np: NowPlaying) { controller(ctx, np.pkg)?.transportControls?.skipToNext() }
    fun previous(ctx: Context, np: NowPlaying) { controller(ctx, np.pkg)?.transportControls?.skipToPrevious() }
    fun open(ctx: Context, np: NowPlaying) {
        ctx.packageManager.getLaunchIntentForPackage(np.pkg)?.let { Actions.start(ctx, it) }
    }
}

// ------------------------------------------------------------ Καιρός (Open-Meteo, χωρίς κλειδί)

data class WeatherDay(val epochSec: Long, val code: Int, val max: Double, val min: Double, val rainPct: Int)

data class Weather(
    val place: String,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int,
    val isDay: Boolean,
    val sunrise: Long,
    val sunset: Long,
    val uv: Double,
    val hours: List<Pair<Long, Double>>,
    val days: List<WeatherDay>,
    val aqi: Int?,
    val pm25: Double?,
    val fetchedAt: Long
)

object WeatherFeed {
    private const val FRESH_MS = 30 * 60_000L
    private val shared = Shared<Pair<Context, String>, Feed<Weather>> { (ctx, city) -> feed(ctx, city) }

    fun get(ctx: Context, city: String): SharedFlow<Feed<Weather>> = shared.get(ctx.applicationContext to city.trim())

    private fun cacheFile(ctx: Context) = File(ctx.cacheDir, "weather.json")

    private fun feed(ctx: Context, city: String): Flow<Feed<Weather>> = flow {
        if (city.isBlank()) { emit(Feed.NeedsAccess(Access.City)); return@flow }
        var last: Weather? = withContext(Dispatchers.IO) { suspendRunCatching { readCache(ctx, city) }.getOrNull() }
        emit(last?.let { Feed.Ready(it) } ?: Feed.Loading)
        while (true) {
            val age = last?.let { System.currentTimeMillis() - it.fetchedAt } ?: Long.MAX_VALUE
            if (age >= FRESH_MS) {
                suspendRunCatching { fetch(ctx, city) }
                    .onSuccess { last = it; emit(Feed.Ready(it)) }
                    .onFailure { e ->
                        android.util.Log.w("CratorWeather", "fetch failed", e)
                        if (last == null) emit(Feed.Failed(explain(e, city)))
                    }
            }
            // αν απέτυχε, ξαναδοκιμάζει σε 2′· αλλιώς όταν «παλιώσει»
            val wait = last?.let { FRESH_MS - (System.currentTimeMillis() - it.fetchedAt) } ?: 120_000L
            delay(wait.coerceIn(60_000L, FRESH_MS))
        }
    }

    private class UnknownCity : Exception()
    private class ApiError(val code: Int, val reason: String?) : java.io.IOException("HTTP $code $reason")

    /** Συγκεκριμένο μήνυμα, ώστε να ξέρουμε τι φταίει (όχι γενικό «χωρίς σύνδεση») */
    private fun explain(e: Throwable, city: String): String = when (e) {
        is UnknownCity -> "Δεν βρέθηκε η πόλη «$city» — πάτα για αλλαγή"
        is ApiError -> "Open-Meteo: σφάλμα ${e.code}" + (e.reason?.let { " · $it" } ?: "")
        is java.net.UnknownHostException -> "Χωρίς σύνδεση στο internet"
        is java.net.SocketTimeoutException -> "Αργή σύνδεση — ξαναδοκιμάζει"
        is javax.net.ssl.SSLException -> "Σφάλμα ασφαλούς σύνδεσης"
        is org.json.JSONException -> "Απρόσμενη απάντηση καιρού"
        else -> "Σφάλμα: ${e.javaClass.simpleName}"
    }

    private suspend fun get(url: String): String = runInterruptible(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "Crator launcher")
            val code = c.responseCode
            if (code !in 200..299) {
                val body = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw ApiError(code, body?.let { runCatching { JSONObject(it).optString("reason").ifBlank { null } }.getOrNull() })
            }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private suspend fun fetch(ctx: Context, city: String): Weather {
        val q = URLEncoder.encode(city, "UTF-8")
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=el&format=json"))
        val hit = geo.optJSONArray("results")?.optJSONObject(0) ?: throw UnknownCity()
        val lat = hit.getDouble("latitude"); val lon = hit.getDouble("longitude")
        val place = hit.optString("name", city)
        val forecast = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                "&forecast_days=6&timezone=auto&timeformat=unixtime"
        )
        // η ποιότητα αέρα είναι προαιρετική: αν αποτύχει, ο καιρός δείχνεται κανονικά
        val air = suspendRunCatching {
            get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=european_aqi,pm2_5&timeformat=unixtime")
        }.getOrNull()
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            suspendRunCatching {
                cacheFile(ctx).writeText(JSONObject().put("city", city).put("place", place).put("at", now)
                    .put("forecast", forecast).put("air", air ?: "").toString())
            }
        }
        return parse(place, forecast, air, now)
    }

    private fun readCache(ctx: Context, city: String): Weather? {
        val f = cacheFile(ctx)
        if (!f.exists()) return null
        val j = JSONObject(f.readText())
        if (!j.optString("city").equals(city, ignoreCase = true)) return null
        return parse(j.optString("place", city), j.getString("forecast"), j.optString("air").ifBlank { null }, j.getLong("at"))
    }

    private fun parse(place: String, forecast: String, air: String?, at: Long): Weather {
        val f = JSONObject(forecast)
        val cur = f.getJSONObject("current")
        val d = f.getJSONObject("daily")
        val h = f.getJSONObject("hourly")
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).map { i ->
            WeatherDay(
                dt.getLong(i), d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i), d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i, 0)
            )
        }
        val ht = h.getJSONArray("time"); val hv = h.getJSONArray("temperature_2m")
        // οι επόμενες 24 ώρες από τώρα (το API δίνει όλες τις ημέρες)
        val from = System.currentTimeMillis() / 1000 - 3600
        val hours = (0 until ht.length()).map { ht.getLong(it) to hv.optDouble(it) }.filter { it.first >= from && !it.second.isNaN() }.take(24)
        val a = air?.let { runCatching { JSONObject(it).getJSONObject("current") }.getOrNull() }
        return Weather(
            place = place,
            temp = cur.optDouble("temperature_2m"),
            feels = cur.optDouble("apparent_temperature"),
            humidity = cur.optInt("relative_humidity_2m"),
            wind = cur.optDouble("wind_speed_10m"),
            code = cur.optInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            sunrise = d.getJSONArray("sunrise").optLong(0) * 1000,
            sunset = d.getJSONArray("sunset").optLong(0) * 1000,
            uv = d.getJSONArray("uv_index_max").optDouble(0, 0.0),
            hours = hours,
            days = days,
            aqi = a?.takeIf { it.has("european_aqi") && !it.isNull("european_aqi") }?.optInt("european_aqi"),
            pm25 = a?.takeIf { it.has("pm2_5") && !it.isNull("pm2_5") }?.optDouble("pm2_5"),
            fetchedAt = at
        )
    }

    /** Κωδικοί WMO → λέξη στα ελληνικά */
    fun describe(code: Int): String = when (code) {
        0 -> "Αίθριος"
        1 -> "Κυρίως αίθριος"
        2 -> "Αραιή συννεφιά"
        3 -> "Συννεφιά"
        45, 48 -> "Ομίχλη"
        51, 53, 55, 56, 57 -> "Ψιχάλα"
        61, 63, 65, 66, 67 -> "Βροχή"
        71, 73, 75, 77 -> "Χιόνι"
        80, 81, 82 -> "Μπόρες"
        85, 86 -> "Χιονόπτωση"
        95, 96, 99 -> "Καταιγίδα"
        else -> "—"
    }
}

// ------------------------------------------------------------ Ημερολόγιο

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

object CalendarFeed {
    private val shared = Shared<Context, Feed<List<CalEvent>>> { ctx -> LiveScope.accessTick.flatMapLatest { events(ctx) } }
    fun get(ctx: Context): SharedFlow<Feed<List<CalEvent>>> = shared.get(ctx.applicationContext)

    private fun events(ctx: Context): Flow<Feed<List<CalEvent>>> = callbackFlow {
        if (!ctx.has(Manifest.permission.READ_CALENDAR)) { send(Feed.NeedsAccess(Access.Calendar)); awaitClose { }; return@callbackFlow }
        fun reload() = launch {
            val list = withContext(Dispatchers.IO) { suspendRunCatching { query(ctx) }.getOrDefault(emptyList()) }
            send(Feed.Ready(list))
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reload() }
        }
        ctx.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        reload()
        // ανανέωση κάθε 5′ ώστε να «φεύγουν» τα γεγονότα που τελείωσαν
        val ticker = launch { while (true) { delay(5 * 60_000L); reload() } }
        awaitClose { ticker.cancel(); ctx.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun query(ctx: Context): List<CalEvent> {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now - 12 * 3_600_000L)
            android.content.ContentUris.appendId(it, now + 14 * 86_400_000L)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR
        )
        val out = ArrayList<CalEvent>()
        ctx.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 6) {
                val end = c.getLong(2)
                if (end <= now) continue
                out += CalEvent(c.getString(0) ?: "(χωρίς τίτλο)", c.getLong(1), end, c.getInt(3) == 1, c.getInt(4))
            }
        }
        return out
    }
}

// ------------------------------------------------------------ Επόμενο ξυπνητήρι

object AlarmFeed {
    private val shared = Shared<Context, Long?> { ctx -> next(ctx) }
    fun get(ctx: Context): SharedFlow<Long?> = shared.get(ctx.applicationContext)

    private fun next(ctx: Context): Flow<Long?> = callbackFlow {
        val am = ctx.getSystemService(AlarmManager::class.java)
        fun read() = am.nextAlarmClock?.triggerTime
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { trySend(read()) }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        send(read())
        awaitClose { runCatching { ctx.unregisterReceiver(r) } }
    }
}

// ------------------------------------------------------------ Βήματα (αισθητήρας του κινητού)

object StepsFeed {
    private val shared = Shared<Context, Feed<Int>> { ctx -> LiveScope.accessTick.flatMapLatest { steps(ctx) } }
    fun get(ctx: Context): SharedFlow<Feed<Int>> = shared.get(ctx.applicationContext)

    private fun today() = Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

    /**
     * Ο μετρητής του Android μετρά από την εκκίνηση της συσκευής. Κρατάμε «βάση» ανά ημέρα:
     * τη τελευταία τιμή που είδαμε χθες (ή 0 μετά από επανεκκίνηση).
     */
    private fun steps(ctx: Context): Flow<Feed<Int>> = callbackFlow {
        if (!ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) { send(Feed.NeedsAccess(Access.Activity)); awaitClose { }; return@callbackFlow }
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) { send(Feed.Failed("Χωρίς μετρητή βημάτων")); awaitClose { }; return@callbackFlow }
        val sp = ctx.getSharedPreferences("crator_steps", Context.MODE_PRIVATE)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val total = e.values[0].toLong()
                val day = today()
                var base = sp.getLong("base", -1L)
                val baseDay = sp.getInt("day", -1)
                val last = sp.getLong("last", -1L)
                when {
                    base < 0 -> base = total
                    baseDay != day -> base = if (last in 0..total) last else 0L
                    total < base -> base = 0L // επανεκκίνηση
                }
                sp.edit().putLong("base", base).putInt("day", day).putLong("last", total).apply()
                trySend(Feed.Ready((total - base).toInt().coerceAtLeast(0)))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(l) }
    }
}

// ------------------------------------------------------------ Χρόνος οθόνης & δεδομένα (Πρόσβαση χρήσης)

data class ScreenTime(val totalMs: Long, val top: List<Pair<String, Long>>)
data class DataUse(val wifiBytes: Long, val mobileBytes: Long)

object UsageFeed {
    private val screenShared = Shared<Context, Feed<ScreenTime>> { ctx -> LiveScope.accessTick.flatMapLatest { poll(ctx) { readScreen(ctx) } } }
    private val dataShared = Shared<Context, Feed<DataUse>> { ctx -> LiveScope.accessTick.flatMapLatest { poll(ctx) { readData(ctx) } } }
    fun screen(ctx: Context): SharedFlow<Feed<ScreenTime>> = screenShared.get(ctx.applicationContext)
    fun data(ctx: Context): SharedFlow<Feed<DataUse>> = dataShared.get(ctx.applicationContext)

    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun <T> poll(ctx: Context, read: (Context) -> T): Flow<Feed<T>> = flow {
        if (!granted(ctx)) { emit(Feed.NeedsAccess(Access.Usage)); return@flow }
        while (true) {
            val v = withContext(Dispatchers.IO) { suspendRunCatching { read(ctx) } }
            emit(v.fold<Feed<T>, T>({ Feed.Ready(it) }, { Feed.Failed("Μη διαθέσιμο") }))
            delay(60_000L)
        }
    }

    private fun startOfDay() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Από τα γεγονότα (resume/pause) — πιο ακριβές από τα «κουτιά» του queryUsageStats */
    private fun readScreen(ctx: Context): ScreenTime {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val start = startOfDay(); val now = System.currentTimeMillis()
        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val sum = HashMap<String, Long>()
        val home = ctx.packageName
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(pkg)?.let { t -> sum[pkg] = (sum[pkg] ?: 0L) + (e.timeStamp - t) }
            }
        }
        open.forEach { (pkg, t) -> sum[pkg] = (sum[pkg] ?: 0L) + (now - t) }
        sum.remove(home)
        val pm = ctx.packageManager
        val top = sum.entries.sortedByDescending { it.value }.take(3).map { (pkg, ms) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            label to ms
        }
        return ScreenTime(sum.values.sum(), top)
    }

    private fun readData(ctx: Context): DataUse {
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
        val start = startOfDay(); val now = System.currentTimeMillis()
        fun total(type: Int): Long = runCatching {
            val b: NetworkStats.Bucket = nsm.querySummaryForDevice(type, null, start, now)
            b.rxBytes + b.txBytes
        }.getOrDefault(0L)
        return DataUse(total(ConnectivityManager.TYPE_WIFI), total(ConnectivityManager.TYPE_MOBILE))
    }
}

// ------------------------------------------------------------ Φεγγάρι (υπολογισμός, χωρίς internet)

object Moon {
    private const val SYNODIC = 29.530588853
    private const val NEW_MOON_2000 = 947182440000L // 6 Ιαν 2000 18:14 UTC

    /** ηλικία σε ημέρες (0..29.5) και φωτισμός 0..1 */
    fun now(t: Long = System.currentTimeMillis()): Pair<Double, Double> {
        val days = (t - NEW_MOON_2000) / 86_400_000.0
        val age = days - floor(days / SYNODIC) * SYNODIC
        val illum = (1 - cos(2 * Math.PI * age / SYNODIC)) / 2
        return age to illum
    }

    fun name(age: Double): String = when {
        age < 1.0 || age > 28.5 -> "Νέα Σελήνη"
        age < 6.4 -> "Αύξων μηνίσκος"
        age < 8.4 -> "Πρώτο τέταρτο"
        age < 13.8 -> "Αύξουσα αμφίκυρτη"
        age < 15.8 -> "Πανσέληνος"
        age < 21.1 -> "Φθίνουσα αμφίκυρτη"
        age < 23.1 -> "Τελευταίο τέταρτο"
        else -> "Φθίνων μηνίσκος"
    }

    fun daysToFull(age: Double): Int {
        val d = 14.765 - age
        return (if (d < 0) d + SYNODIC else d).toInt()
    }
}
