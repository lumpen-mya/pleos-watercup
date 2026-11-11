package app.watercup.engine

/**
 * 주행 중 음성 안내 사이의 최소 간격을 지킨다.
 * 넘침·차선·회전 예고가 한꺼번에 몰려 운전자를 연달아 부르지 않게 한다.
 */
class SpeechGate(private val gapMs: Long = 8000L) {
    private var spoken = false
    private var lastAt = 0L

    /** 지금 말해도 되면 true 를 돌려주고 그 시각을 기록한다. */
    fun allow(nowMs: Long): Boolean {
        if (spoken && nowMs - lastAt <= gapMs) return false
        spoken = true
        lastAt = nowMs
        return true
    }
}
