package app.wallglass.ui

import app.wallglass.ha.Entity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A player that can only pick a device, not play: Spotify after its session went stale.
 * Starting it means moving playback to a device first, then pressing play.
 */
object PlayerStart {

    private const val PAUSE = 1L
    private const val SELECT_SOURCE = 2048L
    private const val PLAY = 16384L

    fun sources(player: Entity?): List<String> =
        (player?.attributes?.get("source_list") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()

    fun needsDevice(player: Entity?): Boolean {
        val features = (player?.attributes?.get("supported_features") as? JsonPrimitive)?.longOrNull ?: return false
        return features and SELECT_SOURCE != 0L && features and (PLAY or PAUSE) == 0L && sources(player).isNotEmpty()
    }

    /** The device it last played on, else one that is also a player on this panel (a speaker), else the first. */
    fun defaultDevice(player: Entity?, otherPlayers: List<Entity>): String? {
        val sources = sources(player)
        player?.attrString("source")?.takeIf { it in sources }?.let { return it }
        return otherPlayers.map { it.name }.firstOrNull { it in sources } ?: sources.firstOrNull()
    }
}
