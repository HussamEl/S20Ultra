package se.eldebosh.nastastopp.route

import android.content.Context
import androidx.core.content.edit
import se.eldebosh.nastastopp.core.parse.TextNorm

/**
 * Where the driver put a place that YouDrive writes without a town: once the driver has added the
 * town to "Sjukhuset Huvudentrén", the same place comes with it next time. Only places without a
 * house number are kept (a hospital, a health centre), never a home's address or a name; in
 * app-private storage, never backed up or logged.
 */
interface PlaceMemory {
    /** The address the driver gave [place] ("Sjukhuset Huvudentrén, Karlstad"), or null. */
    fun recall(place: String): String?

    fun remember(place: String, address: String)

    /** Remembers nothing (tests). */
    object None : PlaceMemory {
        override fun recall(place: String): String? = null

        override fun remember(place: String, address: String) = Unit
    }

    companion object {
        /** A place that may be remembered: letters, no house number. */
        fun isPlace(text: String): Boolean = text.none { it.isDigit() } && TextNorm.letterCount(text) >= 4

        fun key(place: String): String = TextNorm.key(place)
    }
}

/** [PlaceMemory] in the app's own preferences. */
class StoredPlaceMemory(context: Context) : PlaceMemory {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun recall(place: String): String? = prefs.getString(PlaceMemory.key(place), null)

    override fun remember(place: String, address: String) {
        if (!PlaceMemory.isPlace(place)) return
        prefs.edit { putString(PlaceMemory.key(place), address.trim()) }
    }

    private companion object {
        const val FILE = "places"
    }
}
