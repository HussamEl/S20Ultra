package se.eldebosh.nastastopp.core.parse

import java.io.File

object TestLocalities {
    /** Loads the real bundled asset file (unit tests run with the module dir as working dir). */
    val instance: Localities by lazy {
        val candidates = listOf(
            File("src/main/assets/localities_se.txt"),
            File("app/src/main/assets/localities_se.txt"),
        )
        val file = candidates.first { it.exists() }
        Localities.parse(file.readText())
    }
}
