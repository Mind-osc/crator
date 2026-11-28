@file:OptIn(ExperimentalAnimationApi::class)

package com.crator.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Παλέτα «Smoke»: φιμέ γυαλί, μαργαριταρένιο κείμενο */
object Smoke {
    val tint = Color(0xFF14151D)
    val graphite = Color(0xFF22242F)
    val pearl = Color(0xFFF3F2F7)
    val mist = Color(0xFFA9ACBD)
    val haze = Color(0xFF6E7186)
    val danger = Color(0xFFE8606A)
}

val Manrope = FontFamily(
    Font(R.font.manrope_extralight, FontWeight.ExtraLight),
    Font(R.font.manrope_extralight, FontWeight.Thin),
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_bold, FontWeight.Black)
)

fun cratorTypography(): Typography {
    val t = Typography()
    val f = Manrope
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f)
    )
}

/** Κινήσεις με ελατήρια: ό,τι κινείται, κινείται «οργανικά» */
object Motion {
    /** Γενική ταχύτητα και ανά κατηγορία (1 = κανονική) */
    // Διαβάζονται κατευθείαν από τις ρυθμίσεις (state), ώστε όποιο composable τις χρησιμοποιεί
    // να ανανεώνεται μόνο του — χωρίς εγγραφές σε global μέσα στο composition
    private var prefs: Prefs? = null
    private var systemScale = 1f
    fun bind(p: Prefs, animatorScale: Float) { prefs = p; systemScale = animatorScale }
    val speed: Float get() {
        val p = prefs ?: return 1f
        return if (systemScale == 0f) 8f else (p.animSpeed / 100f) / systemScale.coerceAtLeast(0.25f)
    }
    val overlay: Float get() = (prefs?.speedOverlay ?: 100) / 100f
    val sheet: Float get() = (prefs?.speedSheet ?: 100) / 100f
    val drag: Float get() = (prefs?.speedDrag ?: 100) / 100f
    val icon: Float get() = (prefs?.speedIcon ?: 100) / 100f
    val page: Float get() = (prefs?.speedPage ?: 100) / 100f
    private fun k(s: Float, c: Float): Float { val v = speed * c; return s * v * v }
    fun <T> soft(c: Float = 1f): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = k(240f, c))
    fun <T> lively(c: Float = 1f): SpringSpec<T> = spring(dampingRatio = 0.74f, stiffness = k(380f, c))
    fun <T> snappy(c: Float = 1f): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = k(900f, c))
    fun <T> pop(c: Float = 1f): SpringSpec<T> = spring(dampingRatio = 0.46f, stiffness = k(520f, c))
    /** Χωρίς αναπήδηση: για ό,τι ανοίγει/κλείνει με κουμπί (δεν ασκήθηκε δύναμη) */
    fun <T> settle(c: Float = 1f): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = k(520f, c))
    /** Τέλος χειρονομίας: ελάχιστη αναπήδηση, ξεκινάει με την ταχύτητα του δαχτύλου */
    fun <T> fling(c: Float = 1f): SpringSpec<T> = spring(dampingRatio = 0.84f, stiffness = k(420f, c))
    fun fadeMs(ms: Int, c: Float = 1f) = (ms / (speed * c)).toInt().coerceAtLeast(1)
}

/** Φιμέ γυαλί: απόχρωση + λεπτή λάμψη από πάνω + φωτεινή ακμή */
fun Modifier.smokedGlass(
    shape: Shape,
    alpha: Float = 0.62f,
    tint: Color = Smoke.tint,
    edge: Boolean = true
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = alpha))
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.09f),
            0.4f to Color.White.copy(alpha = 0.015f),
            1f to Color.Transparent
        )
    )
    .then(
        if (edge) Modifier.border(
            0.8.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))),
            shape
        ) else Modifier
    )

val LocalPresence = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Κρατάει την τελευταία τιμή ώστε το exit animation να παίζει με περιεχόμενο */
@Composable
fun <T : Any> Presence(
    value: T?,
    enter: EnterTransition = fadeIn(tween(Motion.fadeMs(180, Motion.sheet))),
    exit: ExitTransition = fadeOut(tween(Motion.fadeMs(160, Motion.sheet))),
    content: @Composable (T) -> Unit
) {
    var last by remember { mutableStateOf(value) }
    if (value != null && value != last) last = value
    AnimatedVisibility(visible = value != null, enter = enter, exit = exit) {
        CompositionLocalProvider(LocalPresence provides this) {
            last?.let { content(it) }
        }
    }
}

/** Είσοδος/έξοδος κάρτας sheet: ανεβαίνει, μεγαλώνει ελαφρά, σαν να «αναδύεται» */
@Composable
fun Modifier.sheetMotion(): Modifier {
    val scope = LocalPresence.current ?: return this
    return with(scope) {
        this@sheetMotion.animateEnterExit(
            enter = slideInVertically(Motion.lively(Motion.sheet)) { it / 3 } +
                scaleIn(Motion.lively(Motion.sheet), 0.9f, TransformOrigin(0.5f, 1f)) +
                fadeIn(tween(Motion.fadeMs(160, Motion.sheet))),
            exit = slideOutVertically(Motion.snappy(Motion.sheet)) { it / 4 } +
                scaleOut(tween(Motion.fadeMs(160, Motion.sheet)), 0.96f, TransformOrigin(0.5f, 1f)) +
                fadeOut(tween(Motion.fadeMs(120, Motion.sheet)))
        )
    }
}

/** «Σκάει» με ελατήριο όταν το key γίνει ενεργό· με ring = κύμα γύρω του */
@Composable
fun PopIn(active: Boolean, ring: Boolean = false, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s = remember { Animatable(1f) }
    val r = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            s.snapTo(0.55f)
            if (ring) { r.snapTo(0f) }
            coroutineScope {
                launch { s.animateTo(1f, Motion.pop(Motion.icon)) }
                if (ring) launch { r.animateTo(1f, tween(Motion.fadeMs(700, Motion.icon))) }
            }
        }
    }
    Box(
        modifier
            .drawBehind {
                val p = r.value
                if (p < 1f) {
                    val rad = size.minDimension / 2f * (0.9f + p * 0.9f)
                    drawCircle(Color.White.copy(alpha = 0.45f * (1f - p)), rad, style = Stroke(width = 2.dp.toPx() * (1f - p) + 0.5f))
                }
            }
            .graphicsLayer { scaleX = s.value; scaleY = s.value }
    ) { content() }
}


/** Είσοδος popup από τη γωνία που «ακουμπάει» το εικονίδιο */
@Composable
fun Modifier.popupMotion(origin: TransformOrigin): Modifier {
    val scope = LocalPresence.current ?: return this
    return with(scope) {
        this@popupMotion.animateEnterExit(
            enter = scaleIn(Motion.lively(Motion.sheet), 0.6f, origin) + fadeIn(tween(Motion.fadeMs(140, Motion.sheet))),
            exit = scaleOut(Motion.snappy(Motion.sheet), 0.8f, origin) + fadeOut(tween(Motion.fadeMs(110, Motion.sheet)))
        )
    }
}
