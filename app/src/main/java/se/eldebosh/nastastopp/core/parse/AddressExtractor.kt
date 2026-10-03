package se.eldebosh.nastastopp.core.parse

/**
 * One stop extracted from OCR text. Only address text is kept — never other lines.
 *
 * @property displayText cleaned address in Title Case (postal code formatted "123 45").
 * @property candidates ordered strings for the geocoder to try (full line first, then with
 *   leading tokens progressively dropped while the street token remains).
 * @property parsedPostalCode formatted "123 45" or null.
 * @property parsedTown town as written after the postal code / comma, or null.
 * @property parsedTownKnown true if [parsedTown] is in the bundled locality list.
 * @property sourceOrder index of the (first) OCR line this stop came from.
 * @property time scheduled time of the trip as shown in the screenshot ("12:48"), or null.
 * @property kind pick-up / drop-off / depot, from the list's label next to the trip, or null.
 * @property name the passenger's first and last name from the line above the address, or null.
 * @property place the named place written with the address on a YouDrive card ("Kils
 *   Vårdcentral", "Centralsjukhuset Huvudentrén"), or null; see [Places].
 * @property card everything on the trip's YouDrive card, as written, or null: shown only on the
 *   passenger display's trip card, when the driver opens it; never said, sent anywhere else or logged.
 */
data class ExtractedStop(
    val displayText: String,
    val candidates: List<String>,
    val parsedPostalCode: String?,
    val parsedTown: String?,
    val sourceOrder: Int,
    val parsedTownKnown: Boolean = false,
    val time: String? = null,
    val kind: TripKind? = null,
    val name: String? = null,
    /** YouDrive's card says the trip is done ("Performed"). */
    val youDriveDone: Boolean = false,
    val place: String? = null,
    val card: String? = null,
)

/** Which detection rule accepted the line. */
enum class MatchRule { POSTAL_TOWN, STREET_NUMBER, KNOWN_TOWN, MANUAL }

/** Result of analysing one (possibly joined) line. */
data class ParsedAddress(
    val rule: MatchRule,
    val streetPart: String,
    val postalCode: String?,
    val town: String?,
    val townKnown: Boolean,
    val candidates: List<String>,
    /** Folded street core (from the anchor token on) used for duplicate detection. */
    val coreKey: String,
) {
    val displayText: String get() = candidates.first()
}

/**
 * Pure-Kotlin extraction of Swedish addresses from OCR lines. No Android dependencies.
 */
class AddressExtractor(private val localities: Localities) {

    /** Extract stops from lines already sorted in reading order. */
    fun extract(lines: List<String>, startOrder: Int = 0): List<ExtractedStop> {
        // (parsed address, first line index, last line index)
        val found = ArrayList<Triple<ParsedAddress, Int, Int>>()
        var i = 0
        while (i < lines.size) {
            val line = withCarriedPrefix(lines.getOrNull(i - 1), lines[i])
            val next = lines.getOrNull(i + 1)
            val single = safeParse(line)
            val singleHasPlace = single != null && (single.postalCode != null || single.town != null)
            if (next != null && !singleHasPlace && safeCheck { isJoinable(line) && startsWithPostalOrTown(next) }) {
                val joined = safeParse("${line.trim().trimEnd(',')}, ${next.trim()}", joined = true)
                if (joined != null) {
                    found += Triple(joined, i, i + 1)
                    i += 2
                    continue
                }
            }
            if (single != null) found += Triple(single, i, i)
            i++
        }
        val (times, kinds) = timesAndKinds(lines, found.map { it.second to it.third })
        return mergeConsecutiveDuplicates(
            found.mapIndexed { k, (p, first, _) ->
                // The passenger's name is the line right above the address (never past the previous trip).
                val above = first - 1
                val name = if (above >= 0 && (k == 0 || above > found[k - 1].third)) personName(lines[above]) else null
                p.toStop(startOrder + first).copy(time = times.getOrNull(k), kind = kinds.getOrNull(k), name = name) to p
            },
        )
    }

    /**
     * The time and kind label ("Pick-up") of every address span. Both sit in the same card, so one
     * direction (above or below the address) is chosen for both, by whichever gives more spans a
     * time or a label: a trip never takes its neighbour's kind while keeping its own time.
     */
    private fun timesAndKinds(lines: List<String>, spans: List<Pair<Int, Int>>): Pair<List<String?>, List<TripKind?>> =
        try {
            val times = TripTimes.timesNearby(lines, spans)
            val kinds = TripTimes.nearby(lines, spans) { TripKinds.labelIn(lines[it]) }
            val above = times.count(above = true) + kinds.count(above = true) >= times.count(above = false) + kinds.count(above = false)
            times.values(above) to kinds.values(above)
        } catch (_: RuntimeException) {
            List(spans.size) { null } to List(spans.size) { null }
        }

    /**
     * Parses a manually typed (or edited) address. Never rejects: if the text does not look like an
     * address by the OCR rules it is still kept as-is (cleaned) so the geocoder can try it.
     */
    fun fromManualText(text: String, order: Int = 0): ExtractedStop? {
        val parsed = parse(text, manual = true)
        if (parsed != null) return parsed.toStop(order)
        val cleaned = clean(text) ?: return null
        if (TextNorm.letterCount(cleaned) + cleaned.count { it.isDigit() } < 2) return null
        val display = TitleCase.apply(cleaned)
        return ExtractedStop(display, listOf(display), null, null, order)
    }

    /** True if two stops are the same address (used to merge consecutive duplicates). */
    fun isSameAddress(a: ExtractedStop, b: ExtractedStop): Boolean {
        val pa = parse(a.displayText, manual = true)
        val pb = parse(b.displayText, manual = true)
        val ka = pa?.coreKey ?: TextNorm.key(a.displayText)
        val kb = pb?.coreKey ?: TextNorm.key(b.displayText)
        if (ka != kb) return false
        val postalOk = a.parsedPostalCode == null || b.parsedPostalCode == null || a.parsedPostalCode == b.parsedPostalCode
        val townOk = a.parsedTown == null || b.parsedTown == null || TextNorm.fold(a.parsedTown) == TextNorm.fold(b.parsedTown)
        return postalOk && townOk
    }

    /**
     * A passenger's name line ("Per Johan Albin Stenbäck", "ANNA TESTSSON") as first + last name
     * ("Per Stenbäck", "Anna Testsson"): the only part of it that is kept. Anything that is not
     * clearly a name gives null: digits or commas, a street, a town, a kind / status word, or
     * (when [strict], for text of unknown layout) a lower-case word other than "van", "af" ….
     * A YouDrive card's name line is known by its place (the card's first line), so there
     * [strict] is off and "anna testsson" counts too.
     */
    fun personName(line: String, strict: Boolean = true): String? {
        val text = TextNorm.collapseSpaces(line)
        if (text.any { it.isDigit() || it == ',' || it == ':' } || TripKinds.labelIn(text) != null) return null
        val words = text.split(' ')
        if (words.size !in 2..6) return null
        val first = words.first()
        val last = words.last()
        val letters = { w: String -> w.length >= 2 && w.all { it.isLetter() || it == '-' || it == '\'' } }
        val nameWord = { w: String -> letters(w) && (!strict || w.first().isUpperCase()) }
        if (!nameWord(first) || !nameWord(last)) return null
        if (!words.drop(1).dropLast(1).all { nameWord(it) || TextNorm.fold(it) in NAME_PARTICLES }) return null
        if (words.any { TextNorm.fold(it) in NOT_NAME_WORDS }) return null
        if (localities.canonical(text) != null || findStreetToken(words + "1") >= 0) return null
        if (words.any { w -> STREET_SUFFIXES.any { s -> w.length > s.length + 1 && w.lowercase(TextNorm.SWEDISH).endsWith(s) } }) return null
        return "${nameCase(first)} ${nameCase(last)}"
    }

    /** "ANNA" / "anna" → "Anna", "ANNA-KARIN" → "Anna-Karin"; a mixed-case word stays as written. */
    private fun nameCase(word: String): String =
        if (word.any { it.isLowerCase() } && word.any { it.isUpperCase() }) word
        else word.split('-').joinToString("-") { part -> part.lowercase(TextNorm.SWEDISH).replaceFirstChar { it.titlecase(TextNorm.SWEDISH) } }

    // ---------------------------------------------------------------------------------------
    // Line analysis

    /** A malformed line must never fail the whole screenshot: it is simply skipped. */
    private fun safeParse(raw: String, joined: Boolean = false): ParsedAddress? =
        try {
            parse(raw, joined)
        } catch (_: RuntimeException) {
            null
        }

    /**
     * Analyses one line. Returns null if the line is rejected / not an address.
     * @param joined true when the line is the concatenation of two OCR lines.
     * @param manual true for user-typed text (UI-word / money filters are not applied).
     */
    fun parse(raw: String, joined: Boolean = false, manual: Boolean = false): ParsedAddress? {
        val rawNorm = normalizeRaw(raw)
        if (rawNorm.isEmpty()) return null

        // Flags computed on the raw line.
        val isMoney = !manual && MONEY.containsMatchIn(rawNorm)
        val hasUiWord = !manual && UI_WORDS.containsMatchIn(rawNorm)
        if (!manual && isShortCodeLine(rawNorm)) return null

        val text = clean(rawNorm) ?: return null
        if (TextNorm.letterCount(text) < 2) return null
        // A note written as a sentence ("följes in till plan 3", "så är det vid …") is not an
        // address, even when it mentions a street.
        if (!manual && lowerCaseWords(text) >= PROSE_WORDS) return null

        // --- Rule (a): postal code followed by a town word.
        val postal = findPostal(text)
        var streetPart: String
        var town: String? = null
        var townKnown = false
        var postalCode: String? = null
        var ruleA = false
        if (postal != null) {
            postalCode = postal.code
            streetPart = text.substring(0, postal.start)
            val townRaw = extractTownAfterPostal(text.substring(postal.end))
            if (townRaw != null) {
                val canon = localities.canonical(townRaw)
                town = canon ?: TitleCase.apply(townRaw)
                townKnown = canon != null
            }
            streetPart = trimPunct(streetPart)
            ruleA = town != null && TextNorm.letterCount(streetPart) >= 2
        } else {
            streetPart = text
            // "<text>, <Town>" or "<street> <no> <Town>" without postal code.
            val comma = streetPart.lastIndexOf(',')
            if (comma > 0) {
                val right = trimPunct(streetPart.substring(comma + 1)).removeCountrySuffix()
                val canon = localities.canonical(right)
                if (canon != null) {
                    town = canon
                    townKnown = true
                    streetPart = trimPunct(streetPart.substring(0, comma))
                }
            }
        }
        streetPart = trimPunct(streetPart)
        if (TextNorm.letterCount(streetPart) < 2) {
            // Only a postal code + town (or nothing) — not a stop on its own.
            return null
        }

        var tokens = withoutRepeatedWords(tokenize(streetPart))
        var streetIdx = findStreetToken(tokens)

        if (town == null && streetIdx >= 0) {
            // "<street> <no> <Town>" (no comma, no postal code): split a trailing known locality
            // off, as long as the street token + number remain.
            val n = localities.suffixWords(streetPart)
            if (n in 1 until tokens.size) {
                val keep = tokens.dropLast(n)
                if (findStreetToken(keep) >= 0) {
                    town = localities.canonical(tokens.takeLast(n).joinToString(" "))
                    townKnown = true
                    tokens = keep
                    streetIdx = findStreetToken(tokens)
                }
            }
        }

        val ruleB = streetIdx >= 0
        val hasNumber = tokens.any { HOUSE_NUMBER.matches(it.stripPunct()) }
        val ruleC = postalCode == null && town != null && townKnown

        val rule = when {
            ruleA -> MatchRule.POSTAL_TOWN
            manual -> MatchRule.MANUAL
            isMoney || hasUiWord -> return null
            ruleB -> MatchRule.STREET_NUMBER
            ruleC -> MatchRule.KNOWN_TOWN
            else -> return null
        }

        if (joined && !manual) {
            // A joined line must carry a house number (avoids "Name\nTown" → address).
            if (!hasNumber) return null
        }

        // Anchor: first token of the street name — the earliest token we may drop leading tokens up to.
        val anchor = anchorIndex(tokens, streetIdx)
        val titled = TitleCase.applyTokens(tokens)
        val place = buildString {
            if (postalCode != null) append(formatPostal(postalCode))
            if (town != null) {
                if (isNotEmpty()) append(' ')
                append(town)
            }
        }
        val candidates = LinkedHashSet<String>()
        for (start in 0..anchor) {
            val street = trimPunct(titled.drop(start).joinToString(" "))
            if (TextNorm.letterCount(street) < 2) continue
            candidates += if (place.isEmpty()) street else "$street, $place"
        }
        val core = trimPunct(titled.drop(anchor).joinToString(" "))
        if (postalCode != null && town != null) {
            // Fallback without postal code in case OCR mangled it.
            candidates += "$core, $town"
        }
        if (candidates.isEmpty()) return null

        return ParsedAddress(
            rule = rule,
            streetPart = trimPunct(titled.joinToString(" ")),
            postalCode = postalCode?.let { formatPostal(it) },
            town = town,
            townKnown = townKnown,
            candidates = candidates.toList(),
            coreKey = TextNorm.key(core),
        )
    }

    /**
     * OCR sometimes splits one row in two: "10:45 Västra" | "Torggatan 12, 652 24 Karlstad". A
     * street prefix word left at the end of the previous line is put back in front of the street.
     */
    private fun withCarriedPrefix(previous: String?, line: String): String {
        val last = previous?.trim()?.split(' ')?.lastOrNull()?.stripPunct() ?: return line
        // Capitalised like a street name ("Västra"), so UI text such as "Visa nya" is not carried.
        if (last.firstOrNull()?.isUpperCase() != true || !isStreetPrefix(last)) return line
        val first = line.trim().split(' ').firstOrNull()?.stripPunct() ?: return line
        if (first.isEmpty() || !first.first().isLetter() || isStreetPrefix(first)) return line
        return "$last ${line.trim()}"
    }

    private fun isStreetPrefix(word: String) = TextNorm.fold(word) in STREET_PREFIXES

    private fun ParsedAddress.toStop(order: Int) = ExtractedStop(
        displayText = displayText,
        candidates = candidates,
        parsedPostalCode = postalCode,
        parsedTown = town,
        sourceOrder = order,
        parsedTownKnown = townKnown,
    )

    private fun mergeConsecutiveDuplicates(items: List<Pair<ExtractedStop, ParsedAddress>>): List<ExtractedStop> {
        val out = ArrayList<Pair<ExtractedStop, ParsedAddress>>()
        for (item in items) {
            val last = out.lastOrNull()
            if (last != null && sameParsed(last.second, item.second) && noConflict(last.first, item.first)) {
                // Keep the more complete of the two (more place info), at the first position.
                val time = last.first.time ?: item.first.time
                val kind = last.first.kind ?: item.first.kind
                val name = last.first.name ?: item.first.name
                val keep = if (placeScore(item.second) > placeScore(last.second)) {
                    item.first.copy(sourceOrder = last.first.sourceOrder, time = time, kind = kind, name = name) to item.second
                } else {
                    last.first.copy(time = time, kind = kind, name = name) to last.second
                }
                out[out.lastIndex] = keep
            } else {
                out += item
            }
        }
        return out.map { it.first }
    }

    /** Words of letters that start in lower case ("inne", "följes", "så"): many of them make a sentence. */
    private fun lowerCaseWords(text: String): Int =
        text.split(' ', ',').count { w -> w.length >= 2 && w.first().isLowerCase() && w.all { it.isLetter() || it == '/' } }

    /**
     * Nothing known says [a] and [b] are two trips: no two different kinds (a drop-off and a
     * pick-up at the same address are two stops), times or passengers. A field that one of them
     * lacks says nothing, so a card cut by the edge of a screenshot still merges with its whole read.
     */
    fun noConflict(a: ExtractedStop, b: ExtractedStop): Boolean =
        (a.kind == null || b.kind == null || a.kind == b.kind) &&
            (a.time == null || b.time == null || TripTimes.minutes(a.time) == TripTimes.minutes(b.time)) &&
            (a.name == null || b.name == null || TextNorm.fold(a.name) == TextNorm.fold(b.name))

    /** One trip read twice (one after the other in an image, or across two overlapping images): its address, nothing in conflict. */
    fun isSameTrip(a: ExtractedStop, b: ExtractedStop): Boolean = isSameAddress(a, b) && noConflict(a, b)

    private fun placeScore(p: ParsedAddress) = (if (p.postalCode != null) 2 else 0) + (if (p.town != null) 1 else 0)

    private fun sameParsed(a: ParsedAddress, b: ParsedAddress): Boolean {
        if (a.coreKey != b.coreKey) return false
        val postalOk = a.postalCode == null || b.postalCode == null || a.postalCode == b.postalCode
        val townOk = a.town == null || b.town == null || TextNorm.fold(a.town) == TextNorm.fold(b.town)
        return postalOk && townOk
    }

    // ---------------------------------------------------------------------------------------
    // Joining helpers

    private inline fun safeCheck(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (_: RuntimeException) {
            false
        }

    private fun isJoinable(line: String): Boolean {
        val n = normalizeRaw(line)
        if (n.isEmpty() || MONEY.containsMatchIn(n) || UI_WORDS.containsMatchIn(n) || isShortCodeLine(n)) return false
        val c = clean(n) ?: return false
        return TextNorm.letterCount(c) >= 2 && findPostal(c) == null
    }

    /**
     * The next line starts with a postal code, or consists of nothing but a known locality
     * (so a line like "Bara hämtning" — "Bara" is also a town — is not glued to an address).
     */
    private fun startsWithPostalOrTown(line: String): Boolean {
        val n = clean(line) ?: return false
        val postal = findPostal(n)
        if (postal != null && n.substring(0, postal.start).isBlank()) return true
        val words = n.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val k = localities.prefixWords(n)
        return k > 0 && k == words.size
    }

    // ---------------------------------------------------------------------------------------
    // Cleaning

    private fun normalizeRaw(raw: String): String {
        var t = raw.replace(' ', ' ').replace('\t', ' ')
        t = t.replace(Regex("\\.{3,}"), "…")
        t = t.replace('’', '\'').replace('´', '\'')
        return TextNorm.collapseSpaces(t)
    }

    /** Removes noise (phones, times, apartment parts, truncation). Returns null if nothing is left. */
    internal fun clean(input: String): String? {
        var t = normalizeRaw(input)
        t = PHONE.replace(t, " ")
        t = TIME.replace(t, " ")
        t = handleTruncation(t)
        t = removeCareOf(t)
        for (re in EXTRA_PARTS) t = re.replace(t, ",")
        t = t.replace(Regex("\\s*,\\s*"), ", ")
        t = t.replace(Regex("(,\\s*)+"), ", ")
        t = t.replace(Regex("^[\\s,;:|•·*\\-–]+"), "")
        t = trimPunct(TextNorm.collapseSpaces(t))
        return t.ifEmpty { null }
    }

    /**
     * Truncation marks: everything after the first "…" is dropped. The word directly before it is
     * dropped too (it is probably cut), unless it is a known locality or a complete postal code.
     */
    private fun handleTruncation(text: String): String {
        val idx = text.indexOf('…')
        if (idx < 0) return text
        val before = text.substring(0, idx)
        if (before.isNotEmpty() && !before.last().isWhitespace() && before.last() != ',') {
            val m = Regex("([\\p{L}\\p{N}\\-]+)$").find(before)
            if (m != null) {
                val head = before.substring(0, m.range.first)
                val word = m.value
                // Is the trailing word (possibly with preceding words) a known locality?
                val n = localities.suffixWords(before.trim())
                val keep = n > 0 || Regex("\\d{5}").matches(word)
                return if (keep) before else head
            }
        }
        return before
    }

    /**
     * Removes "c/o <name>" — the name part ends at a comma, a number, a street token or after 3 words,
     * so a street written right after the c/o name is kept.
     */
    private fun removeCareOf(text: String): String {
        val m = CARE_OF.find(text) ?: return text
        val before = text.substring(0, m.range.first).trimEnd()
        val tokens = text.substring(m.range.last + 1).trim().split(' ').filter { it.isNotEmpty() }
        var consumed = 0
        while (consumed < tokens.size && consumed < 3) {
            val tok = tokens[consumed]
            if (tok.any { it.isDigit() }) break
            if (findStreetToken(tokens.subList(consumed, tokens.size)) == 0) break
            consumed++
            if (tok.endsWith(",")) break
        }
        val rest = tokens.drop(consumed).joinToString(" ")
        return when {
            before.isEmpty() -> rest
            rest.isEmpty() -> before
            else -> "$before, $rest"
        }
    }

    private fun isShortCodeLine(line: String): Boolean {
        if (line.any { it.isLowerCase() }) return false
        val tokens = line.split(Regex("[\\s,;/]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 5) return false
        return tokens.all { SHORT_CODE_TOKEN.matches(it) } && tokens.any { tok -> tok.any { it.isLetter() } }
    }

    // ---------------------------------------------------------------------------------------
    // Postal code

    private data class PostalMatch(val start: Int, val end: Int, val code: String)

    /** Finds a Swedish postal code, applying OCR fixes only inside the 5 postal positions. */
    private fun findPostal(text: String): PostalMatch? {
        var best: PostalMatch? = null
        for (m in POSTAL.findAll(text)) {
            val chars = (m.groupValues[1] + m.groupValues[2])
            if (chars.count { it.isDigit() } < 3) continue
            val fixed = chars.map { OCR_DIGIT_FIX[it] ?: it }.joinToString("")
            if (!fixed.all { it.isDigit() }) continue
            val value = fixed.toInt()
            if (value < 10000 || value > 98999) continue
            val candidate = PostalMatch(m.range.first, m.range.last + 1, fixed)
            // Prefer the last postal code that is followed by a town word.
            val followedByTown = extractTownAfterPostal(text.substring(candidate.end)) != null
            if (best == null || followedByTown) best = candidate
        }
        return best
    }

    private fun extractTownAfterPostal(rest: String): String? {
        var t = rest.trimStart(' ', ',', '.', ':', ';', '-')
        val stop = t.indexOfFirst { it.isDigit() || it == ',' || it == '(' || it == '|' || it == '/' }
        if (stop >= 0) t = t.substring(0, stop)
        t = trimPunct(t).removeCountrySuffix()
        // Keep at most 3 words of letters.
        val words = t.split(' ').filter { w -> w.isNotEmpty() && w.all { it.isLetter() || it == '-' } }
        if (words.isEmpty()) return null
        // If the first 1..3 words are a known locality, take exactly that.
        val n = localities.prefixWords(words.joinToString(" "))
        val town = if (n > 0) words.take(n).joinToString(" ") else words.take(2).joinToString(" ")
        return town.takeIf { TextNorm.letterCount(it) >= 2 }
    }

    private fun String.removeCountrySuffix(): String =
        replace(COUNTRY_SUFFIX, "").trim()

    // ---------------------------------------------------------------------------------------
    // Street tokens

    private fun tokenize(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

    /** "Depågatan Depågatan 1" (a place named after its street) → "Depågatan 1". */
    private fun withoutRepeatedWords(tokens: List<String>): List<String> = tokens.filterIndexed { i, t ->
        i == 0 || t.none { it.isLetter() } || !t.stripPunct().equals(tokens[i - 1].stripPunct(), ignoreCase = true)
    }

    /** Index of the token carrying a street suffix that is followed by a house number, or -1. */
    private fun findStreetToken(tokens: List<String>): Int {
        for (i in tokens.indices) {
            val word = tokens[i].stripPunct()
            if (word.isEmpty() || !word.all { it.isLetter() || it == '-' || it == '.' || it == ':' }) continue
            val lower = word.lowercase(TextNorm.SWEDISH)
            val suffix = STREET_SUFFIXES.firstOrNull { lower.endsWith(it) } ?: continue
            val standalone = lower.length == suffix.length
            if (standalone && (i == 0 || tokens[i - 1].stripPunct().none { it.isLetter() })) continue
            if (hasHouseNumberAt(tokens, i + 1)) return i
        }
        return -1
    }

    private fun hasHouseNumberAt(tokens: List<String>, idx: Int): Boolean {
        val t = tokens.getOrNull(idx)?.stripPunct() ?: return false
        return HOUSE_NUMBER.matches(t)
    }

    /**
     * Earliest token the street name may start at. For a compound street word ("Storgatan") that
     * token itself; for a standalone suffix ("Karl Johans gata") the token before it; without a
     * street suffix, the letter token right before the first house number.
     */
    private fun anchorIndex(tokens: List<String>, streetIdx: Int): Int {
        if (streetIdx >= 0) {
            val word = tokens[streetIdx].stripPunct().lowercase(TextNorm.SWEDISH)
            val standalone = STREET_SUFFIXES.any { it == word }
            var anchor = if (standalone) maxOf(0, streetIdx - 1) else streetIdx
            // "Västra Torggatan", "Gamla Kilsvägen": the prefix belongs to the street name.
            while (anchor > 0 && isStreetPrefix(tokens[anchor - 1].stripPunct())) anchor--
            return anchor
        }
        val numIdx = tokens.indexOfFirst { HOUSE_NUMBER.matches(it.stripPunct()) }
        if (numIdx > 0) {
            // The token before the number must contain letters to be a street name.
            val prev = numIdx - 1
            if (tokens[prev].any { it.isLetter() } && !tokens[prev].endsWith(",")) return prev
        }
        return 0
    }

    private fun String.stripPunct(): String = trim(',', '.', ';', ':', '!', '?', '(', ')', '"', '\'', '|')

    private fun trimPunct(s: String): String = s.trim().trim(',', ';', ':', '-', '–', '|', '•', '·', '/').trim()

    companion object {
        /** Common Swedish street suffixes (plus a few OCR-diacritic-less variants). */
        val STREET_SUFFIXES: List<String> = listOf(
            "esplanaden", "promenaden", "platsen", "stråket", "slingan", "gården", "höjden",
            "torget", "stigen", "backen", "vägen", "gatan", "gränd", "leden", "liden", "kajen",
            "udden", "ringen", "allén", "allé", "gata", "torg", "plan", "väg",
            "vagen", "allen", "alle",
        ).sortedByDescending { it.length }

        /** Lower-case words inside a name ("Anna van der Berg"), folded. */
        private val NAME_PARTICLES = setOf("van", "von", "der", "den", "de", "af", "av", "la", "le", "el", "al", "bin", "ibn", "da", "di", "du")

        /** Words that never occur in a passenger's name line (list headings, statuses, fees), folded. */
        private val NOT_NAME_WORDS = setOf(
            "performed", "departed", "arrive", "arrived", "compensation", "client", "fee", "status", "trips",
            "start", "pull", "pick", "drop", "tel", "mobil", "resor", "idag", "korningar", "utford", "avgatt",
            "no", "not", "show", "stop", "cancelled", "waiting", "min", "summary", "total",
        )

        /** A line with this many lower-case words is a sentence (a note), not an address. */
        private const val PROSE_WORDS = 4

        /** Words that start a street name ("Västra Torggatan"), folded (no å ä ö, lower case). */
        val STREET_PREFIXES: Set<String> = setOf(
            "vastra", "ostra", "norra", "sodra", "stora", "lilla", "gamla", "nya",
            "ovre", "nedre", "yttre", "inre", "sankt", "s:t",
        )

        private val HOUSE_NUMBER = Regex("\\d{1,4}\\s?[A-Za-z]?|\\d{1,4}-\\d{1,4}")

        // Postal code: "123 45" or "12345", allowing typical OCR confusions (fixed later).
        private val POSTAL = Regex("(?<![\\p{L}\\p{N}])([1-9OoIlZSB][0-9OoIlZSB]{2}) ?([0-9OoIlZSB]{2})(?![\\p{L}\\p{N}])")
        private val OCR_DIGIT_FIX = mapOf('O' to '0', 'o' to '0', 'I' to '1', 'l' to '1', 'Z' to '2', 'S' to '5', 'B' to '8')

        private val PHONE = Regex("(?<![\\p{N}])(?:(?:\\+|00)46[\\s-]?\\(?0?\\)?|0)\\d{1,3}(?:[\\s-]?\\d){5,8}(?![\\p{N}])")
        private val TIME = Regex("(?<![\\p{N}])(?:[01]?\\d|2[0-3])[:.][0-5]\\d(?![\\p{N}])")
        private val MONEY = Regex("(?<![\\p{L}])(?:KR|kr|Kr|SEK|sek)(?![\\p{L}])")
        private val UI_WORDS = Regex(
            "(?<![\\p{L}])(?:fee|fees|compensation|performed|departed|pick-?up|drop-?off|status|" +
                "avgift|ersättning|utförd|utförda|avgått)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
        private val COUNTRY_SUFFIX = Regex("[,\\s]*\\b(sverige|sweden|se)\\s*$", RegexOption.IGNORE_CASE)
        private val CARE_OF = Regex("(?<![\\p{L}])c/o(?![\\p{L}])", RegexOption.IGNORE_CASE)
        private val SHORT_CODE_TOKEN = Regex("[A-ZÅÄÖ0-9]{1,4}")

        /** Apartment / extra parts that are removed from the address. */
        private val EXTRA_PARTS = listOf(
            Regex("(?<![\\p{L}])(?:lgh|lägenhet)(?![\\p{L}])\\.?\\s*(?:nr\\.?\\s*)?\\d*", RegexOption.IGNORE_CASE),
            Regex("(?<![\\p{L}])vån(?:ing)?(?![\\p{L}])\\.?\\s*\\d*\\s*(?:tr(?:appor)?\\.?)?(?![\\p{L}])", RegexOption.IGNORE_CASE),
            Regex("(?<![\\p{L}\\p{N}])\\d+\\s*tr(?:appor)?\\.?(?![\\p{L}])", RegexOption.IGNORE_CASE),
            Regex("(?<![\\p{L}])port(?:kod)?(?![\\p{L}])\\.?\\s*[:.]?\\s*[\\p{L}\\p{N}]{0,6}(?![\\p{L}])", RegexOption.IGNORE_CASE),
            Regex("(?<![\\p{L}])uppg(?:ång)?(?![\\p{L}])\\.?\\s*[\\p{L}\\p{N}]{0,3}(?![\\p{L}])", RegexOption.IGNORE_CASE),
            // Floor ("plan 3") only when written after a comma — a street "…plan 3" is kept.
            Regex(",\\s*plan\\s*\\d+(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
        )

        fun formatPostal(code: String): String {
            val digits = code.filter { it.isDigit() }
            return if (digits.length == 5) "${digits.substring(0, 3)} ${digits.substring(3)}" else code
        }
    }
}
