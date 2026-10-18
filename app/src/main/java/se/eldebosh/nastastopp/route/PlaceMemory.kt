package se.eldebosh.nastastopp.route

import android.content.Context
import androidx.core.content.edit
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
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

/**
 * [e] with the town the driver once gave its place, when it is written without one: what he
 * typed is read again ([extractor]) and asked first. Any other stop as it is.
 */
fun PlaceMemory.withTown(e: ExtractedStop, extractor: AddressExtractor): ExtractedStop {
    if (e.parsedTown != null || e.parsedPostalCode != null || !PlaceMemory.isPlace(e.displayText)) return e
    val given = recall(e.place ?: e.displayText)?.let { extractor.fromManualText(it) } ?: return e
    if (given.parsedTown == null && given.parsedPostalCode == null) return e
    return e.copy(
        candidates = (given.candidates + e.candidates).distinct(),
        parsedTown = given.parsedTown,
        parsedPostalCode = given.parsedPostalCode,
        parsedTownKnown = given.parsedTownKnown,
    )
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
