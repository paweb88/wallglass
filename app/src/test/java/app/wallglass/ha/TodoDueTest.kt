package app.wallglass.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TodoDueTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val now = LocalDateTime.of(2026, 11, 15, 8, 10)

    private fun item(uid: String, due: String?, done: Boolean = false) = TodoItem(uid, "Zadanie $uid", done, due, null)

    @Test
    fun parsesDateOnly() {
        assertEquals(TodoDue(LocalDate.of(2026, 11, 20), null), TodoDue.parse("2026-11-20", warsaw))
    }

    @Test
    fun parsesDateTimeInLocalZone() {
        val due = TodoDue.parse("2026-11-15T07:10:00+00:00", warsaw)
        assertEquals(TodoDue(LocalDate.of(2026, 11, 15), LocalTime.of(8, 10)), due)
    }

    @Test
    fun ignoresMissingOrBrokenDue() {
        assertNull(TodoDue.parse(null, warsaw))
        assertNull(TodoDue.parse("jutro", warsaw))
    }

    @Test
    fun remindsOnlyOpenItemsWithTimeThatArePastDue() {
        val items = listOf(
            item("a", "2026-11-15T08:10:00+01:00"),          // due now
            item("b", "2026-11-15T07:00:00+01:00"),          // overdue
            item("c", "2026-11-15T09:00:00+01:00"),          // later today
            item("d", "2026-11-15"),                         // date only: a task, not a reminder
            item("e", "2026-11-15T08:00:00+01:00", done = true),
            item("f", null),
        )
        val reminders = dueReminders(items, now, warsaw)
        assertEquals(listOf("todo:a", "todo:b"), reminders.map { it.id })
        assertEquals("a", reminders.first().todoUid)
        assertEquals("Zadanie a", reminders.first().message)
        assertEquals("Przypomnienie", reminders.first().title)
    }

    @Test
    fun labels() {
        fun label(raw: String) = dueLabel(TodoDue.parse(raw, warsaw), now)
        assertEquals("dziś 18:00", label("2026-11-15T18:00:00+01:00"))
        assertEquals("jutro 8:30", label("2026-11-16T08:30:00+01:00"))
        assertEquals("za 3 dni, 9:05", label("2026-11-18T09:05:00+01:00"))
        assertEquals("24.11 18:00", label("2026-11-24T18:00:00+01:00"))
        assertEquals("po terminie", label("2026-11-15T07:00:00+01:00"))
        assertEquals("dziś", label("2026-11-15"))
        assertEquals("po terminie", label("2026-11-14"))
        assertEquals("1.12", label("2026-12-01"))
        assertNull(dueLabel(null, now))
    }

    @Test
    fun urgentWhenDueTodayOrEarlier() {
        assertEquals(true, TodoDue.parse("2026-11-15", warsaw)!!.isUrgent(now))
        assertEquals(true, TodoDue.parse("2026-11-15T23:00:00+01:00", warsaw)!!.isUrgent(now))
        assertEquals(false, TodoDue.parse("2026-11-16T07:00:00+01:00", warsaw)!!.isUrgent(now))
    }
}
