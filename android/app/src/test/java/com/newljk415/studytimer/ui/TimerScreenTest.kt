package com.newljk415.studytimer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.newljk415.studytimer.domain.TimerController
import com.newljk415.studytimer.domain.TimerMath
import com.newljk415.studytimer.domain.Durations
import com.newljk415.studytimer.domain.now
import com.newljk415.studytimer.fakes.FakeTime
import com.newljk415.studytimer.fakes.InMemoryStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 실기기 대신 Robolectric으로 기본 화면과 핵심 타이머 흐름(UI ↔ 컨트롤러)을 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerScreenTest {
    @get:Rule val rule = createComposeRule()

    private val time = FakeTime(wall = 1_800_000_000_000L)
    private val store = InMemoryStore()
    private val controller = TimerController(store, time, { Durations.DEFAULT })

    @Test fun startPauseResumeResetFlow() {
        runBlocking { controller.refresh() }
        var ui by mutableStateOf(TimerUi())
        fun sync() { val s = store.state.value!!; ui = TimerUi(s, TimerMath.remaining(s, time.now()), 0) }
        sync()
        rule.setContent {
            TimerScreen(ui,
                onStart = { runBlocking { controller.start() }; sync() },
                onPause = { runBlocking { controller.pause() }; sync() },
                onReset = { runBlocking { controller.reset() }; sync() })
        }
        rule.onNodeWithText("집중 세션").assertIsDisplayed()
        rule.onNodeWithContentDescription("남은 시간 25분 0초").assertIsDisplayed()
        rule.onNodeWithText("초기화").assertIsNotEnabled()

        rule.onNodeWithText("집중 시작").performClick()
        rule.onNodeWithText("진행 중").assertIsDisplayed()
        time.advance(90_000); sync()
        rule.onNodeWithContentDescription("남은 시간 23분 30초").assertIsDisplayed()

        rule.onNodeWithText("일시정지").performClick()
        rule.onNodeWithText("일시정지됨").assertIsDisplayed()
        time.advance(600_000); sync()
        rule.onNodeWithContentDescription("남은 시간 23분 30초").assertIsDisplayed()
        rule.onNodeWithText("재개").performClick()
        rule.onNodeWithText("진행 중").assertIsDisplayed()

        rule.onNodeWithText("초기화").performClick()
        rule.onNodeWithText("타이머를 초기화할까요?").assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
        rule.onNodeWithText("진행 중").assertIsDisplayed()
        rule.onNodeWithText("초기화").performClick()
        rule.onAllNodes(androidx.compose.ui.test.hasText("초기화") and androidx.compose.ui.test.hasClickAction())[1].performClick()
        rule.onNodeWithText("대기 중").assertIsDisplayed()
        rule.onNodeWithContentDescription("남은 시간 25분 0초").assertIsDisplayed()
        assertEquals(90_000L, store.totalFocus())
    }

    @Test fun completedFocusShowsManualBreakPrompt() {
        runBlocking { controller.start() }
        time.advance(25 * 60_000L)
        runBlocking { controller.refresh() }
        val s = store.state.value!!
        rule.setContent { TimerScreen(TimerUi(s, TimerMath.remaining(s, time.now()), 0), {}, {}, {}) }
        rule.onNodeWithText("휴식 세션").assertIsDisplayed()
        rule.onNodeWithText("휴식 시작").assertIsDisplayed()
        rule.onNodeWithText("대기 중").assertIsDisplayed()
        rule.onNodeWithContentDescription("남은 시간 5분 0초").assertIsDisplayed()
    }
}
