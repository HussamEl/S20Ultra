package se.eldebosh.nastastopp.robo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.core.geo.StreetMapBuilder
import se.eldebosh.nastastopp.geo.OverpassDownload
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
}
