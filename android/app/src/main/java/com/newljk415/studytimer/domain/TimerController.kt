package com.newljk415.studytimer.domain

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 타이머 상태 전이. 모든 연산은 저장소 트랜잭션 안에서 "저장된 상태 + 현재 시각"으로 계산되므로
 * 화면 재생성·프로세스 재시작·알림 수신이 겹쳐도 같은 세션이 두 번 기록되지 않는다.
 *
 * 정책
 * - 모든 연산은 먼저 [settle]을 호출한다. 이미 끝난 세션이면 정확히 세션 길이까지만 기록하고 완료 처리한다.
 * - 완료 후 다음 종류(집중↔휴식)의 IDLE 세션을 만든다. 자동 시작하지 않는다.
 * - 시간 설정 변경은 IDLE 세션과 이후 세션에만 적용한다. 실행/일시정지 중 세션은 시작 시 길이를 유지한다.
 * - 집중 시간은 실행 구간이 닫힐 때(일시정지·초기화·종료·완료) 지역 날짜별로 나눠 기록한다.
 * - 완료 횟수는 세션이 끝난 시점(구간 시작 벽시계 + 실제 경과)의 지역 날짜에 귀속한다.
 */
class TimerController(
    private val store: TimerStore,
    private val time: TimeSource,
    private val durations: suspend () -> Durations,
    private val scheduler: CompletionScheduler? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val snapshots: Flow<TimerSnapshot?> = store.observeSnapshot()

    private suspend fun idle(type: SessionType, from: TimerSnapshot? = null) = TimerSnapshot(
        sessionId = newId(),
        type = type,
        durationMs = durations().millisFor(type),
        status = TimerStatus.IDLE,
        lastCompletedSessionId = from?.lastCompletedSessionId,
        lastCompletedType = from?.lastCompletedType,
    )

    private suspend fun TimerTransaction.current(): TimerSnapshot =
        snapshot() ?: idle(SessionType.FOCUS).also { save(it) }

    /** 실행 구간을 닫고 집중 시간을 기록한다. */
    private suspend fun TimerTransaction.closeSegment(s: TimerSnapshot, now: Now): TimerSnapshot {
        if (s.status != TimerStatus.RUNNING) return s
        val seg = TimerMath.segmentElapsed(s, now)
        if (s.type == SessionType.FOCUS) {
            TimerMath.splitByLocalDate(s.segmentStartWall, seg, now.zone).forEach { (d, ms) -> addFocus(d, ms) }
        }
        return s.copy(status = TimerStatus.PAUSED, accumulatedMs = s.accumulatedMs + seg,
            segmentStartWall = 0, segmentStartElapsed = 0, segmentBootId = "")
    }

    /** 끝난 세션이면 한 번만 완료 처리. 반환: (최신 상태, 이번 호출에서 발생한 완료 이벤트) */
    private suspend fun TimerTransaction.settle(now: Now): Pair<TimerSnapshot, CompletionEvent?> {
        val s = current()
        if (!TimerMath.isFinished(s, now)) return s to null
        val seg = TimerMath.segmentElapsed(s, now)
        closeSegment(s, now)
        if (s.type == SessionType.FOCUS) {
            addCompletedFocus(TimerMath.localDate(s.segmentStartWall + seg, now.zone))
        }
        val next = idle(s.type.next()).copy(lastCompletedSessionId = s.sessionId, lastCompletedType = s.type)
        save(next)
        return next to CompletionEvent(s.sessionId, s.type)
    }

    private suspend fun <T> tx(block: suspend TimerTransaction.(Now) -> Pair<TimerSnapshot, T>): T {
        val now = time.now()
        val (snap, result) = store.transaction { block(now) }
        scheduler?.sync(snap, TimerMath.remaining(snap, now))
        return result
    }

    suspend fun refresh(): CompletionEvent? = tx { now -> settle(now) }

    /** IDLE이면 시작, PAUSED면 재개. */
    suspend fun start(): CompletionEvent? = tx { now ->
        val (s, ev) = settle(now)
        if (ev != null || s.status == TimerStatus.RUNNING) return@tx s to ev
        val running = s.copy(status = TimerStatus.RUNNING, segmentStartWall = now.wall,
            segmentStartElapsed = now.elapsed, segmentBootId = now.bootId,
            lastCompletedSessionId = null, lastCompletedType = null)
        save(running)
        running to null
    }

    suspend fun pause(): CompletionEvent? = tx { now ->
        val (s, ev) = settle(now)
        if (ev != null || s.status != TimerStatus.RUNNING) return@tx s to ev
        val paused = closeSegment(s, now)
        save(paused)
        paused to null
    }

    /** 초기화: 지금까지의 실제 집중 시간은 기록하고, 같은 종류의 새 IDLE 세션으로 돌아간다. */
    suspend fun reset(): CompletionEvent? = tx { now ->
        val (s, ev) = settle(now)
        if (ev != null) return@tx s to ev
        closeSegment(s, now)
        val fresh = idle(s.type)
        save(fresh)
        fresh to null
    }

    /** 앱 종료 버튼: 실행 중이면 일시정지(기록 반영)해 두고, 다음 실행 때 이어서 재개할 수 있다. */
    suspend fun pauseForExit(): CompletionEvent? = pause()

    /** 설정 변경: IDLE 세션만 새 길이를 반영한다. */
    suspend fun applyDurations(): CompletionEvent? = tx { now ->
        val (s, ev) = settle(now)
        if (ev != null || s.status != TimerStatus.IDLE) return@tx s to ev
        val d = durations().millisFor(s.type)
        val updated = if (d == s.durationMs) s else s.copy(durationMs = d).also { save(it) }
        updated to null
    }
}
