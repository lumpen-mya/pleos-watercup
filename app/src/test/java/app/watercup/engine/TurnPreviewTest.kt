package app.watercup.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnPreviewTest {
    private val speed = 50f / 3.6f // 8초 앞 = 약 111m

    @Test
    fun `멀리 있을 때는 예고하지 않는다`() {
        assertNull(TurnPreview().check("우회전", 400f, speed))
    }

    @Test
    fun `가까워지면 한 번만 예고한다`() {
        val preview = TurnPreview()
        assertNull(preview.check("우회전", 200f, speed))
        assertEquals("우회전", preview.check("우회전", 105f, speed))
        assertNull(preview.check("우회전", 90f, speed))
        assertNull(preview.check("우회전", 40f, speed))
    }

    @Test
    fun `다음 회전 지점으로 넘어가면 다시 예고한다`() {
        val preview = TurnPreview()
        assertEquals("우회전", preview.check("우회전", 100f, speed))
        assertNull(preview.check("우회전", 20f, speed))
        assertNull(preview.check("우회전", 500f, speed))
        assertEquals("우회전", preview.check("우회전", 100f, speed))
        assertEquals("좌회전", preview.check("좌회전", 95f, speed))
    }

    @Test
    fun `빠를수록 더 멀리서 예고한다`() {
        assertNull(TurnPreview().check("우회전", 200f, speed))
        assertEquals("우회전", TurnPreview().check("우회전", 200f, 100f / 3.6f))
    }

    @Test
    fun `회전 지점을 지나치기 직전에는 말하지 않는다`() {
        assertNull(TurnPreview().check("우회전", 10f, speed))
    }
}
