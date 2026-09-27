package se.eldebosh.nastastopp.core.ocr

/** One recognised text line with its bounding box in full (scaled) image coordinates. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
}
