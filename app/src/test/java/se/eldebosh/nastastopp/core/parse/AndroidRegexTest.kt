package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android's regular expressions (ICU) refuse some flags the computer's (the JVM's, which the
 * tests run on) accept: an inline `U` ("(?U)", "(?iU)") compiles here and crashes on the phone.
 * No pattern in the app may use it; Android's `(?i)` and `\b` already know å, ä and ö.
 */
class AndroidRegexTest {

    @Test
    fun noPatternUsesAFlagAndroidRefuses() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val flag = Regex("""\(\?[a-zA-Z]*U[a-zA-Z]*[):]""")
        val found = root.walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> if (flag.containsMatchIn(line)) "${file.name}:${i + 1}" else null }
        }.toList()
        assertTrue("Inline U flag (Android refuses it): $found", found.isEmpty())
    }
}
