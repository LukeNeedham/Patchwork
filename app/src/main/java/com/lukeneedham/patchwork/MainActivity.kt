package com.lukeneedham.patchwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.common.logger.Logger
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ─── Palette ────────────────────────────────────────────────────────────────
val Pink500   = Color(0xFFE91E8C)
val Purple600 = Color(0xFF6A1BDB)
val Dark900   = Color(0xFF0D0D14)
val Dark800   = Color(0xFF16161F)
val Dark700   = Color(0xFF1E1E2C)
val Surface   = Color(0xFF242435)
val Subtle    = Color(0xFF9090A8)
val White     = Color(0xFFFFFFFF)

enum class GradientDir { DIAGONAL, REVERSE_DIAGONAL, TOP_BOTTOM, LEFT_RIGHT, RADIAL_CENTER, RADIAL_CORNER }

sealed class BorderStyle {
    data class SolidColor(val color: Color) : BorderStyle()
    data class GradientPattern(val colors: List<Color>, val label: String, val dir: GradientDir = GradientDir.DIAGONAL) : BorderStyle()
}

val AppGradient = Brush.linearGradient(listOf(Pink500, Purple600))

class PhotoState(initialBitmap: Bitmap) {
    var bitmap by mutableStateOf(initialBitmap)
    var panX by mutableStateOf(0f)
    var panY by mutableStateOf(0f)
    var zoom by mutableStateOf(1f)

    fun toPhotoData() = PhotoData(bitmap, panX, panY, zoom)
}

/** A photo in the user's pool, independent of whether (or where) it's placed in the layout. */
data class PoolPhoto(val id: String, val state: PhotoState)

// ─── Activity ────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
    }
}


// ─── Color Spectrum Picker ───────────────────────────────────────────────────
@Composable
fun ColorSpectrumPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit
) {
    var hsv by remember { 
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsvArr)
        mutableStateOf(hsvArr) 
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.isNotEmpty()) {
                                val pos = event.changes.first().position
                                val sat = (pos.x / size.width).coerceIn(0f, 1f)
                                val value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                                hsv = floatArrayOf(hsv[0], sat, value)
                                onColorChanged(Color(android.graphics.Color.HSVToColor(hsv)))
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
                drawRect(brush = Brush.horizontalGradient(colors = listOf(Color.White, hueColor)))
                drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black)))

                val thumbX = hsv[1] * size.width
                val thumbY = (1f - hsv[2]) * size.height
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(thumbX, thumbY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.isNotEmpty()) {
                                val pos = event.changes.first().position
                                val hue = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                                hsv = floatArrayOf(hue, hsv[1], hsv[2])
                                onColorChanged(Color(android.graphics.Color.HSVToColor(hsv)))
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                            Color.Blue, Color.Magenta, Color.Red
                        )
                    )
                )
                val thumbX = (hsv[0] / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

// ─── Layout chip ─────────────────────────────────────────────────────────────
// The chip picks a *starting* shape - every box in it stays fully editable
// afterwards (move / resize / add / remove), so this only seeds the tree.
@Composable
fun LayoutChip(template: CollageTemplate, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        if (isSelected) Pink500 else Dark700,
        animationSpec = tween(200), label = "chip_bg"
    )
    val scale by animateFloatAsState(
        if (isSelected) 1.06f else 1f,
        animationSpec = tween(200), label = "chip_scale"
    )
    val cellColor = if (isSelected) Color.White.copy(alpha = 0.35f) else Color(0xFF3A3A55)
    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Dark900)
                .padding(3.dp)
        ) {
            LayoutTreeThumbnail(node = template.tree, cellColor = cellColor, gap = 2.dp, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            template.displayName,
            color = White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PhotoCell(state: PhotoState, modifier: Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                // Two fingers only (pinch to zoom, two-finger drag to pan) - a
                // single finger is left for the box's own long-press-to-move
                // gesture, so the two don't fight over the same drag.
                detectTwoFingerTransformGestures { pan, zoom ->
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        state.zoom = (state.zoom * zoom).coerceIn(1f, 5f)
                        state.panX += pan.x / canvasSize.width
                        state.panY += pan.y / canvasSize.height
                    }
                }
            }
    ) {
        canvasSize = IntSize(size.width.toInt(), size.height.toInt())
        val bmp = state.bitmap
        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()
        val dstWidth = size.width
        val dstHeight = size.height

        val srcAspect = bmpWidth / bmpHeight
        val dstAspect = dstWidth / dstHeight

        val newW: Float
        val newH: Float
        if (srcAspect > dstAspect) {
            newW = bmpHeight * dstAspect
            newH = bmpHeight
        } else {
            newW = bmpWidth
            newH = bmpWidth / dstAspect
        }

        val scaledW = newW / state.zoom
        val scaledH = newH / state.zoom

        val dxOffset = state.panX * scaledW
        val dyOffset = state.panY * scaledH

        val dx = ((bmpWidth - scaledW) / 2) - dxOffset
        val clampedDx = dx.coerceIn(0f, bmpWidth - scaledW)

        val dy = ((bmpHeight - scaledH) / 2) - dyOffset
        val clampedDy = dy.coerceIn(0f, bmpHeight - scaledH)

        val srcOffset = IntOffset(clampedDx.toInt(), clampedDy.toInt())
        val srcSize = IntSize(scaledW.toInt(), scaledH.toInt())
        
        drawImage(
            image = bmp.asImageBitmap(),
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset.Zero,
            dstSize = canvasSize
        )
    }
}
