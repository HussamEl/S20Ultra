package se.eldebosh.nastastopp.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail

/** What this device is used for. Every device can be either; the user picks it in the app. */
enum class DeviceRole {
    /** Full control: import, review, route, GPS, announcements, Maps. */
    CONTROLLER,

    /** Passenger display: mirrors a controller over Bluetooth. */
    DISPLAY,
}

data class AppSettings(
    /** "ar" (default), "sv" or "en". */
    val uiLanguage: String = "ar",
    val detail: AnnouncementDetail = AnnouncementDetail.DISTRICT,
    val englishRepeat: Boolean = false,
    val speechRate: Float = 0.9f,
    val arrivalRadiusM: Int = 75,
    val onboardingDone: Boolean = false,
    val role: DeviceRole = DeviceRole.CONTROLLER,
    /** Controller: accept passenger displays over Bluetooth. */
    val displayLinkEnabled: Boolean = false,
    /** Passenger display shows the full address under the area name (off = area only, private). */
    val displayFullAddress: Boolean = false,
    /** Display role: also speak announcements on this device. */
    val displaySpeaks: Boolean = false,
    /** Display role: Bluetooth address of the controller device to connect to. */
    val displayControllerAddress: String? = null,
    /** Floating button hidden by the driver (can be shown again from the app). */
    val overlayHidden: Boolean = false,
    /** Floating button shrunk to a small bubble (tap it to expand). */
    val overlayMinimized: Boolean = false,
    /** How long finished trips stay in the history on the Home screen (12 h, 24 h or 7 days). */
    val historyRetentionHours: Int = 12,
)

/** Small settings store on SharedPreferences (no addresses are ever stored here). */
class SettingsStore(context: Context) {
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
            putInt(K_RADIUS, next.arrivalRadiusM)
            putBoolean(K_ONBOARD, next.onboardingDone)
            putString(K_ROLE, next.role.name)
            putBoolean(K_LINK, next.displayLinkEnabled)
            putBoolean(K_FULL_ADDR, next.displayFullAddress)
            putBoolean(K_DISPLAY_SPEAKS, next.displaySpeaks)
            putString(K_CONTROLLER, next.displayControllerAddress)
            putBoolean(K_OVERLAY_HIDDEN, next.overlayHidden)
            putBoolean(K_OVERLAY_MIN, next.overlayMinimized)
            putInt(K_HISTORY_HOURS, next.historyRetentionHours)
        }
        _state.value = next
    }

    fun overlayPosition(): Pair<Int, Int>? {
        if (!prefs.contains(K_OX)) return null
        return prefs.getInt(K_OX, 0) to prefs.getInt(K_OY, 0)
    }

    fun setOverlayPosition(x: Int, y: Int) = prefs.edit { putInt(K_OX, x); putInt(K_OY, y) }

    private fun read() = AppSettings(
        uiLanguage = prefs.getString(K_LANG, "ar") ?: "ar",
        detail = runCatching { AnnouncementDetail.valueOf(prefs.getString(K_DETAIL, null) ?: "") }
            .getOrDefault(AnnouncementDetail.DISTRICT),
        englishRepeat = prefs.getBoolean(K_EN, false),
        speechRate = prefs.getFloat(K_RATE, 0.9f),
        arrivalRadiusM = prefs.getInt(K_RADIUS, 75),
        onboardingDone = prefs.getBoolean(K_ONBOARD, false),
        role = runCatching { DeviceRole.valueOf(prefs.getString(K_ROLE, null) ?: "") }.getOrDefault(DeviceRole.CONTROLLER),
        displayLinkEnabled = prefs.getBoolean(K_LINK, false),
        displayFullAddress = prefs.getBoolean(K_FULL_ADDR, false),
        displaySpeaks = prefs.getBoolean(K_DISPLAY_SPEAKS, false),
        displayControllerAddress = prefs.getString(K_CONTROLLER, null),
        overlayHidden = prefs.getBoolean(K_OVERLAY_HIDDEN, false),
        overlayMinimized = prefs.getBoolean(K_OVERLAY_MIN, false),
        historyRetentionHours = prefs.getInt(K_HISTORY_HOURS, 12),
    )

    companion object {
        const val PREFS = "settings"
        const val K_LANG = "ui_language"
        private const val K_DETAIL = "announcement_detail"
        private const val K_EN = "english_repeat"
        private const val K_RATE = "speech_rate"
        private const val K_RADIUS = "arrival_radius"
        private const val K_ONBOARD = "onboarding_done"
        private const val K_ROLE = "device_role"
        private const val K_LINK = "display_link_enabled"
        private const val K_FULL_ADDR = "display_full_address"
        private const val K_DISPLAY_SPEAKS = "display_speaks"
        private const val K_CONTROLLER = "display_controller_address"
        private const val K_OVERLAY_HIDDEN = "overlay_hidden"
        private const val K_OVERLAY_MIN = "overlay_minimized"
        private const val K_HISTORY_HOURS = "history_retention_hours"
        private const val K_OX = "overlay_x"
        private const val K_OY = "overlay_y"

        /** Read synchronously in attachBaseContext (before the Application graph is needed). */
        fun readLanguage(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LANG, "ar") ?: "ar"
    }
}
