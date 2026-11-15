package app.wallglass.ambient

/**
 * Decides whether the room is dark enough to dim the screen.
 *
 * The light sensor only reports changes, so in a room that stays dark there may be no reading at all;
 * until the first one arrives the sun (below the horizon) stands in for it.
 */
object RoomDarkness {

    /** Below this the room counts as dark. */
    const val DARK_LUX = 8f

    /** Above this the room counts as lit again; the gap stops flicker around a single threshold. */
    const val LIGHT_LUX = 20f

    fun isDark(wasDark: Boolean, lux: Float?, sunDown: Boolean): Boolean = when {
        lux == null -> sunDown
        lux < DARK_LUX -> true
        lux > LIGHT_LUX -> false
        else -> wasDark
    }
}
