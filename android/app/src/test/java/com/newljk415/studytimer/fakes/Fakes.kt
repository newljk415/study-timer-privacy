package com.newljk415.studytimer.fakes

import com.newljk415.studytimer.domain.CompletionScheduler
import com.newljk415.studytimer.domain.DailyStat
import com.newljk415.studytimer.domain.TimeSource
import com.newljk415.studytimer.domain.TimerSnapshot
import com.newljk415.studytimer.domain.TimerStore
import com.newljk415.studytimer.domain.TimerTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.time.LocalDate
import java.time.ZoneId

class FakeTime(
    var wall: Long,
    var elapsed: Long = 1_000_000L,
    var boot: String = "b1",
    var zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) : TimeSource {
    override fun wallMillis() = wall
    override fun elapsedMillis() = elapsed
    override fun bootId() = boot
    override fun zone() = zoneId

    /** 실제 시간 흐름: 벽시계와 단조 시계가 함께 진행 */
    fun advance(ms: Long) { wall += ms; elapsed += ms }

    /** 재부팅: 단조 시계 초기화, 부팅 ID 변경, 그동안 벽시계는 [downMs]만큼 흐름 */
    fun reboot(downMs: Long) { wall += downMs; elapsed = 5_000L; boot = boot + "'" }
}

/** 영속 저장소 대용(프로세스 재생성 시에도 같은 인스턴스를 재사용). 트랜잭션은 Mutex로 직렬화하고 실패 시 롤백. */
class InMemoryStore : TimerStore {
    private val mutex = Mutex()
    val state = MutableStateFlow<TimerSnapshot?>(null)
    val daily = MutableStateFlow<Map<LocalDate, DailyStat>>(emptyMap())

    override suspend fun <T> transaction(block: suspend TimerTransaction.() -> T): T = mutex.withLock {
        var s = state.value
        var d = daily.value
        val tx = object : TimerTransaction {
            override suspend fun snapshot() = s
            override suspend fun save(snapshot: TimerSnapshot) { s = snapshot; yield() }
            override suspend fun addFocus(date: LocalDate, ms: Long) {
                val cur = d[date] ?: DailyStat(date, 0, 0)
                d = d + (date to cur.copy(focusMs = cur.focusMs + ms))
            }
            override suspend fun addCompletedFocus(date: LocalDate) {
                val cur = d[date] ?: DailyStat(date, 0, 0)
                d = d + (date to cur.copy(completedFocusSessions = cur.completedFocusSessions + 1))
            }
        }
        val r = tx.block()
        state.value = s; daily.value = d // commit
        r
    }

    override fun observeSnapshot(): Flow<TimerSnapshot?> = state
    override fun observeDaily(from: LocalDate): Flow<List<DailyStat>> =
        daily.map { m -> m.values.filter { it.date >= from }.sortedBy { it.date } }

    fun day(date: LocalDate) = daily.value[date] ?: DailyStat(date, 0, 0)
    fun totalFocus() = daily.value.values.sumOf { it.focusMs }
    fun totalCompleted() = daily.value.values.sumOf { it.completedFocusSessions }
}

class FakeScheduler : CompletionScheduler {
    var lastRemaining: Long? = null
    var lastSnapshot: TimerSnapshot? = null
    override fun sync(snapshot: TimerSnapshot, remainingMs: Long) { lastSnapshot = snapshot; lastRemaining = remainingMs }
}
