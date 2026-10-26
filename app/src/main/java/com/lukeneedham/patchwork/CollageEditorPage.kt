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

/** The collage editor: connects [CollageEditorViewModel] to the photo picker and the toasts, and draws nothing itself. */
@Composable
fun CollageEditorPage() {
    val viewModel = viewModel<CollageEditorViewModel>()
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> viewModel.onPhotoPicked(uri) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CollageEditorEvent.Saved -> Toast.makeText(context, "✅ Saved to Pictures/Patchwork!", Toast.LENGTH_SHORT).show()
                CollageEditorEvent.SaveFailed -> Toast.makeText(context, "❌ Save failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CollageEditorPageContent(
        state = viewModel.uiState,
        onTemplateSelect = viewModel::onTemplateSelect,
        onTreeChange = viewModel::onTreeChange,
        onRequestPhoto = { boxId ->
            viewModel.onPickerRequested(boxId)
            photoPickerLauncher.launch("image/*")
        },
        onAssignPhoto = viewModel::onAssignPhoto,
        onRequestAddToPool = {
            viewModel.onPickerRequested(null)
            photoPickerLauncher.launch("image/*")
        },
        onBorderPxChange = viewModel::onBorderPxChange,
        onBorderStyleChange = viewModel::onBorderStyleChange,
        onSave = viewModel::onSave,
    )
}
