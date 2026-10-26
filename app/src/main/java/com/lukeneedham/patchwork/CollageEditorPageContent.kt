package com.lukeneedham.patchwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.common.logger.Logger
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel

/** All of the editor's UI: state down, events up. */
@Composable
internal fun CollageEditorPageContent(
    state: CollageEditorUiState,
    onTemplateSelect: (CollageTemplate) -> Unit,
    onTreeChange: (LayoutNode) -> Unit,
    onRequestPhoto: (boxId: String) -> Unit,
    onAssignPhoto: (boxId: String, poolPhotoId: String) -> Unit,
    onRequestAddToPool: () -> Unit,
    onBorderPxChange: (Float) -> Unit,
    onBorderStyleChange: (BorderStyle) -> Unit,
    onSave: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Dark900)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", color = White, fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Patchwork",
                    color = White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Settings", onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = Subtle, fontSize = 22.sp)
                }
            }

            // ── Layout picker ────────────────────────────────────────────────
            Text(
                "Choose a Starting Layout",
                color = Subtle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(defaultTemplates) { template ->
                    LayoutChip(
                        template = template,
                        isSelected = template.id == state.selectedTemplateId,
                        onClick = { onTemplateSelect(template) }
                    )
                }
            }
            Text(
                "Long-press a box to move or remove it, drag edges to resize, tap a box or drag a photo in to fill it",
                color = Subtle,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
            )

            Spacer(Modifier.height(18.dp))

            // ── Preview / editor canvas ─────────────────────────────────────────

            // CollageEditorCanvas sizes itself: the square canvas, the "drag to
            // add" handle, and the photo pool, so no aspectRatio here.
            CollageEditorCanvas(
                root = state.root,
                photosByBoxId = state.photosByBoxId,
                photoPool = state.photoPool,
                borderPx = state.borderPx,
                borderStyle = state.borderStyle,
                onTreeChange = onTreeChange,
                onRequestPhoto = onRequestPhoto,
                onAssignPhoto = onAssignPhoto,
                onRequestAddToPool = onRequestAddToPool,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // ── Border & Color Controls ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Border:", color = White, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = state.borderPx,
                    onValueChange = onBorderPxChange,
                    valueRange = 0f..40f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500)
                )
            }
            var showColorPicker by remember { mutableStateOf(false) }

            if (showColorPicker) {
                val initColor = (state.borderStyle as? BorderStyle.SolidColor)?.color ?: Color.White
                AlertDialog(
                    onDismissRequest = { showColorPicker = false },
                    title = { Text("Pick Border Color", color = White) },
                    text = {
                        Column {
                            Box(Modifier.fillMaxWidth().height(40.dp).background(initColor).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.height(16.dp))
                            ColorSpectrumPicker(
                                initialColor = initColor,
                                onColorChanged = { onBorderStyleChange(BorderStyle.SolidColor(it)) }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showColorPicker = false }) { Text("Done", color = Pink500) }
                    },
                    containerColor = Dark800
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val currentStyle = state.borderStyle
                val availableColors = listOf(Color.White, Color.Black, Pink500, Purple600, Color(0xFFF44336), Color(0xFF4CAF50), Color(0xFF2196F3))
                val isCustomSelected = currentStyle is BorderStyle.SolidColor && !availableColors.contains(currentStyle.color)
                item {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                            .border(2.dp, if (isCustomSelected) White else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                            .clickable { showColorPicker = true }
                    )
                }
                items(availableColors) { c ->
                    val isSel = currentStyle is BorderStyle.SolidColor && currentStyle.color == c
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(c)
                            .border(2.dp, if (isSel) White else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                            .clickable { onBorderStyleChange(BorderStyle.SolidColor(c)) }
                    )
                }
            }

            var showGradients by remember { mutableStateOf(false) }
            
            // ── Gradients ────────────────────────────────────────────────────
            @Composable
            fun GradientBubble(p: BorderStyle.GradientPattern) {
                val isSel = state.borderStyle == p
                val brush = when (p.dir) {
                    GradientDir.TOP_BOTTOM -> Brush.verticalGradient(p.colors)
                    GradientDir.LEFT_RIGHT -> Brush.horizontalGradient(p.colors)
                    GradientDir.REVERSE_DIAGONAL -> Brush.linearGradient(p.colors, start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY))
                    GradientDir.RADIAL_CENTER -> Brush.radialGradient(p.colors)
                    GradientDir.RADIAL_CORNER -> Brush.radialGradient(p.colors, center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 200f)
                    else -> Brush.linearGradient(p.colors)
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(brush)
                        .border(2.dp, if (isSel) White else Color(0x44FFFFFF), androidx.compose.foundation.shape.CircleShape)
                        .clickable { onBorderStyleChange(p) }
                )
            }

            val gradientPatterns = listOf(
                BorderStyle.GradientPattern(listOf(Color(0xFF00FFFF), Color(0xFF7B00FF), Color(0xFFFF00FF), Color(0xFF00FFCC), Color(0xFF0055FF)), "Neon Night", GradientDir.DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFF4B0082), Color(0xFF7B00FF), Color(0xFF00FFD5), Color(0xFF00BFFF), Color(0xFF8A2BE2)), "Cyber Violet", GradientDir.TOP_BOTTOM),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF6600), Color(0xFFFF0066), Color(0xFFFFD700), Color(0xFFFF3300), Color(0xFFFF9900)), "Blaze", GradientDir.LEFT_RIGHT),
                BorderStyle.GradientPattern(listOf(Color(0xFF003366), Color(0xFF00BFFF), Color(0xFF7FFFD4), Color(0xFF00FFFF), Color(0xFF1E90FF)), "Electric Ocean", GradientDir.REVERSE_DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFF39FF14), Color(0xFF00FF7F), Color(0xFF00FFCC), Color(0xFFADFF2F), Color(0xFF39FF14)), "Matrix Green", GradientDir.RADIAL_CENTER),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF007F), Color(0xFFFF4500), Color(0xFFFFD700), Color(0xFFFF69B4), Color(0xFFFF6347)), "Sunset Rave", GradientDir.DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFF00F5D4), Color(0xFF7209B7), Color(0xFFF72585), Color(0xFF4361EE), Color(0xFF4CC9F0)), "Aurora", GradientDir.TOP_BOTTOM),
                BorderStyle.GradientPattern(listOf(Color(0xFFFFB3C6), Color(0xFFCDB4DB), Color(0xFFA2D2FF), Color(0xFFBDE0FE), Color(0xFFFFAFCC)), "Cotton Candy", GradientDir.LEFT_RIGHT),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF0080), Color(0xFF8B00FF), Color(0xFF001AFF), Color(0xFF00BFFF), Color(0xFFFF0080)), "Retrowave", GradientDir.REVERSE_DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF4500), Color(0xFFFF8C00), Color(0xFFFFD700), Color(0xFFFFFFE0), Color(0xFFFF6347)), "Lava Core", GradientDir.RADIAL_CENTER),
                BorderStyle.GradientPattern(listOf(Color(0xFF00FF00), Color(0xFF00FFAA), Color(0xFF0088FF), Color(0xFFAA00FF), Color(0xFFFF0088)), "Tropic Fusion", GradientDir.DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF8C00), Color(0xFFFFB300), Color(0xFFFFD700), Color(0xFFFFEA00), Color(0xFFFF6500)), "Golden Hour", GradientDir.LEFT_RIGHT),
                BorderStyle.GradientPattern(listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00BCD4), Color(0xFF006064), Color(0xFF80CBC4)), "Ice Storm", GradientDir.TOP_BOTTOM),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF0000), Color(0xFFFF7700), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF8B00FF)), "Carnival", GradientDir.REVERSE_DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFFB76E79), Color(0xFFD4A017), Color(0xFFE8C4A0), Color(0xFFC9956C), Color(0xFFB76E79)), "Rose Gold", GradientDir.RADIAL_CORNER),
                BorderStyle.GradientPattern(listOf(Color(0xFF2E0047), Color(0xFF7B2D8B), Color(0xFFE040FB), Color(0xFFFF6E40), Color(0xFFFFD740)), "Deep Galaxy", GradientDir.RADIAL_CENTER),
                // 5 new dynamic entries
                BorderStyle.GradientPattern(listOf(Color(0xFF0FF0FC), Color(0xFF00B4D8), Color(0xFF9B5DE5), Color(0xFFF15BB5), Color(0xFFFEE440), Color(0xFF00F5D4)), "Prism", GradientDir.DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF1744), Color(0xFFFF6D00), Color(0xFFFFEA00), Color(0xFF76FF03), Color(0xFF00E5FF), Color(0xFFD500F9)), "Full Spectrum", GradientDir.LEFT_RIGHT),
                BorderStyle.GradientPattern(listOf(Color(0xFF212121), Color(0xFF37474F), Color(0xFF00E5FF), Color(0xFF1DE9B6), Color(0xFF212121)), "Obsidian Glow", GradientDir.RADIAL_CENTER),
                BorderStyle.GradientPattern(listOf(Color(0xFFAA00FF), Color(0xFFFF6EC7), Color(0xFFFFD700), Color(0xFFFF4081), Color(0xFF40C4FF), Color(0xFFAA00FF)), "Holographic", GradientDir.REVERSE_DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53), Color(0xFFFF6B6B), Color(0xFFFF8E53), Color(0xFFFECE00), Color(0xFFFF6B6B)), "Phoenix", GradientDir.RADIAL_CORNER),
                // 5 additional dynamic gradients
                BorderStyle.GradientPattern(listOf(Color(0xFF0B0033), Color(0xFF3700B3), Color(0xFF6200EE), Color(0xFFBB86FC), Color(0xFFFF00FF)), "Starry Nebula", GradientDir.DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFF1A1A1A), Color(0xFF32CD32), Color(0xFF00CED1), Color(0xFF1A1A1A)), "Cyber Lime", GradientDir.LEFT_RIGHT),
                BorderStyle.GradientPattern(listOf(Color(0xFFFF4E50), Color(0xFFFC913A), Color(0xFFF9D423), Color(0xFFEDE574)), "Sunset Silk", GradientDir.TOP_BOTTOM),
                BorderStyle.GradientPattern(listOf(Color(0xFF000428), Color(0xFF004e92), Color(0xFF0083B0), Color(0xFF00B4DB)), "Oceanic Drift", GradientDir.REVERSE_DIAGONAL),
                BorderStyle.GradientPattern(listOf(Color(0xFF000000), Color(0xFF4B0000), Color(0xFFB22222), Color(0xFFFF4500), Color(0xFFFFD700)), "Solar Eclipse", GradientDir.RADIAL_CENTER)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Dark700)
                    .clickable { showGradients = !showGradients }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✨", fontSize = 16.sp)
                    Text("Gradient Borders", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (showGradients) "▲" else "▼", color = Subtle, fontSize = 11.sp)
            }
            androidx.compose.animation.AnimatedVisibility(visible = showGradients) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(gradientPatterns) { p -> GradientBubble(p) }
                }
            }

            } // Close scrollable Column

            // ── Action buttons ───────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save collage
                val canSave = state.canSave
                Button(
                    onClick = { if (canSave) onSave() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Surface
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (canSave) AppGradient else Brush.linearGradient(listOf(Surface, Surface))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("💾  Save Collage", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    CollageEditorPageContent(
        state = CollageEditorUiState.initial(),
        onTemplateSelect = {},
        onTreeChange = {},
        onRequestPhoto = {},
        onAssignPhoto = { _, _ -> },
        onRequestAddToPool = {},
        onBorderPxChange = {},
        onBorderStyleChange = {},
        onSave = {},
        onOpenSettings = {},
    )
}
