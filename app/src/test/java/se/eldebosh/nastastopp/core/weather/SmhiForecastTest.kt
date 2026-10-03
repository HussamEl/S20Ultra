package se.eldebosh.nastastopp.core.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SmhiForecastTest {
    private val answer = """
        {"createdTime":"2026-09-30T10:00:00Z","timeSeries":[
          {"time":"2026-09-30T11:00:00Z","data":{"air_temperature":14.1,"symbol_code":6,"cloud_area_fraction":8}},
          {"time":"2026-09-30T12:00:00Z","data":{"air_temperature":-2.6,"symbol_code":26}}
        ]}
    """.trimIndent()

    @Test
    fun theHourNearestNow() {
        val w = SmhiForecast.parse(answer, Instant.parse("2026-09-30T11:10:00Z").toEpochMilli())!!
        assertEquals(DisplayWeather(14, 6), w)
        assertEquals(WeatherKind.CLOUDY, w.kind)
        assertEquals("Mulet", w.swedish)
        val later = SmhiForecast.parse(answer, Instant.parse("2026-09-30T11:50:00Z").toEpochMilli())!!
        assertEquals(-3, later.tempC)
        assertEquals(WeatherKind.SNOW, later.kind)
        assertEquals("Snöfall", later.swedish)
    }

    @Test
    fun anAnswerThatCannotBeReadGivesNothing() {
        assertNull(SmhiForecast.parse("not json", 0))
        assertNull(SmhiForecast.parse("""{"timeSeries":[]}""", 0))
        assertNull(SmhiForecast.parse("""{"timeSeries":[{"time":"2026-09-30T11:00:00Z","data":{"symbol_code":99,"air_temperature":1}}]}""", 0))
    }
}
