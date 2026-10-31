package eu.darken.sdmse.furina.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.furina.feature.chat.ChatViewModel
import eu.darken.sdmse.furina.feature.settings.GeminiSettingsViewModel

/**
 * Self-contained entry point for the Furina layer (chat + settings); independent of SD Maid's navigation.
 * Open with startActivity(Intent(context, FurinaHostActivity::class.java)).
 */
@AndroidEntryPoint
class FurinaHostActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val settingsViewModel: GeminiSettingsViewModel by viewModels()

    // A denied permission only means scheduled-clean notifications stay off.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FurinaTheme {
                FurinaApp(chatViewModel, settingsViewModel)
            }
        }
    }
}
