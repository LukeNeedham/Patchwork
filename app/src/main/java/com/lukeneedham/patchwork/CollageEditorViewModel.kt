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

/** One-off results that the page shows once. */
sealed interface CollageEditorEvent {
    data class Saved(val uri: Uri) : CollageEditorEvent
    data object SaveFailed : CollageEditorEvent
}

class CollageEditorViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(CollageEditorUiState.initial())
        private set

    private val eventChannel = Channel<CollageEditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    // Set before the picker opens: which box (if any) the newly picked photo is also assigned to, besides joining the pool.
    private var pendingAssignBoxId: String? = null

    fun onTemplateSelect(template: CollageTemplate) {
        // Carry existing photos across to the new shape, in the same order, so switching templates keeps what is picked.
        val oldPhotosInOrder = uiState.root.leafIds().mapNotNull { uiState.photosByBoxId[it] }
        val remapped = LinkedHashMap<String, PhotoState>()
        template.tree.leafIds().forEachIndexed { i, id -> oldPhotosInOrder.getOrNull(i)?.let { remapped[id] = it } }
        uiState = uiState.copy(root = template.tree, selectedTemplateId = template.id, photosByBoxId = remapped)
    }

    fun onTreeChange(newRoot: LayoutNode) {
        val validIds = newRoot.leafIds().toSet()
        uiState = uiState.copy(root = newRoot, photosByBoxId = uiState.photosByBoxId.filterKeys { it in validIds })
    }

    fun onBorderPxChange(borderPx: Float) {
        uiState = uiState.copy(borderPx = borderPx)
    }

    fun onBorderStyleChange(borderStyle: BorderStyle) {
        uiState = uiState.copy(borderStyle = borderStyle)
    }

    fun onAssignPhoto(boxId: String, poolPhotoId: String) {
        val photo = uiState.photoPool.firstOrNull { it.id == poolPhotoId } ?: return
        uiState = uiState.copy(photosByBoxId = uiState.photosByBoxId + (boxId to photo.state))
    }

    /** The page is about to open the photo picker, for [boxId] or (null) for the pool only. */
    fun onPickerRequested(boxId: String?) {
        pendingAssignBoxId = boxId
    }

    /** The picker returned [uri], or null when it was dismissed. Every picked photo joins the pool. */
    fun onPhotoPicked(uri: Uri?) {
        if (uri == null) return
        val assignBoxId = pendingAssignBoxId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    withContext(Dispatchers.Main) {
                        val poolPhoto = PoolPhoto(UUID.randomUUID().toString(), PhotoState(bitmap))
                        val placed = if (assignBoxId != null) uiState.photosByBoxId + (assignBoxId to poolPhoto.state) else uiState.photosByBoxId
                        uiState = uiState.copy(photoPool = uiState.photoPool + poolPhoto, photosByBoxId = placed)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Could not load the photo $uri", e)
            }
        }
    }

    fun onSave() {
        val state = uiState
        if (!state.canSave) return
        uiState = state.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val uri = renderAndSaveCollage(
                    context = getApplication<Application>(),
                    layout = state.root,
                    photosByBoxId = state.photosByBoxId.mapValues { it.value.toPhotoData() },
                    borderPx = state.borderPx.toInt(),
                    borderStyle = state.borderStyle,
                )
                if (uri != null) {
                    Logger.i("Saved the collage to $uri")
                    eventChannel.send(CollageEditorEvent.Saved(uri))
                } else {
                    Logger.e("Saving the collage failed: no file could be created")
                    eventChannel.send(CollageEditorEvent.SaveFailed)
                }
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}
