package se.eldebosh.nastastopp.core.parse

/**
 * A named place in a trip's address, as YouDrive writes it before the street or instead of one
 * ("Provby Vårdcentral Strandvägen 3", "Centralsjukhuset huvudentrén", "Provby Äldreboende …").
 *
 * - A place of care (a hospital, a health centre, public dental care) may be shown to the
 *   passengers and said, by the place's own name only ([publicName]): never a department or ward.
 * - Any other place (a care home, short-term housing, or a name not known as care) is never shown
 *   or said to the passengers: it stays on the driver's screens.
 * - A few well-known places ([KNOWN]) have a short name for the display, a spoken name and a town,
 *   so a place written without its town is still found in the right one.
 */
object Places {

    /** A well-known place: [short] on the passenger display, [spoken] aloud, in [town]. */
    data class Known(val word: String, val short: String, val spoken: String, val town: String) {
        /** What the geocoder is asked first. */
        val candidate: String get() = "$spoken, $town"
    }

    val KNOWN = listOf(
        Known(word = "centralsjukhuset", short = "C-Sjukhuset", spoken = "Centralsjukhuset", town = "Karlstad"),
    )

    /** [text] as it is written on the screens: a well-known place by its short name ("Centralsjukhuset Karlstad" → "C-Sjukhuset Karlstad"). */
    fun written(text: String): String = KNOWN.fold(text) { t, k -> t.replace(Regex("(?i)\\b${k.word}\\b"), k.short) }

    /** The well-known place named in [place], or null. */
    fun known(place: String?): Known? {
        val words = words(place ?: return null)
        return KNOWN.firstOrNull { k -> words.any { it == k.word } }
    }

    /**
     * The name the passengers may see and hear ("Kils Vårdcentral", "Centralsjukhuset"), or null
     * when [place] is not a place of care. Only the care word itself and the name before it (a
     * town, [isLocality], or a district: "Kils", "Herrhagens") are kept, never a word that tells a
     * department or treatment: a home or a department is never shown.
     */
    fun publicName(place: String?, isLocality: (String) -> Boolean): String? {
        val tokens = place?.trim()?.split(' ')?.filter { it.isNotEmpty() } ?: return null
        if (tokens.any { t -> HOME_WORDS.any { TextNorm.fold(t).contains(it) } }) return null
        val at = tokens.indexOfLast { t -> CARE_WORDS.any { TextNorm.fold(t).trimEnd(',', '.').contains(it) } }
        if (at < 0) return null
        val care = tokens[at].trimEnd(',', '.')
        val before = tokens.getOrNull(at - 1)?.trimEnd(',', '.')
        val named = before?.takeIf { b -> !isEntrance(b) }?.takeIf { b ->
            isLocality(b) || (b.endsWith("s") && isLocality(b.dropLast(1))) ||
                (b.first().isUpperCase() && b.all { it.isLetter() || it == '-' } && TREATMENT_WORDS.none { TextNorm.fold(b).contains(it) })
        }
        return listOfNotNull(named, care).joinToString(" ")
    }

    /**
     * The entrances written with the place, as written: every word that names an entrance
     * ("Huvudentrén", "Dialysentrén", "Entré 3", "Ingång B"). An entrance is a door, never a
     * department, so it goes with the place's name wherever the name is written or said.
     */
    fun entrances(place: String?): List<String> {
        val tokens = place?.trim()?.split(' ')?.filter { it.isNotEmpty() } ?: return emptyList()
        return tokens.indices.filter { isEntrance(tokens[it]) }.map { i ->
            val number = tokens.getOrNull(i + 1)?.trimEnd(',', '.')?.takeIf { it.isNotEmpty() && it.length <= 2 && (it.all(Char::isDigit) || it.all(Char::isUpperCase)) }
            listOfNotNull(tokens[i].trimEnd(',', '.'), number).joinToString(" ")
        }
    }

    /** The entrances as said ("huvudentrén", "dialysentrén"), lower case, or null. */
    fun entrance(place: String?): String? = entrances(place).joinToString(", ").lowercase(TextNorm.SWEDISH).ifEmpty { null }

    private fun isEntrance(word: String): Boolean = TextNorm.fold(word).trimEnd(',', '.').let { w -> ENTRANCE_WORDS.any { w.endsWith(it) } }

    /**
     * How a place of care is said: "Centralsjukhuset, huvudentrén" (a well-known place by its
     * spoken name), "Kils Vårdcentral"; null when it is not one.
     */
    fun spokenName(place: String?, isLocality: (String) -> Boolean): String? {
        val name = known(place)?.spoken ?: publicName(place, isLocality) ?: return null
        return listOfNotNull(name, entrance(place)).joinToString(", ")
    }

    /**
     * How a place of care is written on the passenger display: a well-known one by its short name,
     * with its entrances as written ("C-Sjukhuset Dialysentrén").
     */
    fun displayName(place: String?, isLocality: (String) -> Boolean): String? {
        val name = publicName(place, isLocality) ?: return null
        return (listOf(known(place)?.short ?: name) + entrances(place)).joinToString(" ")
    }

    private fun words(text: String): List<String> = TextNorm.fold(text).split(' ', ',', '.').filter { it.isNotEmpty() }

    /** Folded parts of a place-of-care word ("vårdcentralen", "centralsjukhuset", "Arvika sjukhus"). */
    private val CARE_WORDS = listOf("sjukhus", "lasarett", "vardcentral", "halsocentral", "tandvard")

    /** Folded parts of a word for where people live: such a place is never shown to the passengers. */
    private val HOME_WORDS = listOf("boende", "servicehus", "hemtjanst", "lss", "gruppbostad", "demens")

    /** Folded parts of a word that tells a department or treatment: never shown with a place's name. */
    private val TREATMENT_WORDS = listOf(
        "psyk", "beroende", "missbruk", "onkolog", "cancer", "dialys", "abort", "kvinno", "ungdom",
        "hiv", "smitt", "habilit", "rehab", "minne", "geriatri", "avd",
    )

    /** Folded endings of an entrance word. */
    private val ENTRANCE_WORDS = listOf("entren", "entre", "ingang", "ingangen")
}
