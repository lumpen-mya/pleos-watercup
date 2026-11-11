package app.watercup.signal

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

data class NextTurn(val label: String, val distanceM: Float)

/** Pleos NaviHelper SDK에서 길 안내 중인 경로의 다음 회전 지점을 읽는다. */
class NaviGuide(context: Context) {
    private val navi = NaviHelper(context.applicationContext)

    @Volatile var ready = false
        private set

    @Volatile var next: NextTurn? = null
        private set

    /** 가장 가까운 과속방지턱까지 남은 거리(m). 안내가 없으면 null */
    @Volatile var bumpM: Int? = null
        private set

    /** 지금 달리는 도로의 제한속도(km/h). 모르면 null */
    @Volatile var speedLimitKph: Int? = null
        private set

    @Volatile var error: String? = null
        private set

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfos: List<TBTInfo>) {
            // 감속이 필요한 가장 가까운 안내 지점만 쓴다
            next = tbtInfos.firstNotNullOfOrNull { info ->
                info.type.label()?.let { NextTurn(it, info.distance.toFloat()) }
            }
        }

        override fun onCautionPointInfo(cautionPoints: List<CautionPoint>) {
            bumpM = cautionPoints
                .filter { it.type == CautionPointType.SPEED_BUMP }
                .minOfOrNull { it.remainDistance }
        }

        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            speedLimitKph = speedLimit.takeIf { it > 0 }
        }

        override fun onRouteCancelled() {
            next = null
            bumpM = null
        }

        override fun onRouteEnded() {
            next = null
            bumpM = null
        }

        override fun onError(errorCode: NaviErrorCode) {
            error = errorCode.toString()
        }
    }

    fun start() {
        try {
            navi.initialize(
                {
                    navi.addListener(listener)
                    ready = true
                },
                { e -> error = e.message ?: e.javaClass.simpleName },
            )
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
            Log.w("WaterCup", "navi init failed", e)
        }
    }

    /** 다음 회전 안내를 요청한다. 결과는 리스너로 들어온다. */
    fun poll() {
        if (!ready) return
        try {
            navi.getTBTInfo()
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun stop() {
        try {
            if (ready) navi.removeListener(listener)
            navi.release()
        } catch (e: Throwable) {
            Log.w("WaterCup", "navi release failed", e)
        }
        ready = false
    }

    private fun TurnType.label(): String? = when {
        this == TurnType.TURN_UTURN -> "유턴"
        this == TurnType.TURN_RIGHT || this == TurnType.TURN_SHARP_R -> "우회전"
        this == TurnType.TURN_LEFT || this == TurnType.TURN_SHARP_L -> "좌회전"
        this == TurnType.TURN_TOLL -> "요금소"
        this == TurnType.TURN_ARRIVE -> "목적지"
        name.contains("ROUNDABOUT") -> "회전교차로"
        name.contains("_IC_") -> "나들목"
        else -> null
    }
}
