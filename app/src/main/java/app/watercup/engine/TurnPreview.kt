package app.watercup.engine

import kotlin.math.max
import kotlin.math.min

/**
 * 다음 회전 지점에 가까워지면 한 번만 예고한다.
 * 속도가 빠를수록 더 멀리서 알려 감속할 시간을 준다.
 */
class TurnPreview(
    private val leadSec: Float = 8f,
    private val minDistanceM: Float = 80f,
    private val maxDistanceM: Float = 300f,
) {
    private var announced: String? = null
    private var lastDistance = Float.MAX_VALUE

    fun reset() {
        announced = null
        lastDistance = Float.MAX_VALUE
    }

    /** 예고할 때가 되면 회전 이름을 돌려준다. 아니면 null */
    fun check(turn: String?, distanceM: Float?, speedMps: Float): String? {
        if (turn == null || distanceM == null) return null
        // 거리가 다시 늘었으면 다음 회전 지점으로 넘어간 것이다
        if (turn != announced || distanceM > lastDistance + NEXT_TURN_JUMP_M) announced = null
        lastDistance = distanceM
        if (announced != null) return null
        val trigger = min(maxDistanceM, max(minDistanceM, speedMps * leadSec))
        if (distanceM > trigger || distanceM < TOO_LATE_M) return null
        announced = turn
        return turn
    }

    private companion object {
        const val NEXT_TURN_JUMP_M = 50f
        const val TOO_LATE_M = 15f
    }
}
