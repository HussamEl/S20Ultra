package se.eldebosh.nastastopp.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail

data class AppSettings(
    /** "ar" (default), "sv" or "en". */
    val uiLanguage: String = "ar",
    val detail: AnnouncementDetail = AnnouncementDetail.DISTRICT,
    val englishRepeat: Boolean = false,
    val speechRate: Float = 0.9f,
    val arrivalRadiusM: Int = 75,
    val onboardingDone: Boolean = false,
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
    )

    companion object {
        const val PREFS = "settings"
        const val K_LANG = "ui_language"
        private const val K_DETAIL = "announcement_detail"
        private const val K_EN = "english_repeat"
        private const val K_RATE = "speech_rate"
        private const val K_RADIUS = "arrival_radius"
        private const val K_ONBOARD = "onboarding_done"
        private const val K_OX = "overlay_x"
        private const val K_OY = "overlay_y"

        /** Read synchronously in attachBaseContext (before the Application graph is needed). */
        fun readLanguage(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LANG, "ar") ?: "ar"
    }
}
