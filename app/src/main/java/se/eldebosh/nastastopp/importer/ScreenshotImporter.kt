package se.eldebosh.nastastopp.importer

import android.net.Uri
import se.eldebosh.nastastopp.core.parse.AddressExtractor
import se.eldebosh.nastastopp.core.parse.ExtractedStop
import se.eldebosh.nastastopp.ocr.OcrEngine
import se.eldebosh.nastastopp.util.DebugLog

data class ImportResult(val stops: List<ExtractedStop>, val images: Int, val failedImages: Int)

/**
 * Runs OCR + address extraction on screenshots, in the order received. Only the extracted
 * addresses leave this class; the OCR text itself is dropped as soon as it has been parsed.
 */
class ScreenshotImporter(private val ocr: OcrEngine, private val extractor: AddressExtractor) {

    suspend fun import(uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult {
        val all = ArrayList<ExtractedStop>()
        var failed = 0
        var lineOffset = 0
        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size)
            try {
                val lines = ocr.recognize(uri).map { it.text }
                val stops = extractor.extract(lines, startOrder = lineOffset)
                lineOffset += lines.size
                DebugLog.d { "image ${index + 1}: ${lines.size} lines, ${stops.size} addresses" }
                for (stop in stops) {
                    val last = all.lastOrNull()
                    if (last != null && extractor.isSameAddress(last, stop)) continue // merge across images
                    all += stop
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w(e) { "image ${index + 1} failed" }
                failed++
            } catch (e: OutOfMemoryError) {
                DebugLog.w(e) { "image ${index + 1} too large" }
                failed++
            }
        }
        onProgress(uris.size, uris.size)
        return ImportResult(all, uris.size, failed)
    }
}
