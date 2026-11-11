package app.watercup.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RimTuningTest {
    @Test
    fun `불편했다면 기준이 민감해진다`() {
        assertEquals(-1f, RimTuning.adjust(0f, comfortable = false, spills = 0), 0.001f)
        assertEquals(-1f, RimTuning.adjust(0f, comfortable = false, spills = 3), 0.001f)
    }

    @Test
    fun `쏟았는데 괜찮았다면 기준이 여유로워진다`() {
        assertEquals(0.5f, RimTuning.adjust(0f, comfortable = true, spills = 2), 0.001f)
    }

    @Test
    fun `쏟지 않았고 괜찮았다면 그대로 둔다`() {
        assertEquals(1f, RimTuning.adjust(1f, comfortable = true, spills = 0), 0.001f)
    }

    @Test
    fun `보정값은 범위를 넘지 않는다`() {
        assertEquals(RimTuning.MAX_OFFSET_DEG, RimTuning.adjust(RimTuning.MAX_OFFSET_DEG, comfortable = true, spills = 1), 0.001f)
        assertEquals(-RimTuning.MAX_OFFSET_DEG, RimTuning.adjust(-RimTuning.MAX_OFFSET_DEG, comfortable = false, spills = 1), 0.001f)
    }

    @Test
    fun `실제 기준은 전체 허용 범위 안에 머문다`() {
        assertEquals(RimTuning.MIN_RIM_DEG, RimTuning.rim(9f, -4f), 0.001f)
        assertEquals(RimTuning.MAX_RIM_DEG, RimTuning.rim(17f, 4f), 0.001f)
        assertEquals(10.5f, RimTuning.rim(11.5f, -1f), 0.001f)
    }

    @Test
    fun `어른 기본값은 초당 약 7킬로미터 변화에 해당한다`() {
        assertEquals(7.19f, RimTuning.kphPerSec(11.5f), 0.1f)
    }
}
