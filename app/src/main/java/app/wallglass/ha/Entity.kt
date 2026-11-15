package app.wallglass.ha

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Immutable snapshot of one Home Assistant entity. */
data class Entity(
    val entityId: String,
    val state: String,
    val attributes: JsonObject,
) {
    val domain: String get() = entityId.substringBefore('.')
    val name: String get() = attrString("friendly_name") ?: entityId
    val deviceClass: String? get() = attrString("device_class")
    val isOn: Boolean get() = state == "on"
    val isAvailable: Boolean get() = state != "unavailable" && state != "unknown"
    val numericState: Double? get() = state.toDoubleOrNull()

    fun attrString(key: String): String? = (attributes[key] as? JsonPrimitive)?.contentOrNull
    fun attrDouble(key: String): Double? = (attributes[key] as? JsonPrimitive)?.doubleOrNull

    companion object {
        fun fromJson(obj: JsonObject): Entity? {
            val id = obj["entity_id"]?.jsonPrimitive?.contentOrNull ?: return null
            val state = obj["state"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val attrs = obj["attributes"]?.jsonObject ?: JsonObject(emptyMap())
            return Entity(id, state, attrs)
        }
    }
}

/** A Home Assistant persistent notification shown on the panel. */
data class PanelNotification(
    val id: String,
    val title: String?,
    val message: String,
    val createdAt: String?,
    /** Set for reminders made from to-do items: "done" completes the item instead of dismissing. */
    val todoUid: String? = null,
)

/** One item of a Home Assistant to-do list. */
data class TodoItem(
    val uid: String,
    val summary: String,
    val done: Boolean,
    val due: String?,
    val description: String?,
)

/** One day of a weather forecast. */
data class ForecastDay(
    val datetime: String,
    val condition: String,
    val tempHigh: Double?,
    val tempLow: Double?,
)
