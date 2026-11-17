package app.wallglass.ui

import app.wallglass.ha.Entity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WasteTileTest {

    private val today = LocalDate.of(2026, 11, 17) // Tuesday

    private fun sensor(state: String, vararg attrs: Pair<String, Any>) = Entity(
        "sensor.wywoz", state,
        JsonObject(attrs.associate { (k, v) -> k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) }),
    )

    @Test
    fun `next collection from the schedule shows its date and fraction colours`() {
        val t = wasteTile(sensor("Bio", "2026-11-10" to "Szkło", "2026-11-19" to "Bio", "2026-11-25" to "Papier"), today)!!
        assertEquals("czw, 19.11", t.sub)
        assertEquals(listOf(WasteColors.colorFor("Bio")), t.marks)
        assertFalse(t.active)
    }

    @Test
    fun `tomorrow is highlighted and lists every fraction`() {
        val t = wasteTile(sensor("Plastik i metal, Papier", "2026-11-18" to "Plastik i metal, Papier"), today)!!
        assertEquals("jutro", t.sub)
        assertEquals(2, t.marks.size)
        assertTrue(t.active)
    }

    @Test
    fun `without a schedule the days count is used`() {
        val t = wasteTile(sensor("Bio", "daysTo" to 0), today)!!
        assertEquals("dziś", t.sub)
        assertTrue(t.active)
    }

    @Test
    fun `an unavailable sensor has no tile`() {
        assertNull(wasteTile(sensor("unavailable"), today))
    }
}
