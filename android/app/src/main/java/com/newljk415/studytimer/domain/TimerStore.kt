package com.newljk415.studytimer.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 타이머 상태와 기록을 하나의 트랜잭션으로 갱신하는 저장소. 구현은 트랜잭션을 직렬화해야 한다. */
interface TimerStore {
    suspend fun <T> transaction(block: suspend TimerTransaction.() -> T): T
    fun observeSnapshot(): Flow<TimerSnapshot?>
    fun observeDaily(from: LocalDate): Flow<List<DailyStat>>
}

interface TimerTransaction {
    suspend fun snapshot(): TimerSnapshot?
    suspend fun save(snapshot: TimerSnapshot)
    suspend fun addFocus(date: LocalDate, ms: Long)
    suspend fun addCompletedFocus(date: LocalDate)
}

/** 완료 알림 예약(정확한 알람 아님). 테스트에서는 가짜로 대체한다. */
interface CompletionScheduler {
    fun sync(snapshot: TimerSnapshot, remainingMs: Long)
}
