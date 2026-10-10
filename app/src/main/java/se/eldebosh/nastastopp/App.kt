package se.eldebosh.nastastopp

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.weather.WeatherSource
import se.eldebosh.nastastopp.weather.WeatherWidgets
import se.eldebosh.nastastopp.nav.MapsNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import se.eldebosh.nastastopp.core.geo.Districts
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.Localities
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.geo.Geocoding
import se.eldebosh.nastastopp.geo.StreetCaller
import se.eldebosh.nastastopp.geo.StreetMapStore
import se.eldebosh.nastastopp.importer.ScreenshotImporter
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.maps.MapsLauncher
import se.eldebosh.nastastopp.ocr.OcrEngine
import se.eldebosh.nastastopp.overlay.LinkPanelSource
import se.eldebosh.nastastopp.overlay.OverlayManager
import se.eldebosh.nastastopp.overlay.RoutePanelSource
import se.eldebosh.nastastopp.settings.CompanyDevice
import se.eldebosh.nastastopp.settings.DeviceRole
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.route.RouteRepository
import se.eldebosh.nastastopp.route.AddressLog
import se.eldebosh.nastastopp.route.StoredEntrances
import se.eldebosh.nastastopp.route.StoredPlaceMemory
import se.eldebosh.nastastopp.route.TripHistory
import se.eldebosh.nastastopp.service.Notifications
import se.eldebosh.nastastopp.service.RouteNotifier
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.tts.Announcer
import se.eldebosh.nastastopp.util.DebugLog
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.youdrive.YouDriveLogin
import se.eldebosh.nastastopp.youdrive.YouDriveWatcher

/** Manual dependency graph (no DI framework). Created once per process. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val localities: Localities = app.assets.open("localities_se.txt").bufferedReader().use { Localities.parse(it.readText()) }
    /** Karlstad's own districts and their streets, so a district is never the geocoder's guess. */
    val districts: Districts = app.assets.open(Districts.ASSET).bufferedReader().use { Districts.parse(it.readText()) }
    val extractor = AddressExtractor(localities)
    val announcer = Announcer(app, settings)
    val maps = MapsLauncher(app)
    val repository = RouteRepository(app)
    /** Finds the stops' points; Lantmäteriet's addresses are read in the background at the start, ready for the first route. */
    val geocoding = Geocoding(app).also { scope.launch(Dispatchers.IO) { Geocoding.register(app) } }
    val history = TripHistory(app, settings, scope)
    val controller = RouteController(app, scope, repository, settings, geocoding, announcer, maps, localities, extractor, history, StoredPlaceMemory(app), StoredEntrances(app), districts)
    val notifier = RouteNotifier(app, controller, settings, scope)

    /** The addresses the routes went to (never a name), for the driver to share (Settings 302). */
    val addressLog = AddressLog(app).also { log ->
        // A fault in the log never stops the route: the addresses are then simply not kept.
        scope.launch {
            controller.route.collect { r ->
                runCatching { log.record(r?.stops.orEmpty().mapNotNull(controller::logLine)) }
                    .onFailure { DebugLog.w(it) { "address log skipped" } }
            }
        }
    }

    /** The street the vehicle is on now (floating button and route screen). */
    val street = CurrentStreet(scope, lookup = geocoding::reverse, districts = districts)

    /** The offline street map (downloaded in Settings), for exact street names. */
    val streetMap = StreetMapStore(app, street, scope)

    /** Says the street's name when it changes (the driver can switch it off). */
    val streetCaller = StreetCaller(street, controller, scope)
    val importer = ScreenshotImporter(OcrEngine(app), extractor)

    /** Controller: Bluetooth server for passenger displays (runs only when enabled). */
    val displayServer = DisplayLinkServer(app, controller, settings, scope)

    /** The phone's floating panel over Maps (its own route, street and speed; drives the linked display). */
    val overlay = OverlayManager(app, RoutePanelSource(controller, street, announcer, displayServer, scope), settings, scope) { it.role != DeviceRole.DISPLAY }

    /** Passenger displays shown on this phone itself (the screen counts itself while open). */
    val localDisplays = MutableStateFlow(0)

    /** The area's weather, fetched only while a passenger display shows a route. */
    val weather = WeatherSource(scope)

    /** Display role: a weather app's widget on this tablet's display (207). */
    val weatherWidgets by lazy { WeatherWidgets(app) }

    /** Display role: Bluetooth client towards the driver's device. */
    val displayClient by lazy {
        DisplayLinkClient(app, scope) { address ->
            // Remember the device that answered, so it is tried first next time.
            scope.launch {
                if (settings.current.displayControllerAddress != address) {
                    settings.update { it.copy(displayControllerAddress = address) }
                }
            }
        }
    }

    /**
     * Display role: the phone's floating panel on this tablet, over the passenger display (Settings
     * on the tablet: 206). Its Next, Back and Repeat go to the phone.
     */
    val tabletPanel = OverlayManager(app, LinkPanelSource(displayClient, announcer), settings, scope, bubbleScale = 2f, swellLastMinute = true) {
        it.role == DeviceRole.DISPLAY && it.tabletPanel
    }

    /** The driver's YouDrive page, watched for added / cancelled trips (alerts as notifications). */
    /** The YouDrive login, only if the driver saved it on this phone (encrypted). */
    val youDriveLogin = YouDriveLogin(app)

    /**
     * Display role: this tablet's connection to the company's server (311), for its map key, ways
     * and travel times. Made only by the tablet's screens: no phone path builds it, so the phone's
     * position never leaves the phone.
     */
    val companyDevice by lazy { CompanyDevice(app) }

    val youDrive = YouDriveWatcher(app, settings, extractor, youDriveLogin) { changes ->
        Notifications.postTripChanges(app, changes.map { it.id to it.change })
    }

    init {
        // The current street is only kept while a route is active.
        scope.launch { controller.route.collect { if (it?.active != true) street.reset() } }
        // The weather is asked for only while a passenger display (here or on a tablet) shows a route.
        scope.launch {
            combine(controller.route, displayServer.state, localDisplays) { r, link, local ->
                r?.active == true && (link.status == DisplayLinkServer.Status.CONNECTED || local > 0)
            }.distinctUntilChanged().collect { weather.want(it) }
        }
        scope.launch {
            combine(weather.weather, MapsNavigation.eta) { w, eta -> w to eta }.collect { (w, eta) -> controller.setDisplayExtras(w, eta) }
        }
    }
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
