package se.eldebosh.nastastopp.settings

/**
 * Where one of the display's floating windows sits and how big it is: its middle as a share of the
 * screen's width ([x]) and height ([y]), and its size ([scale], 1 = as drawn).
 */
data class WindowPlace(val x: Float, val y: Float, val scale: Float = 1f) {
    companion object {
        const val SMALLEST = 0.6f
        const val LARGEST = 2.2f
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
