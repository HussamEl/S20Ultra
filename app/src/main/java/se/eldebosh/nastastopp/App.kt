package se.eldebosh.nastastopp

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.Localities
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.importer.ScreenshotImporter
import se.eldebosh.nastastopp.maps.MapsLauncher
import se.eldebosh.nastastopp.ocr.OcrEngine
import se.eldebosh.nastastopp.overlay.OverlayManager
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.route.RouteRepository
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.service.RouteNotifier
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.tts.Announcer
import se.eldebosh.nastastopp.util.LocaleHelper

/** Manual dependency graph (no DI framework). Created once per process. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val localities: Localities = app.assets.open("localities_se.txt").bufferedReader().use { Localities.parse(it.readText()) }
    val extractor = AddressExtractor(localities)
    val announcer = Announcer(app, settings)
    val maps = MapsLauncher(app)
    val repository = RouteRepository(app)
    val geocoding = Geocoding(app)
    val controller = RouteController(app, scope, repository, settings, geocoding, announcer, maps, localities, extractor)
    val notifier = RouteNotifier(app, controller, settings, scope)
    val overlay = OverlayManager(app, controller, settings, scope)
    val importer = ScreenshotImporter(OcrEngine(app.contentResolver), extractor)
}

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base, SettingsStore.readLanguage(base)))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
        // Keep the stored UI language in sync with a per-app language chosen in system settings.
        LocaleHelper.systemPerAppLanguage(this)?.let { sys ->
            if (sys != graph.settings.current.uiLanguage) graph.settings.update { it.copy(uiLanguage = sys) }
        }
        LocaleHelper.applyAppLocale(this, graph.settings.current.uiLanguage)
        Notifications.createChannels(this)
    }

    companion object {
        @Volatile
        private var instance: App? = null

        /**
         * The running Application. Uses the instance set in onCreate rather than
         * context.applicationContext, which can be a different object for contexts made with
         * createConfigurationContext (used for the in-app language).
         */
        fun from(context: Context): App = instance ?: context.applicationContext as App
    }
}
