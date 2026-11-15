package app.wallglass.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WasteColorsTest {
    @Test
    fun `recognises polish fractions`() {
        assertEquals(WasteColors.colorFor("Papier"), WasteColors.colorFor("PAPIER"))
        assertEquals(WasteColors.colorFor("Plastik i metal"), WasteColors.colorFor("METALE I TWORZYWA SZTUCZNE"))
        assertEquals(WasteColors.colorFor("Szkło"), WasteColors.colorFor("szklo"))
    }

    @Test
    fun `splits multiple fractions`() {
        assertEquals(2, WasteColors.colorsFor("Bio, Papier").size)
    }

    @Test
    fun `unknown fraction uses fallback`() {
        assertEquals(WasteColors.fallback, WasteColors.colorFor("Elektrośmieci"))
    }
}
