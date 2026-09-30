package se.eldebosh.nastastopp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
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
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
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
                            )
                            // The passengers look at this screen: the driver's floating panel stays away.
                            DisposableEffect(Unit) {
                                graph.overlay.suppress(DISPLAY_KEY, true)
                                onDispose { graph.overlay.suppress(DISPLAY_KEY, false) }
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
                                onSwitchToController = { vm.setRole(DeviceRole.CONTROLLER) },
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
