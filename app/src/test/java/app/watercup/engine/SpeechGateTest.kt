package app.watercup.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {
    @Test
    fun `처음에는 바로 말한다`() {
        assertTrue(SpeechGate(8000L).allow(1000L))
    }

    @Test
    fun `간격 안에서는 다시 말하지 않는다`() {
        val gate = SpeechGate(8000L)
        assertTrue(gate.allow(10_000L))
        assertFalse(gate.allow(12_000L))
        assertFalse(gate.allow(18_000L))
        assertTrue(gate.allow(18_001L))
    }

    @Test
    fun `막힌 요청은 간격을 늘리지 않는다`() {
        val gate = SpeechGate(8000L)
        assertTrue(gate.allow(0L))
        assertFalse(gate.allow(7_000L))
        assertTrue(gate.allow(8_500L))
    }
}
