package app.watercup.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LaneChangeWatchTest {
    private val dt = 0.033f

    /** 시속 72km에서 핸들을 한쪽으로 돌렸다 반대로 돌려 옆 차로로 옮기고 직진한다. */
    private fun laneChange(watch: LaneChangeWatch, signal: Boolean, steerDeg: Float = 6f): LaneChange? {
        var result: LaneChange? = null
        fun run(sec: Float, steer: Float, on: Boolean) {
            var t = 0f
            while (t < sec) {
                watch.step(dt, 20f, steer, on)?.let { result = it }
                t += dt
            }
        }
        run(1.5f, steerDeg, signal)
        run(1.5f, -steerDeg, signal)
        run(1.5f, 0f, false)
        return result
    }

    @Test
    fun `방향지시등을 켜고 차선을 바꾸면 켠 것으로 기록된다`() {
        val change = laneChange(LaneChangeWatch(), signal = true)
        assertNotNull(change)
        assertEquals(true, change!!.withSignal)
    }

    @Test
    fun `방향지시등 없이 차선을 바꾸면 표시가 남는다`() {
        val change = laneChange(LaneChangeWatch(), signal = false)
        assertNotNull(change)
        assertEquals(false, change!!.withSignal)
    }

    @Test
    fun `차로 안에서 살짝 움직인 것은 차선 변경이 아니다`() {
        assertNull(laneChange(LaneChangeWatch(), signal = false, steerDeg = 1.5f))
    }

    @Test
    fun `진행 방향이 바뀌는 회전은 차선 변경이 아니다`() {
        val watch = LaneChangeWatch()
        var result: LaneChange? = null
        var t = 0f
        while (t < 4f) { watch.step(dt, 10f, 60f, false)?.let { result = it }; t += dt }
        while (t < 6f) { watch.step(dt, 10f, 0f, false)?.let { result = it }; t += dt }
        assertNull(result)
    }
}
