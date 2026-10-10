package se.eldebosh.nastastopp.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.display.MotionStyle
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail
import se.eldebosh.nastastopp.core.nav.WaySource

/** What this device is used for. Every device can be either; the user picks it in the app. */
enum class DeviceRole {
    /** Full control: import, review, route, GPS, announcements, Maps. */
    CONTROLLER,

    /** Passenger display: mirrors a controller over Bluetooth. */
    DISPLAY,
}

/** The app's look: day (YouDrive's light colours), night (dark), or following the phone. */
enum class Appearance { DAY, NIGHT, AUTOMATIC }

data class AppSettings(
    /** "en" (the default), "ar" or "sv". */
    val uiLanguage: String = "en",
    /** Explanations (hints, help, onboarding) in Arabic whatever the UI language (during set-up). */
    val explanationsArabic: Boolean = true,
    /** Small reference numbers on every control (hidden by default), to point at one by number. */
    val showRefNumbers: Boolean = false,
    val detail: AnnouncementDetail = AnnouncementDetail.FULL,
    val englishRepeat: Boolean = false,
    val speechRate: Float = 0.9f,
    val onboardingDone: Boolean = false,
    val role: DeviceRole = DeviceRole.CONTROLLER,
    /** Controller: accept passenger displays over Bluetooth. */
    val displayLinkEnabled: Boolean = false,
    /** Passenger display shows the street address with the house number (off = area only). */
    val displayFullAddress: Boolean = true,
    /** Display role: speak the announcements on this device too, when the driver taps Next. */
    val displaySpeaks: Boolean = true,
    /** Display role: show the phone's floating panel on this tablet too (Next and Back for the phone). */
    val tabletPanel: Boolean = false,
    /** The passenger display on this device is black (on) or light (223). */
    val displayDark: Boolean = true,
    /** Display role: the look of the motion band on the top line (297), switched by a long press there. */
    val motionStyle: MotionStyle = MotionStyle.TRAILS,
    /**
     * Display role: the driver's own Google Maps key for the map on this tablet, or null. His own
     * keys (this and [mapmapKey]) are used only while the tablet is not connected to the company's
     * server; connected, they stay here unused. The tablet's pass to the server is never kept in
     * these settings (settings/CompanyDevice).
     */
    val mapsKey: String? = null,
    /** Display role: who gives this tablet's map its ways and travel times (304). */
    val waySource: WaySource = WaySource.MAPMAP,
    /** Display role: the driver's own mapmap.ai key for the ways on this tablet, or null. */
    val mapmapKey: String? = null,
    /** Display role: the weather app's widget shown on this tablet's display, or -1 for SMHI's weather. */
    val weatherWidgetId: Int = -1,
    /** Display role: Bluetooth address of the controller device to connect to. */
    val displayControllerAddress: String? = null,
    /** Floating button hidden by the driver (can be shown again from the app). */
    val overlayHidden: Boolean = false,
    /** Floating button shrunk to a small bubble (tap it to expand). */
    val overlayMinimized: Boolean = false,
    /** How long finished trips stay in the history on the Home screen (12 h, 24 h or 7 days). */
    val historyRetentionHours: Int = 12,
    /** Keep the YouDrive page open in the background and alert when trips are added or cancelled. */
    val youDriveWatch: Boolean = false,
    /** Say the street's name each time the vehicle turns into another. */
    val sayStreetChanges: Boolean = true,
    /** Sign in to YouDrive by itself when its login form shows (needs a saved login). */
    val youDriveAutoSignIn: Boolean = false,
    /** Day, night or automatic (the phone's dark mode). */
    val appearance: Appearance = Appearance.DAY,
)

/** Small settings store on SharedPreferences (no addresses are ever stored here). */
class SettingsStore(context: Context) : WindowPlaces {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit {
            putString(K_LANG, next.uiLanguage)
            putString(K_DETAIL, next.detail.name)
            putBoolean(K_EN, next.englishRepeat)
            putFloat(K_RATE, next.speechRate)
            putBoolean(K_ONBOARD, next.onboardingDone)
            putString(K_ROLE, next.role.name)
            putBoolean(K_LINK, next.displayLinkEnabled)
            putBoolean(K_FULL_ADDR, next.displayFullAddress)
            putBoolean(K_DISPLAY_SPEAKS, next.displaySpeaks)
            putBoolean(K_TABLET_PANEL, next.tabletPanel)
            putBoolean(K_DISPLAY_DARK, next.displayDark)
            putString(K_MOTION_STYLE, next.motionStyle.name)
            putString(K_MAPS_KEY, next.mapsKey)
            putString(K_WAY_SOURCE, next.waySource.name)
            putString(K_MAPMAP_KEY, next.mapmapKey)
            putInt(K_WEATHER_WIDGET, next.weatherWidgetId)
            putString(K_CONTROLLER, next.displayControllerAddress)
            putBoolean(K_OVERLAY_HIDDEN, next.overlayHidden)
            putBoolean(K_OVERLAY_MIN, next.overlayMinimized)
            putInt(K_HISTORY_HOURS, next.historyRetentionHours)
            putBoolean(K_EXPLAIN_AR, next.explanationsArabic)
            putBoolean(K_REF_NUMBERS, next.showRefNumbers)
            putBoolean(K_YD_WATCH, next.youDriveWatch)
            putBoolean(K_YD_AUTO, next.youDriveAutoSignIn)
            putBoolean(K_SAY_STREET, next.sayStreetChanges)
            putString(K_APPEARANCE, next.appearance.name)
        }
        _state.value = next
    }

    fun overlayPosition(): Pair<Int, Int>? {
        if (!prefs.contains(K_OX)) return null
        return prefs.getInt(K_OX, 0) to prefs.getInt(K_OY, 0)
    }

    fun setOverlayPosition(x: Int, y: Int) = prefs.edit { putInt(K_OX, x); putInt(K_OY, y) }

    /** The floating panel's window size (pixels) as the driver left it, or null until he sizes it. */
    fun overlaySize(): Pair<Int, Int>? {
        if (!prefs.contains(K_OW)) return null
        return prefs.getInt(K_OW, 0) to prefs.getInt(K_OH, 0)
    }

    fun setOverlaySize(width: Int, height: Int) = prefs.edit { putInt(K_OW, width); putInt(K_OH, height) }

    override fun place(name: String): WindowPlace? {
        if (!prefs.contains(K_WINDOW + name + "_x")) return null
        return WindowPlace(
            prefs.getFloat(K_WINDOW + name + "_x", 0.5f),
            prefs.getFloat(K_WINDOW + name + "_y", 0.5f),
            prefs.getFloat(K_WINDOW + name + "_w", 0f),
            prefs.getFloat(K_WINDOW + name + "_h", 0f),
        )
    }

    override fun keep(name: String, place: WindowPlace) = prefs.edit {
        putFloat(K_WINDOW + name + "_x", place.x)
        putFloat(K_WINDOW + name + "_y", place.y)
        putFloat(K_WINDOW + name + "_w", place.width)
        putFloat(K_WINDOW + name + "_h", place.height)
        remove(K_WINDOW + name + "_scale")
    }

    private fun read() = AppSettings(
        uiLanguage = prefs.getString(K_LANG, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE,
        explanationsArabic = prefs.getBoolean(K_EXPLAIN_AR, true),
        showRefNumbers = prefs.getBoolean(K_REF_NUMBERS, false),
        youDriveWatch = prefs.getBoolean(K_YD_WATCH, false),
        youDriveAutoSignIn = prefs.getBoolean(K_YD_AUTO, false),
        sayStreetChanges = prefs.getBoolean(K_SAY_STREET, true),
        detail = runCatching { AnnouncementDetail.valueOf(prefs.getString(K_DETAIL, null) ?: "") }
            .getOrDefault(AnnouncementDetail.FULL),
        englishRepeat = prefs.getBoolean(K_EN, false),
        speechRate = prefs.getFloat(K_RATE, 0.9f),
        onboardingDone = prefs.getBoolean(K_ONBOARD, false),
        role = runCatching { DeviceRole.valueOf(prefs.getString(K_ROLE, null) ?: "") }.getOrDefault(DeviceRole.CONTROLLER),
        displayLinkEnabled = prefs.getBoolean(K_LINK, false),
        displayFullAddress = prefs.getBoolean(K_FULL_ADDR, true),
        displaySpeaks = prefs.getBoolean(K_DISPLAY_SPEAKS, true),
        tabletPanel = prefs.getBoolean(K_TABLET_PANEL, false),
        displayDark = prefs.getBoolean(K_DISPLAY_DARK, true),
        motionStyle = runCatching { MotionStyle.valueOf(prefs.getString(K_MOTION_STYLE, null) ?: "") }.getOrDefault(MotionStyle.TRAILS),
        mapsKey = prefs.getString(K_MAPS_KEY, null),
        waySource = runCatching { WaySource.valueOf(prefs.getString(K_WAY_SOURCE, null) ?: "") }.getOrDefault(WaySource.MAPMAP),
        mapmapKey = prefs.getString(K_MAPMAP_KEY, null),
        weatherWidgetId = prefs.getInt(K_WEATHER_WIDGET, -1),
        displayControllerAddress = prefs.getString(K_CONTROLLER, null),
        overlayHidden = prefs.getBoolean(K_OVERLAY_HIDDEN, false),
        overlayMinimized = prefs.getBoolean(K_OVERLAY_MIN, false),
        historyRetentionHours = prefs.getInt(K_HISTORY_HOURS, 12),
        appearance = runCatching { Appearance.valueOf(prefs.getString(K_APPEARANCE, null) ?: "") }.getOrDefault(Appearance.DAY),
    )

    companion object {
        const val PREFS = "settings"
        const val K_LANG = "ui_language"
        private const val K_DETAIL = "announcement_detail"
        private const val K_EN = "english_repeat"
        private const val K_RATE = "speech_rate"
        private const val K_ONBOARD = "onboarding_done"
        private const val K_ROLE = "device_role"
        private const val K_LINK = "display_link_enabled"
        private const val K_FULL_ADDR = "display_full_address"
        private const val K_DISPLAY_SPEAKS = "display_speaks_announcements"
        private const val K_TABLET_PANEL = "tablet_floating_panel"
        private const val K_DISPLAY_DARK = "display_dark"
        private const val K_MAPS_KEY = "tablet_maps_key"
        private const val K_WAY_SOURCE = "tablet_way_source"
        private const val K_MOTION_STYLE = "tablet_motion_style"
        private const val K_MAPMAP_KEY = "tablet_mapmap_key"
        private const val K_WEATHER_WIDGET = "tablet_weather_widget"
        private const val K_CONTROLLER = "display_controller_address"
        private const val K_OVERLAY_HIDDEN = "overlay_hidden"
        private const val K_OVERLAY_MIN = "overlay_minimized"
        private const val K_HISTORY_HOURS = "history_retention_hours"
        private const val K_OX = "overlay_x"
        private const val K_OY = "overlay_y"
        private const val K_OW = "overlay_w"
        private const val K_OH = "overlay_h"
        private const val K_WINDOW = "window_"
        private const val K_EXPLAIN_AR = "explanations_arabic"
        private const val K_REF_NUMBERS = "show_ref_numbers"
        private const val K_YD_WATCH = "youdrive_watch"
        private const val K_YD_AUTO = "youdrive_auto_sign_in"
        private const val K_SAY_STREET = "say_street_changes"
        private const val K_APPEARANCE = "appearance"
        private const val DEFAULT_LANGUAGE = "en"

        /** Read synchronously in attachBaseContext (before the Application graph is needed). */
        fun readLanguage(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getString(K_LANG, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        }
    }
}
