package se.eldebosh.nastastopp.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AddressRegisterTest {

    // Invented streets, laid out as tools/make-addresses.py writes them.
    private val sample = AddressRegister.parse(
        """
        # street|post town|postcode|municipality|number:lat:lng;…
        Provgatan|Karlstad|65224|Karlstad|1:59.38:13.50;14:59.381:13.501;14B:59.382:13.502
        Provgatan|Molkom|65560|Karlstad|14:59.60:13.72;20:59.601:13.721
        Exempelvägen|Skoghall|66330|Hammarö|3:59.32:13.46
        Exempelvägen|Skoghall|66331|Hammarö|9:59.33:13.47
        Testallén|Karlstad|65225|Karlstad|5:59.37:13.49
        Testallén|Karlstad|65226|Karlstad|5:59.39:13.52
        """.trimIndent(),
    )

    @Test
    fun anAddressIsFoundWhereItIsWritten() {
        val found = sample.find("Provgatan 14, 652 24 Karlstad", "652 24", "Karlstad")!!
        assertEquals(59.381, found.lat, 1e-9)
        assertEquals("65224", found.postalCode)
        assertEquals("Karlstad", found.locality)
        assertEquals("Provgatan", found.thoroughfare)
        assertEquals("Provgatan 14, 652 24 Karlstad", found.addressLine)
        // The same street and number in another post town of the same municipality.
        assertEquals(59.60, sample.find("Provgatan 14", "655 60", null)!!.lat, 1e-9)
        assertEquals(59.60, sample.find("Provgatan 14", null, "Molkom")!!.lat, 1e-9)
        // The letter is the house's: written apart or together, in either case.
        assertEquals(59.382, sample.find("Provgatan 14 b, Karlstad", null, "Karlstad")!!.lat, 1e-9)
        assertEquals(59.382, sample.find("provgatan 14B", "65224", null)!!.lat, 1e-9)
        assertNull(sample.find("Provgatan 14 C", "65224", null))
    }

    /** A town is never guessed. */
    @Test
    fun aTownIsNeverGuessed() {
        // Neither postcode nor town written.
        assertNull(sample.find("Provgatan 14", null, null))
        // The written postcode or town is not the register's.
        assertNull(sample.find("Provgatan 14", "65225", "Karlstad"))
        assertNull(sample.find("Provgatan 1", null, "Kil"))
        // Karlstad is a post town: Molkom's Provgatan 20, in Karlstad municipality, is not taken for it.
        assertNull(sample.find("Provgatan 20", null, "Karlstad"))
        // A municipality that is no post town's name, when the address is in one post town there.
        assertEquals(59.32, sample.find("Exempelvägen 3", null, "Hammarö")!!.lat, 1e-9)
        // Two such addresses in one town, apart: neither is chosen.
        assertNull(sample.find("Testallén 5", null, "Karlstad"))
        assertNotNull(sample.find("Testallén 5", "65226", null))
        // No number: the register knows houses only.
        assertNull(sample.find("Provgatan, Karlstad", null, "Karlstad"))
    }

    @Test
    fun theDriversRegisterIsBundled() {
        val text = listOf(File("src/main/assets/${AddressRegister.ASSET}"), File("app/src/main/assets/${AddressRegister.ASSET}")).first { it.exists() }.readText()
        val register = AddressRegister.parse(text)
        assertTrue(register.size > 25_000)
        // A testdata address: on its street in Karlstad, not under another postcode.
        assertNotNull(register.find("Västra Torggatan 12", null, "Karlstad"))
        assertNull(register.find("Västra Torggatan 12", "652 24", "Karlstad"))
    }
}
