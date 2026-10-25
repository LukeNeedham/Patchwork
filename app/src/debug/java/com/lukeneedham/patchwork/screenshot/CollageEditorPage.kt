package com.lukeneedham.patchwork.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.patchwork.BorderStyle
import com.lukeneedham.patchwork.CollageEditorPageContent
import com.lukeneedham.patchwork.CollageEditorUiState
import com.lukeneedham.patchwork.PhotoState
import com.lukeneedham.patchwork.PoolPhoto
import com.lukeneedham.patchwork.defaultTemplates
import com.lukeneedham.patchwork.leafIds

/** Rendered by CI to docs/screenshots/<position in order.txt>-CollageEditorPage.png: the editor with a photo in every box. */
@Preview
@Composable
private fun CollageEditorPageScreenshot() {
    val template = defaultTemplates.first { it.id == "big_left" }
    fun photo(from: Int, to: Int): PhotoState {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 400f, 400f, from, to, Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRect(0f, 0f, 400f, 400f, paint)
        return PhotoState(bitmap)
    }
    val photos = listOf(
        photo(0xFF7B00FF.toInt(), 0xFFFF00FF.toInt()),
        photo(0xFF00B4D8.toInt(), 0xFF00F5D4.toInt()),
        photo(0xFFFF6B6B.toInt(), 0xFFFECE00.toInt()),
    )
    val leafIds = template.tree.leafIds()
    val state = CollageEditorUiState(
        root = template.tree,
        selectedTemplateId = template.id,
        photosByBoxId = leafIds.zip(photos).toMap(),
        photoPool = photos.mapIndexed { index, photo -> PoolPhoto("photo$index", photo) },
        borderPx = 12f,
        borderStyle = BorderStyle.SolidColor(Color(0xFFE91E8C)),
    )
    CollageEditorPageContent(
        state = state,
        onTemplateSelect = {},
        onTreeChange = {},
        onRequestPhoto = {},
        onAssignPhoto = { _, _ -> },
        onRequestAddToPool = {},
        onBorderPxChange = {},
        onBorderStyleChange = {},
        onSave = {},
    )
}

/** Rendered by CI to docs/screenshots/<position in order.txt>-CollageEditorEmptyPage.png: a fresh editor with no photos yet. */
@Preview
@Composable
private fun CollageEditorEmptyPageScreenshot() {
    val template = defaultTemplates.first { it.id == "split_horizontal" }
    val state = CollageEditorUiState(
        root = template.tree,
        selectedTemplateId = template.id,
        borderPx = 20f,
        borderStyle = BorderStyle.SolidColor(Color.White),
    )
    CollageEditorPageContent(
        state = state,
        onTemplateSelect = {},
        onTreeChange = {},
        onRequestPhoto = {},
        onAssignPhoto = { _, _ -> },
        onRequestAddToPool = {},
        onBorderPxChange = {},
        onBorderStyleChange = {},
        onSave = {},
    )
}
