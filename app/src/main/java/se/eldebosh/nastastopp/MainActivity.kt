package se.eldebosh.nastastopp

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.ui.AppRoot
import se.eldebosh.nastastopp.ui.MainViewModel
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.NastaTheme
import se.eldebosh.nastastopp.ui.theme.SystemBarsFollowTheme
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.youdrive.YouDriveService

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, SettingsStore.readLanguage(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars; SystemBarsFollowTheme picks their icon colour for day or night.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        // The window starts in the chosen look's background (no flash of the other colour).
        val look = AppTheme.colorsFor(App.from(this).graph.settings.current.appearance, isNightMode())
        window.setBackgroundDrawable(look.background.toArgb().toDrawable())
        if (savedInstanceState == null) {
            handleShareIntent(intent)
            handleOpenIntent(intent)
        }
        setContent {
            val settings by App.from(this).graph.settings.state.collectAsStateWithLifecycle()
            NastaTheme(settings.appearance) {
                SystemBarsFollowTheme()
                AppRoot(vm, onRecreate = { recreate() })
            }
        }
    }

    private fun isNightMode() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleOpenIntent(intent)
    }

    /** Opens YouDrive's window, or the list review after trips were added from YouDrive. */
    private fun handleOpenIntent(intent: Intent?) {
        when (intent?.getStringExtra(Notifications.EXTRA_OPEN)) {
            Notifications.OPEN_YOUDRIVE -> vm.openYouDrive()
            Notifications.OPEN_REVIEW -> vm.openReview()
            else -> return
        }
        intent.removeExtra(Notifications.EXTRA_OPEN)
    }

    override fun onResume() {
        super.onResume()
        val graph = App.from(this).graph
        graph.controller.clearIfExpired()
        graph.history.prune()
        graph.overlay.refresh()
        // Watching YouDrive: (re)start its service while we are in the foreground.
        if (graph.settings.current.youDriveWatch) YouDriveService.start(this)
    }

    /** ACTION_SEND / ACTION_SEND_MULTIPLE with an image MIME type: images are processed in the order received. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            }
            else -> return
        }
        val clip = intent.clipData
        if (uris.isEmpty() && clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it }
        }
        if (uris.isNotEmpty()) vm.importImages(uris.toList())
        // Consume the share so a configuration change does not import it again.
        intent.action = Intent.ACTION_MAIN
    }
}
