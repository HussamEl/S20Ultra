package se.eldebosh.nastastopp.youdrive

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.MainActivity
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.youdrive.YouDriveCards
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.ui.LocalExplainResources
import se.eldebosh.nastastopp.ui.RefNumbers
import se.eldebosh.nastastopp.ui.screens.YouDriveBar
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.ui.theme.NastaTheme
import se.eldebosh.nastastopp.ui.theme.SystemBarsFollowTheme
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.util.SystemIntents

/**
 * YouDrive in its own full-screen window: a slim bar on top and the page at the phone's real size
 * below it, as a plain WebView in the window (like a browser), so the login form and the keyboard
 * behave as in Chrome. The same page keeps running in the background while watching.
 */
class YouDriveActivity : ComponentActivity() {

    private lateinit var holder: FrameLayout
    private lateinit var root: LinearLayout
    private var web: WebView? = null
    private val graph get() = App.from(this).graph

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, SettingsStore.readLanguage(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = ComposeView(this).apply { setContent { Bar() } }
        holder = FrameLayout(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Behind the status bar: the same colour as the bar (the look's card colour).
            setBackgroundColor(AppTheme.colorsFor(graph.settings.current.appearance, isNightMode()).card.toArgb())
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Keep clear of the status bar, the navigation bar and the keyboard (the page moves up).
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        showPage()
        graph.youDrive.onPageReplaced = {
            web?.let { holder.removeView(it) }
            if (!isFinishing && !isDestroyed) showPage()
        }
        // Back goes back inside the page first (e.g. from its Settings to the login).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val w = web
                    if (w != null && w.canGoBack()) graph.youDrive.goBack() else finish()
                }
            },
        )
    }

    private fun showPage() {
        val page = graph.youDrive.attach(this)
        holder.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        web = page
    }

    @androidx.compose.runtime.Composable
    private fun Bar() {
        val settings by graph.settings.state.collectAsStateWithLifecycle()
        val state by graph.youDrive.state.collectAsStateWithLifecycle()
        SideEffect { RefNumbers.enabled = settings.showRefNumbers }
        val explainResources = remember(settings.explanationsArabic, settings.uiLanguage) {
            LocaleHelper.explanationContext(this, settings.uiLanguage, settings.explanationsArabic)
                .takeIf { it !== this }?.resources
        }
        NastaTheme(settings.appearance) {
            SystemBarsFollowTheme()
            val barColor = AppTheme.colors.card
            SideEffect { root.setBackgroundColor(barColor.toArgb()) }
            // Surface: gives the bar's texts and icons the theme's content colour.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { testTagsAsResourceId = true }, // "ref_<n>" resource-ids
            ) {
                CompositionLocalProvider(LocalExplainResources provides explainResources) {
                    YouDriveBar(
                        state = state,
                        watching = settings.youDriveWatch,
                        onBack = { finish() },
                        onWatch = ::setWatch,
                        onStartPage = { graph.youDrive.openStart() },
                        onReload = { graph.youDrive.reload() },
                        onReadNow = { graph.youDrive.readNow() },
                        onImportAll = ::importAll,
                        onApply = ::apply,
                        onDismiss = { graph.youDrive.dismiss(it.id) },
                        onLogout = ::logout,
                    )
                }
            }
        }
    }

    private fun setWatch(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SystemIntents.hasNotifications(this)) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        graph.settings.update { it.copy(youDriveWatch = on) }
        if (on) YouDriveService.start(this) else YouDriveService.stop(this)
    }

    private fun importAll() {
        // Trips already done today ("Performed", "Departed") are not added.
        val added = graph.controller.importTrips(YouDriveCards.toAdd(graph.youDrive.state.value.trips))
        if (added == 0) {
            toast(getString(R.string.youdrive_nothing_new))
            return
        }
        toast(getString(R.string.import_result, added))
        // A new list is reviewed in the app; an active route was updated in place.
        if (!graph.controller.isActive) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(Notifications.EXTRA_OPEN, Notifications.OPEN_REVIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    private fun apply(change: YouDriveWatcher.PendingChange) {
        val stop = change.change.trip.stop ?: return
        val done = if (change.change.added) graph.controller.insertTrip(stop) else graph.controller.removeTrip(stop)
        toast(getString(if (done) R.string.youdrive_applied else R.string.youdrive_not_in_list))
        graph.youDrive.dismiss(change.id)
    }

    /** Logs out completely and shows a brand-new page (the login). */
    private fun logout() {
        web = null
        graph.youDrive.logout { if (!isFinishing && !isDestroyed) showPage() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun isNightMode() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onDestroy() {
        graph.youDrive.onPageReplaced = null
        web?.let { holder.removeView(it) }
        web = null
        graph.youDrive.detach()
        super.onDestroy()
    }
}
