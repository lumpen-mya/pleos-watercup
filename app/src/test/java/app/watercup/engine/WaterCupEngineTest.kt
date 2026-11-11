package app.watercup.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterCupEngineTest {
    private val dt = 1f / 30f

    /** from에서 to(km/h)까지 seconds 동안 일정하게 변속한다. */
    private fun WaterCupEngine.ramp(from: Float, to: Float, seconds: Float, brake: Boolean = false, steering: Float = 0f) {
        val steps = (seconds / dt).toInt()
        for (i in 1..steps) {
            val kph = from + (to - from) * i / steps
            step(dt, kph / 3.6f, steering, brake)
        }
    }

    private fun WaterCupEngine.hold(kph: Float, seconds: Float, brake: Boolean = false, steering: Float = 0f) =
        ramp(kph, kph, seconds, brake, steering)

    @Test
    fun `부드럽게 가속하고 멈추면 쏟지 않는다`() {
        val engine = WaterCupEngine()
        engine.ramp(0f, 50f, 12f)
        engine.hold(50f, 5f)
        engine.ramp(50f, 0f, 12f, brake = true)
        engine.hold(0f, 3f)
        assertEquals(0, engine.state().events.size)
        assertEquals(0f, engine.state().spilledMl, 0.001f)
    }

    @Test
    fun `브레이크를 밟은 급감속은 브레이크 원인으로 기록된다`() {
        val engine = WaterCupEngine()
        engine.ramp(0f, 60f, 15f)
        engine.hold(60f, 3f)
        engine.ramp(60f, 0f, 2.5f, brake = true)
        engine.hold(0f, 3f)
        val events = engine.state().events
        assertTrue("넘침이 기록되어야 한다", events.isNotEmpty())
        assertEquals(SpillCause.BRAKE, events.first().cause)
        assertTrue(engine.state().spilledMl > 0f)
    }

    @Test
    fun `페달 없이 급감속하면 회생제동 원인으로 기록된다`() {
        val engine = WaterCupEngine()
        engine.ramp(0f, 60f, 15f)
        engine.hold(60f, 3f)
        engine.ramp(60f, 20f, 2f, brake = false)
        engine.hold(20f, 3f)
        assertEquals(SpillCause.LIFT_OFF, engine.state().events.first().cause)
    }

    @Test
    fun `급가속은 가속 원인으로 기록된다`() {
        val engine = WaterCupEngine()
        engine.hold(0f, 1f)
        engine.ramp(0f, 60f, 3f)
        engine.hold(60f, 3f)
        assertEquals(SpillCause.ACCEL, engine.state().events.first().cause)
    }

    @Test
    fun `속도가 붙은 상태의 급조향은 조향 원인으로 기록된다`() {
        val engine = WaterCupEngine()
        engine.ramp(0f, 60f, 15f)
        engine.hold(60f, 3f)
        engine.hold(60f, 2f, steering = 90f)
        engine.hold(60f, 3f)
        assertEquals(SpillCause.STEER, engine.state().events.first().cause)
    }

    @Test
    fun `방향지시등 없이 급조향하면 표시가 남는다`() {
        val engine = WaterCupEngine()
        engine.ramp(0f, 60f, 15f)
        repeat(60) { engine.step(dt, 60f / 3.6f, 90f, false, turnSignalOn = false) }
        assertTrue(engine.state().events.first().withoutSignal)

        val signaled = WaterCupEngine()
        signaled.ramp(0f, 60f, 15f)
        repeat(60) { signaled.step(dt, 60f / 3.6f, 90f, false, turnSignalOn = true) }
        assertEquals(false, signaled.state().events.first().withoutSignal)
    }

    @Test
    fun `민감도를 높이면 같은 감속에도 쏟는다`() {
        val normal = WaterCupEngine()
        normal.ramp(0f, 50f, 12f)
        normal.ramp(50f, 0f, 7f, brake = true)
        assertEquals(0, normal.state().events.size)

        val gentle = WaterCupEngine()
        gentle.setRimDeg(7f)
        gentle.ramp(0f, 50f, 12f)
        gentle.ramp(50f, 0f, 7f, brake = true)
        assertTrue(gentle.state().events.isNotEmpty())
    }

    @Test
    fun `주행 거리와 시간을 누적한다`() {
        val engine = WaterCupEngine()
        engine.hold(36f, 10f)
        assertEquals(100f, engine.state().distanceM, 1f)
        assertEquals(10f, engine.state().durationSec, 0.1f)
    }
}
