package com.newljk415.studytimer.domain

import com.newljk415.studytimer.fakes.FakeScheduler
import com.newljk415.studytimer.fakes.FakeTime
import com.newljk415.studytimer.fakes.InMemoryStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MIN = 60_000L

class TimerControllerTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun wallAt(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 11, 8)

    private val time = FakeTime(wall = wallAt(2026, 11, 8, 10, 0))
    private val store = InMemoryStore()
    private var durations = Durations.DEFAULT
    private val scheduler = FakeScheduler()
    private var idSeq = 0
    private fun controller() = TimerController(store, time, { durations }, scheduler, { "s${++idSeq}" })
    private val c = controller()

    private fun snap() = store.state.value!!
    private fun remaining() = TimerMath.remaining(snap(), time.now())

    @Test fun defaultsAre25And5Minutes() = runTest {
        c.refresh()
        assertEquals(SessionType.FOCUS, snap().type)
        assertEquals(TimerStatus.IDLE, snap().status)
        assertEquals(25 * MIN, snap().durationMs)
        assertEquals(5 * MIN, Durations.DEFAULT.millisFor(SessionType.BREAK))
    }

    @Test fun startPauseResumeReset() = runTest {
        c.start(); assertEquals(TimerStatus.RUNNING, snap().status)
        time.advance(10 * MIN)
        assertEquals(15 * MIN, remaining())
        c.pause(); assertEquals(TimerStatus.PAUSED, snap().status)
        time.advance(30 * MIN) // 일시정지 동안은 줄지 않음
        assertEquals(15 * MIN, remaining())
        c.start(); assertEquals(TimerStatus.RUNNING, snap().status)
        time.advance(5 * MIN)
        assertEquals(10 * MIN, remaining())
        c.reset()
        assertEquals(TimerStatus.IDLE, snap().status)
        assertEquals(SessionType.FOCUS, snap().type)
        assertEquals(25 * MIN, remaining())
        // 초기화 전까지 실제 집중한 15분은 기록, 완료 횟수는 0
        assertEquals(15 * MIN, store.day(day).focusMs)
        assertEquals(0, store.day(day).completedFocusSessions)
    }

    @Test fun pausedTimeIsExcludedFromFocus() = runTest {
        c.start(); time.advance(7 * MIN)
        c.pause(); time.advance(60 * MIN)
        c.start(); time.advance(18 * MIN)
        c.refresh()
        assertEquals(25 * MIN, store.day(day).focusMs)
        assertEquals(1, store.day(day).completedFocusSessions)
    }

    @Test fun doublePauseAndDoubleStartAreNoOps() = runTest {
        c.start(); c.start(); time.advance(3 * MIN)
        c.pause(); c.pause()
        assertEquals(3 * MIN, store.totalFocus())
    }

    @Test fun timeAfterSessionEndIsNotAccumulated() = runTest {
        c.start(); time.advance(3 * 60 * MIN) // 3시간 뒤 복귀
        c.pause() // 일시정지를 눌러도 완료로 처리
        assertEquals(25 * MIN, store.totalFocus())
        assertEquals(1, store.totalCompleted())
        assertEquals(SessionType.BREAK, snap().type)
        assertEquals(TimerStatus.IDLE, snap().status) // 휴식은 수동 시작
    }

    @Test fun completionTransitionsFocusBreakFocusWithoutAutoStart() = runTest {
        c.start(); time.advance(25 * MIN)
        val ev = c.refresh()
        assertEquals(CompletionEvent("s1", SessionType.FOCUS), ev)
        assertEquals(SessionType.BREAK, snap().type)
        assertEquals(SessionType.FOCUS, snap().lastCompletedType)
        time.advance(10 * MIN)
        assertEquals(TimerStatus.IDLE, snap().status)
        c.start(); time.advance(5 * MIN)
        assertEquals(SessionType.BREAK, c.refresh()?.type)
        assertEquals(SessionType.FOCUS, snap().type)
        assertEquals(25 * MIN, store.totalFocus()) // 휴식은 집중 시간에 포함되지 않음
        assertEquals(1, store.totalCompleted())
    }

    @Test fun processRecreationRestoresRunningSessionAndCompletesOnce() = runTest {
        c.start(); time.advance(5 * MIN)
        // 프로세스 종료: 컨트롤러 폐기. 같은 영속 저장소로 새 컨트롤러 생성
        time.advance(40 * MIN)
        val restored = controller()
        assertNotNull(restored.refresh())
        assertNull(restored.refresh())
        assertNull(controller().refresh())
        assertEquals(25 * MIN, store.totalFocus())
        assertEquals(1, store.totalCompleted())
    }

    @Test fun processRecreationRestoresPausedState() = runTest {
        c.start(); time.advance(8 * MIN); c.pause()
        time.advance(120 * MIN)
        val restored = controller()
        restored.refresh()
        assertEquals(TimerStatus.PAUSED, snap().status)
        assertEquals(17 * MIN, remaining())
        restored.start(); time.advance(17 * MIN); restored.refresh()
        assertEquals(25 * MIN, store.totalFocus())
    }

    @Test fun concurrentCompletionRacesRecordOnce() = runTest {
        c.start(); time.advance(26 * MIN)
        val others = List(5) { controller() } // UI, 알림 수신기, 복원 등 경합
        val events = (others + c).map { ctl -> async { ctl.refresh() } }.awaitAll()
        assertEquals(1, events.count { it != null })
        assertEquals(25 * MIN, store.totalFocus())
        assertEquals(1, store.totalCompleted())
    }

    @Test fun focusAcrossMidnightIsSplitByLocalDate() = runTest {
        time.wall = wallAt(2026, 11, 8, 23, 50)
        c.start(); time.advance(25 * MIN)
        c.refresh()
        assertEquals(10 * MIN, store.day(LocalDate.of(2026, 11, 8)).focusMs)
        assertEquals(15 * MIN, store.day(LocalDate.of(2026, 11, 9)).focusMs)
        // 완료 횟수는 세션이 끝난 날짜에 귀속
        assertEquals(0, store.day(LocalDate.of(2026, 11, 8)).completedFocusSessions)
        assertEquals(1, store.day(LocalDate.of(2026, 11, 9)).completedFocusSessions)
    }

    @Test fun wallClockChangeDuringSameBootDoesNotAffectRemaining() = runTest {
        c.start(); time.advance(5 * MIN)
        time.wall += 3 * 60 * MIN // 사용자가 시계를 3시간 앞으로
        assertEquals(20 * MIN, remaining())
        time.wall -= 6 * 60 * MIN // 뒤로
        assertEquals(20 * MIN, remaining())
    }

    @Test fun rebootFallsBackToWallClockAndCapsAtSessionEnd() = runTest {
        c.start(); time.advance(5 * MIN)
        time.reboot(downMs = 3 * MIN)
        assertEquals(17 * MIN, remaining())
        time.reboot(downMs = 60 * MIN)
        assertNotNull(controller().refresh())
        assertEquals(25 * MIN, store.totalFocus())
        assertEquals(1, store.totalCompleted())
    }

    @Test fun rebootWithWallClockMovedBackwardsNeverGoesNegative() = runTest {
        c.start(); time.advance(5 * MIN)
        time.reboot(downMs = 0); time.wall -= 60 * MIN
        assertEquals(25 * MIN, remaining())
        c.pause()
        assertEquals(0L, store.totalFocus())
    }

    @Test fun durationChangeAppliesOnlyToIdleSessions() = runTest {
        c.refresh()
        durations = Durations(50, 10); c.applyDurations()
        assertEquals(50 * MIN, snap().durationMs)
        c.start(); time.advance(MIN)
        durations = Durations(30, 10); c.applyDurations()
        assertEquals(50 * MIN, snap().durationMs) // 진행 중 세션은 유지
        c.pause(); c.applyDurations()
        assertEquals(50 * MIN, snap().durationMs) // 일시정지 세션도 유지
        c.reset()
        assertEquals(30 * MIN, snap().durationMs) // 초기화 후 새 길이
    }

    @Test fun schedulerReceivesRemainingTime() = runTest {
        c.start(); time.advance(MIN)
        c.refresh()
        assertEquals(24 * MIN, scheduler.lastRemaining)
        c.pause()
        assertEquals(TimerStatus.PAUSED, scheduler.lastSnapshot?.status)
    }

    @Test fun last7DaysFillsMissingDaysAndExcludesOlder() = runTest {
        time.wall = wallAt(2026, 11, 1, 9, 0)
        c.start(); time.advance(25 * MIN); c.refresh() // 8일 전(11/1)은 제외
        time.wall = wallAt(2026, 11, 3, 9, 0)
        c.start(); time.advance(5 * MIN); c.refresh() // 휴식
        c.start(); time.advance(25 * MIN); c.refresh()
        time.wall = wallAt(2026, 11, 8, 9, 0)
        c.start(); time.advance(5 * MIN); c.refresh() // 휴식 완료 → 집중
        c.start(); time.advance(5 * MIN); c.pause() // 오늘 집중 5분(미완료)
        val from = day.minusDays(6)
        val list = TimerMath.last7Days(store.observeDaily(from).first(), day)
        assertEquals(7, list.size)
        assertEquals(from, list.first().date)
        assertEquals(day, list.last().date)
        assertEquals(25 * MIN, list.first { it.date == LocalDate.of(2026, 11, 3) }.focusMs)
        assertEquals(1, list.sumOf { it.completedFocusSessions })
        assertEquals(30 * MIN, list.sumOf { it.focusMs })
    }
}
