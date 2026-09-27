package se.eldebosh.nastastopp.core.parse

import java.util.Locale

/** Small, dependency-free text helpers shared by the parser (pure Kotlin, unit-tested). */
object TextNorm {
    val SWEDISH: Locale = Locale.forLanguageTag("sv-SE")

    private val WHITESPACE = Regex("\\s+")

    /** Lower-case, strip Swedish/accents diacritics, hyphens to spaces, collapse whitespace. */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase(SWEDISH)) {
            sb.append(
                when (ch) {
                    'å', 'ä', 'á', 'à', 'â' -> 'a'
                    'ö', 'ø', 'ó', 'ò', 'ô' -> 'o'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'ü', 'ú', 'ù' -> 'u'
                    'í', 'ì', 'ï' -> 'i'
                    '-', '_' -> ' '
                    else -> ch
                },
            )
        }
        return sb.toString().replace(WHITESPACE, " ").trim()
    }

    /** Alphanumeric-only folded key, used for duplicate detection. */
    fun key(text: String): String = fold(text).filter { it.isLetterOrDigit() }

    fun collapseSpaces(text: String): String = text.replace(WHITESPACE, " ").trim()

    fun letterCount(text: String): Int = text.count { it.isLetter() }

    /** Levenshtein-based similarity in [0, 1] on folded text. */
    fun similarity(a: String, b: String): Double {
        val x = fold(a)
        val y = fold(b)
        if (x == y) return 1.0
        val max = maxOf(x.length, y.length)
        if (max == 0) return 1.0
        return 1.0 - levenshtein(x, y).toDouble() / max
    }

    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }
}
