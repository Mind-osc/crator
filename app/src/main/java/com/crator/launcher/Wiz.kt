package com.crator.launcher

import android.content.Context
import android.net.wifi.WifiManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Τοπικός έλεγχος λαμπών WiZ μέσω UDP (θύρα 38899) — χωρίς cloud, χωρίς λογαριασμό. */
object Wiz {
    private const val PORT = 38899
    private const val MIN_DIM = 10
    private const val STEP = 20

    data class Bulb(val ip: String, val on: Boolean, val dim: Int)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("crator_wiz", Context.MODE_PRIVATE)
    fun saved(ctx: Context): List<String> =
        sp(ctx).getString("ips", "").orEmpty().split(",").filter { it.isNotBlank() }
    private fun save(ctx: Context, ips: List<String>) = sp(ctx).edit().putString("ips", ips.joinToString(",")).apply()

    private fun broadcastTargets(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .forEach { nif -> nif.interfaceAddresses.mapNotNullTo(list) { it.broadcast } }
        }
        list += InetAddress.getByName("255.255.255.255")
        return list.distinct()
    }

    /** Βρίσκει όλες τις λάμπες WiZ στο τρέχον Wi-Fi */
    suspend fun discover(ctx: Context): List<String> = withContext(Dispatchers.IO) {
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val lock = runCatching { wm.createMulticastLock("crator-wiz").apply { setReferenceCounted(false); acquire() } }.getOrNull()
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 300
                val msg = """{"method":"getPilot","params":{}}""".toByteArray()
                val targets = broadcastTargets()
                val found = linkedSetOf<String>()
                val buf = ByteArray(2048)
                repeat(3) { round ->
                    targets.forEach { t -> runCatching { s.send(DatagramPacket(msg, msg.size, t, PORT)) } }
                    val end = System.currentTimeMillis() + if (round == 2) 1200 else 500
                    while (System.currentTimeMillis() < end) {
                        try {
                            val p = DatagramPacket(buf, buf.size)
                            s.receive(p)
                            val txt = String(p.data, 0, p.length)
                            if (txt.contains("\"result\"")) p.address.hostAddress?.let { found += it }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) { break }
                    }
                }
                found.toList().sortedBy { ip -> ip.substringAfterLast('.').toIntOrNull() ?: 0 }.also { if (it.isNotEmpty()) save(ctx, it) }
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    private fun request(ip: String, json: String): JSONObject? {
        repeat(2) {
            try {
                DatagramSocket().use { s ->
                    s.soTimeout = 700
                    val b = json.toByteArray()
                    s.send(DatagramPacket(b, b.size, InetAddress.getByName(ip), PORT))
                    val buf = ByteArray(2048)
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    return JSONObject(String(p.data, 0, p.length))
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun status(ip: String): Bulb? = request(ip, """{"method":"getPilot","params":{}}""")
        ?.optJSONObject("result")
        ?.let { Bulb(ip, it.optBoolean("state", false), it.optInt("dimming", 100)) }

    private fun pilot(ip: String, params: JSONObject): Boolean =
        request(ip, JSONObject().put("method", "setPilot").put("params", params).toString())
            ?.optJSONObject("result")?.optBoolean("success", false) == true

    /** Κατάσταση όλων· αν δεν απαντά καμία από τις αποθηκευμένες, ξαναψάχνει (άλλαξε IP / άλλο δίκτυο) */
    suspend fun states(ctx: Context): List<Bulb> = withContext(Dispatchers.IO) {
        suspend fun query(ips: List<String>) = coroutineScope { ips.map { async { status(it) } }.awaitAll().filterNotNull() }
        val saved = saved(ctx)
        val res = if (saved.isEmpty()) emptyList() else query(saved)
        if (res.isNotEmpty() && res.size == saved.size) res
        else {
            val fresh = discover(ctx)
            if (fresh.isEmpty()) res else query(fresh)
        }
    }

    suspend fun power(ctx: Context, on: Boolean): List<Bulb> = withContext(Dispatchers.IO) {
        val bulbs = states(ctx)
        coroutineScope { bulbs.map { b -> async { pilot(b.ip, JSONObject().put("state", on)) } }.awaitAll() }
        bulbs.map { it.copy(on = on) }
    }

    /** +/- φωτεινότητα. Η αύξηση ανάβει και τις σβηστές· η μείωση αφορά μόνο τις αναμμένες. */
    suspend fun step(ctx: Context, up: Boolean): List<Bulb> = withContext(Dispatchers.IO) {
        val bulbs = states(ctx)
        coroutineScope {
            bulbs.map { b ->
                async {
                    when {
                        up && !b.on -> { pilot(b.ip, JSONObject().put("state", true).put("dimming", MIN_DIM)); b.copy(on = true, dim = MIN_DIM) }
                        up -> { val d = (b.dim + STEP).coerceAtMost(100); pilot(b.ip, JSONObject().put("state", true).put("dimming", d)); b.copy(dim = d) }
                        b.on -> { val d = (b.dim - STEP).coerceAtLeast(MIN_DIM); pilot(b.ip, JSONObject().put("dimming", d)); b.copy(dim = d) }
                        else -> b
                    }
                }
            }.awaitAll()
        }
    }
}

/** Μαύρο widget WiZ: Όλα ON / Όλα OFF / Φωτεινότερο / Χαμηλότερο */
@Composable
fun WizWidget(modifier: Modifier, press: Modifier, corner: Int) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val res = rememberResumed()
    var bulbs by remember { mutableStateOf<List<Wiz.Bulb>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun act(block: suspend () -> List<Wiz.Bulb>) {
        if (busy) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        busy = true
        scope.launch { runCatching { bulbs = block() }; busy = false }
    }

    LaunchedEffect(res) { if (res && !busy) { busy = true; runCatching { bulbs = Wiz.states(ctx) }; busy = false } }

    val list = bulbs
    val anyOn = list?.any { it.on } == true
    val avg = list?.filter { it.on }?.takeIf { it.isNotEmpty() }?.map { it.dim }?.average()?.toInt()
    val sub = when {
        list == null -> "Αναζήτηση λαμπών…"
        list.isEmpty() -> "Δεν βρέθηκαν λάμπες · πάτα για νέα αναζήτηση"
        anyOn -> "${list.count { it.on }}/${list.size} αναμμένες · ${avg ?: 0}%"
        else -> "${list.size} λάμπες · σβηστές"
    }
    val shape = RoundedCornerShape(corner.dp)

    Column(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF151517), Color(0xFF050506))))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .then(press)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .clickable { act { Wiz.discover(ctx); Wiz.states(ctx) } }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Λάμπες", color = Color.White, fontSize = 20.sp)
                Text(sub, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1)
            }
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(if (busy) Icons.Rounded.Refresh else Icons.Rounded.Lightbulb, null,
                    tint = if (anyOn) Color(0xFFFFE7A8) else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WizButton("All ON", Icons.Rounded.PowerSettingsNew, glow = list != null && anyOn) { act { Wiz.power(ctx, true) } }
            WizButton("All OFF", Icons.Rounded.PowerSettingsNew, glow = list != null && list.isNotEmpty() && !anyOn) { act { Wiz.power(ctx, false) } }
            WizButton("Brighten", Icons.Rounded.BrightnessHigh, glow = false) { act { Wiz.step(ctx, true) } }
            WizButton("Dim down", Icons.Rounded.BrightnessLow, glow = false) { act { Wiz.step(ctx, false) } }
        }
    }
}

@Composable
private fun WizButton(label: String, icon: ImageVector, glow: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            // λάμψη πίσω από το ενεργό κουμπί
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(
                    Brush.radialGradient(
                        if (glow) listOf(Color(0xFFFFF3D1).copy(alpha = 0.55f), Color.Transparent)
                        else listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (glow) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = if (glow) 0.5f else 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (glow) Color.White else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            }
        }
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1)
    }
}
