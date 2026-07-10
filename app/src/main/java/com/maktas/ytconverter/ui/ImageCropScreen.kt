package com.maktas.ytconverter.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // Image fills screen; user pans and zooms it behind a fixed square crop box.
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )

        // Crop overlay — four dark rectangles around a clear square + white border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val cropSize = min(size.width, size.height) * 0.85f
                    val left = (size.width - cropSize) / 2
                    val top = (size.height - cropSize) / 2
                    val right = left + cropSize
                    val bottom = top + cropSize
                    val dim = Color.Black.copy(alpha = 0.55f)

                    drawRect(dim, Offset.Zero, Size(size.width, top))
                    drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
                    drawRect(dim, Offset(0f, top), Size(left, cropSize))
                    drawRect(dim, Offset(right, top), Size(size.width - right, cropSize))

                    // Border + corner accents
                    drawRect(
                        Color.White,
                        Offset(left, top),
                        Size(cropSize, cropSize),
                        style = Stroke(1.5.dp.toPx()),
                    )
                    val cl = 20.dp.toPx()
                    val cw = 3.dp.toPx()
                    val corners = listOf(
                        Triple(Offset(left, top), Offset(left + cl, top), Offset(left, top + cl)),
                        Triple(Offset(right, top), Offset(right - cl, top), Offset(right, top + cl)),
                        Triple(Offset(left, bottom), Offset(left + cl, bottom), Offset(left, bottom - cl)),
                        Triple(Offset(right, bottom), Offset(right - cl, bottom), Offset(right, bottom - cl)),
                    )
                    corners.forEach { (c, a, b) ->
                        drawLine(Color.White, c, a, strokeWidth = cw)
                        drawLine(Color.White, c, b, strokeWidth = cw)
                    }
                }
        )

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = {
                    onConfirm(cropBitmap(bitmap, screenW, screenH, scale, offsetX, offsetY))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Use photo")
            }
        }
    }
}

private fun cropBitmap(
    bitmap: Bitmap,
    screenW: Float,
    screenH: Float,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
): Bitmap {
    val bW = bitmap.width.toFloat()
    val bH = bitmap.height.toFloat()

    // How the image fits the screen with ContentScale.Fit
    val fitScale = min(screenW / bW, screenH / bH)
    val imgLeft = (screenW - bW * fitScale) / 2f
    val imgTop = (screenH - bH * fitScale) / 2f

    // Crop square in screen coords
    val cropSize = min(screenW, screenH) * 0.85f
    val cropLeft = (screenW - cropSize) / 2f
    val cropTop = (screenH - cropSize) / 2f
    val cropRight = cropLeft + cropSize
    val cropBottom = cropTop + cropSize

    // Invert the graphicsLayer transform (pivot = screen centre) + the fit transform.
    fun toImgX(px: Float): Int {
        val cx = (px - screenW / 2f - offsetX) / userScale + screenW / 2f
        return ((cx - imgLeft) / fitScale).roundToInt().coerceIn(0, bitmap.width)
    }
    fun toImgY(py: Float): Int {
        val cy = (py - screenH / 2f - offsetY) / userScale + screenH / 2f
        return ((cy - imgTop) / fitScale).roundToInt().coerceIn(0, bitmap.height)
    }

    val x0 = toImgX(cropLeft)
    val y0 = toImgY(cropTop)
    val x1 = toImgX(cropRight).coerceAtLeast(x0 + 1)
    val y1 = toImgY(cropBottom).coerceAtLeast(y0 + 1)

    return Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
}
