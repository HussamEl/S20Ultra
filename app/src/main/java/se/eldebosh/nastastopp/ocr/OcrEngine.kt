package se.eldebosh.nastastopp.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import se.eldebosh.nastastopp.core.ocr.OcrLine
import se.eldebosh.nastastopp.core.ocr.OcrLineMerger
import se.eldebosh.nastastopp.core.ocr.TilePlan
import se.eldebosh.nastastopp.core.ocr.TilePlanner
import kotlin.math.roundToInt

/**
 * On-device OCR with the bundled ML Kit Latin model (supports å ä ö). The image is read from
 * the content URI into memory only — never copied or written to storage — and released after use.
 * Long screenshots are processed in overlapping tiles (see [TilePlanner]).
 */
class OcrEngine(private val resolver: ContentResolver) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    class UnreadableImageException(message: String) : Exception(message)

    suspend fun recognize(uri: Uri): List<OcrLine> {
        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw UnreadableImageException("cannot open image")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadableImageException("not an image")

        val plan = TilePlanner.plan(bounds.outWidth, bounds.outHeight)
        val perTile = ArrayList<List<OcrLine>>(plan.tiles.size)
        val regionDecoder = withContext(Dispatchers.IO) { newRegionDecoder(bytes) }
        try {
            for (index in plan.tiles.indices) {
                val bitmap = withContext(Dispatchers.Default) {
                    decodeTile(bytes, regionDecoder, bounds.outWidth, bounds.outHeight, plan, index)
                } ?: throw UnreadableImageException("tile decode failed")
                try {
                    val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                    val offset = plan.tiles[index].top
                    val lines = text.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            val value = line.text.trim()
                            if (value.isEmpty()) null
                            else OcrLine(value, box.left, box.top + offset, box.right, box.bottom + offset)
                        }
                    }
                    perTile += lines
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            regionDecoder?.recycle()
        }
        return OcrLineMerger.merge(plan, perTile)
    }

    private fun newRegionDecoder(bytes: ByteArray): BitmapRegionDecoder? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        }
    } catch (_: Exception) {
        null
    }

    /** Decodes one tile scaled to the plan's width. Falls back to a full decode + crop. */
    private fun decodeTile(
        bytes: ByteArray,
        decoder: BitmapRegionDecoder?,
        srcW: Int,
        srcH: Int,
        plan: TilePlan,
        index: Int,
    ): Bitmap? {
        val tile = plan.tiles[index]
        val srcTop = (tile.top / plan.scale).roundToInt().coerceIn(0, srcH - 1)
        val srcBottom = (tile.bottom / plan.scale).roundToInt().coerceIn(srcTop + 1, srcH)
        val sample = sampleSizeFor(plan.scale)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val region = decoder?.let { d -> runCatching { d.decodeRegion(Rect(0, srcTop, srcW, srcBottom), opts) }.getOrNull() }
        val raw = region ?: run {
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            val top = (srcTop / sample).coerceIn(0, full.height - 1)
            val h = ((srcBottom - srcTop) / sample).coerceIn(1, full.height - top)
            Bitmap.createBitmap(full, 0, top, full.width, h).also { if (it !== full) full.recycle() }
        }
        val targetW = plan.width
        val targetH = tile.height.coerceAtLeast(1)
        if (raw.width == targetW && raw.height == targetH) return raw
        val scaled = raw.scale(targetW, targetH)
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    private fun sampleSizeFor(scale: Double): Int {
        var sample = 1
        while (scale * sample * 2 <= 1.0) sample *= 2
        return sample
    }
}
