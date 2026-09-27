package se.eldebosh.nastastopp.core.ocr

import se.eldebosh.nastastopp.core.parse.TextNorm
import kotlin.math.abs

/** Combines per-tile OCR results into one ordered list of lines. */
object OcrLineMerger {

    const val SIMILARITY = 0.85

    /**
     * Keeps a tile's lines (already mapped to full-image coordinates) that belong to it: lines
     * cut by the tile's inner edges are dropped (the neighbour sees them whole), and lines whose
     * centre is far outside the tile's responsible zone are dropped.
     */
    fun filterForTile(plan: TilePlan, index: Int, lines: List<OcrLine>): List<OcrLine> {
        if (plan.tiles.size == 1) return lines
        val tile = plan.tiles[index]
        val zone = plan.responsibleZone(index)
        val margin = ((tile.height * TilePlanner.OVERLAP) / 4).toInt()
        val edge = 2
        return lines.filter { l ->
            val cutTop = index > 0 && l.top <= tile.top + edge
            val cutBottom = index < plan.tiles.lastIndex && l.bottom >= tile.bottom - edge
            !cutTop && !cutBottom && l.centerY >= zone.top - margin && l.centerY <= zone.bottom + margin
        }
    }

    /**
     * Removes duplicates from overlap zones: same text or ≥ 85 % similarity within a small
     * vertical distance. The longer text wins.
     */
    fun dedupe(lines: List<OcrLine>): List<OcrLine> {
        val kept = ArrayList<OcrLine>()
        for (line in lines.sortedBy { it.top }) {
            val dupIndex = kept.indexOfFirst { isDuplicate(it, line) }
            if (dupIndex < 0) {
                kept += line
            } else if (line.text.trim().length > kept[dupIndex].text.trim().length) {
                kept[dupIndex] = line
            }
        }
        return kept
    }

    fun isDuplicate(a: OcrLine, b: OcrLine): Boolean {
        val tolerance = maxOf(a.height, b.height, 8)
        if (abs(a.centerY - b.centerY) > tolerance) return false
        val horizontalOverlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        if (horizontalOverlap <= 0) return false
        return a.text.trim() == b.text.trim() || TextNorm.similarity(a.text, b.text) >= SIMILARITY
    }

    /** Top-to-bottom, then left-to-right for lines on the same visual row. */
    fun sortReadingOrder(lines: List<OcrLine>): List<OcrLine> {
        val byTop = lines.sortedWith(compareBy({ it.top }, { it.left }))
        val rows = ArrayList<MutableList<OcrLine>>()
        for (line in byTop) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowTop = row.minOf { it.top }
                val rowBottom = row.maxOf { it.bottom }
                val center = line.centerY
                if (center >= rowTop && center <= rowBottom) {
                    row += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows.flatMap { row -> row.sortedBy { it.left } }
    }

    /** Full pipeline for per-tile results (each already offset to full-image coordinates). */
    fun merge(plan: TilePlan, perTile: List<List<OcrLine>>): List<OcrLine> {
        val all = perTile.flatMapIndexed { i, lines -> filterForTile(plan, i, lines) }
        return sortReadingOrder(dedupe(all))
    }
}
