package com.crator.launcher

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ρολόι ταμπλό με Orbitron: τα γράμματα μεγαλώνουν όσο το ύψος που του δίνεις */
@Composable
fun DashClock(dark: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val time by produceState(now(ctx)) {
        while (true) {
            value = now(ctx)
            // ξυπνά μόνο στην αλλαγή του λεπτού
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = LocalDensity.current
        // το Orbitron είναι φαρδύ: ~0,82 em ανά χαρακτήρα
        val byHeight = constraints.maxHeight * 0.72f
        val byWidth = constraints.maxWidth / (time.length * 0.82f)
        val px = minOf(byHeight, byWidth).coerceAtLeast(1f)
        val color = if (dark) Color.Black else Color.White
        Text(
            time,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TextStyle(
                fontFamily = Orbitron,
                fontWeight = FontWeight.Medium,
                fontSize = with(d) { px.toSp() },
                shadow = Shadow((if (dark) Color.White else Color.Black).copy(alpha = 0.25f), blurRadius = 12f)
            )
        )
    }
}

private fun now(ctx: android.content.Context): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}
