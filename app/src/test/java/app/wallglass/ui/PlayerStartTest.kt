package app.wallglass.ui

import app.wallglass.ha.Entity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStartTest {

    private val devices = listOf("MacBook Pro", "Pawel’s Mac Studio", "Speaker")

    private fun spotify(features: Long, source: String? = null) = Entity(
        "media_player.spotify", "idle",
        JsonObject(buildMap {
            put("supported_features", JsonPrimitive(features))
            put("source_list", JsonArray(devices.map(::JsonPrimitive)))
            source?.let { put("source", JsonPrimitive(it)) }
        }),
    )

    private val speaker = Entity("media_player.speaker", "off", JsonObject(mapOf("friendly_name" to JsonPrimitive("Speaker"))))

    @Test
    fun `stale spotify needs a device, a playing one does not`() {
        assertTrue(PlayerStart.needsDevice(spotify(2048)))
        assertFalse(PlayerStart.needsDevice(spotify(2048 or 16384 or 1)))
        assertFalse(PlayerStart.needsDevice(null))
    }

    @Test
    fun `a speaker on the panel is preferred over computers`() {
        assertEquals("Speaker", PlayerStart.defaultDevice(spotify(2048), listOf(speaker)))
        assertEquals("MacBook Pro", PlayerStart.defaultDevice(spotify(2048), emptyList()))
    }

    @Test
    fun `the last used device wins`() {
        assertEquals("Pawel’s Mac Studio", PlayerStart.defaultDevice(spotify(2048, "Pawel’s Mac Studio"), listOf(speaker)))
    }
}
