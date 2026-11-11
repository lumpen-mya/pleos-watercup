package app.watercup

import android.content.Context
import app.watercup.engine.CupState
import app.watercup.engine.SpillCause
import org.json.JSONArray
import org.json.JSONObject

/**
 * 넘침 민감도. 값은 물이 넘치기 시작하는 기울기(도)로, 작을수록 민감하다.
 * 기본값의 근거는 docs/넘침-기준-근거.md 에 정리했다 (서서히 걸 때 넘치기 시작하는 가속도 기준).
 * - 아이·반려동물 9도 ≈ 1.55 m/s²: 승차감 한계 0.15 g(1.47 m/s²)에 맞춘 값. 전용 연구는 없어 설계 선택이다.
 * - 어른 11.5도 ≈ 2.0 m/s²: 앉은 승객의 쾌적 한계로 인용되는 약 2 m/s².
 * - 혼자 17도 ≈ 3.0 m/s²: 교통공학의 "편안한 감속" 약 3 m/s².
 */
enum class Sensitivity(val label: String, val rimDeg: Float) {
    GENTLE("아이·반려동물", 9f),
    NORMAL("어른", 11.5f),
    RELAXED("혼자", 17f),
}

data class TripRecord(
    val endedAt: Long,
    val distanceM: Float,
    val durationSec: Float,
    val spilledMl: Float,
    val counts: Map<SpillCause, Int>,
) {
    val spills: Int get() = counts.values.sum()

    companion object {
        fun from(cup: CupState, endedAt: Long) = TripRecord(
            endedAt = endedAt,
            distanceM = cup.distanceM,
            durationSec = cup.durationSec,
            spilledMl = cup.spilledMl,
            counts = cup.events.groupingBy { it.cause }.eachCount(),
        )
    }
}

/** 주행 기록과 설정을 기기 안에만 저장한다. */
class TripStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watercup", Context.MODE_PRIVATE)

    fun trips(): List<TripRecord> = try {
        val array = JSONArray(prefs.getString(KEY_TRIPS, "[]"))
        (0 until array.length()).map { parse(array.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    /** 최신 기록이 맨 앞에 오도록 저장하고 전체 목록을 돌려준다. */
    fun add(trip: TripRecord): List<TripRecord> {
        val all = (listOf(trip) + trips()).take(MAX_TRIPS)
        val array = JSONArray()
        all.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_TRIPS, array.toString()).apply()
        return all
    }

    var sensitivity: Sensitivity
        get() = Sensitivity.entries.firstOrNull { it.name == prefs.getString(KEY_SENSITIVITY, null) }
            ?: Sensitivity.NORMAL
        set(value) = prefs.edit().putString(KEY_SENSITIVITY, value.name).apply()

    /** 동승자 확인으로 쌓인 민감도별 보정값(도). 0이면 기본값이다. */
    fun rimOffset(sensitivity: Sensitivity): Float = prefs.getFloat(KEY_OFFSET + sensitivity.name, 0f)

    fun setRimOffset(sensitivity: Sensitivity, offsetDeg: Float) =
        prefs.edit().putFloat(KEY_OFFSET + sensitivity.name, offsetDeg).apply()

    fun clearRimOffsets() {
        val editor = prefs.edit()
        Sensitivity.entries.forEach { editor.remove(KEY_OFFSET + it.name) }
        editor.apply()
    }

    private fun toJson(trip: TripRecord) = JSONObject().apply {
        put("endedAt", trip.endedAt)
        put("distanceM", trip.distanceM.toDouble())
        put("durationSec", trip.durationSec.toDouble())
        put("spilledMl", trip.spilledMl.toDouble())
        put("counts", JSONObject().apply { trip.counts.forEach { (cause, n) -> put(cause.name, n) } })
    }

    private fun parse(json: JSONObject): TripRecord {
        val counts = json.getJSONObject("counts")
        return TripRecord(
            endedAt = json.getLong("endedAt"),
            distanceM = json.getDouble("distanceM").toFloat(),
            durationSec = json.getDouble("durationSec").toFloat(),
            spilledMl = json.getDouble("spilledMl").toFloat(),
            counts = SpillCause.entries.filter { counts.has(it.name) }.associateWith { counts.getInt(it.name) },
        )
    }

    private companion object {
        const val KEY_TRIPS = "trips"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_OFFSET = "rim_offset_"
        const val MAX_TRIPS = 30
    }
}
