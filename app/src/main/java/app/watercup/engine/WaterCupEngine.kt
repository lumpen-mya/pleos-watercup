package app.watercup.engine

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.tan

/** 물을 쏟게 만든 조작. */
enum class SpillCause {
    /** 브레이크 페달을 밟은 급감속 */
    BRAKE,

    /** 페달을 밟지 않은 감속 (회생제동·가속 페달 급해제) */
    LIFT_OFF,

    /** 급가속 */
    ACCEL,

    /** 급조향 */
    STEER,
}

data class SpillEvent(
    val atSec: Float,
    val cause: SpillCause,
    val speedKph: Float,
    val ml: Float,
    /** 급조향일 때 방향지시등을 켜지 않았는지 */
    val withoutSignal: Boolean = false,
)

data class CupState(
    /** 앞뒤 기울기(rad). +는 물이 앞으로 쏠림(감속) */
    val tiltLong: Float = 0f,
    /** 좌우 기울기(rad). +는 물이 오른쪽으로 쏠림 */
    val tiltLat: Float = 0f,
    val spilling: Boolean = false,
    val spilledMl: Float = 0f,
    val distanceM: Float = 0f,
    val durationSec: Float = 0f,
    val events: List<SpillEvent> = emptyList(),
)

/**
 * 속도·핸들 각도·브레이크 신호로 컵 속 물의 출렁임을 계산한다.
 *
 * 물 표면은 차량 가속도를 따라가는 감쇠 진동으로 본다. 같은 감속량이라도 급하게 걸리면
 * 출렁임이 커져 넘치므로, 가속도의 크기뿐 아니라 변화 속도(저크)가 승차감에 반영된다.
 */
class WaterCupEngine(
    /** 물이 넘치기 시작하는 기울기(도). 작을수록 민감 */
    rimDeg: Float = 13f,
    private val wheelbaseM: Float = 2.9f,
    private val steeringRatio: Float = 15f,
) {
    private var rim = Math.toRadians(rimDeg.toDouble()).toFloat()

    fun setRimDeg(deg: Float) {
        rim = Math.toRadians(deg.toDouble()).toFloat()
    }

    private var filteredSpeed = 0f
    private var hasSpeed = false
    private var tiltLong = 0f
    private var tiltLat = 0f
    private var velLong = 0f
    private var velLat = 0f
    private var spilledMl = 0f
    private var distanceM = 0f
    private var elapsed = 0f
    private var lastEventAt = -100f
    private var spilling = false
    private var pending: SpillEvent? = null
    private val events = ArrayList<SpillEvent>()

    fun reset() {
        hasSpeed = false
        filteredSpeed = 0f
        tiltLong = 0f; tiltLat = 0f; velLong = 0f; velLat = 0f
        spilledMl = 0f; distanceM = 0f; elapsed = 0f
        lastEventAt = -100f
        spilling = false
        pending = null
        events.clear()
    }

    /** 한 틱 진행. 새로 물이 넘치기 시작했으면 그 사건을 돌려준다. */
    fun step(
        dtSec: Float,
        speedMps: Float,
        steeringDeg: Float,
        brakePressed: Boolean,
        turnSignalOn: Boolean = false,
    ): SpillEvent? {
        var remaining = min(dtSec, 0.5f)
        var started: SpillEvent? = null
        while (remaining > 1e-4f) {
            val dt = min(remaining, MAX_STEP)
            started = substep(dt, speedMps, steeringDeg, brakePressed, turnSignalOn) ?: started
            remaining -= dt
        }
        return started
    }

    fun state() = CupState(tiltLong, tiltLat, spilling, spilledMl, distanceM, elapsed, events.toList())

    private fun substep(
        dt: Float,
        speedMps: Float,
        steeringDeg: Float,
        brakePressed: Boolean,
        turnSignalOn: Boolean,
    ): SpillEvent? {
        elapsed += dt
        distanceM += speedMps * dt

        // 속도 신호는 계단형으로 들어오므로 저역 통과 후 미분해 가속도를 얻는다
        if (!hasSpeed) {
            filteredSpeed = speedMps
            hasSpeed = true
        }
        val prev = filteredSpeed
        filteredSpeed += (speedMps - filteredSpeed) * (dt / (SPEED_TAU + dt))
        val accelLong = (filteredSpeed - prev) / dt

        val wheelAngle = Math.toRadians((steeringDeg / steeringRatio).toDouble())
        val accelLat = (filteredSpeed * filteredSpeed * tan(wheelAngle) / wheelbaseM).toFloat()

        // 감속하면 물은 앞으로, 우회전하면 왼쪽으로 쏠린다
        val targetLong = atan(-accelLong / G)
        val targetLat = atan(-accelLat / G)

        velLong += (OMEGA * OMEGA * (targetLong - tiltLong) - 2f * ZETA * OMEGA * velLong) * dt
        velLat += (OMEGA * OMEGA * (targetLat - tiltLat) - 2f * ZETA * OMEGA * velLat) * dt
        tiltLong += velLong * dt
        tiltLat += velLat * dt

        val over = hypot(tiltLong, tiltLat) - rim
        if (over <= 0f) {
            spilling = false
            pending = null
            return null
        }
        val ml = over * SPILL_RATE * dt
        spilledMl += ml
        val wasSpilling = spilling
        spilling = true

        if (!wasSpilling && pending == null && elapsed - lastEventAt > EVENT_GAP_SEC) {
            val cause = when {
                abs(tiltLat) > abs(tiltLong) -> SpillCause.STEER
                accelLong > 0f -> SpillCause.ACCEL
                brakePressed -> SpillCause.BRAKE
                else -> SpillCause.LIFT_OFF
            }
            pending = SpillEvent(
                atSec = elapsed,
                cause = cause,
                speedKph = filteredSpeed * 3.6f,
                ml = 0f,
                withoutSignal = cause == SpillCause.STEER && !turnSignalOn,
            )
        }
        // 가장자리를 살짝 스친 정도는 세지 않고, 일정량 넘쳤을 때 사건으로 확정한다
        pending?.let { candidate ->
            val grown = candidate.copy(ml = candidate.ml + ml)
            if (grown.ml < MIN_EVENT_ML) {
                pending = grown
                return null
            }
            pending = null
            lastEventAt = elapsed
            events.add(grown)
            return grown
        }
        // 같은 출렁임에서 계속 넘친 양은 직전 사건에 더한다
        if (events.isNotEmpty()) {
            val last = events.last()
            events[events.lastIndex] = last.copy(ml = last.ml + ml)
        }
        return null
    }

    private companion object {
        const val G = 9.81f
        const val MAX_STEP = 1f / 60f
        const val SPEED_TAU = 0.45f
        /** 컵 속 물의 고유 진동수(rad/s)와 감쇠비 */
        const val OMEGA = 6.9f
        const val ZETA = 0.25f
        /** 넘친 기울기 1rad·1초당 쏟아지는 양(ml) */
        const val SPILL_RATE = 40f
        const val EVENT_GAP_SEC = 1.5f
        const val MIN_EVENT_ML = 0.5f
    }
}
