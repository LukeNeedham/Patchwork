package com.lukeneedham.patchwork

import androidx.compose.runtime.Composable

/** The settings page: connects the page to the navigation, and delegates all UI. No preview. */
@Composable
fun SettingsPage(onBack: () -> Unit, onOpenDebug: () -> Unit) {
    SettingsPageContent(onBack = onBack, onOpenDebug = onOpenDebug)
}
