package app.watercup

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.watercup.engine.CupState
import app.watercup.engine.SpillCause
import app.watercup.engine.SpillEvent
import app.watercup.engine.TurnPreview
import app.watercup.engine.WaterCupEngine
import app.watercup.signal.CarSpeedSource
import app.watercup.signal.GearPos
import app.watercup.signal.NaviGuide
import app.watercup.signal.NextTurn
import app.watercup.signal.SignalSnapshot
import app.watercup.signal.VehicleSignals
import app.watercup.signal.Voice
import app.watercup.signal.VoiceAsk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { READY, DRIVING, REVIEW }

data class UiState(
    val phase: Phase = Phase.READY,
    val cup: CupState = CupState(),
    val signals: SignalSnapshot = SignalSnapshot(),
    val speedKph: Float? = null,
    val speedError: String? = null,
    val voiceReady: Boolean = false,
    /** 가장 최근에 물을 쏟게 한 원인. 주행 화면에서 잠깐 보여 준다 */
    val lastCause: SpillCause? = null,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    /** 최신순 주행 기록 */
    val history: List<TripRecord> = emptyList(),
    /** 방금 끝난 주행 직전의 기록. 도착 리뷰에서 비교한다 */
    val previous: TripRecord? = null,
    /** 길 안내 중 다음 회전 지점. 예고한 뒤 잠깐 보여 준다 */
    val upcomingTurn: NextTurn? = null,
    val naviReady: Boolean = false,
    val askReady: Boolean = false,
    val listening: Boolean = false,
    /** 음성 질문으로 들은 말과 그 답 */
    val heard: String? = null,
    val answer: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val signals = VehicleSignals(app)
    private val speed = CarSpeedSource(app)
    private val voice = Voice(app)
    private val store = TripStore(app)
    private val engine = WaterCupEngine()
    private val navi = NaviGuide(app)
    private val preview = TurnPreview()
    private val ask = VoiceAsk(app) { text -> onHeard(text) }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var phase = Phase.READY
    private var lastCause: SpillCause? = null
    private var lastCauseAt = 0L
    private var lastSpokenAt = 0L
    private var sensitivity = store.sensitivity
    private var history = store.trips()
    private var previous: TripRecord? = null
    private var careMode = false
    private var upcomingTurn: NextTurn? = null
    private var upcomingAt = 0L
    private var lastNaviPollAt = 0L
    private var listenStartedAt = 0L
    @Volatile private var heard: String? = null
    @Volatile private var answer: String? = null

    init {
        signals.start()
        voice.start()
        navi.start()
        ask.start()
        viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                tick((now - last) / 1000f, now)
                last = now
            }
        }
    }

    /** 속도 권한을 받은 뒤 호출한다. */
    fun startSpeed() = speed.start()

    /** 음성 질문은 정차 중(대기·도착 화면)에만 받는다. */
    fun askByVoice() {
        if (phase == Phase.DRIVING) return
        // 듣는 중에 다시 누르면 취소한다
        if (ask.listening) {
            ask.cancel()
            return
        }
        listenStartedAt = SystemClock.elapsedRealtime()
        heard = null
        answer = null
        ask.ask()
    }

    private fun onHeard(text: String) {
        heard = text
        val reply = answerFor(text, history)
        answer = reply
        voice.say(reply)
    }

    /** 민감도는 정차 중 대기 화면에서만 바꾼다. 다음 주행부터 적용된다. */
    fun selectSensitivity(value: Sensitivity) {
        sensitivity = value
        store.sensitivity = value
    }

    private fun tick(dt: Float, now: Long) {
        val snap = signals.snapshot()
        val mps = speed.speedMps ?: 0f
        val moving = mps > 0.3f

        when (phase) {
            Phase.READY, Phase.REVIEW ->
                // P단이 아니거나 차가 움직이면 새 주행으로 본다
                if ((snap.gear == GearPos.DRIVE || snap.gear == GearPos.REVERSE) || (moving && snap.gear != GearPos.PARK)) {
                    engine.reset()
                    engine.setRimDeg(sensitivity.rimDeg)
                    preview.reset()
                    ask.cancel()
                    heard = null
                    answer = null
                    lastCause = null
                    phase = Phase.DRIVING
                }

            Phase.DRIVING -> {
                careMode = snap.rearOccupied
                val signalOn = snap.turnSignal != null && snap.turnSignal != "OFF"
                val event = engine.step(dt, mps, snap.steeringDeg, snap.brakePressed, signalOn)
                if (event != null) onSpill(event, now)
                previewTurn(mps, now)
                if (snap.gear == GearPos.PARK && !moving) {
                    phase = Phase.REVIEW
                    val cup = engine.state()
                    previous = history.firstOrNull()
                    // 시동만 걸었다 끈 정도의 짧은 이동은 기록하지 않는다
                    if (cup.distanceM > MIN_TRIP_M || cup.events.isNotEmpty()) {
                        history = store.add(TripRecord.from(cup, System.currentTimeMillis()))
                    }
                    voice.say(reviewSpeech(cup, previous))
                }
            }
        }

        if (lastCause != null && now - lastCauseAt > CAUSE_SHOW_MS) lastCause = null
        // 말이 들리지 않으면 계속 기다리지 않고 닫는다
        if (ask.listening && now - listenStartedAt > LISTEN_TIMEOUT_MS) ask.cancel()
        if (upcomingTurn != null && (phase != Phase.DRIVING || now - upcomingAt > TURN_SHOW_MS)) upcomingTurn = null

        _ui.value = UiState(
            phase = phase,
            cup = engine.state(),
            signals = snap,
            speedKph = speed.speedMps?.times(3.6f),
            speedError = speed.error,
            voiceReady = voice.ready,
            lastCause = lastCause,
            sensitivity = sensitivity,
            history = history,
            previous = previous,
            upcomingTurn = upcomingTurn,
            naviReady = navi.ready,
            askReady = ask.ready,
            listening = ask.listening,
            heard = heard,
            answer = answer,
        )
    }

    /** 길 안내 중이면 다음 회전 지점 앞에서 미리 감속하도록 알린다. */
    private fun previewTurn(mps: Float, now: Long) {
        if (now - lastNaviPollAt > NAVI_POLL_MS) {
            lastNaviPollAt = now
            navi.poll()
        }
        val next = navi.next
        val turn = preview.check(next?.label, next?.distanceM, mps) ?: return
        upcomingTurn = next
        upcomingAt = now
        if (careMode) {
            lastSpokenAt = now
            voice.say("곧 $turn, 미리 속도를 줄여 주세요.")
        }
    }

    private fun onSpill(event: SpillEvent, now: Long) {
        lastCause = event.cause
        lastCauseAt = now
        // 주행 중 음성은 동승자가 있을 때(케어 주행)만, 넘친 직후 한 번만 낸다
        if (careMode && now - lastSpokenAt > SPEAK_GAP_MS) {
            lastSpokenAt = now
            voice.say(event.cause.tip())
        }
    }

    override fun onCleared() {
        signals.stop()
        speed.stop()
        voice.stop()
        navi.stop()
        ask.stop()
    }

    private companion object {
        const val TICK_MS = 33L
        const val CAUSE_SHOW_MS = 4000L
        const val SPEAK_GAP_MS = 8000L
        const val MIN_TRIP_M = 50f
        const val NAVI_POLL_MS = 2000L
        const val TURN_SHOW_MS = 6000L
        const val LISTEN_TIMEOUT_MS = 8000L
    }
}

fun SpillCause.label() = when (this) {
    SpillCause.BRAKE -> "브레이크 급감속"
    SpillCause.LIFT_OFF -> "페달 없는 감속"
    SpillCause.ACCEL -> "급가속"
    SpillCause.STEER -> "급조향"
}

fun SpillCause.tip() = when (this) {
    SpillCause.BRAKE -> "정지 직전에 브레이크를 살짝 풀어 주세요."
    SpillCause.LIFT_OFF -> "가속 페달은 천천히 떼어 주세요."
    SpillCause.ACCEL -> "출발은 조금만 더 천천히요."
    SpillCause.STEER -> "핸들은 미리, 천천히 돌려 주세요."
}

/** 지난 주행과 쏟은 횟수를 비교한 한마디. 비교할 기록이 없으면 null */
fun compareText(spills: Int, previous: TripRecord?): String? {
    previous ?: return null
    val diff = spills - previous.spills
    return when {
        diff < 0 -> "지난 주행보다 ${-diff}번 줄었어요"
        diff > 0 -> "지난 주행보다 ${diff}번 늘었어요"
        else -> "지난 주행과 같아요"
    }
}

fun reviewSpeech(cup: CupState, previous: TripRecord? = null): String {
    val km = "%.1f".format(cup.distanceM / 1000f)
    val compare = compareText(cup.events.size, previous)?.let { " $it." } ?: ""
    if (cup.events.isEmpty()) return "도착했어요. ${km}킬로미터 동안 한 방울도 쏟지 않았어요.$compare"
    val top = cup.events.groupingBy { it.cause }.eachCount().maxByOrNull { it.value }!!
    return "도착했어요. ${km}킬로미터 동안 ${cup.events.size}번 쏟았어요.$compare " +
        "가장 많은 원인은 ${top.key.label()}, ${top.value}번이에요. ${top.key.tip()}"
}
