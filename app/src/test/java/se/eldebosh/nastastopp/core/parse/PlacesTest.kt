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
        assertEquals("C-Sjukhuset Huvudentrén", Places.displayName("Centralsjukhuset Huvudentrén", isLocality))
        assertEquals("C-Sjukhuset", Places.displayName("Centralsjukhuset Karlstad", isLocality))
        assertEquals("Centralsjukhuset, huvudentrén", Places.spokenName("Centralsjukhuset Huvudentrén", isLocality))
        assertEquals("Karlstad", Places.known("Centralsjukhuset Huvudentrén")?.town)
        assertEquals("Centralsjukhuset, Karlstad", Places.known("CENTRALSJUKHUSET")?.candidate)
    }

    /** Wherever it is written, the hospital is written short. */
    @Test
    fun theHospitalIsWrittenShortEverywhere() {
        assertEquals("C-Sjukhuset Karlstad", Places.written("Centralsjukhuset Karlstad"))
        assertEquals("C-Sjukhuset huvudentrén, Karlstad", Places.written("CENTRALSJUKHUSET huvudentrén, Karlstad"))
        assertEquals("Storgatan 14, Karlstad", Places.written("Storgatan 14, Karlstad"))
    }

    /** An entrance is a door, not a department: written and said with the place, as written. */
    @Test
    fun everyEntranceGoesWithThePlace() {
        assertEquals("C-Sjukhuset Dialysentrén", Places.displayName("Centralsjukhuset Dialysentrén", isLocality))
        assertEquals("Centralsjukhuset, dialysentrén", Places.spokenName("Centralsjukhuset Dialysentrén", isLocality))
        assertEquals("C-Sjukhuset Dialysentrén", Places.displayName("Dialysentrén Centralsjukhuset", isLocality))
        assertEquals("Arvika Sjukhus Entré 3", Places.displayName("Arvika Sjukhus Entré 3", isLocality))
    }

    @Test
    fun theEntranceIsSaidAfterThePlace() {
        assertEquals("entré 3", Places.entrance("Arvika Sjukhus Entré 3"))
        assertEquals("Kils Vårdcentral", Places.spokenName("Kils Vårdcentral", isLocality))
        assertNull(Places.entrance("Kils Vårdcentral"))
    }
}
