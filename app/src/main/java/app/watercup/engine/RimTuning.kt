package app.watercup.engine

import kotlin.math.tan

/** 동승자 확인으로 넘침 기준(컵 가장자리 각도)을 조금씩 맞춘다. */
object RimTuning {
    /** 쏟았는데도 괜찮았다면 조금만 여유 있게, 불편했다면 그 두 배로 민감하게 한다 (불편 쪽을 더 무겁게 본다). */
    const val RELAX_STEP_DEG = 0.5f
    const val TIGHTEN_STEP_DEG = 1f

    /** 기본값에서 벗어날 수 있는 폭. 문헌에 보고된 개인차(중앙값의 약 ±30~40%)에 해당한다. */
    const val MAX_OFFSET_DEG = 4f

    /** 전체 허용 범위. 6도 ≈ 1.0 m/s²(서 있는 승객 한계), 20도 ≈ 3.6 m/s²(급제동으로 분류되기 시작하는 수준). */
    const val MIN_RIM_DEG = 6f
    const val MAX_RIM_DEG = 20f

    /**
     * 도착 후 동승자 답을 반영한 새 보정값(도).
     * 불편했다면 더 민감하게, 쏟았는데도 괜찮았다면 더 여유 있게 한다.
     * 쏟지 않았고 괜찮았다면 지금 기준이 맞는 것이므로 그대로 둔다.
     */
    fun adjust(offsetDeg: Float, comfortable: Boolean, spills: Int): Float {
        val next = when {
            !comfortable -> offsetDeg - TIGHTEN_STEP_DEG
            spills > 0 -> offsetDeg + RELAX_STEP_DEG
            else -> offsetDeg
        }
        return next.coerceIn(-MAX_OFFSET_DEG, MAX_OFFSET_DEG)
    }

    /** 기본값에 보정값을 더한 실제 기준(도). */
    fun rim(baseDeg: Float, offsetDeg: Float): Float = (baseDeg + offsetDeg).coerceIn(MIN_RIM_DEG, MAX_RIM_DEG)

    /** 이 각도에서 서서히 걸었을 때 넘치기 시작하는 속도 변화(km/h per second). */
    fun kphPerSec(rimDeg: Float): Float = 9.81f * tan(Math.toRadians(rimDeg.toDouble())).toFloat() * 3.6f
}
