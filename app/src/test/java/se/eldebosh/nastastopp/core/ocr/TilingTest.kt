package se.eldebosh.nastastopp.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilingTest {

    @Test
    fun normalScreenshotIsOneTileAndDownscaled() {
        val plan = TilePlanner.plan(1440, 3040)
        assertEquals(1.0, plan.scale, 1e-9)
        assertEquals(2, plan.tiles.size) // 3040 > 2880 → tiled
        val plan2 = TilePlanner.plan(2160, 4000) // wide → scaled to 1440 x 2667 → single tile
        assertEquals(1440, plan2.width)
        assertEquals(2667, plan2.height)
        assertEquals(1, plan2.tiles.size)
    }

    @Test
    fun longScreenshotTiledWithOverlap() {
        val plan = TilePlanner.plan(1080, 10000)
        val tileH = 2160
        assertTrue(plan.tiles.size > 4)
        plan.tiles.forEach { assertEquals(tileH, it.height) }
        assertEquals(0, plan.tiles.first().top)
        assertEquals(10000, plan.tiles.last().bottom)
        // Consecutive tiles overlap by at least 15 %.
        plan.tiles.zipWithNext().forEach { (a, b) ->
            val overlap = a.bottom - b.top
            assertTrue("overlap $overlap", overlap >= (tileH * 0.15).toInt() - 1)
        }
        // Responsible zones partition the image without gaps.
        var expectedStart = 0
        plan.tiles.indices.forEach { i ->
            val z = plan.responsibleZone(i)
            assertEquals(expectedStart, z.top)
            expectedStart = z.bottom
        }
        assertEquals(10000, expectedStart)
    }

    @Test
    fun dedupeOverlapLines() {
        val a = OcrLine("Storgatan 14, 65224 Karlstad", 40, 2000, 800, 2050)
        val b = OcrLine("Storgatan 14, 65224 Karlstad", 41, 2003, 801, 2052)
        val c = OcrLine("Storgatan 14, 65224 Karlstan", 40, 2004, 800, 2051) // ≥ 85 % similar
        val far = OcrLine("Storgatan 14, 65224 Karlstad", 40, 2600, 800, 2650)
        val result = OcrLineMerger.dedupe(listOf(a, b, c, far))
        assertEquals(2, result.size)
    }

    @Test
    fun differentLinesNotDeduped() {
        val a = OcrLine("Storgatan 14", 40, 100, 400, 140)
        val b = OcrLine("Lindvägen 9", 40, 104, 400, 142)
        assertEquals(2, OcrLineMerger.dedupe(listOf(a, b)).size)
    }

    @Test
    fun readingOrderTopThenLeft() {
        val right = OcrLine("12:48", 900, 102, 1000, 140)
        val left = OcrLine("Storgatan 14", 40, 100, 400, 142)
        val below = OcrLine("65224 Karlstad", 40, 160, 400, 200)
        val sorted = OcrLineMerger.sortReadingOrder(listOf(below, right, left))
        assertEquals(listOf("Storgatan 14", "12:48", "65224 Karlstad"), sorted.map { it.text })
    }

    @Test
    fun linesCutAtTileEdgeAreDropped() {
        val plan = TilePlanner.plan(1000, 5000)
        val t0 = plan.tiles[0]
        val cut = OcrLine("partial", 10, t0.bottom - 20, 300, t0.bottom)
        val inside = OcrLine("whole", 10, 100, 300, 140)
        assertEquals(listOf("whole"), OcrLineMerger.filterForTile(plan, 0, listOf(cut, inside)).map { it.text })
    }

    @Test
    fun mergeAcrossTilesKeepsOneCopy() {
        val plan = TilePlanner.plan(1000, 3700)
        assertEquals(2, plan.tiles.size)
        val overlapMid = (plan.tiles[0].bottom + plan.tiles[1].top) / 2
        val lineA = OcrLine("Lindvägen 9, 66430 Grums", 20, overlapMid - 20, 700, overlapMid + 20)
        val lineB = lineA.copy(top = lineA.top + 2, bottom = lineA.bottom + 2)
        val merged = OcrLineMerger.merge(plan, listOf(listOf(lineA), listOf(lineB)))
        assertEquals(1, merged.size)
    }
}
