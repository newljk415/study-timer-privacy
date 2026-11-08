package com.newljk415.studytimer.domain

import java.time.LocalDate

enum class SessionType { FOCUS, BREAK;
    fun next(): SessionType = if (this == FOCUS) BREAK else FOCUS
}

enum class TimerStatus { IDLE, RUNNING, PAUSED }

/**
 * 영속 저장되는 타이머 상태. UI가 아니라 이 값과 현재 시각으로 남은 시간을 계산한다.
 *
 * @property accumulatedMs 이전 실행 구간들의 합(이미 기록에 반영됨). 일시정지 시간은 포함되지 않는다.
 * @property segmentStartWall 현재 실행 구간 시작 시 벽시계(날짜 귀속/재부팅 복구용)
 * @property segmentStartElapsed 현재 실행 구간 시작 시 단조 시계
 * @property segmentBootId 현재 실행 구간을 시작한 부팅 ID
 */
data class TimerSnapshot(
    val sessionId: String,
    val type: SessionType,
    val durationMs: Long,
    val status: TimerStatus,
    val accumulatedMs: Long = 0,
    val segmentStartWall: Long = 0,
    val segmentStartElapsed: Long = 0,
    val segmentBootId: String = "",
    val lastCompletedSessionId: String? = null,
    val lastCompletedType: SessionType? = null,
)

data class Durations(val focusMinutes: Int, val breakMinutes: Int) {
    fun millisFor(type: SessionType): Long =
        (if (type == SessionType.FOCUS) focusMinutes else breakMinutes) * 60_000L

    companion object {
        val DEFAULT = Durations(25, 5)
        val FOCUS_RANGE = 1..180
        val BREAK_RANGE = 1..60
    }
}

data class DailyStat(val date: LocalDate, val focusMs: Long, val completedFocusSessions: Int)

/** 이번 호출이 실제로 완료 처리를 수행했을 때만 반환된다(중복 피드백 방지). */
data class CompletionEvent(val sessionId: String, val type: SessionType)
