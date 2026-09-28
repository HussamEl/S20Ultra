package se.eldebosh.nastastopp.robo

import android.os.Looper
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.BuildConfig
import se.eldebosh.nastastopp.MainActivity
import se.eldebosh.nastastopp.R

/**
 * Smoke-tests the Compose screens in the Arabic UI on Android 13 (like the S20 Ultra).
 * Robolectric does not apply per-app locales, so the "ar" qualifier stands in for them here.
 * (Screen-size qualifiers are left out: with them Robolectric never idles once a TextField is shown.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "ar")
class UiSmokeRoboTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var app: App

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.graph.controller.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Strings as the activity sees them (the in-app UI language, Arabic by default). */
    private var activityContext: android.content.Context? = null
    /** Robolectric's Geocoder never answers: advance virtual time until every lookup timed out. */
    private fun settleGeocoding() {
        repeat(600) {
            val pending = app.graph.controller.route.value?.stops?.any { it.geoStatus == se.eldebosh.nastastopp.route.model.GeoStatus.PENDING } == true
            if (!pending) return
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        }
    }

    private fun s(id: Int, vararg args: Any) = (activityContext ?: app).getString(id, *args)

    /** Waits for the (single) compose root, then asserts that [text] is shown. */
    private fun waitText(text: String) {
        compose.waitForIdle()
        compose.onNodeWithText(text).assertExists()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { sc ->
            shadowOf(Looper.getMainLooper()).idle()
            sc.onActivity { activityContext = it }
        }

    @Test
    fun onboardingThenHomeSettingsAndHelp() {
        app.graph.settings.update { it.copy(onboardingDone = false) }
        launch().use {
            waitText(s(R.string.onb_welcome_title))
            // English is the default UI language since 1.4.0 (explanations stay Arabic while setting up).
            assertEquals("en", app.graph.settings.current.uiLanguage)
            assertTrue(app.graph.settings.current.explanationsArabic)
            compose.onNodeWithText(s(R.string.role_controller)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.onb_location_title)).assertExists()
            // Skip through the permission steps one at a time.
            repeat(4) {
                compose.onNode(hasText(s(R.string.skip)).or(hasText(s(R.string.next_step)))).performScrollTo().performClick()
            }
            compose.onNodeWithText(s(R.string.onb_voice_title)).assertExists()
            compose.onNodeWithText(s(R.string.onb_finish)).performScrollTo().performClick()

            compose.onNodeWithText(s(R.string.home_import)).assertExists()
            assertTrue(app.graph.settings.current.onboardingDone)

            compose.onNodeWithText(s(R.string.home_settings)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.BUILD_DATE))
                .performScrollTo().assertExists()
            compose.onNodeWithText(s(R.string.detail_district)).assertExists()
            compose.onNodeWithText(s(R.string.settings_explain_arabic)).assertExists()
            compose.onNodeWithText(s(R.string.settings_ref_numbers)).performScrollTo().performClick()
            assertEquals(false, app.graph.settings.current.showRefNumbers)
            compose.onNodeWithText(s(R.string.settings_ref_numbers)).performClick()
            assertEquals(true, app.graph.settings.current.showRefNumbers)
            compose.onNodeWithContentDescription(s(R.string.back)).performClick()
            compose.onNodeWithText(s(R.string.home_help)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.help_battery_title)).assertExists()
            compose.onNodeWithText(s(R.string.help_share_title)).performScrollTo().assertExists()
        }
    }

    @Test
    fun reviewAndActiveRouteScreens() {
        app.graph.settings.update { it.copy(onboardingDone = true) }
        app.graph.controller.addManual("Storgatan 14, 65224 Karlstad")
        app.graph.controller.addManual("Järnvägsgatan 3B, 68830 Storfors")
        app.graph.controller.addManual("Björkvägen 7, 66341 Hammarö")
        settleGeocoding()
        launch().use {
            waitText(s(R.string.home_resume_draft))
            compose.onNodeWithText(s(R.string.home_resume_draft)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.review_title)).assertExists()
            compose.onNodeWithText("1.  Storgatan 14, 652 24 Karlstad").assertExists()
            compose.onNodeWithText(s(R.string.review_add_manual)).assertExists()
            compose.onNodeWithText(s(R.string.review_add_screens)).assertExists()
            // Tap a row → edit dialog with the address; save a new text → re-parsed and re-geocoded.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("2.  Järnvägsgatan 3B, 688 30 Storfors"))
            compose.onNodeWithText("2.  Järnvägsgatan 3B, 688 30 Storfors").performClick()
            compose.onNodeWithText(s(R.string.dialog_edit_title)).assertExists()
            compose.onNodeWithText("Järnvägsgatan 3B, 688 30 Storfors").performTextReplacement("kungsgatan 5 65224 karlstad")
            compose.onNodeWithText(s(R.string.save)).performClick()
            assertEquals("Kungsgatan 5, 652 24 Karlstad", app.graph.controller.route.value!!.stops[1].displayText)
            settleGeocoding()
            // Add manually.
            compose.onNodeWithText(s(R.string.review_add_manual)).performClick()
            compose.onNodeWithText(s(R.string.dialog_add_title)).assertExists()
            compose.onNodeWithText(s(R.string.cancel)).performClick()
            // Long-press menu → delete all above → undo snackbar.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("2.  Kungsgatan 5, 652 24 Karlstad"))
            compose.onNodeWithText("2.  Kungsgatan 5, 652 24 Karlstad").performTouchInput { longClick() }
            compose.onNodeWithText(s(R.string.menu_delete_above)).performClick()
            assertEquals(2, app.graph.controller.route.value!!.stops.size)
            compose.onNodeWithText(s(R.string.undo)).performClick()
            assertEquals(3, app.graph.controller.route.value!!.stops.size)
            compose.onNodeWithText(s(R.string.review_start)).assertExists()
        }
    }

    @Test
    fun deviceCanBecomeAPassengerDisplayAndBack() {
        app.graph.settings.update { it.copy(onboardingDone = true) }
        launch().use {
            waitText(s(R.string.switch_to_display))
            compose.onNodeWithText(s(R.string.switch_to_display)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.display_choose_device)).assertExists()
            assertEquals(se.eldebosh.nastastopp.settings.DeviceRole.DISPLAY, app.graph.settings.current.role)
            compose.onNodeWithText(s(R.string.switch_to_controller)).performScrollTo().performClick()
            compose.onNodeWithText(s(R.string.home_import)).assertExists()
        }
    }

    @Test
    fun homeShowsHiddenFloatingButtonAndPreviousTrips() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        app.graph.settings.update { it.copy(onboardingDone = true, overlayHidden = true) }
        app.graph.history.add("12:30", "Karlstad", "Storgatan 14, 652 24 Karlstad", done = true)
        launch().use {
            waitText(s(R.string.home_overlay_hidden))
            // Switching it on brings the floating button back.
            compose.onNodeWithText(s(R.string.home_overlay_hidden)).performScrollTo().performClick()
            assertEquals(false, app.graph.settings.current.overlayHidden)
            compose.onNodeWithText(s(R.string.home_overlay_shown)).assertExists()
            // Previous trips stay listed on the Home screen.
            compose.onNodeWithText(s(R.string.history_title)).performScrollTo().assertExists()
            compose.onNodeWithText("Storgatan 14, 652 24 Karlstad").performScrollTo().assertExists()
        }
        app.graph.history.clear()
    }

    @Test
    fun activeRouteNextButtonAdvances() {
        app.graph.settings.update { it.copy(onboardingDone = true) }
        app.graph.controller.addManual("Storgatan 14, 65224 Karlstad")
        app.graph.controller.addManual("Järnvägsgatan 3B, 68830 Storfors")
        settleGeocoding()
        app.graph.controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        launch().use {
            waitText(s(R.string.btn_next))
            compose.onNodeWithText("Karlstad").assertExists()
            compose.onNodeWithText(s(R.string.active_counts, 0, 2)).assertExists()
            compose.onNodeWithText(s(R.string.btn_next)).performClick()
            compose.waitForIdle()
            assertEquals(1, app.graph.controller.route.value!!.stops.size)
            compose.onNodeWithText(s(R.string.active_counts, 1, 1)).assertExists()
            // The completed trip stays visible (small, faded) with its area and address.
            compose.onNodeWithText("Karlstad · Storgatan 14, 652 24 Karlstad").assertExists()
            // Passenger display on the phone itself.
            compose.onNodeWithContentDescription(s(R.string.open_display)).performClick()
            compose.onNodeWithText("Storfors").assertExists()
            compose.onNodeWithContentDescription(s(R.string.display_repeat)).assertExists()
            compose.onNodeWithContentDescription(s(R.string.display_exit)).performClick()
            compose.onNodeWithText(s(R.string.btn_next)).assertExists()
            // "Back" undoes the last "Next".
            compose.onNodeWithText(s(R.string.overlay_back)).performClick()
            compose.waitForIdle()
            assertEquals(2, app.graph.controller.route.value!!.stops.size)
            compose.onNodeWithText(s(R.string.active_counts, 0, 2)).assertExists()
        }
        app.graph.controller.end()
    }

    @Test
    fun youDriveScreenOpensFromHome() {
        app.graph.settings.update { it.copy(onboardingDone = true) }
        launch().use {
            waitText(s(R.string.youdrive_card_title))
            compose.onNodeWithText(s(R.string.youdrive_card_title)).performScrollTo().performClick()
            // One slim bar: watch bell, start page, reload and a menu; the page gets the rest.
            compose.onNodeWithContentDescription(s(R.string.youdrive_watch)).assertExists()
            compose.onNodeWithContentDescription(s(R.string.youdrive_start_page)).assertExists()
            compose.onNodeWithContentDescription(s(R.string.youdrive_more)).performClick()
            compose.onNodeWithText(s(R.string.youdrive_read_now)).assertExists()
            compose.onNodeWithText(s(R.string.youdrive_read_now)).performClick()
            compose.onNodeWithContentDescription(s(R.string.back)).performClick()
            compose.onNodeWithText(s(R.string.home_import)).assertExists()
        }
    }

    @Test
    fun activeRouteShowsCurrentStreetAndTimeStatus() {
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        app.graph.settings.update { it.copy(onboardingDone = true) }
        val now = java.time.LocalTime.now().plusMinutes(30)
        app.graph.controller.addManual("Storgatan 14, 65224 Karlstad", String.format(java.util.Locale.ROOT, "%02d:%02d", now.hour, now.minute))
        settleGeocoding()
        app.graph.controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        launch().use {
            waitText(s(R.string.street_label))
            compose.onNodeWithText(s(R.string.street_unknown)).assertExists()
            assertTrue("route screen asks for the street", app.graph.street.isWanted)
            // 30 min ahead (29 if a minute boundary passed meanwhile).
            val shown = listOf(30, 29).any { m -> compose.onAllNodesWithText(s(R.string.time_in_min, m.toString())).fetchSemanticsNodes().isNotEmpty() }
            assertTrue("time status shown", shown)
        }
        app.graph.controller.end()
    }
}
