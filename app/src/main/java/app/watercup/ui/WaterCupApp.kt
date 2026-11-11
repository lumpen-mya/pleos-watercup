package app.watercup.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.watercup.Phase
import app.watercup.Sensitivity
import app.watercup.TripRecord
import app.watercup.trendText
import app.watercup.compareText
import app.watercup.UiState
import app.watercup.engine.CupState
import app.watercup.engine.RimTuning
import app.watercup.engine.SpillCause
import app.watercup.label
import app.watercup.signal.VehicleSignals
import app.watercup.tip
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 차량 기본 화면(밝은 바탕, 검은 글자, 파란 포인트)에 맞춘 색. 시스템이 어두운 테마면 어두운 쪽을 쓴다. */
private class Palette(
    val bg: Color, val panel: Color, val ink: Color, val dim: Color, val glassEdge: Color,
    val water: Color, val waterDeep: Color, val warn: Color, val good: Color, val shadow: Float,
)

private val LightPalette = Palette(
    bg = Color(0xFFFFFFFF), panel = Color(0xFFEFF2F5), ink = Color(0xFF15181C), dim = Color(0xFF66707A),
    glassEdge = Color(0xFFA9B6C2), water = Color(0xFF1A73E8), waterDeep = Color(0xFF0D4FA8),
    warn = Color(0xFFE04F2A), good = Color(0xFF1B8A4A), shadow = 0.12f,
)
private val DarkPalette = Palette(
    bg = Color(0xFF0B1622), panel = Color(0xFF142536), ink = Color(0xFFE6F2FA), dim = Color(0xFF8FA6B8),
    glassEdge = Color(0xFFE6F2FA), water = Color(0xFF4FC3F7), waterDeep = Color(0xFF0288D1),
    warn = Color(0xFFFF8A65), good = Color(0xFF81C784), shadow = 0.35f,
)
private var palette = LightPalette

private val Night get() = palette.bg
private val Panel get() = palette.panel
private val Glass get() = palette.ink
private val GlassEdge get() = palette.glassEdge
private val Water get() = palette.water
private val WaterDeep get() = palette.waterDeep
private val Warn get() = palette.warn
private val Dim get() = palette.dim
private val Good get() = palette.good

private const val CUP_ML = 300f
private val Corner = 16.dp
private val COMPACT_WIDTH = 1000.dp

@Composable
fun WaterCupApp(
    state: UiState,
    onSensitivity: (Sensitivity) -> Unit = {},
    onAsk: () -> Unit = {},
    onPassenger: (Boolean) -> Unit = {},
    onRestore: () -> Unit = {},
    forceCompact: Boolean = false,
) {
    palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    // 물이 넘치는 동안 배경이 살짝 물들어 화면을 자세히 보지 않아도 알 수 있다
    val spillTint by animateFloatAsState(
        targetValue = if (state.phase == Phase.DRIVING && state.cup.spilling) 0.1f else 0f,
        animationSpec = tween(if (state.cup.spilling) 120 else 900),
        label = "spillTint",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .background(Warn.copy(alpha = spillTint))
            .padding(32.dp)
    ) {
        // 분할 화면처럼 창이 좁으면 컵과 핵심 숫자만 남긴 작은 화면으로 바꾼다
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = forceCompact || maxWidth < COMPACT_WIDTH
            Crossfade(targetState = state.phase, animationSpec = tween(450), label = "phase") { phase ->
                when (phase) {
                    Phase.READY -> if (compact) CompactReady(state, onSensitivity) else ReadyScreen(state, onSensitivity, onAsk, onRestore)
                    Phase.DRIVING -> if (compact) CompactDriving(state) else DrivingScreen(state)
                    Phase.REVIEW -> if (compact) CompactReview(state, onPassenger) else ReviewScreen(state, onAsk, onPassenger)
                }
            }
        }
    }
}

/** 작은 화면 · 대기: 컵, 시작 방법, 민감도만. */
@Composable
private fun CompactReady(state: UiState, onSensitivity: (Sensitivity) -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Cup(state.cup, Modifier.weight(1f).fillMaxWidth())
        Text("찰랑", color = Glass, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text("기어를 D에 넣으면 시작해요.", color = Water, fontSize = 34.sp, fontWeight = FontWeight.Medium)
        SensitivityChips(state, onSensitivity)
        trendText(state.history)?.let { Text(it, color = Dim, fontSize = 28.sp) }
        Spacer(Modifier.height(8.dp))
    }
}

/** 작은 화면 · 주행: 컵과 쏟은 양, 한마디만. 내비 옆에 띄워 두는 용도. */
@Composable
private fun CompactDriving(state: UiState) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Cup(state.cup, Modifier.weight(1f).fillMaxWidth())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.0f".format(state.cup.spilledMl),
                color = if (state.cup.spilling) Warn else Glass,
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 120.sp,
            )
            Text(" ml", color = Dim, fontSize = 44.sp, modifier = Modifier.padding(bottom = 22.dp))
        }
        DrivingMessage(state)
        Spacer(Modifier.height(8.dp))
    }
}

/** 작은 화면 · 도착: 횟수, 가장 많은 원인의 조언, 동승자 확인. */
@Composable
private fun CompactReview(state: UiState, onPassenger: (Boolean) -> Unit) {
    val cup = state.cup
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Cup(cup, Modifier.weight(1f).fillMaxWidth())
        if (cup.events.isEmpty()) {
            Text("한 방울도 쏟지 않았어요", color = Good, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("${cup.events.size}번 쏟았어요", color = Glass, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            cup.events.groupingBy { it.cause }.eachCount().maxByOrNull { it.value }?.let { top ->
                Text("${top.key.label()} ${top.value}번 · ${top.key.tip()}", color = Water, fontSize = 30.sp)
            }
        }
        compareText(cup.events.size, state.previous)?.let { Text(it, color = Dim, fontSize = 30.sp) }
        PassengerRow(state, onPassenger)
        Spacer(Modifier.height(8.dp))
    }
}

/** 정차 중 대기 화면: 시작 방법, 민감도, 최근 기록, 차량 신호 연결 상태를 보여 준다. */
@Composable
private fun ReadyScreen(state: UiState, onSensitivity: (Sensitivity) -> Unit, onAsk: () -> Unit, onRestore: () -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Cup(state.cup, Modifier.weight(0.8f).fillMaxHeight())
        Column(Modifier.weight(1f).padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("찰랑", color = Glass, fontSize = 80.sp, fontWeight = FontWeight.Bold)
                Text("물컵 챌린지", color = Dim, fontSize = 36.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            Text("뒷자리가 편한 운전인지 물 한 컵으로 확인해 보세요.", color = Dim, fontSize = 34.sp)
            Text("기어를 D에 넣으면 시작해요.", color = Water, fontSize = 40.sp, fontWeight = FontWeight.Medium)
            if (state.signals.rearOccupied) {
                Badge("케어 주행 · 뒷좌석에 탄 사람이 있어 음성으로 알려 드려요")
            }

            Spacer(Modifier.height(8.dp))
            Text("누구와 타나요?", color = Dim, fontSize = 28.sp)
            SensitivityChips(state, onSensitivity)

            RimLine(state, onRestore)

            Trend(state.history)

            Spacer(Modifier.height(8.dp))
            AskRow(state, onAsk)
            SignalStatus(state)
        }
    }
}

@Composable
private fun SensitivityChips(state: UiState, onSensitivity: (Sensitivity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Sensitivity.entries.forEach { option ->
            val selected = option == state.sensitivity
            Text(
                text = option.label,
                color = if (selected) Night else Glass,
                fontSize = 32.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(Corner))
                    .background(if (selected) Water else Panel)
                    .clickable { onSensitivity(option) }
                    .padding(horizontal = 30.dp, vertical = 22.dp),
            )
        }
    }
}

/** 주행 중 한마디: 방금 쏟은 원인, 차선 변경, 다가오는 회전·과속방지턱, 제한속도 순으로 하나만 보여 준다. */
@Composable
private fun DrivingMessage(state: UiState) {
    val cause = state.lastCause
    val turn = state.upcomingTurn
    val alert = cause != null || state.laneNotice || (turn == null && state.overLimit)
    val message = cause?.label()
        ?: "방향지시등 없이 차선 변경".takeIf { state.laneNotice }
        ?: turn?.let { "곧 ${it.label}" }
        ?: state.speedLimitKph?.takeIf { state.overLimit }?.let { "제한속도 $it km/h" }
    val tone = if (alert) Warn else Water
    Text(
        text = message ?: " ",
        color = tone,
        fontSize = 44.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Corner))
            .background(if (message != null) tone.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    )
}

/** 주행별 쏟은 횟수 막대(왼쪽이 오래된 주행)와 나아지는지 한 줄. */
@Composable
private fun Trend(history: List<TripRecord>) {
    if (history.isEmpty()) return
    val trips = history.take(8).reversed()
    val max = trips.maxOf { it.spills }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))
        Text("쏟은 횟수 · 최근 ${trips.size}번 주행", color = Dim, fontSize = 28.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            trips.forEachIndexed { index, trip ->
                val latest = index == trips.lastIndex
                val color = if (trip.spills == 0) Good else Water
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${trip.spills}",
                        color = if (latest) Glass else Dim,
                        fontSize = 26.sp,
                        fontWeight = if (latest) FontWeight.Bold else FontWeight.Normal,
                    )
                    Box(
                        Modifier
                            .width(44.dp)
                            .height((8 + 82 * trip.spills / max).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = if (latest) 1f else 0.4f))
                    )
                }
            }
        }
        trendText(history)?.let { Text(it, color = Water, fontSize = 30.sp, fontWeight = FontWeight.Medium) }
    }
}

/** 도착 리뷰의 주행 체크: 신호를 받은 항목만 한 줄씩 보여 준다. */
@Composable
private fun DriveChecks(state: UiState) {
    val lines = buildList {
        if (state.laneChanges > 0) {
            add(
                if (state.laneChangesNoSignal == 0) true to "차선 변경 ${state.laneChanges}번 모두 방향지시등을 켰어요."
                else false to "차선 변경 ${state.laneChanges}번 중 ${state.laneChangesNoSignal}번은 방향지시등을 켜지 않았어요."
            )
        }
        if (state.hasSpeedLimit) {
            add(
                if (state.overLimitSec < 1f) true to "제한속도를 지켰어요."
                else false to "제한속도 초과 %s · 최고 +%.0f km/h".format(formatDuration(state.overLimitSec), state.maxOverKph)
            )
        }
    }
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { (ok, text) ->
            Text(
                (if (ok) "✓ " else "! ") + text,
                color = if (ok) Good else Warn,
                fontSize = 28.sp,
            )
        }
    }
}

/** 지금 넘침 기준을 운전자가 아는 말(1초당 속도 변화)로 알려 주고, 바뀌어 있으면 기본값 복원 버튼을 보여 준다. */
@Composable
private fun RimLine(state: UiState, onRestore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val changed = state.rimOffsetDeg != 0f
        Text(
            "속도가 1초에 약 %.0f km/h 넘게 변하면 넘쳐요".format(RimTuning.kphPerSec(state.rimDeg)) +
                if (changed) " · 동승자 의견 반영" else "",
            color = Dim,
            fontSize = 26.sp,
        )
        if (changed) Button("기본값으로 복원", small = true) { onRestore() }
    }
}

/** 도착 후 동승자에게 한 번 묻는다. 답에 따라 다음 주행의 넘침 기준이 조금 바뀐다. */
@Composable
private fun PassengerRow(state: UiState, onPassenger: (Boolean) -> Unit) {
    val answer = state.passengerComfortable
    if (answer == null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("같이 탄 분은 어땠나요? (선택)", color = Dim, fontSize = 28.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button("괜찮았어요") { onPassenger(true) }
                Button("불편했어요") { onPassenger(false) }
            }
        }
    } else {
        Text(
            when {
                state.rimChange < 0 -> "다음 주행부터 조금 더 민감하게 볼게요."
                state.rimChange > 0 -> "다음 주행부터 조금 더 여유 있게 볼게요."
                answer -> "지금 기준이 잘 맞네요. 그대로 둘게요."
                else -> "이미 가장 민감한 기준이에요."
            },
            color = Water,
            fontSize = 30.sp,
        )
    }
}

@Composable
private fun Button(text: String, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = Glass,
        fontSize = if (small) 26.sp else 32.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Corner))
            .background(Panel)
            .clickable { onClick() }
            .padding(horizontal = if (small) 22.dp else 30.dp, vertical = if (small) 16.dp else 22.dp),
    )
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = Good,
        fontSize = 28.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(Corner))
            .background(Panel)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** 차량 신호 연결 상태 한 줄. 모두 연결되면 한마디만, 빠진 신호가 있을 때만 이름을 보여 준다. */
@Composable
private fun SignalStatus(state: UiState) {
    val items = listOf(
        VehicleSignals.GEAR, VehicleSignals.BRAKE, VehicleSignals.STEERING, VehicleSignals.SEAT,
    ).map { name -> name to (name in state.signals.live) } +
        ("속도" to (state.speedKph != null)) +
        ("음성 안내" to state.voiceReady) +
        ("음성 인식" to state.askReady) +
        ("내비" to state.naviReady)
    val missing = items.filterNot { it.second }.map { it.first }
    val hasError = state.signals.errors.isNotEmpty() || state.speedError != null

    if (missing.isEmpty()) {
        Text("● 차량과 연결됐어요", color = Good, fontSize = 28.sp)
    } else {
        Text(
            "○ 아직 연결 안 됨: " + missing.joinToString(" · "),
            color = if (hasError) Warn else Dim,
            fontSize = 28.sp,
        )
    }
}

/** 주행 화면: 컵 하나와 쏟은 양만. 읽을 글자를 최소로 둔다. */
@Composable
private fun DrivingScreen(state: UiState) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Cup(state.cup, Modifier.weight(1.4f).fillMaxHeight())
        Column(
            Modifier.weight(1f).padding(start = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.signals.rearOccupied) {
                Badge("케어 주행")
            }
            Text("쏟은 물", color = Dim, fontSize = 32.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.0f".format(state.cup.spilledMl),
                    color = if (state.cup.spilling) Warn else Glass,
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(" ml", color = Dim, fontSize = 48.sp, modifier = Modifier.padding(bottom = 28.dp))
            }
            val spills = state.cup.events.size
            Text(
                if (spills == 0) "아직 안 쏟았어요" else "${spills}번 쏟음",
                color = if (spills == 0) Good else Glass,
                fontSize = 48.sp,
            )
            Spacer(Modifier.height(8.dp))
            val cause = state.lastCause
            val turn = state.upcomingTurn
            DrivingMessage(state)
        }
    }
}

/** 도착 리뷰: 원인별 횟수와 한 줄 조언. */
@Composable
private fun ReviewScreen(state: UiState, onAsk: () -> Unit, onPassenger: (Boolean) -> Unit) {
    val cup = state.cup
    val clean = cup.events.isEmpty()
    Row(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 남은 물과 바닥에 고인 물을 그대로 보여 준다
        Cup(cup, Modifier.weight(0.75f).fillMaxHeight())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("이번 주행", color = Dim, fontSize = 36.sp)
            if (clean) {
                Text("한 방울도 쏟지 않았어요", color = Good, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${cup.events.size}", color = Glass, fontSize = 150.sp, fontWeight = FontWeight.Bold, lineHeight = 150.sp)
                    Text("번 쏟았어요", color = Glass, fontSize = 52.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 30.dp))
                }
            }
            compareText(cup.events.size, state.previous)?.let {
                Text(it, color = Water, fontSize = 38.sp, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("주행 거리", "%.1f km".format(cup.distanceM / 1000f))
                Stat("주행 시간", formatDuration(cup.durationSec))
                Stat("쏟은 물", "%.0f ml".format(cup.spilledMl))
            }
            DriveChecks(state)
            Spacer(Modifier.height(8.dp))
            PassengerRow(state, onPassenger)
            AskRow(state, onAsk)
            Text("기어를 D에 넣으면 새 컵으로 다시 시작해요.", color = Dim, fontSize = 28.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (clean) {
                Text(
                    "급제동·급가속·급핸들 없이\n도착했어요.",
                    color = Glass,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 54.sp,
                )
                Text("뒷자리도 편안했을 거예요.", color = Dim, fontSize = 34.sp)
            } else {
                Text("쏟은 원인", color = Dim, fontSize = 36.sp)
                val counts = cup.events.groupingBy { it.cause }.eachCount()
                val max = counts.values.max()
                // 과속방지턱은 길 안내 중에만 판정되므로 기록이 있을 때만 보여 준다
                SpillCause.entries.filter { it != SpillCause.SPEED_BUMP || it in counts }.forEach { cause ->
                    CauseRow(cause, counts[cause] ?: 0, max)
                }
                val noSignal = cup.events.count { it.withoutSignal }
                if (noSignal > 0) {
                    Text("급핸들 ${noSignal}번은 방향지시등을 켜지 않았어요.", color = Warn, fontSize = 30.sp)
                }
                counts.maxByOrNull { it.value }?.let { top ->
                    Spacer(Modifier.height(4.dp))
                    Text(top.key.tip(), color = Water, fontSize = 38.sp, fontWeight = FontWeight.Medium, lineHeight = 50.sp)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(Corner))
            .background(Panel)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(label, color = Dim, fontSize = 26.sp)
        Text(value, color = Glass, fontSize = 38.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CauseRow(cause: SpillCause, count: Int, max: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner))
            .background(Panel)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cause.label(), color = Glass, fontSize = 30.sp, modifier = Modifier.width(290.dp))
        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Night)) {
            if (count > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(count / max.toFloat())
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (count == max) Warn else Water)
                )
            }
        }
        Text("${count}번", color = if (count > 0) Glass else Dim, fontSize = 32.sp, modifier = Modifier.padding(start = 20.dp))
    }
}

private fun formatDuration(sec: Float): String {
    val total = sec.toInt()
    return if (total >= 60) "${total / 60}분 ${total % 60}초" else "${total}초"
}

/** 정차 중 음성 질문 버튼과 들은 말·답. */
@Composable
private fun AskRow(state: UiState, onAsk: () -> Unit) {
    if (!state.askReady) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = if (state.listening) "듣고 있어요… 누르면 취소" else "말로 물어보기",
                color = if (state.listening) Night else Glass,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(Corner))
                    .background(if (state.listening) Good else Panel)
                    .clickable { onAsk() }
                    .padding(horizontal = 30.dp, vertical = 22.dp),
            )
            if (!state.listening && state.answer == null) {
                Text("“오늘 어땠어?”", color = Dim, fontSize = 28.sp)
            }
        }
        state.heard?.let { Text("“$it”", color = Dim, fontSize = 28.sp) }
        state.answer?.let { Text(it, color = Water, fontSize = 32.sp, lineHeight = 44.sp) }
    }
}

/** 컵과 물. 좌우 기울기는 수면의 경사로, 앞뒤 기울기는 수위와 물결로 표현한다. 넘친 물은 벽을 타고 흘러 바닥에 고인다. */
@Composable
private fun Cup(cup: CupState, modifier: Modifier = Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    // 물이 흘러내린 쪽(왼쪽, 오른쪽). 새 컵으로 시작하면 지운다.
    val wet = remember { BooleanArray(2) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    Canvas(modifier) {
        if (cup.spilledMl <= 0f && !cup.spilling) wet.fill(false)
        if (!cup.spilling && cup.spilledMl > 0.3f && !wet[0] && !wet[1]) wet.fill(true)
        if (cup.spilling) {
            val lateral = abs(cup.tiltLat) > abs(cup.tiltLong)
            if (!lateral || cup.tiltLat < 0f) wet[0] = true
            if (!lateral || cup.tiltLat > 0f) wet[1] = true
        }
        drawCup(cup, frame / 1000f, wet)
    }
}

private fun DrawScope.drawCup(cup: CupState, timeSec: Float, wet: BooleanArray) {
    val cupHeight = minOf(size.height * 0.64f, size.width * 1.1f)
    val topWidth = minOf(size.width * 0.72f, cupHeight * 0.8f)
    val bottomWidth = topWidth * 0.7f
    val cx = size.width / 2f
    val top = (size.height - cupHeight) / 2f - size.height * 0.04f
    val bottom = top + cupHeight
    val wall = topWidth * 0.028f

    // 컵 뒤 은은한 빛
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Water.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(cx, top + cupHeight * 0.55f),
            radius = cupHeight * 0.95f,
        ),
        radius = cupHeight * 0.95f,
        center = Offset(cx, top + cupHeight * 0.55f),
    )

    // 바닥 그림자와 고인 물
    val floorY = bottom + wall
    drawOval(
        color = Color.Black.copy(alpha = palette.shadow),
        topLeft = Offset(cx - bottomWidth * 0.62f, floorY - bottomWidth * 0.06f),
        size = Size(bottomWidth * 1.24f, bottomWidth * 0.12f),
    )
    if (cup.spilledMl > 0.3f) {
        val spread = sqrt((cup.spilledMl / 120f).coerceIn(0f, 1f))
        val rx = bottomWidth * 0.6f + (size.width * 0.47f - bottomWidth * 0.6f) * spread
        val ry = (rx * 0.13f).coerceAtMost(size.height * 0.07f)
        val wobble = sin(timeSec * 1.3f) * rx * 0.012f
        // 여러 타원을 겹쳐 번진 물 모양을 만든다
        val blobs = listOf(
            Triple(0f, 1f, 1f),
            Triple(-0.34f * (if (wet[0]) 1f else 0.4f), 0.72f, 0.82f),
            Triple(0.3f * (if (wet[1]) 1f else 0.4f), 0.78f, 0.9f),
        )
        blobs.forEach { (dx, sx, sy) ->
            val w = rx * sx + wobble
            val h = ry * sy
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(Water.copy(alpha = 0.75f), WaterDeep.copy(alpha = 0.85f)),
                    startY = floorY - h, endY = floorY + h,
                ),
                topLeft = Offset(cx + dx * rx - w, floorY - h),
                size = Size(w * 2f, h * 2f),
            )
        }
        // 고인 물 위 반사광
        drawOval(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset(cx - rx * 0.55f, floorY - ry * 0.55f),
            size = Size(rx * 0.5f, ry * 0.28f),
        )
    }

    val glass = Path().apply {
        moveTo(cx - topWidth / 2f, top)
        lineTo(cx - bottomWidth / 2f, bottom)
        lineTo(cx + bottomWidth / 2f, bottom)
        lineTo(cx + topWidth / 2f, top)
    }
    val inside = Path().apply {
        addPath(glass)
        close()
    }
    // 유리 몸통의 옅은 비침
    drawPath(inside, GlassEdge.copy(alpha = 0.08f))

    // 쏟은 만큼 수위가 내려간다
    val fill = (0.8f * (1f - cup.spilledMl / CUP_ML)).coerceIn(0.08f, 0.8f)
    val level = bottom - cupHeight * fill
    val slope = tan(cup.tiltLat.coerceIn(-0.6f, 0.6f)) * 1.6f
    val surge = cup.tiltLong * cupHeight * 0.9f
    val ripple = (abs(cup.tiltLong) + abs(cup.tiltLat)) * cupHeight * 0.12f + 3f
    val left = cx - topWidth / 2f
    val right = cx + topWidth / 2f
    val steps = 48
    fun surfaceY(i: Int, shift: Float): Float {
        val x = left + (right - left) * i / steps
        return level - surge + (x - cx) * slope + sin(i * 0.42f + timeSec * 5f + shift) * ripple
    }
    fun waterPath(shift: Float, lift: Float) = Path().apply {
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            val y = surfaceY(i, shift) - lift
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }
    clipPath(inside) {
        // 뒤쪽 물결(옅게) + 앞쪽 물(세로 그라데이션)
        drawPath(waterPath(1.7f, ripple * 0.8f + 6f), Water.copy(alpha = 0.35f))
        drawPath(
            waterPath(0f, 0f),
            Brush.verticalGradient(listOf(Water.copy(alpha = 0.85f), Water, WaterDeep), startY = level - ripple, endY = bottom),
        )
        // 수면 반사선
        val surface = Path().apply {
            for (i in 0..steps) {
                val x = left + (right - left) * i / steps
                if (i == 0) moveTo(x, surfaceY(i, 0f)) else lineTo(x, surfaceY(i, 0f))
            }
        }
        drawPath(surface, Color.White.copy(alpha = 0.55f), style = Stroke(width = 5f))
        // 떠오르는 기포
        for (i in 0 until 6) {
            val phase = (timeSec * (0.12f + i * 0.02f) + i * 0.37f) % 1f
            val bx = cx + bottomWidth * 0.38f * sin(i * 2.1f + timeSec * 0.6f)
            val by = bottom - (bottom - level) * phase
            drawCircle(Color.White.copy(alpha = 0.22f * (1f - phase)), radius = 5f + i % 3 * 3f, center = Offset(bx, by))
        }
    }

    // 유리: 두꺼운 바닥, 벽, 세로 반사광
    val rimColor = if (cup.spilling) Warn else GlassEdge
    drawLine(
        GlassEdge.copy(alpha = 0.7f),
        Offset(cx - bottomWidth / 2f, bottom - wall * 0.2f),
        Offset(cx + bottomWidth / 2f, bottom - wall * 0.2f),
        strokeWidth = wall * 2.2f,
        cap = StrokeCap.Round,
    )
    drawPath(
        glass,
        Brush.verticalGradient(listOf(rimColor, GlassEdge.copy(alpha = 0.6f)), startY = top, endY = bottom),
        style = Stroke(width = wall, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    fun wallX(side: Float, t: Float) = cx + side * (topWidth / 2f + (bottomWidth - topWidth) / 2f * t)
    drawLine(
        Color.White.copy(alpha = 0.28f),
        Offset(wallX(-1f, 0.08f) + wall * 2.4f, top + cupHeight * 0.08f),
        Offset(wallX(-1f, 0.7f) + wall * 2.4f, top + cupHeight * 0.7f),
        strokeWidth = wall * 0.9f,
        cap = StrokeCap.Round,
    )
    drawLine(
        Color.White.copy(alpha = 0.14f),
        Offset(wallX(1f, 0.15f) - wall * 2.2f, top + cupHeight * 0.15f),
        Offset(wallX(1f, 0.45f) - wall * 2.2f, top + cupHeight * 0.45f),
        strokeWidth = wall * 0.6f,
        cap = StrokeCap.Round,
    )

    // 컵 바깥벽을 타고 흐르는 물: 넘치는 동안은 굵게, 그 뒤에는 젖은 자국으로 남는다
    listOf(-1f, 1f).forEachIndexed { index, side ->
        if (!wet[index]) return@forEachIndexed
        val out = side * wall * 1.3f
        val flowing = cup.spilling
        val stream = Path().apply {
            moveTo(wallX(side, 0f) + out, top)
            for (k in 1..12) {
                val t = k / 12f
                val wiggle = sin(t * 9f + index * 2f) * wall * 0.5f
                lineTo(wallX(side, t) + out + wiggle, top + cupHeight * t)
            }
        }
        drawPath(
            stream,
            Water.copy(alpha = if (flowing) 0.9f else 0.32f),
            style = Stroke(width = if (flowing) wall * 1.5f else wall * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (flowing) {
            for (k in 0 until 5) {
                val t = (timeSec * 1.5f + k * 0.2f) % 1f
                drawCircle(
                    Color.White.copy(alpha = 0.7f * (1f - t * 0.5f)),
                    radius = wall * (0.9f - 0.3f * t),
                    center = Offset(wallX(side, t) + out, top + cupHeight * t),
                )
            }
            // 가장자리에서 튀는 물방울
            for (k in 0 until 3) {
                val t = (timeSec * 1.9f + k * 0.33f) % 1f
                drawCircle(
                    Water.copy(alpha = 1f - t),
                    radius = wall * (1f - 0.5f * t),
                    center = Offset(
                        wallX(side, 0f) + side * (wall * 2f + topWidth * 0.12f * t),
                        top - cupHeight * 0.05f * sin(t * 3.14f) + cupHeight * 0.12f * t * t,
                    ),
                )
            }
        }
    }
}
