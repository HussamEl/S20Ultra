package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DistrictsTest {

    private val districts = Districts.parse(
        listOf(File("src/main/assets/${Districts.ASSET}"), File("app/src/main/assets/${Districts.ASSET}")).first { it.exists() }.readText(),
    )

    // Points inside three districts, from the municipality's own borders.
    private val herrhagen = 59.3759 to 13.51737
    private val lamberget = 59.37744 to 13.53694
    private val outside = 59.50 to 13.32

    @Test
    fun theArchiveHoldsEveryDistrictWithItsStreets() {
        assertEquals(66, districts.size)
        assertEquals(listOf("Herrhagen"), districts.of("Fryxellsgatan"))
        assertEquals(listOf("Tingvallastaden"), districts.of("drottninggatan"))
        assertNull(districts.of("Påhittadgatan"))
        // A street of Lantmäteriet's register that OpenStreetMap does not have yet.
        assertEquals(listOf("Henstad"), districts.of("Henstapromenaden"))
    }

    @Test
    fun aPointIsInTheDistrictTheMunicipalityDraws() {
        assertEquals("Herrhagen", districts.at(herrhagen.first, herrhagen.second))
        assertEquals("Lamberget", districts.at(lamberget.first, lamberget.second))
        assertNull(districts.at(outside.first, outside.second))
    }

    /** The geocoder's neighbourhood is never taken in Karlstad: a wrong one is what the driver heard. */
    @Test
    fun theDistrictIsTheMunicipalitysNotTheGeocoders() {
        assertEquals("Lamberget", districts.district(lamberget.first, lamberget.second, "Elverumsgatan", "Karlstad", "Tormestad"))
        // A point on the border that the street does not run to: the street's own district.
        assertEquals("Herrhagen", districts.district(lamberget.first, lamberget.second, "Fryxellsgatan", "Karlstad", "Lamberget"))
        // In Karlstad but in no district and on no known street: no district rather than a guess.
        assertNull(districts.district(outside.first, outside.second, "Påhittadgatan", "Karlstad", "Lamberget"))
        // A street of the same name in another town never gets a Karlstad district.
        assertEquals("Storfors", districts.district(59.53, 14.27, "Drottninggatan", "Storfors", "Storfors"))
        assertNull(districts.district(59.53, 14.27, "Drottninggatan", "Storfors", null))
        // Outside Karlstad the geocoder's district is kept.
        assertEquals("Kils centrum", districts.district(outside.first, outside.second, "Påhittadgatan", "Kil", "Kils centrum"))
    }

    @Test
    fun anOfficialDistrictNamedAfterAStreetIsSaid() {
        assertTrue(districts.isOfficial("Edsgatan"))
        assertFalse(districts.isOfficial("Kils centrum"))
        val said = GeoLogic.spokenName("Edsgatan", "Karlstad", null, false, AnnouncementDetail.DISTRICT, thoroughfare = "Gryningsvägen", official = true)
        assertEquals("Edsgatan", said)
        assertEquals("Karlstad", GeoLogic.spokenName("Edsgatan", "Karlstad", null, false, AnnouncementDetail.DISTRICT, thoroughfare = "Gryningsvägen"))
    }
}
