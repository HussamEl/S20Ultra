package se.eldebosh.nastastopp.robo

import android.graphics.Bitmap
import android.graphics.Canvas
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
import se.eldebosh.nastastopp.ui.screens.OnboardingScreen
import java.time.Duration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
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
import se.eldebosh.nastastopp.route.TrackingState
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
        stop(1, "Sjösalagatan 21, 66452 Vålberg", "07:30", kind = TripKind.PICK_UP, name = "Anna Testsson"),
        stop(2, "Brattgårdsgatan 4, 66452 Vålberg", "07:36", kind = TripKind.PICK_UP, name = "Bengt Provare"),
        stop(3, "Majeldsvägen 10, 66450 Vålberg", "08:00", kind = TripKind.DROP_OFF, name = "Anna Testsson"),
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
                HistoryEntry(1, "07:10", "Vålberg", "Kasernhöjden 1, 66452 Vålberg", now - 3_600_000),
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
            tracking = TrackingState(),
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
            permissions = PermissionStatus(notifications = true, overlay = true, battery = false),
            ttsStatus = TtsStatus.READY,
            onBack = {}, onUpdate = {}, onLanguage = {}, onTestVoice = {}, onNotifications = {}, onOverlay = {}, onBattery = {}, onVoice = {},
            link = DisplayLinkServer.State(DisplayLinkServer.Status.WAITING, localName = "Galaxy S20 Ultra"),
            onToggleLink = {}, onFixLink = {},
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
            permissions = PermissionStatus(notifications = true, overlay = true, battery = true),
            ttsStatus = TtsStatus.READY,
            onBack = {}, onUpdate = {}, onLanguage = {}, onTestVoice = {}, onNotifications = {}, onOverlay = {}, onBattery = {}, onVoice = {},
            link = DisplayLinkServer.State(), onToggleLink = {}, onFixLink = {},
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

    private fun renderPanel(name: String, appearance: Appearance) {
        val app = ApplicationProvider.getApplicationContext<App>()
        val graph = app.graph
        ShadowSettings.setCanDrawOverlays(true)
        graph.settings.update { it.copy(overlayHidden = false, overlayMinimized = false, appearance = appearance) }
        graph.controller.clear()
        graph.controller.addExtracted(
            graph.extractor.extract(
                listOf("2026-09-29", "07:36", "Pick-up", "Bengt Provare", "Brattgårdsgatan 4, 66452 Vålberg", "08:00", "Drop-off", "Bengt Provare", "Majeldsvägen 10, 66450 Vålberg"),
            ),
        )
        repeat(600) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) }
        graph.controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        val wm = Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))
        val panel = wm.views.last() // the compose rule's own window comes first
        panel.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val bitmap = Bitmap.createBitmap(panel.measuredWidth + 40, panel.measuredHeight + 40, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(0xFF5A6B57.toInt()) // a map-like background
            translate(20f, 20f)
            panel.draw(this)
        }
        save(name, bitmap)
        graph.controller.end()
        graph.settings.update { it.copy(appearance = Appearance.DAY) }
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
                previous = DisplayItem("07:30", "Sjösalagatan 21", "Vålberg"),
                current = DisplayItem("07:36", "Brattgårdsgatan 4", "Vålberg"),
                upcoming = listOf(DisplayItem("08:00", "Majeldsvägen 10", "Vålberg"), DisplayItem("08:25", "Storgatan 14", "Karlstad")),
            ),
            status = "Connected: Galaxy S20 Ultra",
            connected = true,
            onSpeak = {},
            onExit = {},
        )
    }
}
