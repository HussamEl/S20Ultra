package se.eldebosh.nastastopp.robo

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.core.nav.CompanyServer
import se.eldebosh.nastastopp.core.nav.DeviceToken
import se.eldebosh.nastastopp.core.nav.HttpAsk
import se.eldebosh.nastastopp.core.nav.MapWay
import se.eldebosh.nastastopp.core.nav.OrderPlanner
import se.eldebosh.nastastopp.core.nav.RoutesApi
import se.eldebosh.nastastopp.core.nav.ServerTrouble
import se.eldebosh.nastastopp.core.nav.WayAccess
import se.eldebosh.nastastopp.core.nav.WayService
import se.eldebosh.nastastopp.core.nav.WaySource
import se.eldebosh.nastastopp.net.Reply
import se.eldebosh.nastastopp.net.WayTransport
import se.eldebosh.nastastopp.ui.screens.RouteMap
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The tablet's map with the company's server: the page loads under the company's own address and
 * never gets the pass; the server's refusal of a stopped tablet stops every ask until the access
 * changes; the page's own words never show its key. The stops and the pass are the invented ones
 * of testdata/README.md.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RouteMapServerRoboTest {

    /** Records every ask and answers it by its service's address. */
    private class FakeTransport(private val answer: (HttpAsk) -> Reply) : WayTransport {
        val asks = CopyOnWriteArrayList<HttpAsk>()

        override fun send(ask: HttpAsk): Reply {
            asks += ask
            return answer(ask)
        }
    }

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenText = "ab12".repeat(16)
    private val company = WayAccess.Company(DeviceToken(tokenText), google = true, mapmap = true)
    private val mapsKey = "AIzaSyB-0987654321zyxwvutsrqponmlkjih"

    private val stops = listOf(
        MapWay.Stop(59.3245, 13.4655, "Hamngatan 7, 663 30 Skoghall", id = 3, time = "08:05", label = "Hamngatan", name = "Testsson"),
        MapWay.Stop(59.504, 13.238, "Skolgatan 5, 664 30 Grums", id = 5, time = "10:20", label = "Skolgatan", name = "Provare"),
    )
    private val otherStops = stops.reversed()
    private val trips = listOf(OrderPlanner.Trip(8 * 3600, true, 1), OrderPlanner.Trip(10 * 3600, false, 1))

    @After
    fun tearDown() = scope.cancel()

    /** Lets the asks on their own threads finish and their answers reach the main thread. */
    private fun settle(until: () -> Boolean = { false }) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (until()) return
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun theCompanysKeyLoadsUnderItsOwnAddressAndThePageNeverGetsThePass() {
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, FakeTransport { Reply(0, null) })
        val loaded = shadowOf(map.view).lastLoadDataWithBaseURL
        assertEquals("https://map.nastastopp.se/", loaded.baseUrl)
        assertTrue(loaded.data.contains(mapsKey))
        assertFalse(loaded.data.contains(tokenText))
        assertFalse(loaded.data.contains("Bearer"))
        map.useWays(WaySource.GOOGLE, company)
        assertFalse(shadowOf(map.view).lastLoadDataWithBaseURL.data.contains(tokenText))
        map.destroy()

        // The driver's own key: the address it is restricted to, as before.
        val own = RouteMap(app, mapsKey, true, "#000000", scope)
        assertEquals("https://nastastopp.app/", shadowOf(own.view).lastLoadDataWithBaseURL.baseUrl)
        own.destroy()
    }

    @Test
    fun aStoppedTabletAsksNothingMoreUntilItsAccessChanges() {
        val transport = FakeTransport { Reply(403, null, "stopped") }
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, transport)
        val troubles = mutableListOf<Pair<WayService, ServerTrouble>>()
        map.onServerTrouble = { service, trouble -> troubles += service to trouble }
        map.useWays(WaySource.GOOGLE, company)

        map.show(MapWay(59.3794, 13.503, stops = stops))
        settle { troubles.isNotEmpty() }
        assertEquals(1, transport.asks.size)
        assertEquals(CompanyServer.ROUTES, transport.asks[0].url)
        assertEquals(listOf(WayService.GOOGLE_ROUTES to ServerTrouble.Stopped), troubles)
        assertNotNull(map.routeAnswer)
        val answer = map.routeAnswer!!
        assertEquals(ServerTrouble.Stopped, answer.trouble)
        assertTrue(answer.viaServer)
        assertEquals(403, answer.code)

        // Not again: neither the next stops elsewhere, nor the driver's way, nor Suggest.
        map.show(MapWay(59.40, 13.55, stops = otherStops))
        map.focus(otherStops, 0)
        map.suggest(stops, trips, 7 * 3600)
        settle()
        assertEquals(1, transport.asks.size)
        assertFalse(map.suggesting)

        // A new access (the owner's Start, then Check now) asks afresh.
        map.useWays(WaySource.GOOGLE, company.copy(mapmap = false))
        map.unfocus()
        map.show(MapWay(59.41, 13.56, stops = stops))
        settle { transport.asks.size > 1 }
        assertEquals(2, transport.asks.size)
        map.destroy()
    }

    @Test
    fun anUpstreamRefusalPassedOnIsGooglesNotTheServers() {
        // Google's own 403, passed on by the server without its word: no trouble for the connection.
        val transport = FakeTransport { Reply(403, null) }
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, transport)
        val troubles = mutableListOf<ServerTrouble>()
        map.onServerTrouble = { _, trouble -> troubles += trouble }
        map.useWays(WaySource.GOOGLE, company)
        map.show(MapWay(59.3794, 13.503, stops = stops))
        settle { map.routeAnswer != null }
        assertEquals(RouteMap.WayAnswer(403, WaySource.GOOGLE, viaServer = true, trouble = null), map.routeAnswer)
        assertTrue(troubles.isEmpty())
        // Refused for these stops only.
        map.show(MapWay(59.40, 13.55, stops = stops))
        settle()
        assertEquals(1, transport.asks.size)
        map.destroy()
    }

    /** A daily limit lasts until midnight: a way already given is still drawn meanwhile, without an ask. */
    @Test
    fun aWayAlreadyGivenIsDrawnWhileTheDailyLimitHolds() {
        val way = """{"routes":[{"distanceMeters":5300,"duration":"731s","polyline":{"encodedPolyline":"_p~iF~ps|U_ulLnnqC"}}]}"""
        val answered = AtomicInteger()
        val transport = FakeTransport { if (answered.getAndIncrement() < 2) Reply(200, way) else Reply(429, null, "daily-limit", 3_600L) }
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, transport)
        map.useWays(WaySource.GOOGLE, company)
        val third = stops.take(1)

        map.show(MapWay(59.3794, 13.503, stops = stops))
        settle { map.routeKey == RouteMap.keyOf(stops) }
        map.show(MapWay(59.3795, 13.503, stops = otherStops))
        settle { map.routeKey == RouteMap.keyOf(otherStops) }
        map.show(MapWay(59.3796, 13.503, stops = third))
        settle { map.routeAnswer != null }
        assertTrue(map.routeAnswer?.trouble is ServerTrouble.DailyLimit)
        assertEquals(3, transport.asks.size)

        // Back to the first stops: their way is known, and drawn though the ways may not be asked.
        map.show(MapWay(59.3797, 13.503, stops = stops))
        settle()
        assertEquals(RouteMap.keyOf(stops), map.routeKey)
        assertEquals(3, transport.asks.size)
        map.destroy()
    }

    /** Suggest is the driver's tap (248): the ways' wait never holds it, and a refusal that still holds answers it with its words. */
    @Test
    fun suggestIsAskedOnTheDriversTapAndARefusalThatHoldsSaysWhy() {
        val transport = FakeTransport { ask ->
            if (ask.url == CompanyServer.MATRIX) Reply(429, null, "daily-limit", 3_600L) else Reply(0, null, CompanyServer.NO_ANSWER)
        }
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, transport)
        map.useWays(WaySource.GOOGLE, company)
        map.show(MapWay(59.3794, 13.503, stops = stops))
        settle { map.routeAnswer != null }
        assertEquals(ServerTrouble.Down, map.routeAnswer?.trouble)

        // The ways wait for a server that does not answer...
        map.show(MapWay(59.40, 13.55, stops = otherStops))
        settle()
        assertEquals(1, transport.asks.size)
        // ...the driver's Suggest does not.
        map.suggest(stops, trips, 7 * 3600)
        settle { !map.suggesting }
        assertEquals(2, transport.asks.size)
        assertEquals(CompanyServer.MATRIX, transport.asks[1].url)
        val limit = map.routeAnswer!!
        assertTrue(limit.trouble is ServerTrouble.DailyLimit)
        assertEquals(429, limit.code)

        // Tapped again while today's limit holds: nothing is asked, and the tap is answered with why.
        map.suggest(stops, trips, 7 * 3600)
        settle()
        assertEquals(2, transport.asks.size)
        assertFalse(map.suggesting)
        assertEquals(RouteMap.WayAnswer(0, WaySource.GOOGLE, viaServer = true, trouble = limit.trouble), map.routeAnswer)
        map.destroy()
    }

    @Test
    fun withoutAnAccessNothingIsAsked() {
        val transport = FakeTransport { Reply(200, null) }
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, transport)
        map.useWays(WaySource.GOOGLE, WayAccess.None)
        map.show(MapWay(59.3794, 13.503, stops = stops))
        map.suggest(stops, trips, 7 * 3600)
        settle()
        assertTrue(transport.asks.isEmpty())
        map.destroy()
    }

    @Test
    fun theDriversOwnKeyGoesStraightToGoogleNeverToTheServer() {
        val transport = FakeTransport { Reply(0, null) }
        val map = RouteMap(app, mapsKey, true, "#000000", scope, RouteMap.BASE, transport)
        map.useWays(WaySource.GOOGLE, WayAccess.Own(mapsKey, null))
        map.show(MapWay(59.3794, 13.503, stops = stops))
        settle { transport.asks.isNotEmpty() }
        val ask = transport.asks.single()
        assertEquals(RoutesApi.URL, ask.url)
        assertFalse(ask.viaServer)
        assertEquals(mapsKey, ask.headers["X-Goog-Api-Key"])
        assertFalse(URI(ask.url).host == CompanyServer.HOST)
        map.destroy()
    }

    @Test
    fun thePagesOwnWordsNeverShowItsKey() {
        val map = RouteMap(app, mapsKey, true, "#000000", scope, CompanyServer.PAGE_BASE, FakeTransport { Reply(0, null) })
        val bridge = shadowOf(map.view).getJavascriptInterface("Android")
        bridge.javaClass.getMethod("onProblem", String::class.java, String::class.java).apply { isAccessible = true }
            .invoke(bridge, "page", "Error at maps/api/js?key=$mapsKey&callback=ready")
        settle { map.troubleDetail != null }
        val detail = map.troubleDetail!!
        assertFalse(detail, detail.contains(mapsKey))
        assertTrue(detail, detail.contains("key=***"))
        assertEquals(RouteMap.Trouble.PAGE, map.trouble)
        map.destroy()
    }
}
