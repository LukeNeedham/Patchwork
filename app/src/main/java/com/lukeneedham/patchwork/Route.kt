package com.lukeneedham.patchwork

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 back stack keys: one member per page, so [CollageEditor] is `CollageEditorPage`. */
@Serializable
sealed interface Route : NavKey {
    /** The collage editor, the app's home. */
    @Serializable
    data object CollageEditor : Route

    @Serializable
    data object Settings : Route

    /** The shared library's debug page, opened from the settings, with the logs and crashes pages below it. */
    @Serializable
    data object Debug : Route

    @Serializable
    data object Logs : Route

    @Serializable
    data object Crashes : Route
}
