package app.watercup

import app.watercup.engine.SpillCause
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswersTest {
    private fun trip(vararg counts: Pair<SpillCause, Int>) =
        TripRecord(endedAt = 0, distanceM = 12_000f, durationSec = 900f, spilledMl = 30f, counts = counts.toMap())

    @Test
    fun `기록이 없으면 안내만 한다`() {
        assertTrue("기록이 없" in answerFor("오늘 어땠어", emptyList()))
    }

    @Test
    fun `오늘 어땠어에는 횟수와 지난 주행 비교를 답한다`() {
        val history = listOf(trip(SpillCause.BRAKE to 2), trip(SpillCause.BRAKE to 5))
        val answer = answerFor("오늘 어땠어?", history)
        assertTrue("2번 쏟았어요" in answer)
        assertTrue("3번 줄었어요" in answer)
    }

    @Test
    fun `원인을 물으면 가장 많은 원인과 조언을 답한다`() {
        val history = listOf(trip(SpillCause.LIFT_OFF to 4, SpillCause.BRAKE to 1))
        val answer = answerFor("왜 쏟았어", history)
        assertTrue("페달 없는 감속" in answer)
        assertTrue("4번" in answer)
    }

    @Test
    fun `쏟지 않은 주행은 그렇게 답한다`() {
        assertTrue("한 방울도" in answerFor("결과 알려줘", listOf(trip())))
    }
}
