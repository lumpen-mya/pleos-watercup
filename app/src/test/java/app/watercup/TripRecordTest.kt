package app.watercup

import app.watercup.engine.WaterCupEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRecordTest {
    private val cup = WaterCupEngine().state()

    @Test
    fun `주행 기록에 그때의 민감도와 넘침 기준과 뒷좌석 탑승 여부가 남는다`() {
        val record = TripRecord.from(cup, 1L, Sensitivity.GENTLE, rimDeg = 9.5f, rearOccupied = true)
        assertEquals(Sensitivity.GENTLE, record.sensitivity)
        assertEquals(9.5f, record.rimDeg)
        assertEquals(true, record.rearOccupied)
    }

    @Test
    fun `조건을 남기기 전의 기록은 값이 비어 있다`() {
        val record = TripRecord.from(cup, 1L)
        assertNull(record.sensitivity)
        assertNull(record.rimDeg)
        assertNull(record.rearOccupied)
    }
}
