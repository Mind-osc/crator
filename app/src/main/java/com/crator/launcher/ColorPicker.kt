package com.crator.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun toHsv(argb: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }

private fun hex(argb: Int) = String.format("%06X", argb and 0xFFFFFF)

/** Επιλογέας ακριβούς χρώματος: τετράγωνο κορεσμού/φωτεινότητας, μπάρα απόχρωσης, hex */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { toHsv(initial) }
    var h by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(h, sat, v)
    var hexText by remember { mutableStateOf(hex(initial)) }
    fun sync() { hexText = hex(Color.hsv(h, sat, v).toArgb()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Χρώμα") },
        text = {
            Column {
                // Κορεσμός (οριζόντια) × φωτεινότητα (κάθετα)
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            fun set(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                v = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                sync()
                            }
                            detectTapGestures { set(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { c, _ ->
                                sat = (c.position.x / size.width).coerceIn(0f, 1f)
                                v = 1f - (c.position.y / size.height).coerceIn(0f, 1f)
                                sync()
                            }
                        }
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val p = Offset(sat * size.width, (1f - v) * size.height)
                        drawCircle(Color.White, 10.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                        drawCircle(Color.Black.copy(alpha = 0.4f), 11.5.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Απόχρωση
                Box(
                    Modifier.fillMaxWidth().height(26.dp)
                        .pointerInput(Unit) { detectTapGestures { h = (it.x / size.width * 360f).coerceIn(0f, 359.9f); sync() } }
                        .pointerInput(Unit) {
                            detectDragGestures { c, _ -> h = (c.position.x / size.width * 360f).coerceIn(0f, 359.9f); sync() }
                        }
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val hues = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }
                        drawRoundRect(Brush.horizontalGradient(hues), cornerRadius = CornerRadius(size.height / 2))
                        val x = h / 360f * size.width
                        drawCircle(Color.White, size.height / 2 - 1.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        hexText,
                        { t ->
                            val clean = t.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            hexText = clean
                            if (clean.length == 6) {
                                val hsv = toHsv(0xFF000000.toInt() or clean.toInt(16))
                                h = hsv[0]; sat = hsv[1]; v = hsv[2]
                            }
                        },
                        prefix = { Text("#") },
                        singleLine = true,
                        label = { Text("Hex", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Color.hsv(h, sat, v).toArgb()); onDismiss() }) { Text("Εφαρμογή") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } }
    )
}
