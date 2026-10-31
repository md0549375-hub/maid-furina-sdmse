package eu.darken.sdmse.furina.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.furina.feature.chat.ChatScreen
import eu.darken.sdmse.furina.feature.chat.ChatViewModel
import eu.darken.sdmse.furina.feature.settings.GeminiSettingsScreen
import eu.darken.sdmse.furina.feature.settings.GeminiSettingsViewModel

/** Furina's own two-tab shell: chat and "Gemini AI Core Settings". */
@Composable
fun FurinaApp(chatViewModel: ChatViewModel, settingsViewModel: GeminiSettingsViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_CHAT) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // The bar would float above the keyboard, so hide it while typing.
            if (!imeOpen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == TAB_CHAT,
                        onClick = { tab = TAB_CHAT },
                        icon = { Text("💬") },
                        label = { Text(stringResource(R.string.furina_tab_chat)) },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_SETTINGS,
                        onClick = { tab = TAB_SETTINGS },
                        icon = { Text("⚙️") },
                        label = { Text(stringResource(R.string.furina_tab_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == TAB_CHAT) {
                ChatScreen(chatViewModel)
            } else {
                GeminiSettingsScreen(settingsViewModel)
            }
        }
    }
}

private const val TAB_CHAT = 0
private const val TAB_SETTINGS = 1
