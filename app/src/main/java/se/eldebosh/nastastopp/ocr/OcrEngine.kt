package se.eldebosh.nastastopp.ocr

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import se.eldebosh.nastastopp.core.ocr.OcrLine
import se.eldebosh.nastastopp.core.ocr.OcrLineMerger
import se.eldebosh.nastastopp.core.ocr.TilePlan
import se.eldebosh.nastastopp.core.ocr.TilePlanner
import se.eldebosh.nastastopp.util.DebugLog
import kotlin.math.roundToInt

/** Which step of reading an image failed (shown to the driver so problems can be reported). */
enum class ReadStage { OPEN, DECODE, ENGINE, OCR, PARSE }

/** A screenshot could not be read. The message never contains OCR text or addresses. */
class ImageReadException(val stage: ReadStage, cause: Throwable) :
    Exception("${stage.name}: ${cause.javaClass.simpleName}: ${cause.message.orEmpty().take(160)}", cause)

/**
 * On-device OCR with the bundled ML Kit Latin model (supports å ä ö). The image is read from
 * the content URI into memory only — never copied or written to storage — and released after use.
 * Long screenshots are processed in overlapping tiles (see [TilePlanner]). If that path fails,
 * ML Kit's own image loader is tried once on the whole image.
 */
class OcrEngine(context: Context) {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private var recognizer: TextRecognizer? = null

    private class StageFailure(val stage: ReadStage, cause: Throwable) : Exception(cause)

    private fun recognizer(): TextRecognizer =
        recognizer ?: try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
        } catch (e: Throwable) {
            throw StageFailure(ReadStage.ENGINE, e)
        }

    suspend fun recognize(uri: Uri): List<OcrLine> {
        val first = try {
            return recognizeTiled(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: StageFailure) {
            if (e.stage == ReadStage.ENGINE) throw ImageReadException(e.stage, e.cause ?: e)
            e
        } catch (e: Throwable) {
            StageFailure(ReadStage.DECODE, e)
        }
        DebugLog.w(first.cause) { "tiled OCR failed at ${first.stage}, trying whole image" }
        try {
            return recognizeWhole(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Report the first (more informative) failure.
            throw ImageReadException(first.stage, first.cause ?: first)
        }
    }

    private suspend fun recognizeTiled(uri: Uri): List<OcrLine> {
        val bytes = try {
            withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: throw IllegalStateException("no input stream")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw StageFailure(ReadStage.OPEN, e)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw StageFailure(ReadStage.DECODE, IllegalArgumentException("not a supported image (${bounds.outMimeType})"))
        }

        val plan = TilePlanner.plan(bounds.outWidth, bounds.outHeight)
        val perTile = ArrayList<List<OcrLine>>(plan.tiles.size)
        val regionDecoder = withContext(Dispatchers.IO) { newRegionDecoder(bytes) }
        try {
            for (index in plan.tiles.indices) {
                val bitmap = withContext(Dispatchers.Default) {
                    decodeTile(bytes, regionDecoder, bounds.outWidth, bounds.outHeight, plan, index)
                } ?: throw StageFailure(ReadStage.DECODE, IllegalStateException("tile decode failed"))
                try {
                    val text = runOcr(InputImage.fromBitmap(bitmap, 0))
                    perTile += text.toLines(offsetY = plan.tiles[index].top)
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            regionDecoder?.recycle()
        }
        return OcrLineMerger.merge(plan, perTile)
    }

    /** Fallback: ML Kit loads (and rotates) the image itself; no tiling. */
    private suspend fun recognizeWhole(uri: Uri): List<OcrLine> {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(appContext, uri) }
        val lines = runOcr(image).toLines(offsetY = 0)
        return OcrLineMerger.sortReadingOrder(OcrLineMerger.dedupe(lines))
    }

    private suspend fun runOcr(image: InputImage): Text {
        val r = recognizer()
        return try {
            r.process(image).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw StageFailure(ReadStage.OCR, e)
        }
    }

    private fun Text.toLines(offsetY: Int): List<OcrLine> = textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val value = line.text.trim()
            if (value.isEmpty()) null else OcrLine(value, box.left, box.top + offsetY, box.right, box.bottom + offsetY)
        }
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
