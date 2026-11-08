package com.newljk415.studytimer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import com.newljk415.studytimer.domain.DailyStat
import com.newljk415.studytimer.domain.SessionType
import com.newljk415.studytimer.domain.TimerSnapshot
import com.newljk415.studytimer.domain.TimerStatus
import com.newljk415.studytimer.domain.TimerStore
import com.newljk415.studytimer.domain.TimerTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Entity(tableName = "timer_state")
data class TimerStateEntity(
    @PrimaryKey val id: Int = 0,
    val sessionId: String,
    val type: String,
    val durationMs: Long,
    val status: String,
    val accumulatedMs: Long,
    val segmentStartWall: Long,
    val segmentStartElapsed: Long,
    val segmentBootId: String,
    val lastCompletedSessionId: String?,
    val lastCompletedType: String?,
)

@Entity(tableName = "daily_record")
data class DailyRecordEntity(
    @PrimaryKey val date: String, // ISO-8601 지역 날짜
    val focusMs: Long,
    val completedFocusSessions: Int,
)

@Dao
interface TimerDao {
    @Query("SELECT * FROM timer_state WHERE id = 0")
    suspend fun state(): TimerStateEntity?

    @Query("SELECT * FROM timer_state WHERE id = 0")
    fun observeState(): Flow<TimerStateEntity?>

    @Upsert
    suspend fun saveState(e: TimerStateEntity)

    @Query("INSERT OR IGNORE INTO daily_record(date, focusMs, completedFocusSessions) VALUES(:date, 0, 0)")
    suspend fun ensureDay(date: String)

    @Query("UPDATE daily_record SET focusMs = focusMs + :ms WHERE date = :date")
    suspend fun addFocus(date: String, ms: Long)

    @Query("UPDATE daily_record SET completedFocusSessions = completedFocusSessions + 1 WHERE date = :date")
    suspend fun addCompleted(date: String)

    @Query("SELECT * FROM daily_record WHERE date >= :from ORDER BY date")
    fun observeFrom(from: String): Flow<List<DailyRecordEntity>>
}

@Database(entities = [TimerStateEntity::class, DailyRecordEntity::class], version = 1, exportSchema = true)
abstract class TimerDatabase : RoomDatabase() {
    abstract fun dao(): TimerDao

    companion object {
        fun create(context: Context): TimerDatabase =
            Room.databaseBuilder(context, TimerDatabase::class.java, "study_timer.db").build()
    }
}

private fun TimerStateEntity.toDomain() = TimerSnapshot(
    sessionId, SessionType.valueOf(type), durationMs, TimerStatus.valueOf(status), accumulatedMs,
    segmentStartWall, segmentStartElapsed, segmentBootId, lastCompletedSessionId,
    lastCompletedType?.let { SessionType.valueOf(it) },
)

private fun TimerSnapshot.toEntity() = TimerStateEntity(
    0, sessionId, type.name, durationMs, status.name, accumulatedMs, segmentStartWall,
    segmentStartElapsed, segmentBootId, lastCompletedSessionId, lastCompletedType?.name,
)

/** Room 트랜잭션으로 상태와 기록을 원자적으로 갱신한다. */
class RoomTimerStore(private val db: TimerDatabase) : TimerStore {
    private val dao = db.dao()

    private val txImpl = object : TimerTransaction {
        override suspend fun snapshot() = dao.state()?.toDomain()
        override suspend fun save(snapshot: TimerSnapshot) = dao.saveState(snapshot.toEntity())
        override suspend fun addFocus(date: LocalDate, ms: Long) {
            if (ms <= 0) return
            dao.ensureDay(date.toString()); dao.addFocus(date.toString(), ms)
        }
        override suspend fun addCompletedFocus(date: LocalDate) {
            dao.ensureDay(date.toString()); dao.addCompleted(date.toString())
        }
    }

    override suspend fun <T> transaction(block: suspend TimerTransaction.() -> T): T =
        db.withTransaction { txImpl.block() }

    override fun observeSnapshot(): Flow<TimerSnapshot?> = dao.observeState().map { it?.toDomain() }

    override fun observeDaily(from: LocalDate): Flow<List<DailyStat>> =
        dao.observeFrom(from.toString()).map { list ->
            list.map { DailyStat(LocalDate.parse(it.date), it.focusMs, it.completedFocusSessions) }
        }
}
