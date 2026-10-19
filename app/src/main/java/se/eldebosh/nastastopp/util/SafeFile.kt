package se.eldebosh.nastastopp.util

import java.io.File

/**
 * Writes [text] to [file] all at once: first to a file beside it, then put in its place, so a
 * stop halfway never leaves a broken file.
 */
fun writeWhole(file: File, text: String) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
