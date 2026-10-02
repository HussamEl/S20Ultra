package se.eldebosh.nastastopp.robo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.geo.GeoResult
import se.eldebosh.nastastopp.core.geo.Fix
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.geo.StreetMapStore
import se.eldebosh.nastastopp.overlay.OverlayManager
import se.eldebosh.nastastopp.overlay.PanelSource
import se.eldebosh.nastastopp.overlay.RoutePanelSource
import se.eldebosh.nastastopp.ui.screens.OnboardingScreen
import java.time.Duration
import androidx.compose.material3.MaterialTheme
import se.eldebosh.nastastopp.ui.theme.AppTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import se.eldebosh.nastastopp.core.nav.DisplayEta
import se.eldebosh.nastastopp.core.weather.DisplayWeather
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import java.time.LocalTime
import org.junit.Assert.assertEquals
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import se.eldebosh.nastastopp.ui.screens.RouteMap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.eldebosh.nastastopp.core.display.DisplayItem
import se.eldebosh.nastastopp.core.parse.DeviceFixtures
import se.eldebosh.nastastopp.core.display.DisplaySnapshot
import se.eldebosh.nastastopp.core.youdrive.TripChange
import se.eldebosh.nastastopp.core.youdrive.WatchedTrip
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.route.HistoryEntry
import se.eldebosh.nastastopp.route.model.GeoPoint
import se.eldebosh.nastastopp.route.model.GeoStatus
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.settings.Appearance
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.settings.AppSettings
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.ui.LocalExplainResources
import se.eldebosh.nastastopp.ui.screens.ActiveRouteScreen
import se.eldebosh.nastastopp.ui.screens.HelpScreen
import se.eldebosh.nastastopp.ui.screens.HomeScreen
import se.eldebosh.nastastopp.ui.screens.PassengerDisplayScreen
import se.eldebosh.nastastopp.ui.screens.PermissionStatus
import se.eldebosh.nastastopp.ui.screens.ReviewScreen
import se.eldebosh.nastastopp.ui.screens.SettingsScreen
import se.eldebosh.nastastopp.ui.screens.YouDriveBar
import se.eldebosh.nastastopp.ui.screens.YouDriveCard
import se.eldebosh.nastastopp.ui.theme.NastaTheme
import se.eldebosh.nastastopp.util.LocaleHelper
import se.eldebosh.nastastopp.youdrive.YouDriveWatcher
import java.io.File

/**
 * Renders each screen (English UI, Arabic explanations, reference numbers on) with invented trips
 * to app/build/screenshots/, to check the design without a phone. Only draws; asserts nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "en-w412dp-h915dp-xhdpi")
class ScreenshotsRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun stop(id: Long, text: String, time: String?, located: Boolean = true, kind: TripKind? = null, name: String? = null) = Stop(
        id = id,
        displayText = text,
        candidates = listOf(text),
        geoStatus = if (located) GeoStatus.LOCATED else GeoStatus.NOT_LOCATED,
        geo = if (located) GeoPoint(59.38, 13.50) else null,
        time = time,
        kind = kind,
        name = name,
    )

    private val stops = listOf(
        stop(1, "Järnvägsgatan 3B, 68830 Storfors", "07:30", kind = TripKind.PICK_UP, name = "Anna Testsson"),
        stop(2, "Västra Torggatan 12, 65224 Karlstad", "07:36", kind = TripKind.PICK_UP, name = "Bengt Provare"),
        stop(3, "Hamngatan 7, 66330 Skoghall", "08:00", kind = TripKind.DROP_OFF, name = "Anna Testsson"),
        stop(4, "Storgatan 14, 65224 Karlstad", "08:25", located = false, kind = TripKind.DROP_OFF, name = "Bengt Provare"),
        stop(5, "Lindvägen 9, 66430 Grums", "09:10"),
    )

    /** The day's start point (YouDrive's Pull-out), invented. */
    private val depot = stop(9, "Depågatan 1, 65340 Karlstad", "06:42", kind = TripKind.PULL_OUT)

    private fun spoken(s: Stop) = s.displayText.substringAfterLast(' ')

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String, after: () -> Unit = {}, night: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val context = LocalContext.current
            NastaTheme(if (night) Appearance.NIGHT else Appearance.DAY) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(
                        LocalExplainResources provides LocaleHelper.explanationContext(context, "en", true).resources,
                        content = content,
                    )
                }
            }
        }
        compose.waitForIdle()
        after()
        compose.waitForIdle()
        // A dialog is its own window: capture it when one is open.
        val dialogs = compose.onAllNodes(isDialog())
        val node = if (dialogs.fetchSemanticsNodes().isNotEmpty()) dialogs[0] else compose.onRoot()
        save(name, node.captureToImage().asAndroidBitmap())
    }

    private val youDriveState = YouDriveWatcher.State(
        status = YouDriveWatcher.Status.WATCHING,
        trips = List(19) { WatchedTrip("08:00", "Gata $it") },
        lastReadMs = now,
        changes = listOf(
            YouDriveWatcher.PendingChange(1, TripChange(WatchedTrip("13:40", "Lindvägen 9, 66430 Grums"), added = true), now),
        ),
    )

    @Test
    @Config(qualifiers = "en-w412dp-h1500dp-xhdpi")
    fun home() = shot("home") { HomeContent() }

    @Composable
    private fun HomeContent() {
        HomeScreen(
            route = RouteData(createdAtMs = now, stops = stops),
            ttsStatus = TtsStatus.READY,
            importing = false,
            onImport = {}, onResume = {}, onReview = {}, onClear = {}, onSettings = {}, onHelp = {}, onTtsMissing = {},
            link = DisplayLinkServer.State(DisplayLinkServer.Status.CONNECTED, clients = listOf("Tab S9+")),
            onToggleLink = {}, onFixLink = {}, onUseAsDisplay = {},
            overlayPermission = true, overlayHidden = false, onOverlayVisible = {}, onOverlayPermission = {}, onAddTile = {},
            history = listOf(
                HistoryEntry(1, "07:10", "Kristinehamn", "Kungsgatan 22, 68131 Kristinehamn", now - 3_600_000),
                HistoryEntry(2, "06:45", "Karlstad", "Storgatan 14, 65224 Karlstad", now - 5_000_000, done = false),
            ),
            historyRetentionHours = 12,
            onClearHistory = {},
            youDrive = { YouDriveCard(youDriveState, watching = true) {} },
        )
    }

    @Test
    fun activeRouteNight() = shot("active_night", night = true) { ActiveContent() }

    @Test
    @Config(qualifiers = "en-w412dp-h1500dp-xhdpi")
    fun homeNight() = shot("home_night", night = true) { HomeContent() }

    @Test
    fun reviewNight() = shot("review_night", night = true) { ReviewContent() }

    @Test
    fun activeRoute() = shot("active") { ActiveContent() }

    @Composable
    private fun ActiveContent() {
        ActiveRouteScreen(
            route = RouteData(createdAtMs = now, active = true, stops = stops.drop(1), completed = stops.take(1), depot = depot),
            hasLocationPermission = false,
            spokenName = ::spoken,
            overlayAvailable = true,
            overlayHidden = false,
            street = null,
            onSpeakStreet = {}, onBack = {}, onNext = {}, onPreviousTrip = {}, onRepeat = {}, onOpenMaps = {},
            onEdit = {}, onEnd = {}, onOpenDisplay = {}, onToggleOverlay = {},
        )
    }

    @Test
    fun review() = shot("review") { ReviewContent() }

    @Composable
    private fun ReviewContent() {
        ReviewScreen(
            route = RouteData(createdAtMs = now, stops = stops, depot = depot),
            spokenName = ::spoken,
            importing = false,
            onBack = {}, onMove = { _, _ -> }, onDelete = {}, onDeleteAbove = {}, onEdit = { _, _, _ -> true }, onRetry = {},
            onAddManual = { _, _ -> true }, onSortByTime = {}, onAddScreenshots = {}, onStart = {}, onBackToRoute = {},
        )
    }

    @Test
    @Config(qualifiers = "en-w412dp-h1900dp-xhdpi")
    fun settings() = shot("settings") {
        SettingsScreen(
            settings = AppSettings(),
            permissions = PermissionStatus(location = true, notifications = true, overlay = true, battery = false),
            ttsStatus = TtsStatus.READY,
            onBack = {}, onUpdate = {}, onLanguage = {}, onTestVoice = {}, onLocation = {}, onNotifications = {}, onOverlay = {}, onBattery = {}, onVoice = {},
            link = DisplayLinkServer.State(DisplayLinkServer.Status.WAITING, localName = "Galaxy S20 Ultra"),
            onToggleLink = {}, onFixLink = {},
            youDriveLoginSaved = false, onSaveYouDriveLogin = { _, _ -> }, onDeleteYouDriveLogin = {},
            streetMap = StreetMapStore.State.Ready(41_230, 1_790_000_000_000), onDownloadStreetMap = {}, onDeleteStreetMap = {},
        )
    }

    @Test
    fun help() = shot("help") {
        HelpScreen(onBack = {}, onAppSettings = {}, onBatteryRequest = {}, onInstallVoice = {}, onTtsSettings = {})
    }

    @Test
    fun youDriveBar() = shot("youdrive") {
        YouDriveBar(
            state = youDriveState, watching = true,
            onBack = {}, onWatch = {}, onStartPage = {}, onReload = {}, onReadNow = {}, onImportAll = {},
            onApply = {}, onDismiss = {}, onLogout = {},
        )
    }

    @Test
    fun explanationPopup() = shot("popup", after = {
        compose.onAllNodesWithContentDescription("Explanation")[0].performClick()
    }) {
        SettingsScreen(
            settings = AppSettings(),
            permissions = PermissionStatus(location = false, notifications = true, overlay = true, battery = true),
            ttsStatus = TtsStatus.READY,
            onBack = {}, onUpdate = {}, onLanguage = {}, onTestVoice = {}, onLocation = {}, onNotifications = {}, onOverlay = {}, onBattery = {}, onVoice = {},
            link = DisplayLinkServer.State(), onToggleLink = {}, onFixLink = {},
            youDriveLoginSaved = true, onSaveYouDriveLogin = { _, _ -> }, onDeleteYouDriveLogin = {},
            streetMap = StreetMapStore.State.None, onDownloadStreetMap = {}, onDeleteStreetMap = {},
        )
    }

    @Test
    fun onboarding() = shot("onboarding") {
        OnboardingScreen(resumeTick = 0, ttsStatus = TtsStatus.READY, onTestVoice = {}, onVoiceMissing = {}, onFinish = {}, onChooseDisplay = {})
    }

    /** The floating panel over Maps (a window of its own): drawn into a bitmap. */
    @Test
    fun floatingPanel() = renderPanel("floating", Appearance.DAY)

    @Test
    fun floatingPanelNight() = renderPanel("floating_night", Appearance.NIGHT)

    @Test
    fun floatingBubble() = renderPanel("floating_bubble", Appearance.DAY, minimized = true)

    /** Arabic: the panel is mirrored (Back on the right). */
    @Test
    fun floatingArabic() = renderPanel("floating_ar", Appearance.DAY, language = "ar")

    /**
     * The panel (or its minimised capsule) over a background that is half a light day map with a
     * park and a route line, half a dark wallpaper: the glass must read on all of them.
     */
    /** The panel as a tablet shows it over the passenger display: no name, no street bar or speed. */
    @Test
    fun floatingPanelOnTheTablet() = renderPanel("floating_tablet", Appearance.NIGHT, tablet = true)

    @Test
    fun floatingBubbleOnTheTablet() = renderPanel("floating_tablet_bubble", Appearance.NIGHT, minimized = true, tablet = true)

    private fun renderPanel(name: String, appearance: Appearance, minimized: Boolean = false, language: String = "en", tablet: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<App>()
        val graph = app.graph
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION) // the speed circle shows
        graph.settings.update { it.copy(overlayHidden = false, overlayMinimized = minimized, appearance = appearance, uiLanguage = language) }
        LocaleHelper.applyAppLocale(app, language)
        graph.controller.clear()
        graph.controller.addExtracted(
            graph.extractor.extract(
                listOf("2026-09-29", "07:36", "Pick-up", "Bengt Provare", "Västra Torggatan 12, 65224 Karlstad", "08:00", "Drop-off", "Bengt Provare", "Hamngatan 7, 66330 Skoghall"),
            ),
        )
        repeat(600) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) }
        // The app's own panel stays away; this one knows the street (an invented lookup).
        graph.overlay.suppress("render", true)
        val street = CurrentStreet(graph.scope) { _, _ -> listOf(GeoResult(59.38, 13.5, null, null, "Karlstad", "Centrum", "Västra Torggatan")) }
        street.want("render", true)
        street.onFix(Fix(0, 59.38, 13.5, 12.5f, 5f)) // 45 km/h
        shadowOf(Looper.getMainLooper()).idle()
        street.onFix(Fix(10_000, 59.38, 13.5, 12.5f, 5f)) // the confirming reading
        shadowOf(Looper.getMainLooper()).idle()
        val phone = RoutePanelSource(graph.controller, street)
        // A tablet gets what its passenger display gets: no name, and no street or speed.
        val source = if (!tablet) phone else object : PanelSource by phone {
            override val street: CurrentStreet? = null
            override fun trip() = phone.trip()?.copy(name = null, area = null)
        }
        val panelManager = OverlayManager(app, source, graph.settings, graph.scope, bubbleScale = if (tablet) 2f else 1f) { true }
        graph.controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        val wm = Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))
        val panel = wm.views.last() // the compose rule's own window comes first
        panel.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val w = panel.measuredWidth + 40
        val h = panel.measuredHeight + 40
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint()
            paint.color = 0xFFEDEBE6.toInt() // a light day map
            drawRect(0f, 0f, w / 2f, h.toFloat(), paint)
            paint.color = 0xFFC8E6C9.toInt() // a park
            drawRect(0f, h * 0.55f, w / 2f, h.toFloat(), paint)
            paint.color = 0xFF4285F4.toInt() // the route line
            drawRect(w * 0.28f, 0f, w * 0.34f, h.toFloat(), paint)
            paint.color = 0xFF202124.toInt() // a dark wallpaper
            drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.color = 0xFFFFC61A.toInt() // a bright app icon
            drawRect(w * 0.78f, h * 0.2f, w * 0.92f, h * 0.45f, paint)
            translate(20f, 20f)
            panel.draw(this)
        }
        save(name, bitmap)
        panelManager.hide()
        graph.overlay.suppress("render", false)
        street.want("render", false)
        graph.controller.end()
        graph.settings.update { it.copy(appearance = Appearance.DAY, overlayMinimized = false, uiLanguage = "en") }
        LocaleHelper.applyAppLocale(app, "en")
    }

    /**
     * The invented dispatch lists of [DeviceFixtures] as phone screenshots (1080×2400), for the
     * import test on the real phone. Copied to testdata/screenshots/ when they change.
     */
    @Test
    fun deviceFixtures() {
        listOf(
            "fixture_time_above" to DeviceFixtures.timeAbove,
            "fixture_same_line" to DeviceFixtures.sameLine,
            "fixture_prefixes" to DeviceFixtures.prefixes,
        ).forEach { (name, lines) ->
            val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                typeface = android.graphics.Typeface.DEFAULT
            }
            var y = 70f
            lines.forEachIndexed { i, line ->
                val header = i < 2
                paint.textSize = if (i == 1) 60f else if (i == 0) 34f else 46f
                paint.isFakeBoldText = header || line.first().isDigit()
                if (!header && line.first().isDigit() && i > 2 && name == "fixture_time_above") y += 36f // gap between trips
                canvas.drawText(line, 48f, y, paint)
                y += if (i == 0) 110f else 78f
            }
            val dir = File("build/fixtures").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        drawYouDriveFixture()
    }

    /** [DeviceFixtures.youDriveCards] drawn like YouDrive's list: coloured cards, two columns. */
    private fun drawYouDriveFixture() {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(0xFFF5F5F5.toInt()) }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 34f
        canvas.drawText(DeviceFixtures.youDrive[0], 48f, 50f, paint) // status bar clock
        paint.textSize = 52f
        paint.isFakeBoldText = true
        canvas.drawText(DeviceFixtures.youDrive[1], 48f, 150f, paint)
        val rowH = 60f
        var top = 210f
        for (card in DeviceFixtures.youDriveCards) {
            val bottom = top + 40f + card.rows.size * rowH
            val rect = android.graphics.RectF(24f, top, 1056f, bottom)
            fill.style = android.graphics.Paint.Style.FILL
            fill.color = card.color.toInt()
            canvas.drawRoundRect(rect, 12f, 12f, fill)
            fill.style = android.graphics.Paint.Style.STROKE
            fill.color = 0xFFBDBDBD.toInt()
            canvas.drawRoundRect(rect, 12f, 12f, fill)
            card.rows.forEachIndexed { i, (left, right) ->
                val y = top + 20f + (i + 1) * rowH - 16f
                paint.textSize = 40f
                paint.isFakeBoldText = true
                left?.let { canvas.drawText(it, 48f, y, paint) }
                paint.isFakeBoldText = false
                paint.textSize = 36f
                right?.let { canvas.drawText(it, 260f, y, paint) }
            }
            top = bottom + 24f
        }
        File(File("build/fixtures").apply { mkdirs() }, "fixture_youdrive.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun passengerDisplay() = shot("display") {
        PassengerDisplayScreen(
            snapshot = DisplaySnapshot(
                active = true,
                previous = DisplayItem("07:30", "Järnvägsgatan 3B", "Storfors"),
                current = DisplayItem("07:36", "Västra Torggatan 12", "Karlstad"),
                upcoming = listOf(DisplayItem("08:00", "Hamngatan 7", "Skoghall"), DisplayItem("08:25", "Storgatan 14", "Karlstad")),
            ),
            status = "Galaxy S20",
            connected = true,
            onSpeak = {},
            onExit = {},
        )
    }

    /**
     * Next on the phone: the "Därefter" card grows into the new next stop, and the announcement
     * lights it up. Frames during the move are saved for a look; the end state is checked.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayMovesOnToTheNextStop() {
        val first = DisplaySnapshot(
            active = true,
            current = DisplayItem("07:36", "Västra Torggatan 12", "Karlstad"),
            upcoming = listOf(DisplayItem("08:00", "Hamngatan 7", "Skoghall"), DisplayItem("08:25", "Storgatan 14", "Karlstad")),
            announcementSv = "Nästa stopp: Västra Torggatan 12, Karlstad. Därefter: Hamngatan 7, Skoghall.",
        )
        val next = DisplaySnapshot(
            active = true,
            previous = first.current,
            current = first.upcoming[0],
            upcoming = listOf(first.upcoming[1]),
            announcementSv = "Nästa stopp: Hamngatan 7, Skoghall. Därefter: Storgatan 14, Karlstad.",
        )
        var snapshot by mutableStateOf(first)
        var spoken by mutableIntStateOf(0)
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(snapshot, status = "Galaxy S20", connected = true, onSpeak = {}, onExit = {}, spoken = spoken)
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        snapshot = next
        spoken++
        // 150–1700 ms: the card grows into place; 2600: the next stop is said; 5000: its "Därefter".
        for (ms in listOf(150L, 250L, 300L, 1_000L, 900L, 2_400L)) {
            compose.mainClock.advanceTimeBy(ms)
            save("display_move_${compose.mainClock.currentTime}", compose.onRoot().captureToImage().asAndroidBitmap())
        }
        compose.mainClock.advanceTimeBy(8_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Hamngatan 7").assertExists()
        compose.onNodeWithText("Västra Torggatan 12").assertDoesNotExist()
        compose.onNodeWithText("Storgatan 14").assertExists()
    }

    /**
     * A tap on a card says its time and place and shows its trip in the middle for a moment; a tap
     * on the clock says the time, on this device.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplaySaysWhatIsTapped() {
        val said = mutableListOf<String>()
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    tabletSnapshot,
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = {},
                    onExit = {},
                    onSay = { said += it.swedish },
                    time = { LocalTime.of(8, 11, 5) },
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Hamngatan 7").performClick()
        compose.mainClock.advanceTimeBy(1_700)
        save("display_card_tap", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("ref_88").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        assertEquals(listOf("Klockan 8 ska vi till Hamngatan 7, Skoghall.", "Klockan är 8 och 11."), said)
    }

    /**
     * A tap on the clock says the time and springs it out to fill the screen, seconds and all; a
     * tap anywhere brings the screen back at once.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayClockTapFillsTheScreen() {
        val said = mutableListOf<String>()
        var repeats = 0
        compose.setContent {
            NastaTheme(Appearance.NIGHT) {
                PassengerDisplayScreen(
                    tabletSnapshot,
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = { repeats++ },
                    onExit = {},
                    onSay = { said += it.swedish },
                    time = { LocalTime.of(8, 11, 42) },
                )
            }
        }
        compose.waitForIdle()
        save("display_night", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("ref_88").performClick()
        compose.mainClock.advanceTimeBy(2_500)
        save("display_clock_tap", compose.onRoot().captureToImage().asAndroidBitmap())
        assertEquals(listOf("Klockan är 8 och 11."), said)
        // While the time fills the screen a tap anywhere only brings the screen back; after that
        // the address takes taps again.
        compose.onNodeWithText("Västra Torggatan 12").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(0, repeats)
        compose.onNodeWithText("Västra Torggatan 12").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(1, repeats)
        compose.mainClock.autoAdvance = true
    }

    /** When the minute changes, the time grows into the middle of the screen, stays, then goes back. */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayMinuteGrows() {
        var now = LocalTime.of(8, 10, 58)
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(tabletSnapshot, status = "Galaxy S20", connected = true, onSpeak = {}, onExit = {}, time = { now })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        now = LocalTime.of(8, 11, 0)
        // Growing, the ground fading in, solid at its largest, going back as the ground clears, back.
        for (ms in listOf(1_000L, 3_700L, 1_800L, 2_000L, 900L, 2_000L)) {
            compose.mainClock.advanceTimeBy(ms)
            save("display_minute_${compose.mainClock.currentTime}", compose.onRoot().captureToImage().asAndroidBitmap())
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Västra Torggatan 12").assertExists()
    }

    /**
     * A swipe across the address pages to the following stops, each with its time under it, while
     * the time beside the clock stays the next stop's; Home brings back the next stop. The route
     * itself does not move.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayPagesThroughTheStops() {
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(tabletSnapshot, status = "Galaxy S20", connected = true, onSpeak = {}, onExit = {}, time = { LocalTime.of(8, 11, 5) })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("ref_200").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        // A swipe across the middle of the screen, where the stops are.
        repeat(2) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(1_500)
        }
        save("display_paged", compose.onRoot().captureToImage().asAndroidBitmap())
        // The stop after "Därefter", large at the top (and small on the bottom line) with its time
        // under it; beside the clock the next stop's time stays.
        compose.onAllNodesWithText("Storgatan 14").assertCountEquals(2)
        compose.onNodeWithTag("ref_230", useUnmergedTree = true).assert(hasAnyDescendant(hasText("25")))
        compose.onNodeWithTag("ref_90", useUnmergedTree = true).assert(hasAnyDescendant(hasText("36")))
        compose.onNodeWithText("Västra Torggatan 12").assertDoesNotExist()
        compose.onNodeWithTag("ref_200").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        save("display_paged_home", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Västra Torggatan 12").assertIsDisplayed()
        compose.onNodeWithTag("ref_200").assertDoesNotExist()
        // The other way: the trips done.
        compose.onRoot().performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1_500)
        save("display_paged_back", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onAllNodesWithText("Järnvägsgatan 3B").onFirst().assertExists()
        compose.onNodeWithTag("ref_200").assertExists()
        compose.mainClock.autoAdvance = true
    }

    /**
     * A swipe along the bottom line brings out the strip of all the trips in its place, the top
     * following the trip in the strip's middle; it goes three seconds after the last swipe and the
     * next stop comes back. A tap on a trip in it says the trip and shows it, and puts the strip away.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayListsAllTheTrips() {
        val said = mutableListOf<String>()
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    tabletSnapshot,
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = {},
                    onExit = {},
                    onSay = { said += it.swedish },
                    time = { LocalTime.of(8, 11, 5) },
                )
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("ref_226").assertCountEquals(0)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("ref_94").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        save("display_list", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Nästa stopp", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag("ref_200").assertExists()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onAllNodesWithTag("ref_226").assertCountEquals(0)
        compose.onNodeWithText("Västra Torggatan 12").assertIsDisplayed()
        // Out again from the clock; a tap on a coming trip in the strip says it and shows it.
        compose.onNodeWithTag("ref_88").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(hasTestTag("ref_226") and hasText("Södra Kyrkogatan 7")).performClick()
        compose.mainClock.advanceTimeBy(1_500)
        save("display_list_pick", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onAllNodesWithTag("ref_226").assertCountEquals(0)
        compose.mainClock.autoAdvance = true
        assertEquals(listOf("Klockan 8 och 50 ska vi till Södra Kyrkogatan 7, Kristinehamn."), said)
    }

    /**
     * The weather in the middle of the minute and Google Maps' travel time a little later, each for
     * a moment; a tap brings the screen back at once. The weather sign on the top line brings the
     * weather up too.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayShowsTheWeatherAndTheTravelTime() {
        var now = LocalTime.of(8, 11, 25)
        val snapshot = tabletSnapshot.copy(weather = DisplayWeather(14, 3), eta = DisplayEta(12, 5300))
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(snapshot, status = "Galaxy S20", connected = true, onSpeak = {}, onExit = {}, time = { now })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        now = LocalTime.of(8, 11, 27)
        compose.mainClock.advanceTimeBy(2_500)
        save("display_weather", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("ref_204").assertIsDisplayed()
        compose.onNodeWithText("Halvklart").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithTag("ref_204").assertDoesNotExist()
        // The small weather sign (the degrees beside the picture) brings it up at once.
        save("display_weather_sign", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("ref_222").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Halvklart").assertIsDisplayed()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Halvklart").assertDoesNotExist()
        now = LocalTime.of(8, 11, 45)
        compose.mainClock.advanceTimeBy(2_500)
        save("display_eta", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("12").assertIsDisplayed()
        // A tap anywhere: straight back.
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("12").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    /**
     * The map sign and a long press on a trip open the tablet's own map, filling the screen, and
     * ask for the tablet's position (its permission, when not given yet); until the tablet knows
     * where it is the map says it is looking. A tap brings the screen back. Google Maps is never
     * opened from the display.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayMapAsksForThePosition() {
        var asked = 0
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            val scope = rememberCoroutineScope()
            val map = remember { RouteMap(context, "AIzaTestKeyForTheMapOnly", true, "#000000", scope) }
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    tabletSnapshot,
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = {},
                    onExit = {},
                    time = { LocalTime.of(8, 11, 42) },
                    routeMap = map,
                    onWantPosition = { asked++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("ref_90", useUnmergedTree = true).assert(hasAnyDescendant(hasText("36")))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("ref_219").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        save("display_map_looking", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Söker bilens position…").assertIsDisplayed()
        compose.onRoot().performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Söker bilens position…").assertDoesNotExist()
        compose.onNodeWithText("Hamngatan 7").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Söker bilens position…").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        assertEquals(2, asked)
    }

    /**
     * The next stop's passenger's last name stands under its address; a tap says it and shows it
     * large. No other trip has a name.
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayShowsTheNextPassengersLastName() {
        val said = mutableListOf<String>()
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    tabletSnapshot,
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = {},
                    onExit = {},
                    onSay = { said += it.swedish },
                    time = { LocalTime.of(8, 11, 5) },
                )
            }
        }
        compose.waitForIdle()
        save("display_name", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("ref_231").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("ref_231").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        save("display_name_large", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("ref_232").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithTag("ref_232").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        assertEquals(listOf("Testsson."), said)
    }

    /** A weather app's widget, when the tablet hosts one, takes the weather's moment in the middle. */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayShowsAWeatherWidget() {
        var now = LocalTime.of(8, 11, 25)
        compose.setContent {
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    tabletSnapshot,
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = {},
                    onExit = {},
                    time = { now },
                    weatherWidget = { m -> Box(m.background(AppTheme.colors.info)) { Text("Widget") } },
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        now = LocalTime.of(8, 11, 27)
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithText("Widget").assertIsDisplayed()
        compose.onNodeWithTag("ref_211").assertExists()
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Widget").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    private val tabletSnapshot = DisplaySnapshot(
        active = true,
        previous = DisplayItem("07:30", "Järnvägsgatan 3B", "Storfors", doneInYouDrive = true, doneHere = true),
        earlier = listOf(
            DisplayItem("07:05", "Kyrkogatan 2", "Karlstad", doneInYouDrive = true, doneHere = true),
            DisplayItem("07:30", "Järnvägsgatan 3B", "Storfors", doneInYouDrive = true, doneHere = true),
        ),
        current = DisplayItem("07:36", "Västra Torggatan 12", "Karlstad", lastName = "Testsson"),
        upcoming = listOf(
            DisplayItem("08:00", "Hamngatan 7", "Skoghall"),
            DisplayItem("08:25", "Storgatan 14", "Karlstad", doneInYouDrive = true),
            DisplayItem("08:50", "Södra Kyrkogatan 7", "Kristinehamn"),
            DisplayItem("09:10", "Lindvägen 9", "Grums"),
            DisplayItem("09:35", "Kungsgatan 5", "Karlstad"),
        ),
    )

    /**
     * The look sign on the top line switches the display between black and light, and a tap on the
     * connection offers to close the display (there is no × of its own).
     */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplaySwitchesItsLook() {
        var dark by mutableStateOf(true)
        var exited = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            val scope = rememberCoroutineScope()
            val map = remember { RouteMap(context, "AIzaTestKeyForTheMapOnly", false, "#F3F4F6", scope) }
            NastaTheme(Appearance.DAY) {
                PassengerDisplayScreen(
                    tabletSnapshot.copy(weather = DisplayWeather(14, 3)),
                    status = "Galaxy S20",
                    connected = true,
                    onSpeak = {},
                    onExit = { exited = true },
                    time = { LocalTime.of(8, 11, 42) },
                    routeMap = map,
                    dark = dark,
                    onToggleLook = { dark = !dark },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("ref_224").assertExists()
        compose.onNodeWithTag("ref_223").performClick()
        compose.waitForIdle()
        assertEquals(false, dark)
        save("display_tablet_light", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithTag("ref_87").performClick()
        compose.onNodeWithTag("ref_86").performClick()
        compose.waitForIdle()
        assertEquals(true, exited)
    }

    /** The passenger display on the tablet (a Galaxy Tab S9+ in landscape). */
    @Test
    @Config(qualifiers = "en-w1400dp-h876dp-land-mdpi")
    fun passengerDisplayTablet() = shot("display_tablet") {
        PassengerDisplayScreen(
            snapshot = tabletSnapshot,
            status = "Galaxy S20",
            connected = true,
            onSpeak = {},
            onExit = {},
            time = { LocalTime.of(8, 11, 42) },
        )
    }
}
