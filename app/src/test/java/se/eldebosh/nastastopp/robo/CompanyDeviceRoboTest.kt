package se.eldebosh.nastastopp.robo

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import se.eldebosh.nastastopp.core.nav.CompanyServer
import se.eldebosh.nastastopp.core.nav.DeviceToken
import se.eldebosh.nastastopp.core.nav.HttpAsk
import se.eldebosh.nastastopp.core.nav.ServerTrouble
import se.eldebosh.nastastopp.core.nav.WayAccess
import se.eldebosh.nastastopp.core.nav.WayService
import se.eldebosh.nastastopp.net.Reply
import se.eldebosh.nastastopp.net.WayTransport
import se.eldebosh.nastastopp.settings.CompanyDevice
import se.eldebosh.nastastopp.settings.DeviceState
import se.eldebosh.nastastopp.settings.EnrollResult
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.Crypto
import java.io.File
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The tablet's connection to the company's server: the code is sent once and never kept, the pass
 * is kept only sealed, goes only in the Authorization header to api.nastastopp.se, and never into
 * the settings, a file in plain text or the log; the server's answers move the state as the
 * owner's admin page decides. The code and the pass are the invented ones of testdata/README.md.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CompanyDeviceRoboTest {

    /** Reverses and flips every byte (the Android Keystore does not exist in unit tests). Counts each [forget]. */
    private class FakeCrypto : Crypto {
        var forgotten = 0

        override fun encrypt(plain: ByteArray) = plain.reversedArray().map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = sealed.map { (it.toInt() xor 0x5A).toByte() }.toByteArray().reversedArray()
        override fun forget() {
            forgotten++
        }
    }

    /** Records every ask and answers it by its address. */
    private class FakeServer : WayTransport {
        val asks = CopyOnWriteArrayList<HttpAsk>()
        val answers = mutableMapOf<String, Reply>()

        override fun send(ask: HttpAsk): Reply {
            asks += ask
            return answers[ask.url] ?: Reply(0, null, CompanyServer.NO_ANSWER)
        }

        fun asked(url: String) = asks.count { it.url == url }
    }

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val code = "ABCD2345"
    private val tokenText = "ab12".repeat(16)
    private val mapsKey = "AIzaSyB-0987654321zyxwvutsrqponmlkjih"
    private val label = "tablet-40274"
    private val file get() = File(app.noBackupFilesDir, CompanyDevice.FILE)

    private val crypto = FakeCrypto()
    private val server = FakeServer()
    private var clock = 1_000_000L
    private var wall = 1_790_000_000_000L

    private fun device() = CompanyDevice(app, crypto, server, wall = { wall }, now = { clock })

    private fun config(key: String? = mapsKey, mapmap: Boolean = true) = Reply(
        200,
        """{"mapsJsKey":${key?.let { "\"$it\"" } ?: "null"},"label":"$label","ways":{"google":true,"mapmap":$mapmap}}""",
    )

    private fun enrolled(): CompanyDevice {
        server.answers[CompanyServer.ENROLL] = Reply(200, """{"token":"$tokenText","label":"$label"}""")
        server.answers[CompanyServer.CONFIG] = config()
        val device = device()
        assertEquals(EnrollResult.Connected(label), runBlocking { device.enroll(code) })
        return device
    }

    @Before
    fun setUp() {
        file.delete()
        ShadowLog.clear()
    }

    @Test
    fun connectSendsTheCodeOnceThenAsksTheMapKeyWithThePass() {
        val device = device()
        assertEquals(DeviceState.NotConnected, device.state.value)
        assertEquals(WayAccess.None, device.access())
        server.answers[CompanyServer.ENROLL] = Reply(200, """{"token":"$tokenText","label":"$label"}""")
        server.answers[CompanyServer.CONFIG] = config()

        assertEquals(EnrollResult.Connected(label), runBlocking { device.enroll(" abcd-2345 ") })

        // The code: once, upper case, with the role, and no pass.
        val enroll = server.asks[0]
        assertEquals(CompanyServer.ENROLL, enroll.url)
        assertTrue(enroll.post)
        assertTrue(enroll.viaServer)
        assertFalse(enroll.headers.containsKey("Authorization"))
        assertEquals("""{"code":"ABCD2345","role":"tablet"}""", enroll.body)
        // Then the map key, at once, with the pass.
        val config = server.asks[1]
        assertEquals(CompanyServer.CONFIG, config.url)
        assertFalse(config.post)
        assertEquals("Bearer $tokenText", config.headers["Authorization"])
        assertEquals(2, server.asks.size)

        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device.state.value)
        assertEquals(WayAccess.Company(DeviceToken(tokenText), google = true, mapmap = true), device.access())

        // Kept only sealed, and only here: neither the pass nor the map key in plain text in any of the app's files.
        assertTrue(file.exists())
        assertTrue(file.absolutePath.startsWith(app.noBackupFilesDir.absolutePath))
        for (f in app.dataDir.walkTopDown().filter { it.isFile }) {
            val text = String(f.readBytes(), Charsets.ISO_8859_1)
            assertFalse(f.path, text.contains(tokenText) || text.contains(mapsKey) || text.contains(code))
        }
        val settings = app.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(settings.contains(tokenText))

        // The same tablet later finds its connection again.
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device().state.value)
        assertPassOnlyInItsHeader()
        assertNothingLogged()
    }

    @Test
    fun aRefusedCodeSavesNothing() {
        val device = device()
        assertEquals("not a code: nothing is sent", EnrollResult.WrongCode, runBlocking { device.enroll("ABCD234") })
        assertEquals(EnrollResult.WrongCode, runBlocking { device.enroll("OBCD2345") })
        assertTrue(server.asks.isEmpty())

        for ((answer, result) in listOf(
            Reply(403, null, "unknown") to EnrollResult.WrongCode,
            Reply(400, null, "bad-request") to EnrollResult.WrongCode,
            Reply(0, null, CompanyServer.NO_ANSWER) to EnrollResult.Offline,
            Reply(500, null, "server") to EnrollResult.ServerError(500),
        )) {
            server.answers[CompanyServer.ENROLL] = answer
            assertEquals(result, runBlocking { device.enroll(code) })
            assertFalse(file.exists())
            assertEquals(DeviceState.NotConnected, device.state.value)
        }
        assertEquals("no map key is asked without a pass", 0, server.asked(CompanyServer.CONFIG))
        assertNothingLogged()
    }

    @Test
    fun connectedEvenWhenTheMapKeyDoesNotComeAtOnce() {
        server.answers[CompanyServer.ENROLL] = Reply(200, """{"token":"$tokenText","label":"$label"}""")
        val device = device()
        assertEquals(EnrollResult.Connected(label), runBlocking { device.enroll(code) })
        assertEquals(DeviceState.Connected(label, null, google = false, mapmap = false, down = true), device.state.value)
        // Asked again later, with the pass kept.
        server.answers[CompanyServer.CONFIG] = config()
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device.state.value)
        // Unknown to the server, then a new code: connected again.
        server.answers[CompanyServer.CONFIG] = Reply(401, null, "not-enrolled")
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Unknown, device.state.value)
        server.answers[CompanyServer.CONFIG] = config()
        assertEquals(EnrollResult.Connected(label), runBlocking { device.enroll(code) })
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device.state.value)
    }

    @Test
    fun theMapKeyIsAskedAtMostEveryTenMinutesUnlessCheckedNow() {
        val device = enrolled()
        assertEquals(1, server.asked(CompanyServer.CONFIG))
        runBlocking { device.refresh(force = false) }
        runBlocking { device.refresh(force = false) }
        assertEquals("within ten minutes of the last", 1, server.asked(CompanyServer.CONFIG))
        runBlocking { device.refresh(force = true) }
        assertEquals("Check now asks at once", 2, server.asked(CompanyServer.CONFIG))
        clock += CompanyDevice.CONFIG_MS
        runBlocking { device.refresh(force = false) }
        runBlocking { device.refresh(force = false) }
        assertEquals(3, server.asked(CompanyServer.CONFIG))
    }

    @Test
    fun aStoppedTabletDropsTheMapKeyAndKeepsItsPassForTheOwnersStart() {
        val device = enrolled()
        server.answers[CompanyServer.CONFIG] = Reply(403, null, "stopped")
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Stopped(label), device.state.value)
        assertEquals(WayAccess.None, device.access())
        // Opened again, it is still stopped, with no map key.
        assertEquals(DeviceState.Stopped(label), device().state.value)

        // The owner's Start, then Check now: connected again, without a new code.
        server.answers[CompanyServer.CONFIG] = config()
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device.state.value)
        assertEquals("Bearer $tokenText", server.asks.last().headers["Authorization"])

        // Unknown to the server: a new code is needed.
        server.answers[CompanyServer.CONFIG] = Reply(401, null, "not-enrolled")
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Unknown, device.state.value)
        assertEquals(WayAccess.None, device.access())
        assertEquals(DeviceState.Unknown, device().state.value)
    }

    @Test
    fun noKeyOrNoAnswerKeepsTheConnection() {
        val device = enrolled()
        // No answer: the sealed map key stays, so the map still comes.
        server.answers.remove(CompanyServer.CONFIG)
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true, down = true), device.state.value)
        assertEquals(mapsKey, (device().state.value as DeviceState.Connected).mapKey)
        // A key replaced only by another valid one.
        server.answers[CompanyServer.CONFIG] = config(key = "AIzaSy><script>alert(1)</script>")
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device.state.value)
        // The server has no map key any more.
        server.answers[CompanyServer.CONFIG] = Reply(503, null, "no-key")
        runBlocking { device.refresh(force = true) }
        assertEquals(DeviceState.Connected(label, null, google = true, mapmap = true), device.state.value)
        assertNull((device().state.value as DeviceState.Connected).mapKey)
    }

    @Test
    fun theMapsRefusalsMoveTheState() {
        val device = enrolled()
        val until = wall + 3_600_000L
        device.onTrouble(WayService.GOOGLE_MATRIX, ServerTrouble.DailyLimit(until))
        assertEquals(mapOf(WayService.GOOGLE_MATRIX to until), (device.state.value as DeviceState.Connected).limits)
        device.onTrouble(WayService.MAPMAP_ROUTE, ServerTrouble.NoKey)
        assertEquals(WayAccess.Company(DeviceToken(tokenText), google = true, mapmap = false), device.access())
        // Google or mapmap did not answer the server: the server is up, and /v1/config is not asked more often.
        device.onTrouble(WayService.GOOGLE_ROUTES, ServerTrouble.UpstreamDown)
        assertFalse((device.state.value as DeviceState.Connected).down)
        device.onTrouble(WayService.GOOGLE_ROUTES, ServerTrouble.Down)
        assertTrue((device.state.value as DeviceState.Connected).down)
        device.onTrouble(WayService.GOOGLE_ROUTES, ServerTrouble.Bug(400))
        assertTrue(device.state.value is DeviceState.Connected)
        device.onTrouble(WayService.GOOGLE_ROUTES, ServerTrouble.Stopped)
        assertEquals(DeviceState.Stopped(label), device.state.value)
        device.onTrouble(WayService.GOOGLE_ROUTES, ServerTrouble.NotEnrolled)
        assertEquals(DeviceState.Unknown, device.state.value)
    }

    @Test
    fun aSavedConnectionThatWillNotOpenIsLost() {
        file.writeText(Base64.encodeToString("not sealed".toByteArray(), Base64.NO_WRAP))
        val device = device()
        assertEquals(DeviceState.Lost, device.state.value)
        assertEquals(WayAccess.None, device.access())
        runBlocking { device.refresh(force = true) }
        assertTrue("nothing to ask with", server.asks.isEmpty())
        // A new code connects it again.
        server.answers[CompanyServer.ENROLL] = Reply(200, """{"token":"$tokenText","label":"$label"}""")
        server.answers[CompanyServer.CONFIG] = config()
        assertEquals(EnrollResult.Connected(label), runBlocking { device.enroll(code) })
        assertEquals(DeviceState.Connected(label, mapsKey, google = true, mapmap = true), device().state.value)
    }

    @Test
    fun disconnectLeavesTheServerAndForgetsThePass() {
        val device = enrolled()
        server.answers[CompanyServer.LEAVE] = Reply(200, "{}")
        runBlocking { device.disconnect() }
        assertEquals(1, server.asked(CompanyServer.LEAVE))
        val leave = server.asks.last()
        assertTrue(leave.post)
        assertEquals("Bearer $tokenText", leave.headers["Authorization"])
        assertFalse(file.exists())
        assertEquals(1, crypto.forgotten)
        assertEquals(DeviceState.NotConnected, device.state.value)
        assertEquals(WayAccess.None, device.access())
        assertEquals(DeviceState.NotConnected, device().state.value)
        assertPassOnlyInItsHeader()
        assertNothingLogged()
    }

    @Test
    fun noTextFormShowsThePassOrTheKey() {
        val device = enrolled()
        for (text in listOf(device.toString(), device.state.value.toString(), device.access().toString())) {
            assertFalse(text, text.contains(tokenText) || text.contains(mapsKey))
        }
        assertEquals("CompanyDevice(***)", device.toString())
    }

    /** The pass goes only in the Authorization header, and only to the company's server. */
    private fun assertPassOnlyInItsHeader() {
        for (ask in server.asks) {
            assertFalse(ask.url.contains(tokenText))
            assertFalse(ask.body.orEmpty().contains(tokenText))
            assertFalse(ask.headers.filterKeys { it != "Authorization" }.values.any { it.contains(tokenText) })
            if (ask.headers["Authorization"]?.contains(tokenText) == true) {
                assertEquals(CompanyServer.HOST, URI(ask.url).host)
                assertTrue(CompanyServer.mayCarryToken(ask.url))
            }
        }
    }

    private fun assertNothingLogged() {
        val logs = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable}" }
        assertFalse(logs.contains(tokenText))
        assertFalse(logs.contains(code))
        assertFalse(logs.contains(mapsKey))
    }
}
