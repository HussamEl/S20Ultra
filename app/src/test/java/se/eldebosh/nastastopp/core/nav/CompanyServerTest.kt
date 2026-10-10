package se.eldebosh.nastastopp.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The company server's contract as the app reads it (the invented code and pass of testdata/README.md). */
class CompanyServerTest {

    private val tokenText = "ab12".repeat(16)
    private val token = DeviceToken(tokenText)

    @Test
    fun aCodeIsMadeWholeAndCheckedAsTheServerMakesThem() {
        assertEquals("ABCD2345", CompanyServer.code(" abcd 2345 "))
        assertEquals("ABCD2345", CompanyServer.code("ABCD-2345"))
        assertEquals("ABCD2345", CompanyServer.code("abcd-2345"))
        assertNull("seven", CompanyServer.code("ABCD234"))
        assertNull("nine", CompanyServer.code("ABCD23456"))
        assertNull(CompanyServer.code(""))
        // The letters and digits that look alike are never in a code.
        for (c in listOf('O', '0', 'I', '1', 'L')) assertNull("$c", CompanyServer.code("ABC${c}2345"))
        assertNull(CompanyServer.code("ABCD234Å"))
        assertNull(CompanyServer.code("ABCD_345"))

        assertTrue(CompanyServer.hasLookalikes("ABCO2345"))
        assertTrue(CompanyServer.hasLookalikes("abcl2345"))
        assertTrue(CompanyServer.hasLookalikes("0"))
        assertTrue(CompanyServer.hasLookalikes("i"))
        assertTrue(CompanyServer.hasLookalikes("1"))
        assertFalse(CompanyServer.hasLookalikes("ABCD-2345"))
        assertFalse(CompanyServer.hasLookalikes(""))
    }

    @Test
    fun theEnrolmentSendsOnlyTheCodeAndTheRole() {
        assertEquals("""{"code":"ABCD2345","role":"tablet"}""", CompanyServer.enrollBody("ABCD2345"))
        assertEquals("tablet", CompanyServer.ROLE)
    }

    @Test
    fun theEnrolmentAnswerGivesAPassOnlyAsTheServerMakesThem() {
        val enrolled = CompanyServer.parseEnroll("""{"token":"$tokenText","label":"  tablet-40274  "}""")!!
        assertEquals(token, enrolled.token)
        assertEquals("tablet-40274", enrolled.label)
        assertFalse(enrolled.toString().contains(tokenText))

        assertNull("upper case", CompanyServer.parseEnroll("""{"token":"${tokenText.uppercase()}","label":"tablet-40274"}"""))
        assertNull("63", CompanyServer.parseEnroll("""{"token":"${tokenText.drop(1)}","label":"tablet-40274"}"""))
        assertNull("65", CompanyServer.parseEnroll("""{"token":"${tokenText}a","label":"tablet-40274"}"""))
        assertNull("a number", CompanyServer.parseEnroll("""{"token":12,"label":"tablet-40274"}"""))
        assertNull("none", CompanyServer.parseEnroll("""{"label":"tablet-40274"}"""))
        assertNull("html", CompanyServer.parseEnroll("<html><body>503 Service Unavailable</body></html>"))
        assertNull("empty", CompanyServer.parseEnroll(""))
        assertNull("a list", CompanyServer.parseEnroll("""["$tokenText"]"""))

        // The label: printable characters only, at most 40, trimmed.
        val long = CompanyServer.parseEnroll("""{"token":"$tokenText","label":"tablet-\u0000‮${"x".repeat(60)}\n"}""")!!
        assertEquals("tablet-" + "x".repeat(33), long.label)
        assertEquals("", CompanyServer.parseEnroll("""{"token":"$tokenText"}""")!!.label)
    }

    @Test
    fun theConfigKeepsOnlyAKeyAsGoogleIssuesThem() {
        val key = "AIzaSyA-1234567890abcdefghijklmnopqrstu"
        val config = CompanyServer.parseConfig("""{"mapsJsKey":"$key","label":"tablet-40274","ways":{"google":true,"mapmap":false}}""")!!
        assertEquals(key, config.mapsJsKey)
        assertTrue(config.google)
        assertFalse(config.mapmap)
        assertEquals("tablet-40274", config.label)
        assertFalse(config.toString().contains(key))

        // The key is put into the map page: anything else is dropped, the rest is still read.
        for (bad in listOf("\"\"", "null", "\"AIzaSy\\\"><script>alert(1)</script>\"", "12")) {
            val c = CompanyServer.parseConfig("""{"mapsJsKey":$bad,"ways":{"google":false,"mapmap":true}}""")!!
            assertNull(bad, c.mapsJsKey)
            assertFalse(c.google)
            assertTrue(c.mapmap)
            assertNull(c.label)
        }
        val bare = CompanyServer.parseConfig("{}")!!
        assertNull(bare.mapsJsKey)
        assertFalse(bare.google)
        assertFalse(bare.mapmap)
        // Only true is true.
        assertFalse(CompanyServer.parseConfig("""{"ways":{"google":"true","mapmap":1}}""")!!.google)
        assertNull(CompanyServer.parseConfig("not json"))
        assertNull(CompanyServer.parseConfig("[]"))
    }

    @Test
    fun thePassGoesOnlyToTheCompanyServer() {
        assertTrue(CompanyServer.mayCarryToken("https://api.nastastopp.se/v1/config"))
        assertTrue(CompanyServer.mayCarryToken("https://api.nastastopp.se:443/v1/config"))
        for (
            url in listOf(
                "http://api.nastastopp.se/v1/config",
                "https://api.nastastopp.se:8443/v1/config",
                "https://api.nastastopp.se.evil.com/v1/config",
                "https://api.nastastopp.se@evil.com/v1/config",
                "https://user@api.nastastopp.se/v1/config",
                "https://nastastopp.se/v1/config",
                "https://map.nastastopp.se/",
                "https://routes.googleapis.com/directions/v2:computeRoutes",
                "https://API.nastastopp.se/v1/config",
                "ftp://api.nastastopp.se/",
                "api.nastastopp.se/v1/config",
                "https://api.nastastopp.se\\@evil.com/",
                "",
            )
        ) {
            assertFalse(url, CompanyServer.mayCarryToken(url))
        }
        // Every address of the server's may carry it.
        for (url in listOf(CompanyServer.ENROLL, CompanyServer.CONFIG, CompanyServer.LEAVE, CompanyServer.ROUTES, CompanyServer.MATRIX, CompanyServer.MAPMAP_ROUTE, CompanyServer.MAPMAP_MATRIX)) {
            assertTrue(url, CompanyServer.mayCarryToken(url))
        }
    }

    @Test
    fun theHeadersAreThePassTheFieldMaskAndTheAppsName() {
        assertEquals(
            mapOf("Authorization" to "Bearer $tokenText", "X-Goog-FieldMask" to RoutesApi.FIELDS, "User-Agent" to "NastaStopp"),
            CompanyServer.headers(token, RoutesApi.FIELDS),
        )
        assertEquals(mapOf("Authorization" to "Bearer $tokenText", "User-Agent" to "NastaStopp"), CompanyServer.headers(token))
    }

    @Test
    fun theServersOwnRefusalsAreReadByTheirWord() {
        val wall = 1_700_000_000_000L
        assertEquals(ServerTrouble.NotEnrolled, CompanyServer.classify(401, "not-enrolled", null, wall))
        assertEquals(ServerTrouble.Stopped, CompanyServer.classify(403, "stopped", null, wall))
        assertEquals(ServerTrouble.Role, CompanyServer.classify(403, "role", null, wall))
        assertEquals(ServerTrouble.DailyLimit(wall + 3_600_000L), CompanyServer.classify(429, "daily-limit", 3_600L, wall))
        assertEquals(ServerTrouble.NoKey, CompanyServer.classify(503, "no-key", null, wall))
        // The server answered that Google or mapmap did not: the server itself is up.
        assertEquals(ServerTrouble.UpstreamDown, CompanyServer.classify(502, "no-answer", null, wall))
        assertEquals(ServerTrouble.Down, CompanyServer.classify(500, "server", null, wall))
        // No answer came from the server at all (the app's own Reply with code 0).
        assertEquals(ServerTrouble.Down, CompanyServer.classify(0, CompanyServer.NO_ANSWER, null, wall))
        for (slug in listOf("field-mask", "bad-request", "too-large", "unknown", "a-word-the-app-does-not-know")) {
            assertEquals(slug, ServerTrouble.Bug(400), CompanyServer.classify(400, slug, null, wall))
        }
        assertEquals(ServerTrouble.Stopped, CompanyServer.classify(403, " Stopped ", null, wall))

        // Without a word the code is Google's or mapmap's, passed on: read as when they are asked directly.
        for (code in listOf(401, 403, 429, 500, 0)) {
            assertNull("$code", CompanyServer.classify(code, null, null, wall))
            assertNull("$code", CompanyServer.classify(code, "", 60L, wall))
        }
    }

    @Test
    fun theDailyLimitLastsUntilRetryAfterElseUtcMidnight() {
        val day = 24 * 60 * 60_000L
        val lateEvening = 20_000L * day + (23 * 60 + 59) * 60_000L + 30_000L // 23:59:30 UTC
        assertEquals(ServerTrouble.DailyLimit(lateEvening + 30_000L), CompanyServer.classify(429, "daily-limit", null, lateEvening))
        assertEquals(ServerTrouble.DailyLimit(lateEvening + 30_000L), CompanyServer.classify(429, "daily-limit", 0L, lateEvening))
        assertEquals(lateEvening + 30_000L, CompanyServer.nextUtcMidnight(lateEvening))
        assertEquals(21 * day, CompanyServer.nextUtcMidnight(20 * day))
        // A Retry-After past a day is held to a day.
        assertEquals(ServerTrouble.DailyLimit(lateEvening + day), CompanyServer.classify(429, "daily-limit", Long.MAX_VALUE, lateEvening))
    }

    @Test
    fun thePassNeverShowsInItsTextForm() {
        assertEquals("DeviceToken(***)", token.toString())
        assertEquals(token, DeviceToken("ab12".repeat(16)))
        assertEquals(token.hashCode(), DeviceToken("ab12".repeat(16)).hashCode())
        assertNotEquals(token, DeviceToken("cd34".repeat(16)))
        assertThrows(IllegalArgumentException::class.java) { DeviceToken("AB12".repeat(16)) }
        val refused = runCatching { DeviceToken(tokenText.drop(1)) }.exceptionOrNull()!!
        assertFalse(refused.message.orEmpty().contains(tokenText.drop(1)))
    }

    @Test
    fun theMapPageLoadsUnderTheCompanysOwnName() {
        assertEquals("https://map.nastastopp.se/", CompanyServer.PAGE_BASE)
        assertEquals("https://api.nastastopp.se", CompanyServer.BASE)
    }
}
