package se.eldebosh.nastastopp.importer

import android.net.Uri
import kotlinx.coroutines.CancellationException
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.ocr.ImageReadException
import se.eldebosh.nastastopp.ocr.OcrEngine
import se.eldebosh.nastastopp.ocr.ReadStage
import se.eldebosh.nastastopp.util.DebugLog

/**
 * @property errorDetail short technical reason for the first failed image (no OCR text, no
 *   addresses), shown to the driver so a problem can be reported.
 */
data class ImportResult(
    val stops: List<ExtractedStop>,
    val images: Int,
    val failedImages: Int,
    val errorDetail: String? = null,
)

/**
 * Runs OCR + address extraction on screenshots, in the order received. Only the extracted
 * addresses leave this class; the OCR text itself is dropped as soon as it has been parsed.
 */
class ScreenshotImporter(private val ocr: OcrEngine, private val extractor: AddressExtractor) {

    suspend fun import(uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult {
        val all = ArrayList<ExtractedStop>()
        var failed = 0
        var firstError: String? = null
        var lineOffset = 0
        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size)
            try {
                val lines = ocr.recognize(uri).map { it.text }
                val stops = try {
                    extractor.extract(lines, startOrder = lineOffset)
                } catch (e: RuntimeException) {
                    throw ImageReadException(ReadStage.PARSE, e)
                }
                lineOffset += lines.size
                DebugLog.d { "image ${index + 1}: ${lines.size} lines, ${stops.size} addresses" }
                for (stop in stops) {
                    val last = all.lastOrNull()
                    if (last != null && extractor.isSameTrip(last, stop)) continue // read twice across images
                    all += stop
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Any failure (including errors from the native OCR library) only skips this image.
                DebugLog.w(e) { "image ${index + 1} failed" }
                failed++
                if (firstError == null) {
                    firstError = (e as? ImageReadException)?.message ?: "${e.javaClass.simpleName}: ${e.message.orEmpty().take(160)}"
                }
            }
        }
        onProgress(uris.size, uris.size)
        return ImportResult(all, uris.size, failed, firstError)
    }
}
