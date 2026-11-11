package app.watercup

/** 정차 중 음성 질문에 대한 답을 최근 주행 기록에서 만든다. */
fun answerFor(question: String, history: List<TripRecord>): String {
    val latest = history.firstOrNull() ?: return "아직 주행 기록이 없어요. 한 번 달리고 나서 물어봐 주세요."
    val previous = history.getOrNull(1)
    val q = question.replace(" ", "")
    val top = latest.counts.maxByOrNull { it.value }

    return when {
        listOf("왜", "원인", "이유", "뭐가").any { it in q } ->
            if (top == null) "최근 주행에서는 한 방울도 쏟지 않았어요."
            else "가장 많은 원인은 ${top.key.label()}, ${top.value}번이에요. ${top.key.tip()}"

        listOf("지난", "비교", "줄었", "늘었", "나아").any { it in q } ->
            compareText(latest.spills, previous)?.let { "$it." } ?: "비교할 지난 주행 기록이 아직 없어요."

        else -> {
            val km = "%.1f".format(latest.distanceM / 1000f)
            val compare = compareText(latest.spills, previous)?.let { " $it." } ?: ""
            if (latest.spills == 0) "최근 주행은 ${km}킬로미터 동안 한 방울도 쏟지 않았어요.$compare"
            else "최근 주행은 ${km}킬로미터 동안 ${latest.spills}번 쏟았어요.$compare" +
                (top?.let { " ${it.key.tip()}" } ?: "")
        }
    }
}
