package com.newljk415.studytimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.newljk415.studytimer.AppContainer
import com.newljk415.studytimer.data.AppSettings
import com.newljk415.studytimer.domain.CompletionEvent
import com.newljk415.studytimer.domain.DailyStat
import com.newljk415.studytimer.domain.TimerMath
import com.newljk415.studytimer.domain.TimerSnapshot
import com.newljk415.studytimer.domain.TimerStatus
import com.newljk415.studytimer.domain.now
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TimerUi(val snapshot: TimerSnapshot? = null, val remainingMs: Long = 0, val elapsedMs: Long = 0)

class MainViewModel(private val c: AppContainer) : ViewModel() {
    private val ticker = flow { while (true) { emit(Unit); delay(250) } }
    private val completions = Channel<CompletionEvent>(Channel.BUFFERED)
    val completionEvents = completions.receiveAsFlow()
    @Volatile private var settling = false

    /** 화면 표시는 매초 감소가 아니라 저장된 상태와 현재 시각으로 계산한다. */
    val timer: StateFlow<TimerUi> = combine(c.controller.snapshots.filterNotNull(), ticker) { s, _ ->
        val now = c.time.now()
        if (TimerMath.isFinished(s, now)) settle()
        TimerUi(s, TimerMath.remaining(s, now), TimerMath.elapsed(s, now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUi())

    val settings: StateFlow<AppSettings?> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val last7Days: StateFlow<List<DailyStat>> = c.store
        .observeDaily(LocalDate.now(c.time.zone()).minusDays(6))
        .map { TimerMath.last7Days(it, LocalDate.now(c.time.zone())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { act { c.controller.refresh() } }

    private fun settle() {
        if (settling) return
        settling = true
        act({ settling = false }) { c.controller.refresh() }
    }

    private fun act(after: () -> Unit = {}, block: suspend () -> CompletionEvent?) {
        viewModelScope.launch {
            try { block()?.let { completions.send(it) } } finally { after() }
        }
    }

    fun startOrResume() = act { c.controller.start() }
    fun pause() = act { c.controller.pause() }
    fun reset() = act { c.controller.reset() }
    fun onResume() = act { c.controller.refresh() }

    /** 종료: 실행 중 세션을 일시정지(기록 반영)한 뒤 화면을 닫는다. */
    fun exit(onDone: () -> Unit) = viewModelScope.launch {
        c.controller.pauseForExit()?.let { completions.send(it) }
        onDone()
    }

    fun setDurations(focus: Int, brk: Int, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = c.settings.setDurations(focus, brk)
        if (ok) c.controller.applyDurations()?.let { completions.send(it) }
        onResult(ok)
    }

    fun update(block: suspend com.newljk415.studytimer.data.SettingsRepository.() -> Unit) =
        viewModelScope.launch { c.settings.block() }

    val isRunning get() = timer.value.snapshot?.status == TimerStatus.RUNNING

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(c) as T
    }
}
