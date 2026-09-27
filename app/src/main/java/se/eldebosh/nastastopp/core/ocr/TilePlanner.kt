package se.eldebosh.nastastopp.core.ocr

import kotlin.math.roundToInt

/** A vertical tile in scaled image coordinates. */
data class Tile(val top: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

data class TilePlan(
    /** Factor applied to the source image (≤ 1). */
    val scale: Double,
    val width: Int,
    val height: Int,
    val tiles: List<Tile>,
) {
    /**
     * Zone of the image this tile is responsible for: the halfway points of the overlaps with
     * its neighbours. Lines whose centre falls outside it are expected in the neighbouring tile.
     */
    fun responsibleZone(index: Int): Tile {
        val t = tiles[index]
        val start = if (index == 0) 0 else (tiles[index - 1].bottom + t.top) / 2
        val end = if (index == tiles.lastIndex) height else (t.bottom + tiles[index + 1].top) / 2
        return Tile(start, end)
    }
}

/**
 * Plans OCR tiles for long / scrolling screenshots: downscale so width ≤ [MAX_WIDTH]; if the
 * height is more than 2 × width, split into tiles of height 2 × width with 15 % overlap.
 */
object TilePlanner {
    const val MAX_WIDTH = 1440
    const val OVERLAP = 0.15

    fun plan(sourceWidth: Int, sourceHeight: Int): TilePlan {
        require(sourceWidth > 0 && sourceHeight > 0) { "empty image" }
        val scale = if (sourceWidth > MAX_WIDTH) MAX_WIDTH.toDouble() / sourceWidth else 1.0
        val w = maxOf(1, (sourceWidth * scale).roundToInt())
        val h = maxOf(1, (sourceHeight * scale).roundToInt())
        val tileH = 2 * w
        if (h <= tileH) return TilePlan(scale, w, h, listOf(Tile(0, h)))

        val step = maxOf(1, (tileH * (1 - OVERLAP)).roundToInt())
        val tiles = ArrayList<Tile>()
        var top = 0
        while (true) {
            if (top + tileH >= h) {
                // Last tile is aligned to the bottom so it is full height.
                val lastTop = maxOf(0, h - tileH)
                if (tiles.isEmpty() || lastTop > tiles.last().top) tiles += Tile(lastTop, h)
                break
            }
            tiles += Tile(top, top + tileH)
            top += step
        }
        return TilePlan(scale, w, h, tiles)
    }
}
