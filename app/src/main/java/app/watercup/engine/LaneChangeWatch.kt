package app.watercup.engine

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tan

/** 차선 변경 한 번. */
data class LaneChange(val withSignal: Boolean)

/**
 * 속도와 핸들 각도로 차선 변경을 추정한다. 차선을 직접 보는 신호가 없어
 * "옆으로 한 차로쯤 옮겨 갔지만 진행 방향은 거의 그대로인 움직임"을 차선 변경으로 본다.
 * 회전이나 굽은 길은 진행 방향이 크게 바뀌므로 제외된다.
 */
class LaneChangeWatch(
    private val wheelbaseM: Float = 2.9f,
    private val steeringRatio: Float = 15f,
) {
    private var active = false
    private var heading = 0f
    private var lateral = 0f
    private var elapsed = 0f
    private var quiet = 0f
    private var signalSeen = false
    private var sinceSignal = Float.MAX_VALUE

    fun reset() {
        active = false
        sinceSignal = Float.MAX_VALUE
    }

    fun step(dtSec: Float, speedMps: Float, steeringDeg: Float, signalOn: Boolean): LaneChange? {
        sinceSignal = if (signalOn) 0f else (sinceSignal + dtSec).coerceAtMost(1000f)
        val yawRate = speedMps * tan(Math.toRadians((steeringDeg / steeringRatio).toDouble())).toFloat() / wheelbaseM
        val turning = abs(yawRate) > YAW_START

        if (!active) {
            if (!turning || speedMps < MIN_SPEED_MPS) return null
            active = true
            heading = 0f; lateral = 0f; elapsed = 0f; quiet = 0f
            // 핸들을 돌리기 직전에 켰다 끈 경우도 켠 것으로 본다
            signalSeen = sinceSignal < SIGNAL_LEAD_SEC
        }

        heading += yawRate * dtSec
        lateral += speedMps * sin(heading) * dtSec
        elapsed += dtSec
        if (signalOn) signalSeen = true
        quiet = if (turning) 0f else quiet + dtSec

        if (quiet < SETTLE_SEC && elapsed < MAX_SEC) return null
        active = false
        val moved = abs(lateral) >= MIN_LATERAL_M && abs(lateral) <= MAX_LATERAL_M
        val straight = abs(heading) < MAX_HEADING_RAD
        return if (moved && straight) LaneChange(signalSeen) else null
    }

    private companion object {
        const val YAW_START = 0.012f
        const val MIN_SPEED_MPS = 8.3f
        const val SETTLE_SEC = 0.7f
        const val MAX_SEC = 10f
        const val MIN_LATERAL_M = 2.2f
        const val MAX_LATERAL_M = 5f
        const val MAX_HEADING_RAD = 0.1f
        const val SIGNAL_LEAD_SEC = 3f
    }
}
