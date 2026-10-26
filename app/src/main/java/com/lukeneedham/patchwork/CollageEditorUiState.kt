package com.lukeneedham.patchwork

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Everything the collage editor shows. [photosByBoxId] maps a box of the [root] layout to the photo placed in it, and
 * [photoPool] holds every photo the user picked, placed or not.
 */
data class CollageEditorUiState(
    val root: LayoutNode,
    val selectedTemplateId: String,
    val photosByBoxId: Map<String, PhotoState> = emptyMap(),
    val photoPool: List<PoolPhoto> = emptyList(),
    val borderPx: Float = 8f,
    val borderStyle: BorderStyle = BorderStyle.SolidColor(Color.White),
    val isSaving: Boolean = false,
) {
    /** A collage can be saved once every box has a photo. */
    val canSave: Boolean
        get() {
            val leafIds = root.leafIds()
            return leafIds.isNotEmpty() && leafIds.all { photosByBoxId.containsKey(it) } && !isSaving
        }

    companion object {
        fun initial(): CollageEditorUiState {
            val template = defaultTemplates.first { it.id == "grid_2x2" }
            return CollageEditorUiState(root = template.tree, selectedTemplateId = template.id)
        }
    }
}
