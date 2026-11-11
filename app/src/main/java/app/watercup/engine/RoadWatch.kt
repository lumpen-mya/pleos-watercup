package app.watercup.engine

/**
 * 내비가 알려 주는 과속방지턱까지 남은 거리로 "턱을 넘은 순간"을 찾는다.
 * 차량에서 상하 가속도를 받을 수 없어 턱을 넘을 때의 속도로 판정한다.
 */
class BumpCrossing(private val nearM: Int = 30) {
    private var last: Int? = null

    fun reset() {
        last = null
    }

    /** 바로 앞(nearM 이내)까지 다가왔던 턱이 사라지거나 다음 턱으로 바뀌면 넘은 것으로 본다. */
    fun crossed(remainM: Int?): Boolean {
        val previous = last
        last = remainM
        if (previous == null || previous > nearM) return false
        return remainM == null || remainM > previous + nearM
    }
}

/** 제한속도 초과 시간을 모은다. 계기판 오차를 감안해 여유(marginKph)를 둔다. */
class SpeedWatch(private val marginKph: Float = 5f) {
    var overSec = 0f
        private set
    var maxOverKph = 0f
        private set

    /** 길 안내에서 제한속도를 한 번이라도 받았는지 */
    var hasLimit = false
        private set
    var over = false
        private set

    fun reset() {
        overSec = 0f; maxOverKph = 0f; hasLimit = false; over = false
    }

    fun step(dtSec: Float, speedKph: Float, limitKph: Int?) {
        if (limitKph == null || limitKph <= 0) {
            over = false
            return
        }
        hasLimit = true
        val excess = speedKph - limitKph
        over = excess > marginKph
        if (over) {
            overSec += dtSec
            if (excess > maxOverKph) maxOverKph = excess
        }
    }
}
