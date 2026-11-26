@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Μοιάζει με διεύθυνση; (google.com, www.x.gr, https://…) */
fun asUrl(q: String): String? {
    val t = q.trim()
    if (t.isEmpty() || t.contains(' ')) return null
    if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
    val host = t.substringBefore('/')
    val ok = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?$").matches(host) ||
        Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$").matches(host)
    return if (ok) "https://$t" else null
}

/** Απλή αριθμομηχανή: + - * / ^ ( ) % και , ως δεκαδικό */
fun calc(q: String): String? {
    val s = q.replace(" ", "").replace(',', '.').replace('×', '*').replace('÷', '/').replace('x', '*')
    if (s.isEmpty() || !s.drop(1).any { it in "+-*/^%" } || !s.any { it.isDigit() } || !s.all { it.isDigit() || it in "+-*/^%.()" }) return null
    var i = 0
    fun peek() = if (i < s.length) s[i] else '\u0000'
    lateinit var expr: () -> Double
    fun atom(): Double {
        if (peek() == '-') { i++; return -atom() }
        if (peek() == '+') { i++; return atom() }
        if (peek() == '(') { i++; val v = expr(); if (peek() != ')') throw IllegalArgumentException(); i++; return v }
        val st = i
        while (peek().isDigit() || peek() == '.') i++
        if (st == i) throw IllegalArgumentException()
        var v = s.substring(st, i).toDouble()
        if (peek() == '%') { i++; v /= 100 }
        return v
    }
    fun pow(): Double { val b = atom(); return if (peek() == '^') { i++; Math.pow(b, pow()) } else b }
    fun term(): Double {
        var v = pow()
        while (peek() == '*' || peek() == '/') { val op = s[i++]; val r = pow(); v = if (op == '*') v * r else v / r }
        return v
    }
    expr = {
        var v = term()
        while (peek() == '+' || peek() == '-') { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
        v
    }
    return runCatching {
        val v = expr()
        if (i != s.length || v.isNaN() || v.isInfinite()) null
        else if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString()
        else String.format(Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
    }.getOrNull()
}

/**
 * Αναζήτηση από το swipe κάτω: άδεια στην αρχή, γεμίζει όσο γράφεις.
 * Διευθύνσεις (google.com) ανοίγουν κατευθείαν, εφαρμογές, συντομεύσεις, πράξεις, αναζήτηση στο web.
 */
@Composable
fun SearchScreen(
    apps: List<AppInfo>,
    prefs: Prefs,
    seed: String,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLong: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf(seed) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val nq = q.norm().trim()
    val url = remember(q) { asUrl(q) }
    val math = remember(q) { calc(q) }
    val found = remember(apps, nq, prefs.hidden) {
        if (nq.isEmpty()) emptyList()
        else apps.filter { it.key !in prefs.hidden && (it.label.norm().contains(nq) || it.component.packageName.contains(nq)) }
            .sortedWith(compareBy({ if (it.label.norm().startsWith(nq)) 0 else if (it.label.norm().split(' ').any { w -> w.startsWith(nq) }) 1 else 2 }, { it.label.norm() }))
            .take(30)
    }
    val shortcuts = remember(nq) {
        if (nq.length < 2) emptyList() else DASH_CATALOG.filter { it.id != "search" && (it.label.norm().contains(nq) || it.id.contains(nq)) }.take(6)
    }

    fun open(u: String) { Actions.openUrl(ctx, u); onClose() }
    fun web() = open("https://www.google.com/search?q=" + Uri.encode(q.trim()))
    fun go() {
        when {
            q.isBlank() -> {}
            url != null -> open(url)
            found.isNotEmpty() -> onLaunch(found.first(), null)
            else -> web()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Color(prefs.drawerColor).copy(alpha = prefs.drawerOpacity / 100f))
            .statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        SearchField(
            prefs, q, { q = it }, "Εφαρμογές, διευθύνσεις, αναζήτηση…", focus,
            onGo = { go() },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
        if (q.isBlank()) {
            // τίποτα πάνω: πάτημα στο κενό κλείνει
            Box(Modifier.weight(1f).fillMaxWidth().clickable(MutableInteractionSource(), null) { onClose() })
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp)) {
            if (url != null) item("url") {
                ActionRow(Icons.Rounded.Public, url.removePrefix("https://").removePrefix("http://"), "Άνοιγμα διεύθυνσης", true) { open(url) }
            }
            if (math != null) item("calc") {
                ActionRow(Icons.Rounded.Calculate, "= $math", "Αντιγραφή αποτελέσματος", true) {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Αποτέλεσμα", math))
                    android.widget.Toast.makeText(ctx, "Αντιγράφηκε: $math", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            if (found.isNotEmpty()) {
                item("h_apps") { Title("Εφαρμογές") }
                items(found, key = { "a:" + it.key }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { onLaunch(a, null) }, onLongClick = { onLong(a) })
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(a, 40.dp, prefs)
                        Spacer(Modifier.width(16.dp))
                        Text(a.label, color = Smoke.pearl, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (shortcuts.isNotEmpty()) {
                item("h_sc") { Title("Συντομεύσεις") }
                items(shortcuts, key = { "s:" + it.id }) { s ->
                    ActionRow(s.icon, s.label, null, false) { s.run(ctx); onClose() }
                }
            }
            item("h_web") { Title("Web") }
            item("web") { ActionRow(Icons.Rounded.Search, "«${q.trim()}»", "Αναζήτηση στο Google", url == null && found.isEmpty() && math == null) { web() } }
            item("play") {
                ActionRow(Icons.Rounded.Shop, "«${q.trim()}»", "Αναζήτηση στο Play Store", false) {
                    Actions.start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun Title(t: String) =
    Text(t, color = Smoke.mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp, 14.dp, 10.dp, 4.dp))

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String?, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(16.dp))
            .background(if (primary) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Smoke.pearl, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Smoke.pearl, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Smoke.mist, fontSize = 12.sp, maxLines = 1)
        }
    }
}
