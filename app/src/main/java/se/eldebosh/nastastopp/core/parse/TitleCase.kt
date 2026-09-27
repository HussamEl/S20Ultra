package se.eldebosh.nastastopp.core.parse

/**
 * Title Case for Swedish addresses: "ANDERSSON STORGATAN 14" → "Andersson Storgatan 14",
 * "3b" → "3B", "12 a" → "12 A", keeps å ä ö. A standalone street-suffix word after another
 * word stays lower case, as in Swedish ("KARL JOHANS GATA 5" → "Karl Johans gata 5").
 */
object TitleCase {

    private val HOUSE_WITH_LETTER = Regex("\\d{1,4}[\\p{L}]")
    private val SINGLE_LETTER = Regex("[\\p{L}]")

    fun apply(text: String): String = applyTokens(text.split(' ').filter { it.isNotEmpty() }).joinToString(" ")

    fun applyTokens(tokens: List<String>): List<String> = tokens.mapIndexed { i, tok ->
        val core = tok.trim(',', '.', ';', ':', '(', ')')
        val prevCore = tokens.getOrNull(i - 1)?.trim(',', '.', ';', ':', '(', ')')
        when {
            HOUSE_WITH_LETTER.matches(core) -> tok.uppercase(TextNorm.SWEDISH)
            SINGLE_LETTER.matches(core) && prevCore != null && prevCore.all { it.isDigit() } && prevCore.isNotEmpty() ->
                tok.uppercase(TextNorm.SWEDISH)
            i > 0 && isStandaloneSuffix(core) && prevCore?.any { it.isLetter() } == true ->
                tok.lowercase(TextNorm.SWEDISH)
            else -> capitalizeWord(tok)
        }
    }

    private fun isStandaloneSuffix(word: String): Boolean =
        AddressExtractor.STREET_SUFFIXES.any { it == word.lowercase(TextNorm.SWEDISH) }

    /** Upper-cases the first letter of the word and of each hyphen part; the rest lower-case. */
    private fun capitalizeWord(word: String): String {
        val sb = StringBuilder(word.length)
        var startOfPart = true
        for (ch in word) {
            if (ch.isLetter()) {
                sb.append(if (startOfPart) ch.uppercaseChar() else ch.lowercaseChar())
                startOfPart = false
            } else {
                sb.append(ch)
                // A new part starts after a hyphen or opening punctuation, not after ":" ("S:t").
                startOfPart = ch == '-' || ch == '(' || ch == '"' || (ch.isDigit() && startOfPart)
            }
        }
        return sb.toString()
    }
}
