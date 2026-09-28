package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** Real OCR output is messy: the parser must never throw, whatever the line looks like. */
class AddressExtractorFuzzTest {

    private val extractor = AddressExtractor(TestLocalities.instance)
    private val alphabet = "abcdefghijklmnopqrstuvwxyzåäöéüABCDEFGHIJKLMNOPQRSTUVWXYZÅÄÖ0123456789     ,.;:-–/\\|()[]+*#@&'\"…·•% \t"
    private val seeds = listOf(
        "ANDERSSON STORGATAN 14, 65224 KARLSTAD", "BJÖRKVÄGEN 7 LGH 1102, 66341 HAMMARÖ…", "LINDVÄGEN 9, 66430 GRU…",
        "Storgatan 14, 652 2O Karlstad", "Stadsbiblioteket, Karlstad", "c/o Svensson Storgatan 14 vån 3, 65224 Karlstad",
        "12:48 Pick-up", "Compensation 52.5 KR", "SP1, HLI", "Karl Johans gata 5", "0701234567", "…", "...", ",", "c/o",
        "port", "lgh", "Kil", "65224", "Storgatan 14 uppg B", "Upplands Väsby", "S:t Eriksgatan 12 A, 112 39 Stockholm",
    )

    private fun check(line: String) {
        try {
            extractor.parse(line)
            extractor.parse(line, joined = true)
            extractor.parse(line, manual = true)
            extractor.fromManualText(line)
            extractor.extract(listOf(line, line.reversed(), line))
        } catch (e: Exception) {
            fail("parser threw ${e.javaClass.simpleName} for line \"$line\": ${e.message}")
        }
    }

    @Test
    fun randomLinesNeverThrow() {
        val rnd = Random(20260928)
        repeat(20_000) {
            val len = rnd.nextInt(0, 60)
            check(buildString { repeat(len) { append(alphabet[rnd.nextInt(alphabet.length)]) } })
        }
    }

    @Test
    fun mutatedAddressesNeverThrow() {
        val rnd = Random(7)
        repeat(20_000) {
            val base = StringBuilder(seeds[rnd.nextInt(seeds.size)])
            repeat(rnd.nextInt(1, 6)) {
                when (rnd.nextInt(4)) {
                    0 -> if (base.isNotEmpty()) base.deleteAt(rnd.nextInt(base.length))
                    1 -> base.insert(rnd.nextInt(base.length + 1), alphabet[rnd.nextInt(alphabet.length)])
                    2 -> if (base.isNotEmpty()) base.setCharAt(rnd.nextInt(base.length), alphabet[rnd.nextInt(alphabet.length)])
                    else -> base.append(seeds[rnd.nextInt(seeds.size)].take(rnd.nextInt(0, 12)))
                }
            }
            check(base.toString())
        }
    }
}
