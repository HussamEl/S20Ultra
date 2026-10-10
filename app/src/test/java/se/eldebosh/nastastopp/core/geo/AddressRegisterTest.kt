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
        Nybyggevägen||65229|Karlstad|3:59.36:13.48
        @Provgårdens skola|Molkom|65560|Karlstad|59.61:13.73:Provby 215
        @Exempeltorpet|Karlstad|65224|Karlstad|59.381:13.501:S:t Provgatan 2;59.3812:13.5012:S:t Provgatan 4
        @Gamla Skolan|Karlstad|65224|Karlstad|59.38:13.50:Provgatan 1
        @Gamla Skolan|Karlstad|65225|Karlstad|59.45:13.42:Testallén 5
        @Hagen|Väse|65595|Karlstad|59.35:13.75:Provby 404;59.40:13.80:Provby 900
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

    /** A place written by its popular name (a farm, a school, a church), where it is written to be. */
    @Test
    fun aPlaceIsFoundByItsPopularName() {
        val school = sample.find("Provgårdens skola, Molkom", null, "Molkom")!!
        assertEquals(59.61, school.lat, 1e-9)
        assertEquals("Provgårdens skola, Provby 215, 655 60 Molkom", school.addressLine)
        assertNotNull(sample.find("provgårdens SKOLA", "655 60", null))
        // Two addresses side by side are one place; a street with a colon in it is kept whole.
        assertEquals("Exempeltorpet, S:t Provgatan 2, 652 24 Karlstad", sample.find("Exempeltorpet", null, "Karlstad")!!.addressLine)
        // Never in another town, never without a town, never one of two.
        assertNull(sample.find("Provgårdens skola", null, "Karlstad"))
        assertNull(sample.find("Provgårdens skola", null, null))
        assertNull(sample.find("Gamla Skolan", null, "Karlstad"))
        assertNotNull(sample.find("Gamla Skolan", "65225", null))
        // One name on addresses far apart is not one place.
        assertNull(sample.find("Hagen", null, "Väse"))
    }

    /** An address reserved for a new building has no post town yet: it is found by its postcode. */
    @Test
    fun aNewBuildingIsFoundByItsPostcode() {
        assertEquals(59.36, sample.find("Nybyggevägen 3", "652 29", "Karlstad")!!.lat, 1e-9)
        assertNull(sample.find("Nybyggevägen 3", null, "Karlstad"))
    }

    // Invented place names, laid out as tools/make-places.py writes them.
    private val placed = sample.withPlaces(
        """
        # name|municipality|lat:lng
        Provby|Karlstad|59.70:13.60
        Exempelby|Hammarö|59.31:13.45
        Tvåby|Karlstad|59.50:13.40
        Tvåby|Hammarö|59.30:13.44
        Provgårdens skola|Karlstad|59.99:13.99
        """.trimIndent(),
    )

    /** A place written by its name only is found in the municipality its written town or postcode lies in. */
    @Test
    fun aPlaceIsFoundByItsNameWhereItIsWritten() {
        val found = placed.find("Provby, Molkom", null, "Molkom")!!
        assertEquals(59.70, found.lat, 1e-9)
        assertEquals("Molkom", found.locality)
        assertEquals("Provby, Molkom", found.addressLine)
        assertEquals(59.70, placed.find("Provby", "655 60", null)!!.lat, 1e-9)
        // A municipality that is no post town's name.
        assertEquals(59.31, placed.find("Exempelby", null, "Hammarö")!!.lat, 1e-9)
        // Each namesake where it is written, never the other one.
        assertEquals(59.50, placed.find("Tvåby", null, "Karlstad")!!.lat, 1e-9)
        assertEquals(59.30, placed.find("Tvåby", null, "Skoghall")!!.lat, 1e-9)
        // Neither town nor postcode, or a town of another municipality: none.
        assertNull(placed.find("Provby", null, null))
        assertNull(placed.find("Provby", null, "Skoghall"))
        assertNull(placed.find("Provby", null, "Kil"))
        // The address register's popular name comes first.
        assertEquals(59.61, placed.find("Provgårdens skola", null, "Molkom")!!.lat, 1e-9)
    }

    @Test
    fun theDriversRegisterIsBundled() {
        val text = listOf(File("src/main/assets/${AddressRegister.ASSET}"), File("app/src/main/assets/${AddressRegister.ASSET}")).first { it.exists() }.readText()
        val register = AddressRegister.parse(text)
        assertTrue(register.size > 25_000)
        // A testdata address: on its street in Karlstad, not under another postcode.
        assertNotNull(register.find("Västra Torggatan 12", null, "Karlstad"))
        assertNull(register.find("Västra Torggatan 12", "652 24", "Karlstad"))
        // A village address, and a church by its name.
        assertNotNull(register.find("Nolby 404", null, "Väse"))
        assertNotNull(register.find("Grava kyrka", null, "Karlstad"))
        assertTrue(register.names > 1_000)
        // Värmland's and Örebro's municipalities, each where it is written.
        assertTrue(register.size > 250_000)
        assertNotNull(register.find("Drottninggatan 1", "702 10", "Örebro"))
        // One street name in two towns: each where it is written.
        assertEquals(59.53086, register.find("Järnvägsgatan 6", null, "Storfors")!!.lat, 1e-9)
        assertEquals("Karlstad", register.find("Järnvägsgatan 6", null, "Karlstad")!!.locality)
        // The municipality written for its post town (Ljusnarsberg for Kopparberg); never when the
        // municipality is a post town too (Hammarö: Skoghall's Apelstigen is not taken for it).
        assertEquals(register.find("Andstigen 1", null, "Kopparberg")!!.lat, register.find("Andstigen 1", null, "Ljusnarsberg")!!.lat, 1e-9)
        assertNotNull(register.find("Apelstigen 1", null, "Skoghall"))
        assertNull(register.find("Apelstigen 1", null, "Hammarö"))
    }

    @Test
    fun theDriversPlaceNamesAreBundled() {
        fun asset(name: String) = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name")).first { it.exists() }.readText()
        val register = AddressRegister.parse(asset(AddressRegister.ASSET)).withPlaces(asset(AddressRegister.PLACES_ASSET))
        assertTrue(register.placeNames > 15_000)
        // Villages and churches the address register does not name, each in its municipality.
        assertNotNull(register.find("Glava", null, "Arvika"))
        assertNotNull(register.find("Visnum", null, "Kristinehamn"))
        assertNotNull(register.find("Ölme kyrka", null, "Kristinehamn"))
        assertNotNull(register.find("Glanshammar", null, "Örebro"))
        // A town's name is the town, not a farm of the same name beside it.
        assertEquals(59.597, register.find("Molkom", null, "Karlstad")!!.lat, 1e-3)
        // Never a namesake of another municipality.
        assertNull(register.find("Glava", null, "Örebro"))
    }
}
