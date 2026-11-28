@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.graphics.Rect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

val SquircleShape = GenericShape { size, _ ->
    val a = size.width / 2f
    val b = size.height / 2f
    val e = 2.0 / 4.6
    for (i in 0..144) {
        val t = i / 144.0 * 2 * PI
        val c = cos(t); val s = sin(t)
        val x = (a + a * sign(c) * abs(c).pow(e)).toFloat()
        val y = (b + b * sign(s) * abs(s).pow(e)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val HexagonShape = GenericShape { size, _ ->
    val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension / 2f
    for (i in 0 until 6) {
        val ang = PI / 180 * (60 * i - 90)
        val x = (cx + r * cos(ang)).toFloat(); val y = (cy + r * sin(ang)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val DiamondShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close()
}

fun shapeFor(s: IconShape, corner: Int): Shape = when (s) {
    IconShape.Original, IconShape.Circle -> CircleShape
    IconShape.Squircle -> SquircleShape
    IconShape.Rounded -> RoundedCornerShape(corner.coerceIn(0, 50))
    IconShape.Square -> RoundedCornerShape(6)
    IconShape.Teardrop -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 50)
    IconShape.Leaf -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 10, bottomEndPercent = 50, bottomStartPercent = 10)
    IconShape.Pebble -> RoundedCornerShape(topStartPercent = 46, topEndPercent = 34, bottomEndPercent = 46, bottomStartPercent = 34)
    IconShape.Hexagon -> HexagonShape
    IconShape.Octagon -> CutCornerShape(29)
    IconShape.Diamond -> DiamondShape
}

@Composable
fun AppIcon(app: AppInfo, size: Dp, prefs: Prefs, modifier: Modifier = Modifier) {
    val shape = remember(prefs.iconShape, prefs.iconCorner) { shapeFor(prefs.iconShape, prefs.iconCorner) }
    val sat = prefs.iconSaturation / 100f
    val filter = remember(sat) {
        if (abs(sat - 1f) < 0.01f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) })
    }
    val scale = prefs.iconScale / 100f
    val ic = app.icon
    val themed = prefs.themedIcons && ic.mono != null
    val base = modifier.size(size).graphicsLayer { alpha = prefs.iconOpacity / 100f }
    val decorated = base
        .then(if (prefs.iconShadow) Modifier.shadow(5.dp, shape, clip = false) else Modifier)
        .clip(shape)
        .then(if (prefs.iconBorder > 0) Modifier.border(prefs.iconBorder.dp, Color(prefs.iconBorderColor), shape) else Modifier)

    when {
        themed -> Box(decorated.background(Color(prefs.themedBg)), contentAlignment = Alignment.Center) {
            Image(
                bitmap = ic.mono!!, contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(scale),
                colorFilter = ColorFilter.tint(Color(prefs.themedFg))
            )
        }
        prefs.iconShape == IconShape.Original -> Image(
            bitmap = ic.raw, contentDescription = null, modifier = base.scale(scale), colorFilter = filter
        )
        ic.full != null -> Box(decorated) {
            Image(
                bitmap = ic.full, contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(scale),
                contentScale = ContentScale.Crop, colorFilter = filter
            )
        }
        else -> Box(
            decorated.background(if (prefs.iconPlate) Color(prefs.plateColor) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = ic.raw, contentDescription = null,
                modifier = Modifier.fillMaxSize(if (prefs.iconPlate) 0.74f else 1f).scale(scale),
                colorFilter = filter
            )
        }
    }
}

fun labelStyle(prefs: Prefs, sizeSp: Int = prefs.labelSize) = TextStyle(
    fontFamily = Manrope,
    letterSpacing = 0.1.sp,
    color = Color(prefs.labelColor),
    fontSize = sizeSp.sp,
    fontWeight = if (prefs.labelBold) FontWeight.SemiBold else FontWeight.Medium,
    textAlign = TextAlign.Center,
    shadow = if (prefs.labelShadow) Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 6f) else null
)

@Composable
fun AppCell(
    app: AppInfo,
    prefs: Prefs,
    iconSize: Dp,
    showLabel: Boolean,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color? = null
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // «Εκκίνηση με το άγγιγμα»: ανοίγει τη στιγμή που ακουμπάς, όχι όταν σηκώνεις το δάχτυλο
    val launchedOnPress = remember { booleanArrayOf(false) }
    val bounds = remember { arrayOfNulls<Rect>(1) }
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) launchedOnPress[0] = false
        if (pressed && prefs.instantLaunch) {
            kotlinx.coroutines.delay(55)
            launchedOnPress[0] = true
            onClick(bounds[0])
        }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val target = if (!pressed) 1f else when (prefs.pressEffect) {
        PressEffect.Bounce -> 0.9f
        PressEffect.Shrink -> 0.94f
        PressEffect.Tilt -> 0.95f
        PressEffect.None -> 1f
    }
    val sc by animateFloatAsState(
        target,
        if (prefs.pressEffect == PressEffect.Bounce) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium * Motion.icon * Motion.icon) else tween((120 / Motion.icon).toInt()),
        label = "press"
    )
    val rot by animateFloatAsState(if (pressed && prefs.pressEffect == PressEffect.Tilt) -8f else 0f, label = "tilt")
    Column(
        modifier
            .combinedClickable(
                interactionSource = source, indication = null,
                onClick = { if (launchedOnPress[0]) launchedOnPress[0] = false else onClick(bounds[0]) },
                onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() }
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.graphicsLayer { scaleX = sc; scaleY = sc; rotationZ = rot }) {
            AppIcon(
                app, iconSize, prefs,
                Modifier.onGloballyPositioned { c ->
                    val r = c.boundsInWindow()
                    bounds[0] = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                }
            )
            BadgeView(
                Badges.of(app.component.packageName, prefs), prefs, iconSize,
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.label, style = labelStyle(prefs).let { if (labelColor != null) it.copy(color = labelColor) else it }, maxLines = prefs.labelLines,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
            )
        }
    }
}
