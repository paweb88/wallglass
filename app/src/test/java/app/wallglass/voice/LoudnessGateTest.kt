package app.wallglass.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessGateTest {
    @Test
    fun quietRoomIsRejected() {
        val gate = LoudnessGate()
        repeat(20) { gate.push(500.0) }
        assertFalse(gate.isLoudEnough)
    }

    @Test
    fun speechJustBeforeDetectionPasses() {
        val gate = LoudnessGate()
        repeat(10) { gate.push(500.0) }
        gate.push(12_000.0)
        repeat(5) { gate.push(800.0) }
        assertTrue(gate.isLoudEnough)
        assertEquals(12_000.0, gate.loudest, 0.0)
    }

    @Test
    fun oldSpeechFallsOutOfTheWindow() {
        val gate = LoudnessGate(window = 15)
        gate.push(12_000.0)
        repeat(15) { gate.push(500.0) }
        assertFalse(gate.isLoudEnough)
    }

    @Test
    fun rmsOfChunk() {
        assertEquals(1000.0, LoudnessGate.rms(ShortArray(4) { if (it % 2 == 0) 1000 else -1000 }), 0.001)
        assertEquals(0.0, LoudnessGate.rms(ShortArray(0)), 0.0)
    }
}
