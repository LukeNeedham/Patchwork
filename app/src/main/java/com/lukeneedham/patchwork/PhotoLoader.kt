package com.lukeneedham.patchwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Decodes a photo the user picked into a [Bitmap]. */
class PhotoLoader(private val context: Context) {
    /** Blocking: call it off the main thread. Null when the photo cannot be opened. */
    fun load(uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
}
