package se.eldebosh.nastastopp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import java.util.Locale
import se.eldebosh.nastastopp.ui.theme.displayColors
import se.eldebosh.nastastopp.ui.screens.HostedWidget
import se.eldebosh.nastastopp.ui.screens.RouteMap
import se.eldebosh.nastastopp.core.nav.RoutesApi
import se.eldebosh.nastastopp.core.route.MapsUrlBuilder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import android.appwidget.AppWidgetProviderInfo
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.nav.MapWay
import se.eldebosh.nastastopp.geo.CarMotion
import se.eldebosh.nastastopp.geo.TabletPosition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.route.Announcements
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.ui.screens.ActiveRouteScreen
import se.eldebosh.nastastopp.ui.screens.DisplayRoleScreen
import se.eldebosh.nastastopp.ui.screens.PassengerDisplayScreen
import se.eldebosh.nastastopp.link.Bluetooth
import se.eldebosh.nastastopp.link.LinkAvailability
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.settings.DeviceRole
import androidx.compose.runtime.DisposableEffect
import se.eldebosh.nastastopp.ui.screens.HelpScreen
import se.eldebosh.nastastopp.ui.screens.HomeScreen
import se.eldebosh.nastastopp.ui.screens.OnboardingScreen
import se.eldebosh.nastastopp.ui.screens.PermissionStatus
import se.eldebosh.nastastopp.ui.screens.ReviewScreen
import se.eldebosh.nastastopp.ui.screens.SettingsScreen
import se.eldebosh.nastastopp.ui.screens.TtsMissingScreen
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.util.SystemIntents
import se.eldebosh.nastastopp.overlay.OverlayTileService
import se.eldebosh.nastastopp.ui.screens.YouDriveCard
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import android.widget.Toast

private const val MAX_PICK = 30

/** Key under which the route screen asks for current-street lookups. */
private const val STREET_KEY = "active_screen"

/** Key under which the passenger display keeps the floating panel away. */
private const val DISPLAY_KEY = "passenger_display"

@Composable
fun AppRoot(vm: MainViewModel, onRecreate: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val graph = vm.graph
    val controller = graph.controller
    val stack by vm.stack.collectAsStateWithLifecycle()
    val route by controller.route.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val ttsStatus by graph.announcer.status.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val importError by vm.importError.collectAsStateWithLifecycle()
    val display by controller.display.collectAsStateWithLifecycle()
    val entrances by controller.savedEntrances.collectAsStateWithLifecycle()
    val linkServer by graph.displayServer.state.collectAsStateWithLifecycle()
    val history by graph.history.entries.collectAsStateWithLifecycle()
    val street by graph.street.state.collectAsStateWithLifecycle()
    val youDriveLoginSaved by graph.youDriveLogin.saved.collectAsStateWithLifecycle()
    val streetMap by graph.streetMap.state.collectAsStateWithLifecycle()
    val youDrive by graph.youDrive.state.collectAsStateWithLifecycle()
    val importing = importState is ImportUi.Running
    val snackbar = remember { SnackbarHostState() }
    var resumeTick by remember { mutableIntStateOf(0) }
    val screen = stack.last()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    // Snackbar messages (with optional undo).
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val text = if (msg.arg != null) resources.getString(msg.text, msg.arg) else resources.getString(msg.text)
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = if (msg.undo != null) resources.getString(R.string.undo) else null,
                duration = if (msg.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.undo?.invoke()
        }
    }

    // The route may end from the notification / overlay while the Active screen is shown.
    LaunchedEffect(route?.active, screen) {
        if (screen == Screen.ACTIVE && route?.active != true) vm.resetTo(Screen.HOME)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        vm.importImages(uris)
    }
    fun pickImages() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun startRoute() {
        if (controller.start()) vm.resetTo(Screen.HOME, Screen.ACTIVE)
    }
    val startPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startRoute() }
    fun requestStart() {
        val needed = buildList {
            // Location only names the street the vehicle is on (never for the YouDrive page). Precise
            // location is needed for that: with approximate only, Android offers to upgrade it.
            if (!SystemIntents.hasPreciseLocation(context)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SystemIntents.hasNotifications(context)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) startRoute() else startPermissions.launch(needed.toTypedArray())
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        controller.ensureStreetService()
        resumeTick++
    }

    // Bluetooth (passenger display link): permission → then (re)start the link.
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        graph.displayServer.refresh()
        resumeTick++
    }
    fun fixLink() {
        val perm = Bluetooth.permission
        when {
            perm != null && !Bluetooth.hasPermission(context) -> bluetoothPermission.launch(perm)
            Bluetooth.availability(context) == LinkAvailability.BLUETOOTH_OFF -> SystemIntents.requestEnableBluetooth(context)
            else -> graph.displayServer.refresh()
        }
    }
    fun toggleLink(on: Boolean) {
        graph.settings.update { it.copy(displayLinkEnabled = on) }
        if (on && !Bluetooth.hasPermission(context)) Bluetooth.permission?.let { bluetoothPermission.launch(it) }
    }
    LaunchedEffect(resumeTick) { graph.displayServer.refreshIfIdle() }

    val spokenName: (Stop) -> String = { controller.spokenName(it) }
    fun testVoice() = graph.announcer.speak(Announcements.nextStops("Karlstad", "Hammarö", settings.englishRepeat))

    BackHandler(enabled = stack.size > 1) { vm.back() }

    // Reference numbers on/off; explanation texts in Arabic while the app is being set up.
    SideEffect { RefNumbers.enabled = settings.showRefNumbers }
    val explainResources = remember(settings.explanationsArabic, settings.uiLanguage, resources) {
        LocaleHelper.explanationContext(context, settings.uiLanguage, settings.explanationsArabic)
            .takeIf { it !== context }?.resources
    }

    CompositionLocalProvider(LocalExplainResources provides explainResources) {
    // testTagsAsResourceId: numbered controls appear to UI Automator as resource-id "ref_<n>".
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        // The passenger display is black to the screen's very edge (under the camera's cutout too)
        // and keeps its own content clear of it; every other screen stays inside the safe area.
        val edgeToEdge = screen == Screen.DISPLAY_LOCAL || screen == Screen.DISPLAY_ROLE
        Box(Modifier.fillMaxSize().then(if (edgeToEdge) Modifier else Modifier.safeDrawingPadding())) {
            Column(Modifier.fillMaxSize()) {
                if (importState is ImportUi.Running) {
                    val s = importState as ImportUi.Running
                    Text(
                        stringResource(R.string.importing, (s.done + 1).coerceAtMost(s.total), s.total),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        Screen.ONBOARDING -> OnboardingScreen(
                            resumeTick = resumeTick,
                            ttsStatus = ttsStatus,
                            onTestVoice = ::testVoice,
                            onVoiceMissing = { vm.navigate(Screen.TTS_MISSING) },
                            onFinish = { vm.finishOnboarding() },
                            onChooseDisplay = { vm.setRole(DeviceRole.DISPLAY) },
                        )
                        Screen.HOME -> HomeScreen(
                            route = route,
                            ttsStatus = ttsStatus,
                            importing = importing,
                            onImport = ::pickImages,
                            onResume = { controller.ensureStreetService(); vm.navigate(Screen.ACTIVE) },
                            onReview = { vm.navigate(Screen.REVIEW) },
                            onClear = { controller.clear() },
                            onSettings = { vm.navigate(Screen.SETTINGS) },
                            onHelp = { vm.navigate(Screen.HELP) },
                            onTtsMissing = { vm.navigate(Screen.TTS_MISSING) },
                            link = linkServer,
                            onToggleLink = ::toggleLink,
                            onFixLink = ::fixLink,
                            onUseAsDisplay = { vm.setRole(DeviceRole.DISPLAY) },
                            overlayPermission = remember(resumeTick) { SystemIntents.canDrawOverlays(context) },
                            overlayHidden = settings.overlayHidden,
                            onOverlayVisible = { visible ->
                                graph.settings.update { it.copy(overlayHidden = !visible, overlayMinimized = false) }
                                graph.overlay.refresh()
                            },
                            onAddTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                {
                                    OverlayTileService.requestAdd(context) { added ->
                                        if (added) Toast.makeText(context, R.string.home_overlay_tile_added, Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                null
                            },
                            onOverlayPermission = { SystemIntents.openOverlaySettings(context) },
                            history = history,
                            historyRetentionHours = settings.historyRetentionHours,
                            onClearHistory = { graph.history.clear() },
                            youDrive = { YouDriveCard(youDrive, settings.youDriveWatch) { vm.openYouDrive() } },
                        )
                        Screen.REVIEW -> ReviewScreen(
                            route = route,
                            spokenName = spokenName,
                            importing = importing,
                            onBack = { vm.back() },
                            onMove = { from, to -> controller.move(from, to) },
                            onDelete = { stop ->
                                val before = route?.stops.orEmpty()
                                controller.delete(stop.id)
                                vm.message(UiMessage(R.string.deleted_stop, undo = { controller.restoreStops(before) }))
                            },
                            onDeleteAbove = { stop ->
                                val before = route?.stops.orEmpty()
                                val count = before.indexOfFirst { it.id == stop.id }
                                controller.deleteAllAbove(stop.id)
                                vm.message(UiMessage(R.string.deleted_many, count, undo = { controller.restoreStops(before) }))
                            },
                            onEdit = { stop, text, time -> controller.editText(stop.id, text, time) },
                            onRetry = { stop -> controller.retryLocate(stop.id) },
                            onAddManual = { text, time -> controller.addManual(text, time) },
                            onSortByTime = { controller.sortByTime() },
                            onAddScreenshots = ::pickImages,
                            onStart = ::requestStart,
                            onBackToRoute = { vm.leaveReviewToActive() },
                            entrances = entrances,
                            onEntrance = { stop, point, note ->
                                // The stop as it now reads (its address may just have been edited).
                                val fresh = controller.route.value?.stops?.firstOrNull { it.id == stop.id } ?: stop
                                controller.setEntrance(fresh, point, note)
                            },
                            onNavigate = { controller.navigateTo(it) },
                            onStreetView = { controller.streetViewAt(it) },
                        )
                        Screen.ACTIVE -> route?.takeIf { it.active }?.let { r ->
                            ActiveRouteScreen(
                                route = r,
                                hasLocationPermission = remember(resumeTick) { SystemIntents.hasLocation(context) },
                                spokenName = spokenName,
                                onBack = { if (!vm.back()) vm.resetTo(Screen.HOME) },
                                onNext = { controller.next() },
                                onRepeat = { controller.repeat() },
                                onOpenMaps = { controller.openMaps() },
                                onEdit = { vm.navigate(Screen.REVIEW) },
                                onEnd = { controller.end() },
                                overlayAvailable = remember(resumeTick) { SystemIntents.canDrawOverlays(context) },
                                overlayHidden = settings.overlayHidden,
                                street = street,
                                onSpeakStreet = { controller.speakStreet(street) },
                                onPreviousTrip = { controller.back() },
                                onOpenDisplay = { vm.navigate(Screen.DISPLAY_LOCAL) },
                                onToggleOverlay = { graph.settings.update { it.copy(overlayHidden = !it.overlayHidden, overlayMinimized = false) } },
                                entranceOf = { entrances[it.entranceKey] },
                                onNavigateStop = { controller.navigateTo(it) },
                                onStreetViewStop = { controller.streetViewAt(it) },
                            )
                            // Look up the current street while this screen is shown.
                            DisposableEffect(Unit) {
                                graph.street.want(STREET_KEY, true)
                                onDispose { graph.street.want(STREET_KEY, false) }
                            }
                        }
                        Screen.DISPLAY_LOCAL -> {
                            // Each spoken announcement lights up what it says on the screen.
                            var spoken by remember { mutableIntStateOf(0) }
                            LaunchedEffect(Unit) { controller.announcements.collect { spoken++ } }
                            PassengerDisplayScreen(
                                snapshot = display,
                                status = null,
                                connected = true,
                                spoken = spoken,
                                onSpeak = { controller.repeat() },
                                onSay = { graph.announcer.speak(it) },
                                onExit = { vm.back() },
                                dark = settings.displayDark,
                                onToggleLook = { graph.settings.update { it.copy(displayDark = !it.displayDark) } },
                                places = graph.settings,
                            )
                            // The passengers look at this screen: the driver's floating panel stays away.
                            DisposableEffect(Unit) {
                                graph.overlay.suppress(DISPLAY_KEY, true)
                                graph.localDisplays.value++
                                onDispose {
                                    graph.overlay.suppress(DISPLAY_KEY, false)
                                    graph.localDisplays.value--
                                }
                            }
                        }
                        Screen.DISPLAY_ROLE -> {
                            val client = graph.displayClient
                            val link by client.state.collectAsStateWithLifecycle()
                            val remote by client.snapshot.collectAsStateWithLifecycle()
                            val address = settings.displayControllerAddress
                            val availability = remember(resumeTick, link.status) { Bluetooth.availability(context) }
                            // Connect while this screen is shown; retry after returning from settings.
                            LaunchedEffect(address, resumeTick) { client.start(address) }
                            DisposableEffect(Unit) { onDispose { client.stop() } }
                            // Speak the controller's announcements here too (unless switched off), and
                            // light up on the screen what they say.
                            LaunchedEffect(settings.displaySpeaks) {
                                if (settings.displaySpeaks) client.announcements.collect { graph.announcer.speak(it) }
                            }
                            var spoken by remember { mutableIntStateOf(0) }
                            LaunchedEffect(client) { client.announcements.collect { spoken++ } }
                            // A weather app's widget (207): its slot is bound after the system asks the driver.
                            val widgets = graph.weatherWidgets
                            DisposableEffect(Unit) {
                                widgets.listen(true)
                                onDispose { widgets.listen(false) }
                            }
                            var binding by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }
                            val keepWidget = { id: Int, info: AppWidgetProviderInfo ->
                                val old = settings.weatherWidgetId
                                graph.settings.update { it.copy(weatherWidgetId = id) }
                                if (old >= 0 && old != id) widgets.remove(old)
                                if (widgets.needsSetup(info)) (context as? Activity)?.let { widgets.setUp(it, id) }
                            }
                            val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                                binding?.let { (id, info) -> if (result.resultCode == Activity.RESULT_OK) keepWidget(id, info) else widgets.remove(id) }
                                binding = null
                            }
                            val widgetId = settings.weatherWidgetId
                            val widgetLabel = remember(widgetId, resumeTick) { widgetId.takeIf { it >= 0 }?.let { widgets.label(it) } }
                            // In the passenger display's own look (black or light, 223), whatever the app's.
                            val night = settings.displayDark
                            val ground = displayColors(night).background.toArgb()
                            val mapScope = rememberCoroutineScope()
                            var mapRestarts by remember { mutableIntStateOf(0) }
                            // One map for as long as the display is open (Google counts each map made):
                            // a new look recolours it.
                            val groundHex = String.format(Locale.ROOT, "#%06X", ground and 0xFFFFFF)
                            val routeMap = remember(settings.mapsKey, mapRestarts) {
                                settings.mapsKey?.takeIf { RoutesApi.isKey(it) }?.let {
                                    RouteMap(context, it, night, groundHex, mapScope)
                                }
                            }
                            LaunchedEffect(routeMap, night, groundHex) { routeMap?.setLook(night, groundHex) }
                            DisposableEffect(routeMap) { onDispose { routeMap?.destroy() } }
                            // A map whose renderer stopped is replaced by a new one.
                            LaunchedEffect(routeMap?.gone) { if (routeMap?.gone == true) mapRestarts++ }
                            // Where the car is, for the map: this tablet's own GPS, while the display
                            // is in sight and the location is allowed (asked when the map is wanted).
                            val position = remember { TabletPosition(context.applicationContext) }
                            var located by remember(resumeTick) { mutableStateOf(position.allowed) }
                            val askPosition = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { located = position.allowed }
                            LifecycleStartEffect(routeMap, located) {
                                if (routeMap != null && located) position.start()
                                onStopOrDispose { position.stop() }
                            }
                            val fix by position.fix.collectAsStateWithLifecycle()
                            // Whether the car moves (the tablet's accelerometer and GPS speed), while the
                            // display is in sight: its moments and its map's flights rest when it stands still.
                            val carMotion = remember { CarMotion(context.applicationContext) }
                            LifecycleStartEffect(carMotion) {
                                carMotion.start()
                                onStopOrDispose { carMotion.stop() }
                            }
                            LaunchedEffect(fix) { carMotion.onSpeed(fix?.speedMps) }
                            val carAwake by carMotion.awake.collectAsStateWithLifecycle()
                            // The minute's map: from the car to the next stop only.
                            // Only while the phone is connected: the way to trips that may be old is never asked for.
                            val nextStops = remote?.takeIf { it.active && link.status == DisplayLinkClient.Status.CONNECTED }
                                ?.ahead?.take(1)?.map { it.mapStop }.orEmpty()
                            LaunchedEffect(routeMap, fix, nextStops) {
                                val at = fix ?: return@LaunchedEffect
                                if (nextStops.isEmpty()) return@LaunchedEffect
                                routeMap?.show(MapWay(at.lat, at.lng, at.bearingDeg, nextStops))
                            }
                            DisplayRoleScreen(
                                settings = settings,
                                link = link,
                                snapshot = remote,
                                paired = remember(resumeTick, link.status) { Bluetooth.pairedDevices(context) },
                                bluetoothReady = availability == LinkAvailability.OK,
                                availabilityStatus = when (availability) {
                                    LinkAvailability.NO_PERMISSION -> DisplayLinkClient.Status.NO_PERMISSION
                                    LinkAvailability.BLUETOOTH_OFF -> DisplayLinkClient.Status.BLUETOOTH_OFF
                                    LinkAvailability.NO_BLUETOOTH -> DisplayLinkClient.Status.NO_BLUETOOTH
                                    LinkAvailability.OK -> DisplayLinkClient.Status.IDLE
                                },
                                onRequestPermission = { Bluetooth.permission?.let { bluetoothPermission.launch(it) } },
                                onEnableBluetooth = { SystemIntents.requestEnableBluetooth(context) },
                                onOpenBluetoothSettings = { SystemIntents.openBluetoothSettings(context) },
                                onChoose = { address ->
                                    graph.settings.update { it.copy(displayControllerAddress = address) }
                                    client.choose(address)
                                },
                                onSpeak = { remote?.announcement?.let { graph.announcer.speak(it) } },
                                onSay = { graph.announcer.speak(it) },
                                spoken = spoken,
                                onToggleSpeaks = { v -> graph.settings.update { it.copy(displaySpeaks = v) } },
                                onToggleLook = { graph.settings.update { it.copy(displayDark = !it.displayDark) } },
                                onOrder = { ids -> client.order(ids) },
                                remote = client.remotes,
                                onMapView = { client.mapView(it) },
                                awake = carAwake,
                                motion = if (carMotion.available) ({ carMotion.level.value }) else null,
                                places = graph.settings,
                                panelAllowed = remember(resumeTick) { graph.tabletPanel.canShow },
                                onAllowPanel = { SystemIntents.openOverlaySettings(context) },
                                onTogglePanel = { v ->
                                    // Turned on, it shows even if it was closed with its × before.
                                    graph.settings.update { it.copy(tabletPanel = v, overlayHidden = if (v) false else it.overlayHidden) }
                                    if (v && !graph.tabletPanel.canShow) SystemIntents.openOverlaySettings(context)
                                },
                                onSwitchToController = { vm.setRole(DeviceRole.CONTROLLER) },
                                widgetLabel = widgetLabel,
                                widgetChoices = { widgets.choices() },
                                onChooseWidget = { choice ->
                                    if (choice == null) {
                                        widgets.remove(settings.weatherWidgetId)
                                        graph.settings.update { it.copy(weatherWidgetId = -1) }
                                    } else {
                                        val (id, bound) = widgets.add(choice.info)
                                        if (bound) {
                                            keepWidget(id, choice.info)
                                        } else {
                                            binding = id to choice.info
                                            bindWidget.launch(widgets.bindIntent(id, choice.info))
                                        }
                                    }
                                },
                                weatherWidget = widgetLabel?.let { { m: Modifier -> HostedWidget(widgets, widgetId, m) } },
                                onSaveMapsKey = { key -> graph.settings.update { it.copy(mapsKey = key) } },
                                mapRefused = routeMap?.refused == true,
                                routeMap = routeMap,
                                mapLive = fix != null,
                                onWantPosition = {
                                    if (!position.allowed) askPosition.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                },
                                // Google's own apps on the driver's tap: nothing billed on his key.
                                // From this screen: Back in Google's app comes back to the display.
                                onEarth = { lat, lng -> graph.maps.openEarth(lat, lng, context) },
                                onStreetPhotos = { lat, lng -> graph.maps.open(MapsUrlBuilder.streetViewUrl(lat, lng), context) },
                            )
                        }
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            permissions = remember(resumeTick) {
                                PermissionStatus(
                                    location = SystemIntents.hasLocation(context),
                                    locationApproximate = SystemIntents.hasLocation(context) && !SystemIntents.hasPreciseLocation(context),
                                    notifications = SystemIntents.hasNotifications(context),
                                    overlay = SystemIntents.canDrawOverlays(context),
                                    battery = SystemIntents.isIgnoringBatteryOptimizations(context),
                                    mapsTime = SystemIntents.hasMapsTimeAccess(context),
                                )
                            },
                            ttsStatus = ttsStatus,
                            onBack = { vm.back() },
                            onUpdate = { graph.settings.update(it) },
                            onLanguage = { code ->
                                if (code != settings.uiLanguage) {
                                    graph.settings.update { it.copy(uiLanguage = code) }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        LocaleHelper.applyAppLocale(context, code) // system recreates the activity
                                    } else {
                                        onRecreate()
                                    }
                                }
                            },
                            onTestVoice = ::testVoice,
                            onLocation = {
                                // Approximate only: asking again lets Android offer "precise" (street names need it).
                                if (SystemIntents.hasPreciseLocation(context)) SystemIntents.openAppDetails(context)
                                else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            onNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SystemIntents.hasNotifications(context)) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    SystemIntents.openAppDetails(context)
                                }
                            },
                            onOverlay = { SystemIntents.openOverlaySettings(context) },
                            onMapsTime = { SystemIntents.openMapsTimeSettings(context) },
                            onBattery = { SystemIntents.requestIgnoreBatteryOptimizations(context) },
                            link = linkServer,
                            onToggleLink = ::toggleLink,
                            onFixLink = ::fixLink,
                            youDriveLoginSaved = youDriveLoginSaved,
                            onSaveYouDriveLogin = { u, p -> graph.youDriveLogin.save(u, p) },
                            onDeleteYouDriveLogin = { graph.youDriveLogin.delete() },
                            streetMap = streetMap,
                            onDownloadStreetMap = { graph.streetMap.download() },
                            onDeleteStreetMap = { graph.streetMap.delete() },
                            entrancesSaved = entrances.size,
                            onClearEntrances = { controller.clearEntrances() },
                            onVoice = {
                                if (ttsStatus == se.eldebosh.nastastopp.tts.TtsStatus.READY) testVoice() else vm.navigate(Screen.TTS_MISSING)
                            },
                        )
                        Screen.HELP -> HelpScreen(
                            onBack = { vm.back() },
                            onAppSettings = { SystemIntents.openAppDetails(context) },
                            onBatteryRequest = { SystemIntents.requestIgnoreBatteryOptimizations(context) },
                            onInstallVoice = { SystemIntents.installTtsData(context) },
                            onTtsSettings = { SystemIntents.openTtsSettings(context) },
                        )
                        Screen.TTS_MISSING -> TtsMissingScreen(
                            onBack = { vm.back() },
                            onInstall = { SystemIntents.installTtsData(context) },
                            onRecheck = { graph.announcer.recheck() },
                            onTtsSettings = { SystemIntents.openTtsSettings(context) },
                        )
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp))
        }
    }

    importError?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.dismissImportError() },
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_failed_count, err.failedImages, err.totalImages), style = MaterialTheme.typography.bodyLarge)
                    if (!err.detail.isNullOrBlank()) {
                        Text(
                            err.detail,
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Hint(R.string.import_failed_hint, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissImportError() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
    }
}
