package com.newljk415.studytimer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimerMathTest {
    @Test fun splitAcrossTwoMidnights() {
        val z = ZoneId.of("Asia/Seoul")
        val start = ZonedDateTime.of(2026, 1, 1, 23, 0, 0, 0, z).toInstant().toEpochMilli()
        val parts = TimerMath.splitByLocalDate(start, 26 * 3_600_000L, z)
        assertEquals(listOf(
            LocalDate.of(2026, 1, 1) to 3_600_000L,
            LocalDate.of(2026, 1, 2) to 24 * 3_600_000L,
            LocalDate.of(2026, 1, 3) to 3_600_000L,
        ), parts)
    }

    @Test fun splitHandlesDstShortDay() {
        val z = ZoneId.of("America/New_York") // 2026-03-08 은 23시간
        val start = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, z).toInstant().toEpochMilli()
        val parts = TimerMath.splitByLocalDate(start, 24 * 3_600_000L, z)
        assertEquals(23 * 3_600_000L, parts[0].second)
        assertEquals(3_600_000L, parts[1].second)
    }

    @Test fun zeroDurationProducesNothing() {
        assertEquals(emptyList<Pair<LocalDate, Long>>(), TimerMath.splitByLocalDate(0, 0, ZoneId.of("UTC")))
    }
}
