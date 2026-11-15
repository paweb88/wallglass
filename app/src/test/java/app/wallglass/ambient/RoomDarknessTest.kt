package app.wallglass.ambient

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDarknessTest {

    @Test
    fun `without a reading the sun decides`() {
        assertTrue(RoomDarkness.isDark(wasDark = false, lux = null, sunDown = true))
        assertFalse(RoomDarkness.isDark(wasDark = true, lux = null, sunDown = false))
    }

    @Test
    fun `a reading beats the sun`() {
        assertTrue(RoomDarkness.isDark(wasDark = false, lux = 0f, sunDown = false))
        assertFalse(RoomDarkness.isDark(wasDark = true, lux = 300f, sunDown = true))
    }

    @Test
    fun `between the thresholds the previous state holds`() {
        assertTrue(RoomDarkness.isDark(wasDark = true, lux = 12f, sunDown = false))
        assertFalse(RoomDarkness.isDark(wasDark = false, lux = 12f, sunDown = true))
    }
}
