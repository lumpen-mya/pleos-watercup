package app.watercup.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadWatchTest {
    @Test
    fun `바로 앞까지 온 턱이 사라지면 넘은 것으로 본다`() {
        val bump = BumpCrossing()
        assertFalse(bump.crossed(120))
        assertFalse(bump.crossed(40))
        assertFalse(bump.crossed(12))
        assertTrue(bump.crossed(null))
        assertFalse(bump.crossed(null))
    }

    @Test
    fun `다음 턱으로 거리가 다시 늘어나도 넘은 것으로 본다`() {
        val bump = BumpCrossing()
        bump.crossed(8)
        assertTrue(bump.crossed(150))
    }

    @Test
    fun `멀리 있던 턱 안내가 끊긴 것은 넘은 것이 아니다`() {
        val bump = BumpCrossing()
        bump.crossed(200)
        assertFalse(bump.crossed(null))
    }

    @Test
    fun `턱을 시속 30킬로미터 넘게 지나면 쏟는다`() {
        val engine = WaterCupEngine()
        assertNull(engine.bump(28f))
        val event = engine.bump(45f)
        assertNotNull(event)
        assertEquals(SpillCause.SPEED_BUMP, event!!.cause)
        assertEquals(1, engine.state().events.size)
        assertTrue(engine.state().spilledMl > 0f)
    }

    @Test
    fun `제한속도를 여유보다 크게 넘은 시간만 센다`() {
        val watch = SpeedWatch()
        watch.step(1f, 53f, 50)
        assertEquals(0f, watch.overSec, 0.001f)
        watch.step(2f, 62f, 50)
        assertEquals(2f, watch.overSec, 0.001f)
        assertEquals(12f, watch.maxOverKph, 0.001f)
        assertTrue(watch.hasLimit)
    }

    @Test
    fun `제한속도 정보가 없으면 세지 않는다`() {
        val watch = SpeedWatch()
        watch.step(5f, 120f, null)
        assertEquals(0f, watch.overSec, 0.001f)
        assertFalse(watch.hasLimit)
    }
}
