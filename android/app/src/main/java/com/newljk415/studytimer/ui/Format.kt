package com.newljk415.studytimer.ui

import com.newljk415.studytimer.domain.SessionType

fun formatClock(ms: Long): String {
    val totalSec = (ms + 999) / 1000 // 남은 시간은 올림 표시
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

fun spokenDuration(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val m = totalSec / 60; val s = totalSec % 60
    return if (m > 0) "${m}분 ${s}초" else "${s}초"
}

fun formatFocus(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60; val m = totalMin % 60
    return if (h > 0) "${h}시간 ${m}분" else "${m}분"
}

fun SessionType.label() = if (this == SessionType.FOCUS) "집중" else "휴식"
