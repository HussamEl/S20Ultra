package se.eldebosh.nastastopp.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * In-app UI language (English by default; Arabic RTL and Swedish available)
 * regardless of the phone's language.
 * Android 13+: per-app language via LocaleManager (covers activities, services, notifications).
 * In addition (and on Android 10–12) the Application and Activity base contexts are wrapped with
 * the same language, which is a no-op when the system already applied it.
 */
object LocaleHelper {
    val SUPPORTED = listOf("ar", "sv", "en")

    private fun normalize(language: String) = language.takeIf { it in SUPPORTED } ?: "en"

    fun wrap(base: Context, language: String): Context {
        // Android 13+: a per-app locale chosen in system settings (or set by us) wins.
        val lang = systemPerAppLanguage(base) ?: normalize(language)
        val locale = Locale.forLanguageTag(lang)
        val current = base.resources.configuration.locales[0]
        if (current != null && current.language == locale.language) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Explanation texts are shown in Arabic while the app is being set up (setting). */
    fun explanationContext(base: Context, uiLanguage: String, explanationsArabic: Boolean): Context =
        if (explanationsArabic && uiLanguage != "ar") localized(base, "ar") else base

    /** A context with [language] resources, whatever the app language. */
    fun localized(base: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Android 13+: makes sure the per-app locale matches the stored setting. */
    fun applyAppLocale(context: Context, language: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val lm = context.getSystemService(LocaleManager::class.java) ?: return
        val wanted = LocaleList.forLanguageTags(normalize(language))
        if (lm.applicationLocales.toLanguageTags() != wanted.toLanguageTags()) lm.applicationLocales = wanted
    }

    /** Android 13+: the language the user may have picked in system settings, if any. */
    fun systemPerAppLanguage(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val lm = context.getSystemService(LocaleManager::class.java) ?: return null
        val list = lm.applicationLocales
        if (list.isEmpty) return null
        return list[0].language.takeIf { it in SUPPORTED }
    }
}
