package com.crator.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMsg(val role: String, text: String) {
    var text by mutableStateOf(text)
}

class ChatState {
    val messages = mutableStateListOf<ChatMsg>()
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var draft by mutableStateOf("")
    var job: Job? = null
    val shown = HashSet<ChatMsg>()

    fun clear() { job?.cancel(); messages.clear(); error = null; busy = false }
}

object ClaudeClient {
    fun stream(apiKey: String, model: String, maxTokens: Int, system: String, history: List<Pair<String, String>>): Flow<String> = flow {
        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 90000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("accept", "text/event-stream")
        }
        val msgs = JSONArray()
        history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("stream", true)
            .put("system", system)
            .put("messages", msgs)
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err.take(200))
                throw IOException("HTTP $code: $msg")
            }
            val reader = conn.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                val j = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (j.optString("type")) {
                    "content_block_delta" -> {
                        val d = j.optJSONObject("delta")
                        if (d?.optString("type") == "text_delta") emit(d.optString("text"))
                    }
                    "error" -> throw IOException(j.optJSONObject("error")?.optString("message") ?: "Σφάλμα")
                    "message_stop" -> break
                }
            }
            reader.close()
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}

private val OPEN_CMD = Regex("\\[\\[open:(.+?)]]")

fun buildSystem(prefs: Prefs, apps: List<AppInfo>): String {
    val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale("el", "GR")).format(Date())
    val sb = StringBuilder()
    sb.append("Είσαι ο Claude, ενσωματωμένος στον Android launcher «Crator». Η απάντησή σου εμφανίζεται σε μικρό αιωρούμενο παράθυρο στο κινητό, ")
    sb.append("οπότε απαντάς σύντομα, καθαρά, χωρίς περιττό markdown, στη γλώσσα του χρήστη. Τώρα: $now.\n")
    if (prefs.claudeOpenApps) {
        sb.append("Αν ο χρήστης ζητήσει να ανοίξεις εφαρμογή, γράψε στο τέλος της απάντησης ακριβώς [[open:ΌΝΟΜΑ]] με το όνομα από τη λίστα. ")
        sb.append("Εγκατεστημένες εφαρμογές: ")
        sb.append(apps.joinToString(", ") { it.label })
        sb.append(".\n")
    }
    if (prefs.claudeInstructions.isNotBlank()) sb.append("Οδηγίες χρήστη: ").append(prefs.claudeInstructions).append('\n')
    return sb.toString()
}

fun sendChat(scope: CoroutineScope, chat: ChatState, prefs: Prefs, apps: List<AppInfo>, onOpen: (AppInfo) -> Unit) {
    val text = chat.draft.trim()
    if (text.isEmpty() || chat.busy) return
    chat.draft = ""
    chat.error = null
    chat.messages.add(ChatMsg("user", text))
    val history = chat.messages.filter { it.text.isNotBlank() }.takeLast(20).map { it.role to it.text }
    val reply = ChatMsg("assistant", "")
    chat.messages.add(reply)
    chat.busy = true
    chat.job = scope.launch {
        try {
            ClaudeClient.stream(prefs.apiKey.trim(), prefs.claudeModel.trim(), prefs.maxTokens, buildSystem(prefs, apps), history)
                .collect { reply.text += it }
            val m = OPEN_CMD.find(reply.text)
            if (m != null) {
                reply.text = reply.text.replace(OPEN_CMD, "").trim()
                val name = m.groupValues[1].trim().norm()
                val app = apps.firstOrNull { it.label.norm() == name } ?: apps.firstOrNull { it.label.norm().contains(name) }
                if (app != null) onOpen(app)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            chat.error = e.message ?: "Σφάλμα σύνδεσης"
            if (reply.text.isBlank()) chat.messages.remove(reply)
        } finally {
            chat.busy = false
        }
    }
}

@Composable
fun ClaudePanel(
    chat: ChatState,
    prefs: Prefs,
    apps: List<AppInfo>,
    seed: String,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val send = { sendChat(scope, chat, prefs, apps) { onLaunch(it, null) } }

    LaunchedEffect(seed) {
        if (seed.isNotBlank()) { chat.draft = seed; send() }
    }
    LaunchedEffect(Unit) { if (prefs.claudeKeyboard && prefs.apiKey.isNotBlank()) runCatching { focus.requestFocus() } }
    val lastLen = chat.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(chat.messages.size, lastLen / 40) {
        if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.size - 1)
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) { chat.draft = t; send() }
        }
    }

    val shape = RoundedCornerShape(34.dp)
    val bg: Modifier = when (prefs.panelStyle) {
        PanelStyle.Glass -> Modifier.smokedGlass(shape, alpha = 0.7f)
        PanelStyle.Solid -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
        PanelStyle.Aurora -> {
            val t = rememberInfiniteTransition(label = "aurora")
            val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "a")
            Modifier.background(
                Brush.linearGradient(
                    listOf(Color(0xE6141526), accent.copy(alpha = 0.35f + 0.15f * shift), Color(0xE61A0F2A)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(800f * (0.5f + shift), 1600f)
                ), shape
            ).border(1.dp, Color.White.copy(alpha = 0.16f), shape)
        }
    }

    Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() }) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(10.dp)
                .fillMaxWidth()
                .shadow(40.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .then(bg)
                .clip(shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .animateContentSize()
        ) {
            // Κεφαλίδα
            Row(Modifier.fillMaxWidth().padding(18.dp, 16.dp, 10.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, Color(0xFFFFB86B)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Claude", color = Smoke.pearl, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.2).sp)
                    Text(prefs.claudeModel, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                Icon(Icons.Rounded.EditNote, "Νέα συζήτηση", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.clip(CircleShape).clickable { chat.clear() }.padding(8.dp))
                Icon(Icons.Rounded.Close, "Κλείσιμο", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(8.dp))
            }

            if (prefs.apiKey.isBlank()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Key, null, tint = accent, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Βάλε το Anthropic API key σου για να μιλάς με τον Claude.", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(accent).clickable { onOpenSettings() }.padding(20.dp, 12.dp)) {
                        Text("Ρυθμίσεις Claude", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(14.dp, 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chat.messages.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Τι θέλεις να κάνουμε;", color = Smoke.pearl, fontSize = 26.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.6).sp)
                                Spacer(Modifier.height(16.dp))
                                listOf("Άνοιξε τις Ρυθμίσεις", "Γράψε μου ένα σύντομο μήνυμα για…", "Εξήγησέ μου απλά…", "Τι να μαγειρέψω απόψε;").forEach { s ->
                                    Box(
                                        Modifier.padding(4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f))
                                            .clickable { chat.draft = s; if (!s.endsWith("…")) send() else runCatching { focus.requestFocus() } }
                                            .padding(14.dp, 10.dp)
                                    ) { Text(s, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                    itemsIndexed(chat.messages) { i, m ->
                        val mine = m.role == "user"
                        val ap = remember(m) { androidx.compose.animation.core.Animatable(if (m in chat.shown) 1f else 0f) }
                        LaunchedEffect(m) { chat.shown.add(m); ap.animateTo(1f, Motion.lively()) }
                        Row(
                            Modifier.fillMaxWidth().graphicsLayer {
                                alpha = ap.value.coerceIn(0f, 1f)
                                translationY = (1f - ap.value) * 22.dp.toPx()
                                val s = 0.94f + 0.06f * ap.value; scaleX = s; scaleY = s
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
                            },
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                Modifier.widthIn(max = 300.dp)
                                    .clip(RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp))
                                    .background(
                                        if (mine) Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f)))
                                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.05f)))
                                    )
                                    .padding(14.dp, 10.dp)
                            ) {
                                if (!mine && m.text.isEmpty() && chat.busy && i == chat.messages.lastIndex) TypingDots()
                                else SelectionContainer {
                                    Text(m.text, color = if (mine) Color.Black else Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                                }
                            }
                        }
                    }
                    chat.error?.let { e ->
                        item { Text("⚠ $e", color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(4.dp)) }
                    }
                }
            }

            // Γρήγορη αναζήτηση εφαρμογών όσο γράφεις
            val nq = chat.draft.norm().trim()
            val matches = remember(nq, apps) {
                if (nq.length < 2) emptyList() else apps.filter { it.key !in prefs.hidden && it.label.norm().contains(nq) }.take(8)
            }
            if (matches.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches, key = { it.key }) { app ->
                        AppCell(app, prefs, 44.dp, true, { onLaunch(app, it) }, {}, Modifier.width(64.dp))
                    }
                }
            }

            // Πεδίο εισαγωγής
            Row(
                Modifier.padding(10.dp).fillMaxWidth().smokedGlass(RoundedCornerShape(26.dp), alpha = 0.35f, tint = Color.Black).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (chat.draft.isEmpty()) Text("Ρώτα οτιδήποτε ή γράψε όνομα εφαρμογής", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                    BasicTextField(
                        chat.draft, { chat.draft = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = Manrope),
                        cursorBrush = SolidColor(accent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (prefs.apiKey.isNotBlank()) send() else matches.firstOrNull()?.let { onLaunch(it, null) } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (chat.draft.isBlank() && !chat.busy) {
                    Icon(Icons.Rounded.Mic, "Φωνή", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.clip(CircleShape).clickable {
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Μίλα στον Claude")
                        runCatching { voice.launch(i) }
                    }.padding(10.dp))
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(accent).clickable {
                            if (chat.busy) { chat.job?.cancel(); chat.busy = false } else send()
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(if (chat.busy) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, null, tint = Color.Black) }
                }
            }

            // Λαβή: σύρε πάνω για κλείσιμο
            Box(
                Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) {
                    var t = 0f
                    detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t < -60) onClose() }) { c, dy -> t += dy; c.consume() }
                },
                contentAlignment = Alignment.Center
            ) { Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Color.White))
        }
    }
}
