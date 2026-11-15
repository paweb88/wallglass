package app.wallglass.config

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryTest {

    @Test
    fun `strips shared first word and capitalizes`() {
        val out = Discovery.stripCommonFirstWord(listOf("Rolety Gabinet", "Rolety sypialnia", "rolety Salon Bok"))
        assertEquals(listOf("Gabinet", "Sypialnia", "Salon Bok"), out)
    }

    @Test
    fun `keeps names when first words differ`() {
        val names = listOf("Rolety Gabinet", "Markiza taras")
        assertEquals(names, Discovery.stripCommonFirstWord(names))
    }

    @Test
    fun `keeps single name`() {
        assertEquals(listOf("Rolety Gabinet"), Discovery.stripCommonFirstWord(listOf("Rolety Gabinet")))
    }

    @Test
    fun `cleans sensor noise words`() {
        assertEquals("Salon", Discovery.cleanName("Termometr Salon Temperature"))
    }
}
