package com.lukeneedham.patchwork

import android.content.Context
import android.net.Uri

/** Renders a collage and saves it to the user's pictures. */
class CollageSaver(private val context: Context) {
    /** The [Uri] of the saved file, or null when no file could be created. */
    suspend fun save(
        layout: LayoutNode,
        photosByBoxId: Map<String, PhotoData>,
        borderPx: Int,
        borderStyle: BorderStyle,
    ): Uri? = renderAndSaveCollage(
        context = context,
        layout = layout,
        photosByBoxId = photosByBoxId,
        borderPx = borderPx,
        borderStyle = borderStyle,
    )
}
