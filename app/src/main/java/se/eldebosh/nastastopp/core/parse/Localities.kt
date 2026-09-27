package se.eldebosh.nastastopp.core.parse

/**
 * Bundled list of Swedish localities (all 290 municipalities plus common tätorter).
 * Lookups are case- and diacritic-insensitive; [canonical] returns the display spelling.
 */
class Localities(names: Collection<String>) {

    private val byKey: Map<String, String>

    /** Longest locality name in words (e.g. "Upplands Väsby" = 2). */
    val maxWords: Int

    init {
        val map = HashMap<String, String>()
        var words = 1
        for (raw in names) {
            val name = raw.trim()
            if (name.isEmpty() || name.startsWith("#")) continue
            map.putIfAbsent(TextNorm.fold(name), name)
            words = maxOf(words, TextNorm.fold(name).split(' ').size)
        }
        byKey = map
        maxWords = words
    }

    val size: Int get() = byKey.size

    fun contains(text: String): Boolean = canonical(text) != null

    /** Display spelling if the whole [text] is a known locality, else null. */
    fun canonical(text: String): String? {
        val folded = TextNorm.fold(text.trim().trim(',', '.', ':', ';'))
        if (folded.isEmpty()) return null
        return byKey[folded]
    }

    /**
     * If the text *starts* with a known locality (1..[maxWords] words), returns the number of
     * words it spans; otherwise 0. Longest match wins.
     */
    fun prefixWords(text: String): Int {
        val words = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        for (n in minOf(maxWords, words.size) downTo 1) {
            if (canonical(words.take(n).joinToString(" ")) != null) return n
        }
        return 0
    }

    /**
     * If the text *ends* with a known locality (1..[maxWords] words), returns the number of
     * words it spans; otherwise 0. Longest match wins.
     */
    fun suffixWords(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (n in minOf(maxWords, words.size) downTo 1) {
            if (canonical(words.takeLast(n).joinToString(" ")) != null) return n
        }
        return 0
    }

    companion object {
        fun parse(fileContent: String): Localities =
            Localities(fileContent.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList())
    }
}
