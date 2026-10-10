package se.eldebosh.nastastopp.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's TLS rules (res/xml/network_security_config.xml): never plain HTTP, never a certificate
 * the user added (one a proxy could use to read the tablet's pass or the driver's keys), and no
 * looser rules for debug builds. Certificates are checked against the system's roots, plus
 * YouDrive's one public root for its own domain only.
 */
class NetworkSecurityConfigTest {

    private fun file(path: String) = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    private fun elements(path: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(file(path))
        val all = document.getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }
    }

    @Test
    fun noPlainHttpNoUserCertificatesNoDebugOverrides() {
        val config = elements("res/xml/network_security_config.xml")
        assertEquals("network-security-config", config.first().tagName)
        for (e in config) {
            assertFalse("${e.tagName} allows plain HTTP", e.getAttribute("cleartextTrafficPermitted").equals("true", ignoreCase = true))
            assertFalse("${e.tagName} trusts certificates the user added", e.tagName == "certificates" && e.getAttribute("src") == "user")
            assertFalse("debug builds get looser rules", e.tagName == "debug-overrides")
            assertFalse("a pinned set would break on the server's next certificate", e.tagName == "pin-set")
        }
        // The extra root is for YouDrive's domain only, next to the system's.
        val anchors = config.filter { it.tagName == "certificates" }.map { it.getAttribute("src") }
        assertEquals(listOf("system", "@raw/telia_root_ca_v2"), anchors)
        assertEquals(listOf("regionvarmland.se"), config.filter { it.tagName == "domain" }.map { it.textContent.trim() })
    }

    @Test
    fun theAppUsesItAndNeverAllowsPlainHttp() {
        val app = elements("AndroidManifest.xml").first { it.tagName == "application" }
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("@xml/network_security_config", app.getAttributeNS(android, "networkSecurityConfig"))
        assertFalse(app.getAttributeNS(android, "usesCleartextTraffic").equals("true", ignoreCase = true))
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
    }
}
