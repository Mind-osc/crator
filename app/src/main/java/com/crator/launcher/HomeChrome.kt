@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.crator.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun isDefaultHome(activity: MainActivity, resumed: Boolean): Boolean {
    return remember(resumed) {
        val rm = activity.getSystemService(RoleManager::class.java)
        rm == null || !rm.isRoleAvailable(RoleManager.ROLE_HOME) || rm.isRoleHeld(RoleManager.ROLE_HOME)
    }
}

@Composable
internal fun DefaultBanner(activity: MainActivity, onDismiss: () -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .smokedGlass(RoundedCornerShape(24.dp), alpha = 0.72f)
            .clickable { onDismiss(); activity.requestHomeRole() }
            .pointerInput(Unit) {
                var t = 0f
                detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t < -30) onDismiss() }) { c, dy -> t += dy; c.consume() }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Κάνε το Crator αρχική οθόνη", color = Smoke.pearl, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Πάτα εδώ ή αργότερα από τις Ρυθμίσεις", color = Smoke.mist, fontSize = 12.sp)
        }
    }
}

fun Modifier.pageEffect(state: PagerState, page: Int, effect: PageEffect): Modifier = graphicsLayer {
    val o = (state.currentPage - page) + state.currentPageOffsetFraction
    val a = abs(o).coerceIn(0f, 1f)
    val w = size.width
    cameraDistance = 14f * density
    // Κατά τη μετάβαση η σελίδα «ψήνεται» πρώτα σε μία εικόνα και μετά παίρνει διαφάνεια/περιστροφή:
    // αλλιώς κάθε κάρτα, σκιά και το ημιδιάφανο πάνελ σβήνουν ξεχωριστά και το χρώμα τρεμοπαίζει.
    compositingStrategy = if (a > 0.001f && effect != PageEffect.Slide) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        else androidx.compose.ui.graphics.CompositingStrategy.Auto
    when (effect) {
        PageEffect.Slide -> {}
        PageEffect.Fade -> { translationX = o * w; alpha = 1f - a }
        PageEffect.Zoom -> { val s = 1f - 0.22f * a; scaleX = s; scaleY = s; alpha = 1f - 0.5f * a }
        PageEffect.Depth -> if (o > 0) { translationX = o * w; val s = 1f - 0.25f * a; scaleX = s; scaleY = s; alpha = 1f - a }
        PageEffect.Stack -> if (o < 0) { translationX = o * w; val s = 0.7f + 0.3f * (1f - a); scaleX = s; scaleY = s; alpha = 1f - a }
        PageEffect.Cube -> {
            transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
            rotationY = -90f * o.coerceIn(-1f, 1f)
        }
        PageEffect.CubeIn -> {
            transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
            rotationY = 90f * o.coerceIn(-1f, 1f)
        }
        PageEffect.Flip -> { translationX = o * w; rotationY = 180f * o; alpha = if (a < 0.5f) 1f else 0f }
        PageEffect.Windmill -> {
            translationX = o * w
            transformOrigin = TransformOrigin(0.5f, 1.6f)
            rotationZ = -40f * o
            alpha = 1f - 0.4f * a
        }
        PageEffect.Carousel -> { val s = 1f - 0.18f * a; scaleX = s; scaleY = s; rotationY = 35f * o }
        PageEffect.Accordion -> {
            translationX = o * w
            transformOrigin = TransformOrigin(if (o > 0) 0f else 1f, 0.5f)
            scaleX = 1f - a
        }
        PageEffect.Tilt -> { rotationX = 18f * a; val s = 1f - 0.1f * a; scaleX = s; scaleY = s }
    }
}

@Composable
internal fun ClockHeader(prefs: Prefs) {
    val ctx = LocalContext.current
    val resumedClock = rememberResumed()
    // ενημέρωση μόνο όταν φαίνεται ο launcher (στο παρασκήνιο κοιμάται)
    val now by produceState(Date(), resumedClock) {
        value = Date()
        while (resumedClock) {
            value = Date()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50)
        }
    }
    val el = Locale("el", "GR")
    val time = SimpleDateFormat("HH:mm", el).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", el).format(now).replaceFirstChar { it.uppercase() }
    val open = Modifier.clickable { Actions.start(ctx, android.content.Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 3f), 12f)
    Column(Modifier.fillMaxWidth().padding(24.dp, 18.dp, 24.dp, 8.dp).then(open)) {
        when (prefs.clockStyle) {
            ClockStyle.Big -> {
                Text(time, style = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 84.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-3).sp, color = Smoke.pearl, shadow = shadow))
                Text(date, style = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, color = Smoke.pearl.copy(alpha = 0.82f), shadow = shadow))
            }
            ClockStyle.Minimal -> Row(verticalAlignment = Alignment.Bottom) {
                Text(time, fontSize = 34.sp, fontWeight = FontWeight.Light, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(date, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
            }
            ClockStyle.Stacked -> {
                val (h, m) = time.split(":")
                Text(h, fontSize = 92.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 84.sp)
                Text(m, fontSize = 92.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, lineHeight = 84.sp)
                Text(date, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
            }
            ClockStyle.Hidden -> {}
        }
    }
}

@Composable
internal fun PageIndicator(pager: PagerState, dash: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until pager.pageCount) {
            val sel = pager.currentPage == i
            val w by animateDpAsState(if (sel) 18.dp else 6.dp, label = "dot")
            if (dash && i == 0) {
                Icon(Icons.Rounded.Insights, null, tint = Color.White.copy(alpha = if (sel) 1f else 0.5f), modifier = Modifier.padding(horizontal = 3.dp).size(12.dp))
            } else {
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(Color.White.copy(alpha = if (sel) 0.95f else 0.4f)))
            }
        }
    }
}

@Composable
fun SearchPill(prefs: Prefs, modifier: Modifier = Modifier, onVoice: (() -> Unit)? = null, onClick: () -> Unit) {
    val h = prefs.searchHeight.dp
    val tc = Color(prefs.searchTextColor)
    Row(
        modifier
            .fillMaxWidth()
            .height(h)
            .smokedGlass(RoundedCornerShape(h / 2), alpha = prefs.searchOpacity / 100f, tint = Color(prefs.searchColor))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = tc, modifier = Modifier.size((h.value * 0.45f).dp))
        Spacer(Modifier.width(10.dp))
        Text("Αναζήτηση", color = tc, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (onVoice != null) Icon(
            Icons.Rounded.Mic, "Φωνητική αναζήτηση", tint = tc,
            modifier = Modifier.size((h.value * 0.45f).dp).clip(CircleShape).clickable(onClick = onVoice)
        )
    }
}
