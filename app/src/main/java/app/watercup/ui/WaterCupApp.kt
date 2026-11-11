package app.watercup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import app.watercup.compareText
import app.watercup.UiState
import app.watercup.engine.CupState
import app.watercup.engine.SpillCause
import app.watercup.label
import app.watercup.signal.VehicleSignals
import app.watercup.tip
import kotlin.math.sin
import kotlin.math.tan

private val Night = Color(0xFF0B1622)
private val Panel = Color(0xFF142536)
private val Glass = Color(0xFFE6F2FA)
private val Water = Color(0xFF4FC3F7)
private val WaterDeep = Color(0xFF0288D1)
private val Warn = Color(0xFFFF8A65)
private val Dim = Color(0xFF8FA6B8)
private val Good = Color(0xFF81C784)

private const val CUP_ML = 300f

@Composable
fun WaterCupApp(state: UiState, onSensitivity: (Sensitivity) -> Unit = {}, onAsk: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .padding(32.dp)
    ) {
        when (state.phase) {
            Phase.READY -> ReadyScreen(state, onSensitivity, onAsk)
            Phase.DRIVING -> DrivingScreen(state)
            Phase.REVIEW -> ReviewScreen(state, onAsk)
        }
    }
}

/** 정차 중 대기 화면: 시작 방법, 민감도, 최근 기록, 차량 신호 연결 상태를 보여 준다. */
@Composable
private fun ReadyScreen(state: UiState, onSensitivity: (Sensitivity) -> Unit, onAsk: () -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Cup(state.cup, Modifier.weight(0.9f).fillMaxHeight())
        Column(Modifier.weight(1f).padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("찰랑", color = Glass, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text("물컵 챌린지", color = Dim, fontSize = 34.sp, modifier = Modifier.padding(bottom = 14.dp))
            }
            Text("뒷자리 사람이 편한 운전, 물 한 컵으로 확인하세요.", color = Dim, fontSize = 30.sp)
            Text("기어를 D에 넣으면 시작합니다.", color = Water, fontSize = 34.sp, fontWeight = FontWeight.Medium)

            Spacer(Modifier.height(6.dp))
            Text("누구와 타나요", color = Dim, fontSize = 24.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sensitivity.entries.forEach { option ->
                    val selected = option == state.sensitivity
                    Text(
                        text = option.label,
                        color = if (selected) Night else Glass,
                        fontSize = 28.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Water else Panel)
                            .clickable { onSensitivity(option) }
                            .padding(horizontal = 26.dp, vertical = 16.dp),
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("최근 주행", color = Dim, fontSize = 24.sp)
                state.history.take(3).forEach { trip ->
                    Text(
                        text = "%.1f km · %s · ".format(trip.distanceM / 1000f, formatDuration(trip.durationSec)) +
                            if (trip.spills == 0) "쏟지 않음" else "${trip.spills}번 · %.0f ml".format(trip.spilledMl),
                        color = if (trip.spills == 0) Good else Glass,
                        fontSize = 28.sp,
                    )
                }
            }

            AskRow(state, onAsk)
            Spacer(Modifier.height(6.dp))
            SignalChips(state)
        }
    }
}

@Composable
private fun SignalChips(state: UiState) {
    val items = listOf(
        VehicleSignals.GEAR, VehicleSignals.BRAKE, VehicleSignals.STEERING, VehicleSignals.SEAT,
    ).map { name ->
        Triple(name, name in state.signals.live, state.signals.errors[name])
    } + Triple("속도", state.speedKph != null, state.speedError) +
        Triple("음성", state.voiceReady, null) +
        Triple("음성 인식", state.askReady, null) +
        Triple("내비", state.naviReady, null)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("차량 신호", color = Dim, fontSize = 24.sp)
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (name, ok, error) ->
                    val color = when {
                        ok -> Good
                        error != null -> Warn
                        else -> Dim
                    }
                    Text(
                        text = (if (ok) "● " else "○ ") + name,
                        color = color,
                        fontSize = 26.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (state.signals.rearOccupied) {
            Text("뒷좌석 탑승 감지됨 · 케어 주행", color = Good, fontSize = 26.sp)
        }
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
                Text(
                    "케어 주행",
                    color = Good,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
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
            Text("${state.cup.events.size}번", color = Glass, fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            val cause = state.lastCause
            val turn = state.upcomingTurn
            Text(
                text = cause?.label() ?: turn?.let { "곧 ${it.label}" } ?: " ",
                color = if (cause != null) Warn else Water,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 도착 리뷰: 원인별 횟수와 한 줄 조언. */
@Composable
private fun ReviewScreen(state: UiState, onAsk: () -> Unit) {
    val cup = state.cup
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("오늘의 물컵", color = Dim, fontSize = 34.sp)
            Text(
                if (cup.events.isEmpty()) "한 방울도 쏟지 않았어요" else "${cup.events.size}번 쏟았어요",
                color = if (cup.events.isEmpty()) Good else Glass,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("주행 거리", "%.1f km".format(cup.distanceM / 1000f))
                Stat("주행 시간", formatDuration(cup.durationSec))
                Stat("쏟은 물", "%.0f ml".format(cup.spilledMl))
            }
            compareText(cup.events.size, state.previous)?.let {
                Text(it, color = Water, fontSize = 36.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            Text("기어를 D에 넣으면 새 컵으로 다시 시작합니다.", color = Dim, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            AskRow(state, onAsk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("물을 쏟게 한 순간", color = Dim, fontSize = 34.sp)
            val counts = cup.events.groupingBy { it.cause }.eachCount()
            val max = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
            SpillCause.entries.forEach { cause ->
                CauseRow(cause, counts[cause] ?: 0, max)
            }
            val noSignal = cup.events.count { it.withoutSignal }
            if (noSignal > 0) {
                Text("급조향 중 ${noSignal}번은 방향지시등 없이", color = Warn, fontSize = 28.sp)
            }
            counts.maxByOrNull { it.value }?.let { top ->
                Spacer(Modifier.height(4.dp))
                Text(top.key.tip(), color = Water, fontSize = 36.sp, fontWeight = FontWeight.Medium, lineHeight = 48.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(label, color = Dim, fontSize = 24.sp)
        Text(value, color = Glass, fontSize = 40.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CauseRow(cause: SpillCause, count: Int, max: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cause.label(), color = Glass, fontSize = 32.sp, modifier = Modifier.width(300.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (state.listening) "듣는 중…" else "말로 물어보기  \"오늘 어땠어?\"",
            color = if (state.listening) Night else Glass,
            fontSize = 28.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (state.listening) Good else Panel)
                .clickable { onAsk() }
                .padding(horizontal = 26.dp, vertical = 16.dp),
        )
        state.heard?.let { Text("“$it”", color = Dim, fontSize = 26.sp) }
        state.answer?.let { Text(it, color = Water, fontSize = 28.sp, lineHeight = 38.sp) }
    }
}

/** 컵과 물. 좌우 기울기는 수면의 경사로, 앞뒤 기울기는 수위와 물결로 표현한다. */
@Composable
private fun Cup(cup: CupState, modifier: Modifier = Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    Canvas(modifier) { drawCup(cup, frame / 1000f) }
}

private fun DrawScope.drawCup(cup: CupState, timeSec: Float) {
    val cupHeight = size.height * 0.72f
    val topWidth = minOf(size.width * 0.62f, cupHeight * 0.78f)
    val bottomWidth = topWidth * 0.72f
    val cx = size.width / 2f
    val top = (size.height - cupHeight) / 2f
    val bottom = top + cupHeight

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

    // 쏟은 만큼 수위가 내려간다
    val fill = (0.8f * (1f - cup.spilledMl / CUP_ML)).coerceIn(0.08f, 0.8f)
    val level = bottom - cupHeight * fill
    val slope = tan(cup.tiltLat.coerceIn(-0.6f, 0.6f)) * 1.6f
    val surge = cup.tiltLong * cupHeight * 0.9f
    val ripple = (kotlin.math.abs(cup.tiltLong) + kotlin.math.abs(cup.tiltLat)) * cupHeight * 0.12f + 3f

    val water = Path().apply {
        val left = cx - topWidth / 2f
        val right = cx + topWidth / 2f
        val steps = 40
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            val y = level - surge + (x - cx) * slope + sin(i * 0.5f + timeSec * 5f) * ripple
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }
    clipPath(inside) {
        drawPath(water, WaterDeep)
        drawPath(water, Water.copy(alpha = 0.55f))
    }
    drawPath(
        glass,
        if (cup.spilling) Warn else Glass,
        style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    if (cup.spilling) {
        // 넘치는 쪽 컵 가장자리에서 물방울이 떨어진다
        val side = when {
            kotlin.math.abs(cup.tiltLat) > kotlin.math.abs(cup.tiltLong) -> if (cup.tiltLat > 0f) 1f else -1f
            else -> if ((timeSec * 2f).toInt() % 2 == 0) 1f else -1f
        }
        val startX = cx + side * (topWidth / 2f + 14f)
        for (i in 0 until 5) {
            val phase = (timeSec * 1.6f + i * 0.2f) % 1f
            drawCircle(
                color = Water.copy(alpha = 1f - phase),
                radius = 13f - 7f * phase,
                center = Offset(startX + side * 28f * phase, top + cupHeight * 0.6f * phase),
            )
        }
    }
}
