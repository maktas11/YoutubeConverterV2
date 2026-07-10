package com.maktas.ytconverter.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A hue/saturation wheel + brightness slider for picking an arbitrary color, since
 * Compose Material3 has no built-in color picker. Hue is the angle around the wheel,
 * saturation is the distance from center (white) to edge (fully saturated).
 */
@Composable
fun ColorWheelDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    val currentColor = Color.hsv(hue, saturation, brightness)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pick a color", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))

                HueSaturationWheel(
                    hue = hue,
                    saturation = saturation,
                    onChange = { h, s -> hue = h; saturation = s },
                    modifier = Modifier.size(220.dp),
                )

                Spacer(Modifier.height(20.dp))
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = brightness, onValueChange = { brightness = it })

                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(currentColor, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onColorSelected(currentColor) }) { Text("Use this color") }
                }
            }
        }
    }
}

@Composable
private fun HueSaturationWheel(
    hue: Float,
    saturation: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            fun updateFrom(position: Offset) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f
                val dx = position.x - center.x
                val dy = position.y - center.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
                onChange(angleDeg, if (radius > 0f) dist / radius else 0f)
            }
            detectDragGestures(
                onDragStart = { updateFrom(it) },
                onDrag = { change, _ -> change.consume(); updateFrom(change.position) },
            )
        },
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Base ring: full-saturation hue around the wheel.
        drawCircle(
            brush = Brush.sweepGradient(
                colors = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) },
                center = center,
            ),
            radius = radius,
            center = center,
        )
        // White-to-transparent overlay fades saturation to 0 (white) at the center.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        // Selection indicator ring at the current hue/saturation.
        val angleRad = Math.toRadians(hue.toDouble())
        val indicatorDist = saturation * radius
        val indicatorCenter = Offset(
            center.x + (indicatorDist * cos(angleRad)).toFloat(),
            center.y + (indicatorDist * sin(angleRad)).toFloat(),
        )
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = indicatorCenter, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black, radius = 9.dp.toPx(), center = indicatorCenter, style = Stroke(width = 1.dp.toPx()))
    }
}
