package se.eldebosh.nastastopp.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which named places the passengers may see and hear, and how. The place names are invented, except the hospital. */
class PlacesTest {

    private val isLocality: (String) -> Boolean = TestLocalities.instance::contains

    @Test
    fun aPlaceOfCareIsShownByItsOwnName() {
        assertEquals("Kils Vårdcentral", Places.publicName("Kils Vårdcentral", isLocality))
        assertEquals("Provby Vårdcentral", Places.publicName("Provby Vårdcentral", isLocality))
        assertEquals("Centralsjukhuset", Places.publicName("Centralsjukhuset Huvudentrén", isLocality))
    }

    @Test
    fun aHomeIsNeverShown() {
        assertNull(Places.publicName("Provby Äldreboende", isLocality))
        assertNull(Places.publicName("Kortboende Provgården", isLocality))
        assertNull(Places.publicName("Provby Servicehus", isLocality))
        assertNull(Places.displayName("Kortboende Provgården", isLocality))
        assertNull(Places.spokenName("Provby Äldreboende", isLocality))
        assertNull(Places.publicName(null, isLocality))
    }

    @Test
    fun aDepartmentIsNeverShownWithThePlace() {
        assertEquals("Vårdcentral", Places.publicName("Psykiatri Vårdcentral", isLocality))
        assertEquals("Arvika Sjukhus", Places.publicName("Psykiatriska mottagningen Arvika Sjukhus", isLocality))
    }

    @Test
    fun theHospitalIsWrittenShortAndSaidInFull() {
        assertEquals("Sjukhuset C", Places.displayName("Centralsjukhuset Huvudentrén", isLocality))
        assertEquals("Centralsjukhuset, huvudentrén", Places.spokenName("Centralsjukhuset Huvudentrén", isLocality))
        assertEquals("Karlstad", Places.known("Centralsjukhuset Huvudentrén")?.town)
        assertEquals("Centralsjukhuset, Karlstad", Places.known("CENTRALSJUKHUSET")?.candidate)
    }

    @Test
    fun theEntranceIsSaidAfterThePlace() {
        assertEquals("entré 3", Places.entrance("Arvika Sjukhus Entré 3"))
        assertEquals("Kils Vårdcentral", Places.spokenName("Kils Vårdcentral", isLocality))
        assertNull(Places.entrance("Kils Vårdcentral"))
    }
}
