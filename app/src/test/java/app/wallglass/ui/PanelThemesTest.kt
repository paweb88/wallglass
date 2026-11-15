package app.wallglass.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PanelThemesTest {
    @Test
    fun unknownIdFallsBackToSky() {
        assertSame(PanelThemes.sky, PanelThemes.byId(null))
        assertSame(PanelThemes.sky, PanelThemes.byId("nope"))
        assertSame(PanelThemes.sand, PanelThemes.byId("sand"))
    }

    @Test
    fun lightThemesNeverShowAtNightOrBehindVideo() {
        assertSame(PanelThemes.black, PanelThemes.effective(PanelThemes.light, night = true, darkPage = false))
        assertSame(PanelThemes.black, PanelThemes.effective(PanelThemes.sand, night = false, darkPage = true))
        assertSame(PanelThemes.light, PanelThemes.effective(PanelThemes.light, night = false, darkPage = false))
    }

    @Test
    fun darkThemesStayAsChosen() {
        PanelThemes.all.filterNot { it.light }.forEach {
            assertSame(it, PanelThemes.effective(it, night = true, darkPage = true))
        }
    }

    @Test
    fun idsAreUnique() {
        assertEquals(PanelThemes.all.size, PanelThemes.all.map { it.id }.toSet().size)
    }
}
