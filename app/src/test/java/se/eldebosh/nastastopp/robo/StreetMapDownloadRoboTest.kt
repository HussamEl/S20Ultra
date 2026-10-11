package se.eldebosh.nastastopp.robo

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.core.geo.RoadSink
import se.eldebosh.nastastopp.core.geo.StreetMapBuilder
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.geo.OverpassDownload
import se.eldebosh.nastastopp.geo.StreetMapStore
import java.io.File
import java.io.IOException
import java.io.StringReader

/** The street map's download: what is asked for, and how an answer is read. Invented roads. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class StreetMapDownloadRoboTest {

    @Test
    fun theTilesCoverTheWholeRegionAndNeverAPosition() {
        val tiles = OverpassDownload.tiles()
        assertEquals(48, tiles.size)
        assertEquals(58.70, tiles.first().south, 1e-9)
        assertEquals(11.55, tiles.first().west, 1e-9)
        assertEquals(61.10, tiles.maxOf { it.north }, 1e-9)
        assertEquals(14.75, tiles.maxOf { it.east }, 1e-9)
        // Karlstad lies in exactly one tile.
        assertEquals(1, tiles.count { 59.38 >= it.south && 59.38 < it.north && 13.50 >= it.west && 13.50 < it.east })
        val q = OverpassDownload.query(tiles.first())
        assertTrue(q, q.contains("[\"name\"]") && q.contains("residential") && !q.contains("footway"))
    }

    @Test
    fun anAnswerIsReadRoadByRoad() {
        val json = """
            {"version":0.6,"generator":"Overpass API","elements":[
              {"type":"way","id":11,"bounds":{"minlat":59.3,"minlon":13.4,"maxlat":59.4,"maxlon":13.6},"nodes":[1,2,3],
               "geometry":[{"lat":59.38,"lon":13.49},null,{"lat":59.38,"lon":13.51}],"tags":{"highway":"residential","name":"Testgatan"}},
              {"type":"way","id":12,"nodes":[4,5],"geometry":[{"lat":59.39,"lon":13.49},{"lat":59.39,"lon":13.51}],"tags":{"highway":"service"}},
              {"type":"way","id":11,"nodes":[1,3],"geometry":[{"lat":59.38,"lon":13.49},{"lat":59.38,"lon":13.51}],"tags":{"name":"Testgatan"}},
              {"type":"way","id":13,"geometry":[{"lat":59.40,"lon":13.49},{"lat":59.40,"lon":13.51}],"tags":{"name":"Påhittad väg","highway":"tertiary"}}
            ]}
        """.trimIndent()
        val b = StreetMapBuilder()
        OverpassDownload.parse(StringReader(json), b)
        val map = b.build(0)
        assertEquals("unnamed and repeated roads are left out", 2, map.roadCount)
        assertEquals(listOf("Testgatan", "Påhittad väg"), map.names)
    }

    @Test(expected = IOException::class)
    fun anAnswerCutShortIsRefused() {
        OverpassDownload.parse(StringReader("""{"elements":[],"remark":"runtime error: Query timed out"}"""), StreetMapBuilder())
    }

    /** Four invented tiles, each with one road; [failAt] fails every try of that tile. */
    private class FakeSource(var failAt: Int) : StreetMapStore.TileSource {
        val asked = mutableListOf<Int>()
        private val tiles = List(4) { OverpassDownload.Tile(59.0 + it, 13.0, 59.5 + it, 13.5) }

        override fun tiles() = tiles

        override suspend fun fetch(tile: OverpassDownload.Tile, into: RoadSink, onBusy: (Boolean) -> Unit) {
            val i = tiles.indexOf(tile)
            asked += i
            if (i == failAt) throw IOException("HTTP 504")
            into.add(100L + i, "Påhittad väg $i", listOf(tile.south to 13.1, tile.south to 13.2))
        }
    }

    private fun await(store: StreetMapStore, until: (StreetMapStore.State) -> Boolean): StreetMapStore.State {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (until(store.state.value)) return store.state.value
            Thread.sleep(10)
        }
        throw AssertionError("still ${store.state.value}")
    }

    @Test
    fun aDownloadThatStopsContinuesFromTheTileWhereItStopped() {
        val app = ApplicationProvider.getApplicationContext<App>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val street = CurrentStreet(scope, lookup = { _, _ -> null })
        val parts = File(app.noBackupFilesDir, "streetmap/parts")
        val source = FakeSource(failAt = 2)
        val store = StreetMapStore(app, street, scope, source, pauseMs = 0)
        store.download()
        assertEquals(StreetMapStore.State.Failed(2, 4), await(store) { it is StreetMapStore.State.Failed })
        assertEquals(listOf(0, 1, 2), source.asked)

        // After a restart of the app it says where it stopped, and the next tap continues there.
        source.failAt = -1
        source.asked.clear()
        val again = StreetMapStore(app, street, scope, source, pauseMs = 0)
        assertEquals(StreetMapStore.State.Failed(2, 4), again.state.value)
        again.download()
        val ready = await(again) { it is StreetMapStore.State.Ready } as StreetMapStore.State.Ready
        assertEquals("the kept tiles are not asked for again", listOf(2, 3), source.asked)
        assertEquals(4, ready.roads)
        assertNotNull(street.map)
        assertFalse("the kept tiles are removed once the map is built", parts.exists())
        again.delete()
    }

    @Test
    fun aBusyServerIsWaitedOut() = runBlocking {
        var tries = 0
        val busy = mutableListOf<Boolean>()
        OverpassDownload.withRetries(longArrayOf(0, 0, 0), { busy += it }) {
            tries++
            if (tries < 3) throw IOException("HTTP 504")
        }
        assertEquals(3, tries)
        assertEquals(listOf(true, true, false), busy)

        tries = 0
        val failed = runCatching { OverpassDownload.withRetries(longArrayOf(0, 0), {}) { tries++; throw IOException("HTTP 429") } }
        assertTrue(failed.exceptionOrNull() is IOException)
        assertEquals("one try, then one per pause", 3, tries)
        assertEquals("a tile is tried 8 times", 7, OverpassDownload.RETRY_DELAYS_MS.size)
    }
}
