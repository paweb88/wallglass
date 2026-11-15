package app.wallglass.config

import app.wallglass.ha.Entity

/** Fully resolved view of what the panel shows, built from config plus discovered entities. */
data class ResolvedPanel(
    val weather: String?,
    val doorbell: DoorbellConfig?,
    val cameras: List<CameraConfig>,
    val alarms: List<NamedEntity>,
    val openings: List<NamedEntity>,
    val covers: List<NamedEntity>,
    val heating: HeatingConfig?,
    val rooms: List<RoomConfig>,
    val todo: String?,
    val mediaPlayers: List<String>,
    val waste: String?,
    val chips: List<ChipConfig>,
    val config: PanelConfig,
)

object Discovery {

    private val OPENING_CLASSES = setOf("door", "window", "garage_door", "opening")
    private val IGNORED_TEMP_WORDS = listOf("chip", "battery", "cpu", "core", "processor")
    private val NAME_NOISE = Regex("(?i)\\b(temperature|temperatura|termometr|humidity|wilgotność)\\b")

    fun resolve(config: PanelConfig, states: Map<String, Entity>): ResolvedPanel {
        val visible = states.values.filter { it.entityId !in config.exclude }
        val byDomain = visible.groupBy { it.domain }

        val doorbell = config.doorbell
        val cameras = config.cameras ?: byDomain["camera"].orEmpty()
            .filter { it.isAvailable && it.entityId != doorbell?.camera }
            .sortedBy { it.name }
            .map { CameraConfig(it.entityId, it.name) }

        return ResolvedPanel(
            weather = config.weather ?: byDomain["weather"]?.firstOrNull()?.entityId,
            doorbell = doorbell,
            cameras = cameras,
            alarms = config.alarms ?: named(byDomain["alarm_control_panel"]),
            openings = config.openings ?: named(
                byDomain["binary_sensor"].orEmpty().filter { it.deviceClass in OPENING_CLASSES && it.isAvailable },
            ),
            covers = config.covers ?: named(byDomain["cover"]),
            heating = config.heating,
            rooms = config.rooms ?: discoverRooms(byDomain["sensor"].orEmpty(), states),
            todo = config.todo ?: byDomain["todo"].orEmpty()
                .firstOrNull { it.entityId != "todo.shopping_list" }?.entityId,
            mediaPlayers = config.mediaPlayers ?: byDomain["media_player"].orEmpty()
                .filter { it.isAvailable && it.attributes.containsKey("supported_features") }
                .sortedByDescending { it.state == "playing" }.map { it.entityId },
            waste = config.waste ?: byDomain["sensor"].orEmpty()
                .firstOrNull { it.attributes.containsKey("daysTo") && it.isAvailable }?.entityId,
            chips = config.chips ?: autoChips(config, byDomain),
            config = config,
        )
    }

    /** Default status icons: heating on, alarms armed, doorbell muted, garage doors open. */
    private fun autoChips(config: PanelConfig, byDomain: Map<String, List<Entity>>): List<ChipConfig> = buildList {
        config.heating?.let { add(ChipConfig(it.switch, "flame", "#FF8A50")) }
        byDomain["alarm_control_panel"].orEmpty().forEach { add(ChipConfig(it.entityId, "shield", "#FF5A4F")) }
        config.doorbell?.mute?.let { add(ChipConfig(it, "bell_off", "#FFC83A")) }
        byDomain["binary_sensor"].orEmpty().filter { it.deviceClass == "garage_door" }
            .forEach { add(ChipConfig(it.entityId, "garage", "#FF8A50")) }
    }

    /** Whether a status icon should be visible for this state. */
    fun isActive(e: Entity): Boolean = when (e.domain) {
        "alarm_control_panel" -> e.state.startsWith("armed") || e.state == "triggered" || e.state == "arming"
        "cover" -> e.state == "open" || e.state == "opening"
        "lock" -> e.state == "unlocked" || e.state == "open"
        "climate" -> e.state != "off" && e.isAvailable
        else -> e.state == "on" || e.state == "open" || e.state == "home"
    }

    private fun named(list: List<Entity>?): List<NamedEntity> {
        val sorted = list.orEmpty().sortedBy { it.name }
        val names = stripCommonFirstWord(sorted.map { it.name.trim() })
        return sorted.mapIndexed { i, e -> NamedEntity(e.entityId, names[i]) }
    }

    /** "Rolety Salon", "Rolety Gabinet" → "Salon", "Gabinet" (only when every name shares the word). */
    fun stripCommonFirstWord(names: List<String>): List<String> {
        if (names.size < 2) return names
        val first = names.first().substringBefore(' ')
        val shared = names.all { it.contains(' ') && it.substringBefore(' ').equals(first, ignoreCase = true) }
        return if (shared) names.map { n -> n.substringAfter(' ').trim().replaceFirstChar { it.uppercase() } } else names
    }

    private fun discoverRooms(sensors: List<Entity>, states: Map<String, Entity>): List<RoomConfig> =
        sensors
            .filter { it.deviceClass == "temperature" && it.isAvailable }
            .filter { s -> IGNORED_TEMP_WORDS.none { s.entityId.contains(it) } }
            .map { temp ->
                val humidityId = temp.entityId.replace("temperature", "humidity")
                RoomConfig(
                    name = cleanName(temp.name),
                    temperature = temp.entityId,
                    humidity = humidityId.takeIf { it != temp.entityId && states.containsKey(it) },
                )
            }
            .sortedBy { it.name }

    fun cleanName(raw: String): String =
        raw.replace(NAME_NOISE, "").replace(Regex("\\s+"), " ").trim().ifEmpty { raw }
}
