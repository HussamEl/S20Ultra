package se.eldebosh.nastastopp.core.youdrive

import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.core.parse.TripKinds
import se.eldebosh.nastastopp.core.parse.TripTimes

/**
 * A YouDrive trip card's text laid out the way YouDrive's own details window shows it: the kind
 * and time on top ([title]), the passenger's [name], the [estimated] and [negotiated] times, the
 * [kind] and [status], then one row per field in YouDrive's order: Address, Phone number, Space
 * Type(s), Mobility Aids, Fare amount, Compensation, Eligibility, Instructions.
 *
 * Every word of the card is kept, as written; only the page's own controls ("Arrive", "27 min")
 * are left out. The trip's codes are written out as YouDrive's details write them, with their
 * number, which counts them ("RU1" → "Rullstol 1": one wheelchair); a code not known yet stays as
 * it is.
 */
data class TripCardText(
    val title: String?,
    val name: String?,
    val estimated: String?,
    val negotiated: String?,
    val kind: String?,
    val status: String?,
    val rows: List<Row>,
) {
    /** One line of the card: [label] (as YouDrive names it) and [value], or a line of its own. */
    data class Row(val label: String?, val value: String)

    companion object {
        const val ADDRESS = "Address"
        const val PHONE = "Phone number"
        const val SPACE = "Space Type(s)"
        const val AIDS = "Mobility Aids"
        const val FARE = "Fare amount"
        const val COMPENSATION = "Compensation"
        const val ELIGIBILITY = "Eligibility"
        const val INSTRUCTIONS = "Instructions"

        fun of(card: String): TripCardText {
            val lines = card.lines().map { it.trim() }.filter { it.isNotEmpty() && !isControl(it) }
            // The left column: the times, the kind and the status, in the card's order.
            val times = ArrayList<String>()
            var kind: String? = null
            var status: String? = null
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                when {
                    kind == null && TripKinds.labelIn(line) != null -> kind = line
                    times.size < 2 && isTime(line) -> times += TripTimes.timeIn(line)!!
                    status == null && TextNorm.fold(line).filter { it.isLetter() } in STATUSES -> status = line
                    // An icon's name the page writes as text ("schedule"): not part of the trip.
                    ICON.matches(line) -> Unit
                    else -> break
                }
                i++
            }
            val rest = lines.drop(i)
            // The right column: the name first (none on the depot's cards), then the address on one
            // or two lines, then the fields.
            var j = 0
            val name = rest.firstOrNull()?.takeIf { isName(it) }
            if (name != null) j++
            val address = ArrayList<String>()
            while (j < rest.size && address.size < 2 && !isField(rest[j])) address += rest[j++]

            val phones = ArrayList<String>()
            val space = ArrayList<String>()
            val aids = ArrayList<String>()
            var fare: String? = null
            var compensation: String? = null
            var eligibility: String? = null
            val instructions = ArrayList<String>()
            val more = ArrayList<Row>()
            for (line in rest.drop(j)) {
                val folded = TextNorm.fold(line)
                val paid = fare != null || compensation != null
                when {
                    eligibility == null && !paid && space.isEmpty() && isPhone(line) -> phones += line
                    eligibility == null && !paid && space.isEmpty() && aids.isEmpty() && isCodes(line) ->
                        codes(line).forEach { c -> aid(c)?.let { aids += it } ?: space.add(spaceType(c) ?: c) }
                    folded.startsWith("client fee") -> fare = line.substring("client fee".length).trim()
                    folded.startsWith("compensation") -> compensation = line.substring("compensation".length).trim()
                    eligibility == null && line in ELIGIBILITIES -> eligibility = line
                    eligibility == null && line.contains(':') -> more += Row(line.substringBefore(':').trim().ifEmpty { null }, line.substringAfter(':').trim())
                    else -> instructions += line
                }
            }
            val rows = buildList {
                if (address.isNotEmpty()) add(Row(ADDRESS, address.joinToString("\n")))
                phones.forEach { add(Row(PHONE, it)) }
                if (space.isNotEmpty()) add(Row(SPACE, space.joinToString(", ")))
                if (aids.isNotEmpty()) add(Row(AIDS, aids.joinToString(", ")))
                fare?.let { add(Row(FARE, it)) }
                compensation?.let { add(Row(COMPENSATION, it)) }
                eligibility?.let { add(Row(ELIGIBILITY, it)) }
                if (instructions.isNotEmpty()) add(Row(INSTRUCTIONS, instructions.joinToString("\n")))
                addAll(more)
            }
            val title = listOfNotNull(kind, times.firstOrNull()).joinToString(" ").ifEmpty { null }
            return TripCardText(title, name, times.getOrNull(0), times.getOrNull(1), kind, status, rows)
        }

        /** The page's own controls and countdown, not part of the trip. */
        private fun isControl(line: String): Boolean =
            TextNorm.fold(line) in CONTROLS || MINUTES_LEFT.matches(line)

        private fun isTime(line: String): Boolean = line.length <= 12 && line.none { it.isLetter() } && TripTimes.timeIn(line) != null

        /** A name: two to six words of letters, no digits or commas. */
        private fun isName(line: String): Boolean =
            line.none { it.isDigit() || it == ',' || it == ':' } && line.split(' ').size in 2..6 && TripKinds.labelIn(line) == null

        private fun isField(line: String): Boolean {
            val folded = TextNorm.fold(line)
            return isPhone(line) || isCodes(line) || line in ELIGIBILITIES || folded.startsWith("client fee") || folded.startsWith("compensation")
        }

        private fun isPhone(line: String): Boolean = PHONE_ONLY.matches(line) && line.count { it.isDigit() } >= 7

        private fun isCodes(line: String): Boolean = line !in ELIGIBILITIES && codes(line).let { c -> c.isNotEmpty() && c.all { CODE.matches(it) } }

        private fun codes(line: String): List<String> = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** A code's meaning with its count ("RU1" → "Rullstol 1"), or null when it is not one of [table]'s. */
        private fun written(code: String, table: Map<String, String>): String? {
            val letters = code.trimEnd { it.isDigit() }
            val meaning = table[letters] ?: return null
            return listOf(meaning, code.substring(letters.length)).filter { it.isNotEmpty() }.joinToString(" ")
        }

        /**
         * A phone number as the card shows it: its digits in groups of 3, 4 and 3 ("0738669883" →
         * "073 8669 883"), a longer one 3, 4 and then threes, apart by a small space that never
         * breaks the line. A Swedish number written with +46 is shown as dialled in Sweden
         * ("+46 70 000 00 01" → "070 0000 001"); another country's keeps its +. Not a phone number:
         * as written.
         */
        fun spacedPhone(number: String): String {
            val trimmed = number.trim()
            if (!PHONE_ONLY.matches(trimmed)) return trimmed
            val all = trimmed.filter { it.isDigit() }
            val swedish = trimmed.startsWith("+") && all.startsWith("46")
            val digits = if (swedish) "0" + all.removePrefix("46") else all
            if (digits.length < 7) return trimmed
            val groups = ArrayList<String>()
            var i = 0
            for (size in listOf(3, 4)) {
                groups += digits.substring(i, i + size)
                i += size
            }
            while (i < digits.length) {
                val rest = digits.length - i
                // Never a single digit at the end: the last two groups share what is left.
                val size = if (rest == 4) 2 else minOf(3, rest)
                groups += digits.substring(i, i + size)
                i += size
            }
            return (if (trimmed.startsWith("+") && !swedish) "+" else "") + groups.joinToString(PHONE_GAP)
        }

        /** The small space between a phone number's groups (a no-break space). */
        const val PHONE_GAP = "\u00A0"

        private fun spaceType(code: String): String? = written(code, SPACE_CODES)

        private fun aid(code: String): String? = written(code, AID_CODES)

        private val ICON = Regex("""^[a-z_]+$""")
        private val MINUTES_LEFT = Regex("""^-?\d+\s*min$""", RegexOption.IGNORE_CASE)
        private val PHONE_ONLY = Regex("""^\+?[\d \-]+$""")
        private val CODE = Regex("""^[A-ZÅÄÖ]{2,4}\d{0,2}$""")

        /** Folded statuses a card shows under its kind. */
        private val STATUSES = setOf("performed", "departed", "arrived", "noshow", "cancelled", "canceled", "utford", "avgatt", "ankommen")

        /** Folded words of the page's own buttons. */
        private val CONTROLS = setOf("arrive", "depart", "perform", "no show", "ankomst", "avfard")

        /** Who pays for the trip (färdtjänst, sjukresa). */
        private val ELIGIBILITIES = setOf("FTJ", "SJU", "SJR", "RFT", "RFTJ")

        /** The trip's codes as YouDrive's details write them, without their count: where the passenger sits … */
        private val SPACE_CODES = mapOf(
            "SP" to "Sittande passagerare",
            "FRA" to "Fram",
            "ROL" to "Rollator fällbar",
            "RU" to "Rullstol",
            "TRP" to "Transportrullstol",
        )

        /** … and the help they get. */
        private val AID_CODES = mapOf(
            "HLI" to "Hämtas/Lämnas inne",
            "AVD" to "Hämtning på avdelning",
            "TRA" to "Trappklättrare",
        )
    }
}
