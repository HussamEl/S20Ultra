package se.eldebosh.nastastopp.settings

/**
 * Where one of the display's floating windows sits and how big it is: its middle as a share of the
 * screen's width ([x]) and height ([y]), and its [width] and [height] as shares of the screen's
 * (0: as its content is drawn). Each side is sized by itself: a window can be made narrower
 * without being made lower.
 */
data class WindowPlace(val x: Float, val y: Float, val width: Float = 0f, val height: Float = 0f) {
    companion object {
        /** The least share of the screen a window is made in either direction. */
        const val SMALLEST = 0.15f
    }
}

/** The places the driver last left the display's floating windows in, by window. */
interface WindowPlaces {
    fun place(name: String): WindowPlace?

    fun keep(name: String, place: WindowPlace)

    /** Kept only while the screen is shown (previews and tests). */
    class InMemory : WindowPlaces {
        private val places = HashMap<String, WindowPlace>()

        override fun place(name: String): WindowPlace? = places[name]

        override fun keep(name: String, place: WindowPlace) {
            places[name] = place
        }
    }
}
