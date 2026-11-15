package app.wallglass.ha

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Notification ids of to-do reminders start with this. */
const val REMINDER_PREFIX = "todo:"

/** Due of a to-do item: a date (a task) or a date and time (a reminder), in local time. */
data class TodoDue(val date: LocalDate, val time: LocalTime?) {
    val at: LocalDateTime? get() = time?.let { date.atTime(it) }

    /** Due today or earlier. */
    fun isUrgent(now: LocalDateTime): Boolean = !date.isAfter(now.toLocalDate())

    fun isOverdue(now: LocalDateTime): Boolean = at?.isBefore(now) ?: date.isBefore(now.toLocalDate())

    companion object {
        /** HA sends `2026-11-20` or `2026-11-20T18:00:00+01:00`; anything else is ignored. */
        fun parse(raw: String?, zone: ZoneId = ZoneId.systemDefault()): TodoDue? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                if ('T' in raw) {
                    val local = OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDateTime()
                    TodoDue(local.toLocalDate(), local.toLocalTime().withSecond(0).withNano(0))
                } else {
                    TodoDue(LocalDate.parse(raw), null)
                }
            }.getOrNull()
        }
    }
}

/** Open items whose due time has come, as notification cards (date-only items stay plain tasks). */
fun dueReminders(items: List<TodoItem>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): List<PanelNotification> =
    items.mapNotNull { item ->
        val at = TodoDue.parse(item.due, zone)?.at ?: return@mapNotNull null
        if (item.done || at.isAfter(now)) return@mapNotNull null
        PanelNotification(
            id = REMINDER_PREFIX + item.uid,
            title = "Przypomnienie",
            message = item.summary,
            createdAt = at.toString(),
            todoUid = item.uid,
        )
    }

private val TIME = DateTimeFormatter.ofPattern("H:mm")
private val DAY = DateTimeFormatter.ofPattern("d.MM")

/** "dziś 18:00", "jutro", "za 3 dni, 9:05", "20.11 18:00", "po terminie". */
fun dueLabel(due: TodoDue?, now: LocalDateTime): String? {
    due ?: return null
    if (due.isOverdue(now)) return "po terminie"
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.date)
    val time = due.time?.format(TIME)
    return when {
        days == 0L -> listOfNotNull("dziś", time).joinToString(" ")
        days == 1L -> listOfNotNull("jutro", time).joinToString(" ")
        days < 7 -> listOfNotNull("za $days dni", time).joinToString(", ")
        else -> listOfNotNull(due.date.format(DAY), time).joinToString(" ")
    }
}
