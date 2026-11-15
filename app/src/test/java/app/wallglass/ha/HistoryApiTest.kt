package app.wallglass.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HistoryApiTest {

    private val start = Instant.parse("2026-11-14T00:00:00Z")
    private val end = Instant.parse("2026-11-14T04:00:00Z")

    @Test
    fun `carries last value forward into each bucket`() {
        val points = listOf(
            start to 20f,
            Instant.parse("2026-11-14T02:30:00Z") to 21f,
        )
        assertEquals(listOf(20f, 20f, 21f, 21f), HistoryApi.bucketize(points, start, end, 4))
    }

    @Test
    fun `empty history gives empty series`() {
        assertTrue(HistoryApi.bucketize(emptyList(), start, end, 4).isEmpty())
    }
}
