package se.eldebosh.nastastopp.core.youdrive

/**
 * A trip card's instructions ([TripCardText.INSTRUCTIONS]) as lines to read at a glance: one for
 * each thing the dispatcher wrote, and every phone number on a line of its own, from its start.
 *
 * The text is cut where the dispatcher cut it: at its line ends, at a "/" with a space beside it
 * (or at the edge of a line, or doubled: "resan/ / Är", " // "), and after a sentence's full stop.
 * A "/" between two words or figures ("och/eller", "1/2") stays. Every word is kept, as written.
 *
 * A number is told by its form, a Swedish phone number: 0 or +46, then 8 to 10 figures in all,
 * with spaces or dashes between them ("070-000 00 01"). Whose number it is goes after it, when
 * that is clear: one or two words of letters right before it ("Dotter 070…" → "070… Dotter"), or,
 * when the line starts with a number, right after it ("070… dotter"); a word between two numbers
 * goes after the second ("… alt 070…" → "070… alt"). Anything longer stays a line of its own,
 * before or after the number, as written.
 */
object CardNotes {

    /** One line of the instructions. */
    sealed interface Line {
        /** Words, as written. */
        data class Words(val text: String) : Line

        /** A phone number as written, and whose it is ([label], as written) when that is clear. */
        data class Phone(val number: String, val label: String? = null) : Line
    }

    fun of(text: String): List<Line> = parts(text).flatMap { lines(it) }

    /** The text cut where the dispatcher cut it: line ends, a "/" with a space or an edge beside it, a full stop. */
    internal fun parts(text: String): List<String> =
        text.lines()
            .flatMap { it.split(CUT) }
            .flatMap { it.split(SENTENCE) }
            .map { it.trim().trim('/').trim() }
            .filter { it.isNotEmpty() }

    /** One part: its words and its numbers, each number on a line of its own. */
    private fun lines(part: String): List<Line> {
        val numbers = PHONE.findAll(part).toList()
        if (numbers.isEmpty()) return listOf(Line.Words(part))
        // The words before, between and after the numbers.
        val gaps = ArrayList<String>()
        var from = 0
        for (n in numbers) {
            gaps += part.substring(from, n.range.first).trim()
            from = n.range.last + 1
        }
        gaps += part.substring(from).trim()
        val out = ArrayList<Line>()
        if (gaps.first().isEmpty()) {
            // The line starts with a number: whose it is comes after it.
            numbers.forEachIndexed { i, n ->
                val after = gaps[i + 1]
                if (isLabel(after)) {
                    out += Line.Phone(n.value, label(after))
                } else {
                    out += Line.Phone(n.value)
                    if (after.isNotEmpty()) out += Line.Words(after)
                }
            }
        } else {
            // Whose a number is comes before it.
            numbers.forEachIndexed { i, n ->
                val before = gaps[i]
                if (isLabel(before)) {
                    out += Line.Phone(n.value, label(before))
                } else {
                    if (before.isNotEmpty()) out += Line.Words(before)
                    out += Line.Phone(n.value)
                }
            }
            gaps.last().takeIf { it.isNotEmpty() }?.let { out += Line.Words(it) }
        }
        return out
    }

    /** One or two words of letters (a colon or a dash after them is left out): whose a number is. */
    private fun isLabel(words: String): Boolean {
        val w = label(words)
        return w.isNotEmpty() && w.split(' ').size <= 2 && w.none { it.isDigit() } && w.any { it.isLetter() }
    }

    private fun label(words: String): String = words.trim().trimEnd(':', '-', ',', '–').trim()

    /** A "/" with a space on one side or the other, or at a line's edge, or two of them together. */
    private val CUT = Regex("""\s+/+\s*|/+\s+|^/+|/+$|//+""")

    /** After a sentence's full stop, or "!" or "?", when a capital letter starts the next one. */
    private val SENTENCE = Regex("""(?<=[.!?])\s+(?=\p{Lu})""")

    /** A Swedish phone number: 0 or +46, then 8 to 10 figures in all, spaces or dashes between them. */
    private val PHONE = Regex("""(?<![\d+])(?:\+46[ -]?|0)\d(?:[ -]?\d){6,8}(?!\d)""")
}
