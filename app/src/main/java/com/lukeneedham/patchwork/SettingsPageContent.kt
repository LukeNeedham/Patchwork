package com.lukeneedham.patchwork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.common.ui.DebugButton

/** All of the settings page's UI: state down, events up. It has no settings of its own yet, only the debug entry. */
@Composable
internal fun SettingsPageContent(onBack: () -> Unit, onOpenDebug: () -> Unit) {
    val theme = AppTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = theme.onBackground, fontSize = 22.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("Settings", color = theme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DebugButton(onClick = onOpenDebug)
        }
    }
}

@Preview
@Composable
private fun Preview() {
    AppTheme { SettingsPageContent(onBack = {}, onOpenDebug = {}) }
}
