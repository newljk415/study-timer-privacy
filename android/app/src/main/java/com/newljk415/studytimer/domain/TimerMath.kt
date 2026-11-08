package com.newljk415.studytimer.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Now(val wall: Long, val elapsed: Long, val bootId: String, val zone: ZoneId)

fun TimeSource.now() = Now(wallMillis(), elapsedMillis(), bootId(), zone())

object TimerMath {
    /** 현재 실행 구간의 경과 시간. 세션 남은 시간을 넘지 않도록 잘라낸다(종료 후 시간 미누적). */
    fun segmentElapsed(s: TimerSnapshot, now: Now): Long {
        if (s.status != TimerStatus.RUNNING) return 0
        val sameBoot = now.bootId == s.segmentBootId && now.elapsed >= s.segmentStartElapsed
        val raw = if (sameBoot) now.elapsed - s.segmentStartElapsed else now.wall - s.segmentStartWall
        return raw.coerceIn(0, (s.durationMs - s.accumulatedMs).coerceAtLeast(0))
    }

    fun elapsed(s: TimerSnapshot, now: Now): Long = s.accumulatedMs + segmentElapsed(s, now)

    fun remaining(s: TimerSnapshot, now: Now): Long = (s.durationMs - elapsed(s, now)).coerceAtLeast(0)

    fun isFinished(s: TimerSnapshot, now: Now): Boolean =
        s.status == TimerStatus.RUNNING && remaining(s, now) == 0L

    /** [startWall]부터 [durationMs] 구간을 지역 날짜 경계(자정)로 나눈다. */
    fun splitByLocalDate(startWall: Long, durationMs: Long, zone: ZoneId): List<Pair<LocalDate, Long>> {
        if (durationMs <= 0) return emptyList()
        val result = ArrayList<Pair<LocalDate, Long>>()
        var cur = startWall
        val end = startWall + durationMs
        while (cur < end) {
            val date = Instant.ofEpochMilli(cur).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segEnd = minOf(end, nextMidnight)
            result += date to (segEnd - cur)
            cur = segEnd
        }
        return result
    }

    fun localDate(wall: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(wall).atZone(zone).toLocalDate()

    /** 오늘 포함 최근 7일(오래된 날짜 → 오늘), 기록 없는 날은 0. */
    fun last7Days(records: List<DailyStat>, today: LocalDate): List<DailyStat> {
        val byDate = records.associateBy { it.date }
        return (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            byDate[d] ?: DailyStat(d, 0, 0)
        }
    }
}
