package com.lukeneedham.patchwork

import androidx.compose.runtime.Composable
import com.lukeneedham.common.ui.ProvideDebugSettings

/** The one composable [MainActivity] calls: everything the UI sits in, then the navigation. */
@Composable
fun Root() {
    AppTheme {
        ProvideDebugSettings(theme = AppTheme.current.toDebugTheme()) {
            AppNavigation()
        }
    }
}
